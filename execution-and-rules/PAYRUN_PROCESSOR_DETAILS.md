# PayrunProcessor: Heart of Execution — Full Details

This document describes **PayrunProcessor** and all related types in the payroll engine backend. It is the component that runs payrun jobs: loads regulation, compiles and executes scripts, computes wage types and collectors, and persists results.

**Location**: `payroll-engine-backend/Domain/Domain.Application/`

---

## 1. Overview

| Item | Description |
|------|-------------|
| **Class** | `PayrollEngine.Domain.Application.PayrunProcessor` |
| **Base** | `FunctionToolBase` (provides `Settings`, `FunctionHost`) |
| **Purpose** | Process a single payrun job: one payrun + period/cycle + (optionally) a set of employees. |
| **Entry point** | `Task<PayrunJob> Process(PayrunJobInvocation jobInvocation)` |
| **Invoked by** | `PayrunJobWorkerService` (`Api/Api.Core/PayrunJobWorkerService.cs`), which dequeues jobs and calls `processor.Process(queueItem.JobInvocation)`. |

**Flow in one sentence**: Worker dequeues a job → builds `PayrunProcessorSettings` → creates `PayrunProcessor(tenant, payrun, settings)` → `Process(jobInvocation)` builds context, loads derived wage types/collectors, runs PayrunStart → for each employee runs EmployeeStart → **CalculateEmployeeAsync** (collector start → wage type loop with optional restart → collector apply → collector end) → stores results → EmployeeEnd → PayrunEnd → updates job.

---

## 2. Who Calls PayrunProcessor

**PayrunJobWorkerService** (`payroll-engine-backend/Api/Api.Core/PayrunJobWorkerService.cs`):

- Inherits `BackgroundService`; runs a loop that calls `Queue.DequeueAsync(stoppingToken)`.
- For each dequeued `PayrunJobQueueItem`:
  1. Creates a new DI scope.
  2. Resolves `IDbContext`, `IConfiguration`, `IScriptProvider`, `IWebhookDispatchService`, and all repositories needed for **PayrunProcessorSettings**.
  3. Calls `BuildProcessorSettings(...)` to build **PayrunProcessorSettings** (repositories, `ScriptProvider`, `FunctionLogTimeout`, `AssemblyCacheTimeout`, etc.).
  4. Instantiates `new PayrunProcessor(queueItem.Tenant, queueItem.Payrun, processorSettings)`.
  5. Calls `await processor.Process(queueItem.JobInvocation)`.
  6. Marks job completed and sends job-completion webhook.

So the **only** call path into `PayrunProcessor.Process` is: **PayrunJobWorkerService** → **PayrunProcessor.Process(PayrunJobInvocation)**.

---

## 3. PayrunProcessor Constructor and Fields

**Constructor**:
```csharp
public PayrunProcessor(Tenant tenant, Payrun payrun, PayrunProcessorSettings settings) : base(settings)
```

**Stored**:
- `Tenant`, `Payrun` (immutable for the job).
- `Settings` (as `PayrunProcessorSettings`): repositories, script provider, timeouts, etc. (see §5).
- `ResultProvider`: `new ResultProvider(Settings.PayrollResultRepository, Settings.PayrollConsolidatedResultRepository)` — used to resolve existing results when needed.
- `LogWatch`: `Settings.FunctionLogTimeout != TimeSpan.Zero` — enables per-wage-type/per-step performance logging when a timeout is configured.

**Base** (`FunctionToolBase`): Holds `FunctionToolSettings Settings` and `FunctionHost FunctionHost`. Script execution goes through `FunctionHost` (Domain.Scripting), which uses `Settings` (e.g. `ScriptProvider`, `DbContext`, `AssemblyCacheTimeout`).

---

## 4. Process(jobInvocation) — Main Flow

`Process(PayrunJobInvocation jobInvocation)` overloads to `Process(jobInvocation, new PayrunSetup())`. The internal method does the following in order.

### 4.1 Log level and repositories

- Sets `FunctionHost.LogLevel = jobInvocation.LogLevel`.
- Creates `PayrunProcessorRepositories(Settings, Tenant)` for loading payroll, division, user, payrun, payrun job, and derived regulations.

### 4.2 Load payrun and payroll

- Loads **Payrun** by `jobInvocation.PayrunId`; gets `payrollId = payrun.PayrollId`.
- If caller provided `setup.Payroll`, uses it; otherwise loads **Payroll** by `payrollId`.
- Loads **User** by `jobInvocation.UserId`, **Division** by `context.Payroll.DivisionId`.

### 4.3 PayrunContext (initial)

Builds **PayrunContext** with:

- User, Payroll, Division.
- **RetroDate**: from `GetRetroDateAsync(jobInvocation)` (previous job’s evaluation date when retro pay is enabled).
- **StoreEmptyResults**: from `jobInvocation.StoreEmptyResults`.
- **Culture**: division > tenant > system; pushed onto context’s culture stack.
- **CalendarName**: division calendar or tenant calendar.
- **Calculator**: from `GetCalculatorAsync(tenantId, userId, culture, calendarName)` (cached per calendar name).

### 4.4 Payrun job creation or update

- If `jobInvocation.PayrunJobId > 0`: load existing **PayrunJob**, then **PayrunJobFactory.UpdatePayrunJob** with period/cycle from calculator.
- Else: **PayrunJobFactory.CreatePayrunJob** from `jobInvocation`, division, payroll, calculator.
- If job has **ParentJobId**, loads parent job and sets `context.RetroPayrunJobs = jobInvocation.RetroJobs`.
- Sets **EvaluationDate** and **EvaluationPeriod** from the payrun job.

### 4.5 Regulation and case/lookup setup

- **DerivedRegulations**: loaded via `LoadDerivedRegulationsAsync(payrollId, regulationDate, evaluationDate)`.
- **CaseFieldProvider**: built from `CaseFieldProxyRepository` (payroll repository, tenant, payroll, period end, evaluation date, optional cluster set for case field).
- **GlobalCaseValues**, **NationalCaseValues**, **CompanyCaseValues**: either from `setup` or new **CaseValueCache** instances (tenant/division, evaluation date, forecast).
- **PayrunProcessorRegulation**: `new PayrunProcessorRegulation(FunctionHost, Settings, ResultProvider, Tenant, Payroll, Payrun)` — used to get derived wage types/collectors and to run wage-type and collector scripts.
- **RegulationLookupProvider**: built from payroll repository, regulation repository, lookup set repository, payroll query (tenant, payroll, regulation date, evaluation date).
- **DerivedCollectors**: `GetDerivedCollectors(...)` → `processorRegulation.GetDerivedCollectorsAsync(payrunJob, clusterSetCollector)` (cluster set can be overridden for retro: `ClusterSetCollectorRetro`).
- **DerivedWageTypes**: `GetDerivedWageTypes(...)` → `processorRegulation.GetDerivedWageTypesAsync(payrunJob, clusterSetWageType)`. If none, job is **aborted** (“No wage types available”).

### 4.6 Employees

- Employees: from `setup.Employees` or **SetupEmployeesAsync(context, jobInvocation.EmployeeIdentifiers)**.
  - If `employeeIdentifiers` is set: resolve each identifier in the division; throw if not exactly one employee per identifier.
  - Else: all active employees in the division; optionally filtered by **Payrun.EmployeeAvailableExpression** (script) via **FilterAvailableEmployees**.
- If no employees, job is **aborted**. Otherwise, each employee is added to `context.PayrunJob.Employees`.

### 4.7 Job start and persistence

- **JobStart** = now; **Message** set; job inserted or updated (insert if new, update if pre-created).
- **PayrollValidator.ValidateRegulations**: ensures regulations exist and shared regulations are allowed for this tenant/division. On validation error, job is **aborted**.

### 4.8 Process all employees

- **ProcessAllEmployeesAsync(setup, context, processorRegulation, processorRepositories, employees)**:
  - Validates payroll again for the period.
  - Recomputes **DerivedWageTypes** and **DerivedCollectors** (with retro cluster sets if applicable).
  - Creates **PayrunProcessorScripts** (FunctionHost, Settings, context as regulation provider, ResultProvider, Tenant, Payrun).
  - **PayrunStart(context)**: runs payrun start script (if `Payrun.StartExpression` is set). If it returns false, job is **aborted**.
  - Updates job with **TotalEmployeeCount**; persists.
  - For each employee: **ProcessEmployeeAsync(...)**. After each employee, increments **ProcessedEmployeeCount** and updates the job.
  - If any employee errors are collected in **context.Errors**, job is **aborted** with combined error messages.
  - **PayrunEnd(context)**: runs payrun end script.
- Returns the updated **PayrunJob** (or aborted job).

---

## 5. PayrunProcessorSettings

**File**: `Domain.Application/PayrunProcessorSettings.cs`  
**Base**: `FunctionToolSettings` → `FunctionHostSettings`.

**FunctionHostSettings** (Domain.Scripting): `DbContext`, `TaskRepository`, `LogRepository`, `AssemblyCacheTimeout`, `ScriptProvider`.

**PayrunProcessorSettings** adds:

- **Repositories**: User, Division, Employee, GlobalCaseValue, NationalCaseValue, CompanyCaseValue, EmployeeCaseValue, Payrun, PayrunJob, RegulationLookupSet (ILookupSetRepository), Regulation, RegulationShare, Payroll, PayrollResult, PayrollConsolidatedResult, PayrollResultSet.
- **FunctionLogTimeout**: `TimeSpan`; when non-zero, enables detailed timing logs (e.g. per wage type).
- **Services**: CalendarRepository, PayrollCalculatorProvider, WebhookDispatchService.

**PayrunJobWorkerService.BuildProcessorSettings** builds this from the DI scope (all repositories + server config: `FunctionLogTimeout`, `AssemblyCacheTimeout`, `ScriptProvider`).

---

## 6. PayrunProcessorRepositories

**File**: `Domain.Application/PayrunProcessorRepositories.cs`  
**Visibility**: `internal`. Used only inside `PayrunProcessor`.

**Methods** (all async):

- **LoadPayrollAsync(payrollId)**  
- **LoadDerivedRegulationsAsync(payrollId, regulationDate, evaluationDate)**  
- **ValidatePayrollAsync(payroll, division, period, evaluationDate)** — returns error string or null; checks regulations and regulation sharing.  
- **LoadDivisionAsync(divisionId)**  
- **LoadUserAsync(userId)**  
- **LoadPayrunJobAsync(payrunJobId)**  
- **LoadPayrunAsync(payrunId)**

All use `Settings.*Repository` and `Settings.DbContext`, `Tenant.Id`.

---

## 7. PayrunContext

**File**: `Domain.Application/PayrunContext.cs`  
**Implements**: `IRegulationProvider` (exposes `DerivedCollectors`, `DerivedWageTypes`).

**Purpose**: Holds all employee-independent and per-employee data used during the payrun (passed through script controllers and regulation logic).

**Main properties**:

- **StoreEmptyResults**, **User**, **Division**, **Payroll**, **PayrunJob**, **ParentPayrunJob**, **RetroPayrunJobs**, **ExecutionPhase**.
- **Calculator**, **CaseFieldProvider**, **EvaluationDate**, **RetroDate**, **EvaluationPeriod**.
- **GlobalCaseValues**, **NationalCaseValues**, **CompanyCaseValues**.
- **DerivedRegulations**, **DerivedCollectors**, **DerivedWageTypes**.
- **RegulationLookupProvider**, **RuntimeValueProvider**.
- **CalendarName**, **PayrollCulture** (culture stack: PushPayrollCulture / PopPayrollCulture).
- **Errors**: `Dictionary<Employee, Exception>`; **GetErrorMessages()** for abort message.

---

## 8. PayrunProcessorScripts

**File**: `Domain.Application/PayrunProcessorScripts.cs`  
**Visibility**: `internal`.

**Responsibility**: Invoke payrun-level and employee-level lifecycle scripts via **PayrunScriptController** (Domain.Scripting.Controller).

**Constructor**: Takes `IFunctionHost`, `PayrunProcessorSettings`, `IRegulationProvider`, `IResultProvider`, `Tenant`, `Payrun`.

**Methods**:

- **PayrunStart(PayrunContext context)**  
  - If `Payrun.StartExpression` is empty, returns true.  
  - Otherwise builds a **CaseValueProvider** without employee case values, builds script context, and calls **PayrunScriptController().Start(...)**.  
  - Returns script result ?? true. Used to abort job if start fails.

- **PayrunEnd(PayrunContext context)**  
  - If `Payrun.EndExpression` is empty, returns.  
  - Otherwise runs **PayrunScriptController().End(...)** (no return used).

- **EmployeeStart(ICaseValueProvider caseValueProvider, PayrunContext context)**  
  - If `Payrun.EmployeeStartExpression` is empty, returns true.  
  - Otherwise runs **PayrunScriptController().EmployeeStart(...)**.  
  - Returns result ?? true. If false, employee is skipped (no results stored).

- **EmployeeEnd(ICaseValueProvider caseValueProvider, PayrunContext context)**  
  - If `Payrun.EmployeeEndExpression` is empty, returns.  
  - Otherwise runs **PayrunScriptController().EmployeeEnd(...)**.

Script context passed to controllers always includes: DbContext, PayrollCulture, Namespace, FunctionHost, Tenant, User, Payroll, Payrun, PayrunJob, ParentPayrunJob, ExecutionPhase, RegulationProvider, ResultProvider, CaseValueProvider (where applicable), RegulationLookupProvider, RuntimeValueProvider, DivisionRepository, EmployeeRepository, CalendarRepository, PayrollCalculatorProvider, WebhookDispatchService.

---

## 9. PayrunProcessorRegulation

**File**: `Domain.Application/PayrunProcessorRegulation.cs`  
**Visibility**: `internal`.

**Responsibility**: Load derived wage types and collectors from the payroll layer; run wage-type and collector scripts (value, result, start, apply, end) via **WageTypeScriptController** and **CollectorScriptController**; produce case-value payrun results.

### 9.1 Regulation data

- **GetDerivedCollectorsAsync(payrunJob, clusterSet)**  
  Calls `PayrollRepository.GetDerivedCollectorsAsync` with tenant, payroll, regulation date, evaluation date, cluster set, `OverrideType.Active`. Returns `ILookup<string, DerivedCollector>` by collector name.

- **GetDerivedWageTypesAsync(payrunJob, clusterSet)**  
  Calls `PayrollRepository.GetDerivedWageTypesAsync` with same kind of query. Returns `ILookup<decimal, DerivedWageType>` by wage type number.

### 9.2 Wage type

- **IsWageTypeAvailable(context, derivedWageType, caseValueProvider)**  
  If `Payrun.WageTypeAvailableExpression` is empty, returns true. Otherwise builds script context and calls **PayrunScriptController().IsWageTypeAvailable(...)**. Returns result ?? true.

- **CalculateWageTypeValue(context, derivedWageType, currentPayrollResult, caseValueProvider, executionCount)**  
  - Builds a **WageTypeResultSet** (wage type id, number, name, value type, culture, period start/end).  
  - Iterates **derived** wage type value expressions (ValueScript). For each, calls **WageTypeScriptController().GetValue(...)**.  
    - If script signals **execution restart** (result.Item3), returns a tuple with restart flag true; processor will reset employee results and re-run the wage type loop.  
  - When a non-null value is returned, applies it to result set and then runs **wage type result** scripts (ResultScript) in **reverse** order (bottom-up).  
  - Returns `(WageTypeResultSet, retroPayrunJobs, disabledCollectors, executionRestart)`.

### 9.3 Collector

- **IsCollectorAvailable(derivedWageType, derivedCollector)**  
  Static. Checks whether the wage type’s collector groups / name allow the collector (e.g. `wageType.CollectorAvailable(collector.Name, collector.CollectorGroups)`).

- **CollectorStart(context, derivedCollector, caseValueProvider, currentPayrollResult, collectorResult)**  
  Iterates derived collector **StartScript** expressions; for each, calls **CollectorScriptController().Start(...)**. Collects **RetroPayrunJob** list; returns it.

- **CollectorApply(context, derivedCollector, caseValueProvider, wageTypeResult, currentPayrollResult, collectorResult)**  
  Iterates derived **ApplyScript** expressions; calls **CollectorScriptController().ApplyValue(wageTypeResult, ...)**. If a value is returned, uses it; otherwise fallback to **wageTypeResult.Value**. Updates the **most derived** collector with **AddValue**; returns `(collector.Result, retroPayrunJobs)`.

- **CollectorEnd(context, derivedCollector, caseValueProvider, currentPayrollResult, collectorResult)**  
  Iterates derived **EndScript** expressions; calls **CollectorScriptController().End(...)**. Returns list of retro payrun jobs.

### 9.4 Case value payrun results

- **GetCaseValuePayrunResultsAsync(payroll, payrunJob, caseValueProvider, culture, expandCaseSlots)**  
  Loads derived cases (if expandCaseSlots) and case fields; for each case field (and slot if applicable), gets case value periods from **caseValueProvider.GetCaseValueSplitPeriodsAsync** and builds **PayrunResult** list (source, name, slot, value type, start, end, value, etc.).

---

## 10. ProcessEmployeeAsync — Per-Employee Flow

**Called from**: `ProcessAllEmployeesAsync`, once per employee.

**Steps**:

1. **Employee case values**  
   Use `setup.EmployeeCaseValues` or new **CaseValueCache** for this employee, division, evaluation date, forecast.

2. **CaseValueProvider**  
   Built from employee + global/national/company/employee case value caches + evaluation period/date, calculator, case field provider, retro date.

3. **EmployeeStart**  
   `processorScripts.EmployeeStart(caseValueProvider, context)`. If false, method returns (no results for this employee).

4. **CalculateEmployeeAsync**  
   With **PayrunExecutionPhase.Setup** (see §11). Returns `(PayrollResultSet payrollResult, List<RetroPayrunJob> payrunRetroJobs, CaseValue retroCaseValue)`.  
   If there are no collector and no wage type results, message is set and method returns.

5. **Retro payrun jobs**  
   If **RetroPayMode** is not None and there are retro jobs or a retro date:
   - Determines **retroDate** (from script retro jobs or retro case value).
   - Applies **RetroTimeType** (e.g. Anytime vs Cycle — cycle caps retro to cycle start).
   - For each period from **retroDate** backward until evaluation period start:
     - Creates a **new PayrunProcessor** and **PayrunJobInvocation** for a **retro** job (same employee, period = retro period, **RetroPayMode.None** to avoid recursion, **JobResult.Incremental**).
     - Calls **retroProcessor.Process(retroJobInvocation, payrunSetup)** with a **PayrunSetup** that reuses payroll, division, case value caches and passes this employee only.
     - **CompleteRetroJobAsync(retroJob)**.
   - If any retro jobs were run, **RuntimeValueProvider.EmployeeValues** is cleared and **CalculateEmployeeAsync** is run again with **PayrunExecutionPhase.Reevaluation** to refresh current period results.

6. **Retro result tags**  
   If context has **RetroPayrunJobs**, adds their **ResultTags** to **payrollResult** for periods within or after each retro job’s schedule date.

7. **Store results**  
   If **StoreEmptyResults** or payroll result is not empty, **PayrollResultSetRepository.CreateAsync** for the current **PayrollResultSet**.

8. **EmployeeEnd**  
   `processorScripts.EmployeeEnd(caseValueProvider, context)`.

On exception, any created retro jobs are cleaned up (delete payrun job, set error message, update job).

---

## 11. CalculateEmployeeAsync — Wage Types and Collectors

**Signature**:  
`Task<Tuple<PayrollResultSet, List<RetroPayrunJob>, CaseValue>> CalculateEmployeeAsync(PayrunProcessorRegulation processorRegulation, Employee employee, ICaseValueProvider caseValueProvider, PayrunContext context, PayrunExecutionPhase executionPhase)`

**Execution phases**: **Setup** (initial run) or **Reevaluation** (after retro jobs).

**Steps**:

1. **Context and culture/calendar**  
   Sets **ExecutionPhase**; pushes employee culture (employee > division > tenant > system) and calendar (employee > division > tenant). Gets **employeeCalculator**, pushes it onto context and caseValueProvider.

2. **PayrollResultSet**  
   New set for this employee/division/cycle/period; **SetupEmployeeCollectors** fills **CollectorResults** from **context.DerivedCollectors** (one result per collector, values reset).

3. **Collector Start**  
   For each **context.DerivedCollectors**, finds the matching **CollectorResultSet**, calls **processorRegulation.CollectorStart(...)**; sets **collectorResult.Value** from derived collector’s result; merges retro jobs.

4. **Wage type loop (with optional restart)**  
   - **executionCount** and **executionRestart** loop: `do { ... } while (executionRestart && executionCount < SystemSpecification.PayrunMaxExecutionCount)` (max 100).  
   - For each **context.DerivedWageTypes**:
     - Optional **wage-type-specific calendar**: if wage type has a calendar different from context, a calculator for that calendar is pushed for the duration of this wage type.
     - **IsWageTypeAvailable**: if false, skip this wage type.
     - **CalculateWageTypeValue**: gets `(wageTypeResult, retroJobs, disabledCollectors, executionRestart)`.  
       - If **executionRestart**, reset employee results (**ResetEmployeeResults** → **SetupEmployeeCollectors**, clear wage type and payrun results), set **executionRestart = true**, break out of wage type loop (and re-run from top of do-while).
     - If **wageTypeResult** is not null: append to **payrollResult.WageTypeResults**; merge retro jobs; then **Collector Apply** for each **context.DerivedCollectors** that is not in **disabledCollectors** and for which **IsCollectorAvailable(derivedWageType, derivedCollector)**. For each, **CollectorApply** and update **collectorResult.Value**; merge retro jobs.

5. **Collector End**  
   For each derived collector, **CollectorEnd**; update **collectorResult.Value**; merge retro jobs.

6. **Payrun results (case values)**  
   **processorRegulation.GetCaseValuePayrunResultsAsync(...)** with **expandCaseSlots: true**; append to **payrollResult.PayrunResults**.

7. **Incremental mode**  
   If **context.PayrunJob.JobResult == PayrunJobResult.Incremental**, **RemoveUnchangedResultsAsync** (compare with consolidated results and remove unchanged collector/wage type results to store only deltas).

8. **Cleanup**  
   Pop employee calculator from caseValueProvider and context; pop culture; set result date on **payrollResult**.

**Return**: `(payrollResult, retroPayrunJobs, caseValueProvider.RetroCaseValue)`.

---

## 12. Supporting Types and Constants

- **PayrunSetup** (private class in PayrunProcessor): Optional pre-loaded **Payroll**, **Division**, **Employees**, **GlobalCaseValues**, **NationalCaseValues**, **CompanyCaseValues**, **EmployeeCaseValues**. Used for retro sub-jobs and when invoking with a pre-built setup.
- **PayrunJobFactory**: Creates or updates **PayrunJob** from invocation, division, payroll, calculator (period/cycle names, dates).
- **ResultProvider**: Wraps **PayrollResultRepository** and **PayrollConsolidatedResultRepository**; used when scripts need to read existing results.
- **SystemSpecification.PayrunMaxExecutionCount**: 100 (payroll-engine-core) — max wage-type execution restarts per employee.
- **RetroTimeType**: Anytime (no limit) vs Cycle (retro date capped to cycle start).
- **PayrunJobResult**: Full vs Incremental (incremental: only store changed results; **RemoveUnchangedResultsAsync** prunes unchanged collector/wage type results).

---

## 13. Job Lifecycle (Abort / Complete)

- **AbortJobAsync(payrunJob, message, error)**  
  Sets **JobStatus = Abort**, **JobEnd = now**, **Message**, **ErrorMessage**; updates job via **PayrunJobRepository.UpdateAsync**. Used when: no wage types, no employees, payrun start fails, validation fails, employee errors, or any exception in **ProcessAllEmployeesAsync**.

- **CompleteRetroJobAsync(payrunJob)**  
  Sets **JobStatus = Complete**, **JobEnd**, **Message** (duration); updates job. Used for each retro child job after **Process** returns.

- **PayrunJobWorkerService**  
  On success, marks job completed with **MarkJobCompletedAsync** and sends webhook. On cancellation or exception, **MarkJobAbortedAsync** with reason.

---

## 14. File Reference

| File | Description |
|------|-------------|
| `Domain.Application/PayrunProcessor.cs` | Main processor; `Process`, `ProcessAllEmployeesAsync`, `ProcessEmployeeAsync`, `CalculateEmployeeAsync`, setup, retro, job/result helpers. |
| `Domain.Application/PayrunProcessorSettings.cs` | Settings (repositories, timeouts, services). |
| `Domain.Application/PayrunProcessorRepositories.cs` | Load payroll, regulations, division, user, payrun, job. |
| `Domain.Application/PayrunProcessorScripts.cs` | PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd. |
| `Domain.Application/PayrunProcessorRegulation.cs` | GetDerivedCollectors/WageTypes; IsWageTypeAvailable; CalculateWageTypeValue; CollectorStart/Apply/End; GetCaseValuePayrunResultsAsync. |
| `Domain.Application/PayrunContext.cs` | Context type (IRegulationProvider). |
| `Domain.Application/FunctionToolBase.cs` | Base with Settings, FunctionHost. |
| `Domain.Application/FunctionToolSettings.cs` | Extends FunctionHostSettings. |
| `Domain.Scripting/FunctionHostSettings.cs` | DbContext, TaskRepository, LogRepository, AssemblyCacheTimeout, ScriptProvider. |
| `Api/Api.Core/PayrunJobWorkerService.cs` | Background worker; dequeues job, builds settings, creates processor, calls Process, marks completed/aborted, webhook. |

Script execution itself is in **Domain.Scripting** (e.g. **PayrunScriptController**, **WageTypeScriptController**, **CollectorScriptController**) and uses **FunctionHost** for compilation and invocation (see **COUNTRY_REGULATION_INGESTION_FLOW.md** and execution-model docs).

---

## 15. Summary Diagram

```
PayrunJobWorkerService (BackgroundService)
  └─ Dequeue PayrunJobQueueItem
  └─ BuildProcessorSettings (repositories, ScriptProvider, timeouts)
  └─ new PayrunProcessor(tenant, payrun, settings)
  └─ processor.Process(jobInvocation)
        ├─ PayrunProcessorRepositories: load Payrun, Payroll, Division, User, regulations
        ├─ PayrunContext: culture, calendar, calculator, case caches, RegulationLookupProvider
        ├─ PayrunJob create/update (PayrunJobFactory)
        ├─ PayrunProcessorRegulation.GetDerivedCollectorsAsync / GetDerivedWageTypesAsync
        ├─ SetupEmployeesAsync or setup.Employees
        ├─ PayrunProcessorScripts.PayrunStart(context)
        └─ ProcessAllEmployeesAsync
              ├─ For each employee:
              │     ├─ CaseValueProvider (employee + case caches)
              │     ├─ PayrunProcessorScripts.EmployeeStart(caseValueProvider, context)
              │     ├─ CalculateEmployeeAsync (Setup phase)
              │     │     ├─ Collector Start (script per collector)
              │     │     ├─ Wage type loop (up to PayrunMaxExecutionCount restarts):
              │     │     │     ├─ IsWageTypeAvailable → skip if false
              │     │     │     ├─ CalculateWageTypeValue (ValueScript + ResultScript)
              │     │     │     ├─ If restart: reset results, re-run loop
              │     │     │     └─ Collector Apply for each collector
              │     │     ├─ Collector End (script per collector)
              │     │     └─ GetCaseValuePayrunResultsAsync
              │     ├─ Retro: create child PayrunProcessor, Process(retroInvocation, setup) per period
              │     ├─ Reevaluate current period if retro jobs ran
              │     ├─ PayrollResultSetRepository.CreateAsync(payrollResult)
              │     └─ PayrunProcessorScripts.EmployeeEnd(caseValueProvider, context)
              ├─ PayrunProcessorScripts.PayrunEnd(context)
              └─ return context.PayrunJob
  └─ MarkJobCompletedAsync / SendJobCompletionWebhookAsync
```

This is the full detail of **PayrunProcessor** as the heart of payrun execution.
