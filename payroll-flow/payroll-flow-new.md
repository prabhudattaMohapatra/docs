# End-to-End Payrun Flow (from source code)

This document traces the Payrun flow from the initial HTTP trigger through the backend, core engine, regulation scripts (including France), and final database operations. All references are to actual classes and methods in the codebase.

---

## 1. Initial trigger: API entry point

**Route:** `POST /api/tenants/{tenantId}/payruns/jobs`

- **Controller:** `PayrollEngine.Backend.Controller.PayrunJobController` (inherits from `PayrollEngine.Api.Controller.PayrunJobController`).
- **Method:** `StartPayrunJobAsync(int tenantId, ApiObject.PayrunJobInvocation jobInvocation)`.

**Location:** `payroll-engine-backend/Backend.Controller/PayrunJobController.cs` (HTTP attribute), `payroll-engine-backend/Api/Api.Controller/PayrunJobController.cs` (implementation).

### Request validation (Api.Controller.PayrunJobController.StartPayrunJobAsync)

Before processing, the API:

1. Loads **tenant** via `ParentService.GetAsync(Runtime.DbContext, tenantId)`.
2. Validates **user** with `ServiceSettings.UserRepository.ExistsAsync` for `jobInvocation.UserId`.
3. Loads and validates **payrun**: `ServiceSettings.PayrunRepository.GetAsync`; checks `payrun.Status == ObjectStatus.Active`.
4. Resolves **payroll** from `payrun.PayrollId` and checks `ServiceSettings.PayrollRepository.ExistsAsync`.
5. For non-forecast jobs: checks there is no open draft job for the same payroll via `ServiceSettings.PayrunJobRepository.QueryAsync` with filter `JobStatus == PayrunJobStatus.Draft`.

The request body is mapped from `PayrunJobInvocation` (API model) to domain `PayrunJobInvocation` via `PayrunJobInvocationMap.ToDomain`.

---

## 2. PayrunProcessor construction and Process() entry

**Class:** `PayrollEngine.Domain.Application.PayrunProcessor`  
**File:** `payroll-engine-backend/Domain/Domain.Application/PayrunProcessor.cs`

The controller builds a `PayrunProcessor` with:

- `tenant`, `payrun` (from above).
- `PayrunProcessorSettings` containing:
  - `DbContext`, repositories (`UserRepository`, `DivisionRepository`, `EmployeeRepository`, `PayrunJobRepository`, `PayrollRepository`, `RegulationRepository`, `RegulationShareRepository`, `PayrollResultRepository`, `PayrollConsolidatedResultRepository`, `PayrollResultSetRepository`, case value repos, `CalendarRepository`, `RegulationLookupSetRepository`),
  - `PayrollCalculatorProvider` (from API: `DefaultPayrollCalculatorProvider` in `ApiFactory`),
  - `WebhookDispatchService`, `ScriptProvider` (from `Runtime.ScriptProvider`), and config (e.g. `FunctionLogTimeout`, `AssemblyCacheTimeout`).

Then it calls:

```csharp
var payrunJob = await processor.Process(domainJobInvocation);
```

**Public entry:** `PayrunProcessor.Process(PayrunJobInvocation jobInvocation)` → delegates to `Process(jobInvocation, new())`.

---

## 3. Process(): context and regulation setup

Inside `PayrunProcessor.Process(PayrunJobInvocation jobInvocation, PayrunSetup setup)`:

### 3.1 Repository helper

- **Class:** `PayrunProcessorRepositories`  
- **File:** `payroll-engine-backend/Domain/Domain.Application/PayrunProcessorRepositories.cs`

Used to load:

- Payrun: `LoadPayrunAsync(jobInvocation.PayrunId)` → `Settings.PayrollRepository.GetAsync`.
- User: `LoadUserAsync(jobInvocation.UserId)`.
- Payroll: `LoadPayrollAsync(payrollId)`.
- Division: `LoadDivisionAsync(context.Payroll.DivisionId)`.
- **Derived regulations:** `LoadDerivedRegulationsAsync(payrollId, context.PayrunJob.PeriodEnd, context.EvaluationDate)`  
  → `Settings.PayrollRepository.GetDerivedRegulationsAsync(Settings.DbContext, PayrollQuery)`  
  → implemented by `PayrollRepositoryRegulationCommand.GetDerivedRegulationsAsync` (uses `RegulationRepository`, `PayrollLayerRepository`).  
  Result is stored in `context.DerivedRegulations` (list of `Regulation`); each has a **Namespace** used later for regulation-specific scripts (e.g. France).

### 3.2 PayrunContext

A `PayrunContext` is populated with:

- User, Payroll, Division, RetroDate, StoreEmptyResults.
- Culture (division → tenant → system).
- Calendar name (division or tenant).
- **Calculator:** `GetCalculatorAsync(tenantId, userId, culture, calendarName)` → `Settings.PayrollCalculatorProvider.CreateCalculator(...)` (e.g. `DefaultPayrollCalculatorProvider`).
- **Payrun job:** `PayrunJobFactory.CreatePayrunJob(jobInvocation, divisionId, payrollId, context.Calculator)` → sets cycle/period from calculator (`GetPayrunCycle`, `GetPayrunPeriod`).
- Evaluation date and period from the new payrun job.
- **Case field provider:** `CaseFieldProvider` using `CaseFieldProxyRepository(Settings.PayrollRepository, ...)`.
- **Case value caches:** `CaseValueCache` for Global, National, Company (and per-employee later) using `Settings.GlobalCaseValueRepository`, etc., with `evaluationDate` and `forecast` from the job.
- **Regulation lookups:** `RegulationLookupProvider` using `Settings.PayrollRepository`, `RegulationRepository`, `RegulationLookupSetRepository`.

### 3.3 Regulation and payroll metadata

- **PayrunProcessorRegulation** is constructed (holds `FunctionHost`, `Settings`, `ResultProvider`, `Tenant`, `Payroll`, `Payrun`).
- **Derived collectors:** `GetDerivedCollectors` → `processorRegulation.GetDerivedCollectorsAsync(payrunJob, clusterSetCollector)`  
  → `Settings.PayrollRepository.GetDerivedCollectorsAsync(Settings.DbContext, PayrollQuery, ..., clusterSet, OverrideType.Active)`  
  → `PayrollRepositoryCollectorCommand.GetDerivedCollectorsAsync`.
- **Derived wage types:** `GetDerivedWageTypes` → `processorRegulation.GetDerivedWageTypesAsync(payrunJob, clusterSetWageType)`  
  → `Settings.PayrollRepository.GetDerivedWageTypesAsync(..., OverrideType.Active)`  
  → `PayrollRepositoryWageTypeCommand.GetDerivedWageTypesAsync`.

If there are no derived wage types, the job is aborted with a message.

### 3.4 Employees

- **Employees:** `SetupEmployeesAsync(context, jobInvocation.EmployeeIdentifiers)`:
  - If `employeeIdentifiers` is set: for each identifier, `Settings.EmployeeRepository.QueryAsync` with `DivisionQuery` (division, status Active, filter by identifier); exactly one employee per identifier required; division membership checked via `employee.InDivision(context.Division.Name)`.
  - Otherwise: all active employees in the division; optionally filtered by `Payrun.EmployeeAvailableExpression` via `PayrunScriptController.IsEmployeeAvailable` (scripted filter).
- Each selected employee is added to `context.PayrunJob.Employees` as `PayrunJobEmployee` with `EmployeeId`.

### 3.5 Persist payrun job (first DB write)

- `context.PayrunJob.JobStart = Date.Now`, message set.
- **Create payrun job:**  
  `Settings.PayrunJobRepository.CreateAsync(context, parentId: Tenant.Id, item: context.PayrunJob)`  
  → `PayrunJobRepository` (inherits `ChildDomainRepository<PayrunJob>`): inserts into **PayrunJob** table (e.g. `DbSchema.Tables.PayrunJob`); in `OnCreatedAsync` it then calls `JobEmployeeRepository.CreateAsync(context, payrunJob.Id, payrunJob.Employees)` to insert **PayrunJobEmployee** rows.

### 3.6 Validation

- `PayrollValidator(Settings.PayrollRepository).ValidateRegulations(...)` for the payroll and period; on failure the job is aborted.

---

## 4. Process all employees: ProcessAllEmployeesAsync

**Method:** `PayrunProcessor.ProcessAllEmployeesAsync(PayrunSetup setup, PayrunContext context, PayrunProcessorRegulation processorRegulation, PayrunProcessorRepositories processorRepositories, IList<Employee> employees)`.

- **Payroll validation:** `processorRepositories.ValidatePayrollAsync(context.Payroll, context.Division, context.PayrunJob.GetEvaluationPeriod(), context.EvaluationDate)` (checks derived regulations and regulation sharing).
- **Derived wage types and collectors** are reloaded into context (with possible retro cluster set overrides).
- **Payrun start script:**  
  `PayrunProcessorScripts.PayrunStart(context)`  
  → if `Payrun.StartExpression` is set, `PayrunScriptController.Start(...)` is run (case value provider without employee case values).
- **Job update:** `context.PayrunJob.TotalEmployeeCount = employees.Count`, then `UpdateJobAsync(context.PayrunJob)` → `Settings.PayrunJobRepository.UpdateAsync(Settings.DbContext, Tenant.Id, payrunJob)`.

Then for **each employee**:

```csharp
await ProcessEmployeeAsync(processorRegulation, processorScript, employee, context, setup);
context.PayrunJob.ProcessedEmployeeCount++;
await UpdateJobAsync(context.PayrunJob);
```

- **Payrun end script:** `processorScript.PayrunEnd(context)` (if `Payrun.EndExpression` is set).
- **Complete:** `CompleteJobAsync(context.PayrunJob)` sets `JobEnd`, message, and calls `PayrunJobRepository.UpdateAsync`.

---

## 5. Per-employee processing: ProcessEmployeeAsync

**Method:** `PayrunProcessor.ProcessEmployeeAsync(PayrunProcessorRegulation processorRegulation, PayrunProcessorScripts processorScripts, Employee employee, PayrunContext context, PayrunSetup setup)`.

### 5.1 Case values and provider

- **Employee case value cache:** `CaseValueCache(Settings.DbContext, Settings.EmployeeCaseValueRepository, employee.Id, context.Division.Id, context.EvaluationDate, context.PayrunJob.Forecast)`.
- **CaseValueProvider** is built with the employee and the global/national/company/employee case value repositories and evaluation settings.

### 5.2 Employee start script

- `processorScripts.EmployeeStart(caseValueProvider, context)`  
  → if `Payrun.EmployeeStartExpression` is set, `PayrunScriptController.EmployeeStart(...)`.

### 5.3 Calculate employee (core payrun logic)

- **Method:** `CalculateEmployeeAsync(processorRegulation, employee, caseValueProvider, context, PayrunExecutionPhase.Setup)`.
- Returns a tuple: `(PayrollResultSet payrollResult, List<RetroPayrunJob> retroPayrunJobs, CaseValue retroCaseValue)`.

If there are no collector or wage type results, the method returns early with a message.

### 5.4 Retro payrun jobs (optional)

- If retro pay is enabled and there are retro jobs or a retro date, the processor may create **retro payrun jobs** (nested `PayrunProcessor.Process(retroJobInvocation, payrunSetup)` with `RetroPayMode.None` and single employee) and then **reevaluate** the current period with `CalculateEmployeeAsync(..., PayrunExecutionPhase.Reevaluation)`.

### 5.5 Persist results (main DB write for payrun results)

- If `context.StoreEmptyResults` or the payroll result is not empty:  
  **`Settings.PayrollResultSetRepository.CreateAsync(Settings.DbContext, Tenant.Id, payrollResult)`.**

**PayrollResultSetRepository** (`payroll-engine-backend/Persistence/Persistence/PayrollResultSetRepository.cs`):

- Extends `ChildDomainRepository<PayrollResultSet>`; table is `DbSchema.Tables.PayrollResult` (tenant-scoped).
- **Create flow:** base `CreateAsync` inserts the **PayrollResultSet** row (PayrollId, PayrunId, PayrunJobId, EmployeeId, DivisionId, CycleName, CycleStart, CycleEnd, PeriodName, PeriodStart, PeriodEnd, etc.).
- **OnCreatedAsync** then:
  - **Wage type results:** `WageTypeResultSetRepository.CreateAsync` or `CreateBulkAsync(context, resultSet.Id, resultSet.WageTypeResults)`.
  - **Collector results:** `CollectorResultSetRepository.CreateAsync` or `CreateBulkAsync(context, resultSet.Id, resultSet.CollectorResults)`.
  - **Payrun results:** `PayrunResultRepository.CreateAsync` or `CreateBulkAsync(context, resultSet.Id, resultSet.PayrunResults)`.

So one payrun job produces multiple **PayrollResultSet** rows (one per employee), each with child **WageTypeResult**, **CollectorResult**, and **PayrunResult** rows.

### 5.6 Employee end script

- `processorScripts.EmployeeEnd(caseValueProvider, context)` (if `Payrun.EmployeeEndExpression` is set).

---

## 6. CalculateEmployeeAsync: wage types and collectors

**Method:** `PayrunProcessor.CalculateEmployeeAsync(PayrunProcessorRegulation processorRegulation, Employee employee, ICaseValueProvider caseValueProvider, PayrunContext context, PayrunExecutionPhase executionPhase)`.

### 6.1 Employee culture and calculator

- Culture: employee → division → tenant → system.
- Calendar: employee → division → tenant.
- **Employee calculator:** `GetCalculatorAsync(Tenant.Id, context.User.Id, context.PayrollCulture, context.CalendarName)` and pushed onto `caseValueProvider`.

### 6.2 PayrollResultSet and collector slots

- A **PayrollResultSet** is created with PayrollId, PayrunId, PayrunJobId, EmployeeId, DivisionId, cycle/period fields.
- **SetupEmployeeCollectors:** for each derived collector, collector results are reset and one **CollectorResultSet** per collector is added to `payrollResult.CollectorResults`.

### 6.3 Collector start

- For each `derivedCollector` in `context.DerivedCollectors`:  
  **`processorRegulation.CollectorStart(context, derivedCollector, caseValueProvider, payrollResult, collectorResult)`**  
  → runs **collector start scripts** (derived order): `CollectorScriptController.Start(...)` with the collector’s **StartScript** and the regulation **Namespace** from `context.DerivedRegulations` (by `first.RegulationId`).  
  This is where a regulation such as **France** can run: if the wage type/collector belongs to a regulation with a given Namespace (e.g. France), the script/assembly for that namespace is used.

### 6.4 Wage type loop (with possible execution restart)

- Iterates over `context.DerivedWageTypes`.
- For each wage type:
  - Optional wage-type-specific calculator if the wage type has a different calendar.
  - **Availability:** `processorRegulation.IsWageTypeAvailable(context, derivedWageType, caseValueProvider)`  
    → if `Payrun.WageTypeAvailableExpression` is set, `PayrunScriptController.IsWageTypeAvailable(...)` with regulation **Namespace** from the wage type’s regulation.
  - **Value calculation:**  
    **`processorRegulation.CalculateWageTypeValue(context, derivedWageType, payrollResult, caseValueProvider, executionCount)`**  
    → **WageTypeScriptController.GetValue(...)** with the wage type’s **ValueScript** and regulation **Namespace** (from `context.DerivedRegulations` by `wageType.RegulationId`).  
    The controller invokes the script (e.g. C# regulation assembly); for **payroll-engine-regulation-France**, the script is implemented by types in the **FR** namespace (e.g. `FR.Composite.WageTypeValue`, `WageTypeValueFunction` in `Scripts/Composite/WageTypeValue.cs` and `Scripts/Register/WageTypeValue.Register.cs`).
  - If the result signals an execution restart, employee results are reset and the wage type loop restarts (up to `SystemSpecification.PayrunMaxExecutionCount`).
  - If a **WageTypeResultSet** is produced, it is added to `payrollResult.WageTypeResults`.
  - **Collector apply:** for each derived collector (that is available for the wage type),  
    **`processorRegulation.CollectorApply(context, derivedCollector, caseValueProvider, wageTypeResult, payrollResult, collectorResult)`**  
    → **CollectorScriptController.ApplyValue(...)** with the collector’s **ApplyScript** and regulation **Namespace**.
  - **Wage type result script:** **WageTypeScriptController.Result(...)** with **ResultScript** and regulation **Namespace** (reverse derived order).

### 6.5 Collector end

- For each derived collector:  
  **`processorRegulation.CollectorEnd(context, derivedCollector, caseValueProvider, payrollResult, collectorResult)`**  
  → **CollectorScriptController.End(...)** with **EndScript** and regulation **Namespace**.

### 6.6 Case value payrun results

- **`processorRegulation.GetCaseValuePayrunResultsAsync(payroll, payrunJob, caseValueProvider, culture, expandCaseSlots: true)`**  
  → uses `Settings.PayrollRepository.GetDerivedCasesAsync` and `GetDerivedCaseFieldsAsync`; builds **PayrunResult** entries from case values and appends to `payrollResult.PayrunResults`.

### 6.7 Incremental mode and cleanup

- If `context.PayrunJob.JobResult == PayrunJobResult.Incremental`, **RemoveUnchangedResultsAsync** compares with existing results from `PayrollConsolidatedResultRepository.GetCollectorResultsAsync` / `GetWageTypeResultsAsync` and removes unchanged collector/wage type results.
- Result date is set; calculator and culture are restored; the method returns `(payrollResult, retroPayrunJobs, retroCaseValue)`.

---

## 7. How the core engine calls France (and other regulations)

- **Regulations** are loaded per payroll and period via **PayrollRepository.GetDerivedRegulationsAsync** (PayrollRepositoryRegulationCommand). Each **Regulation** has an id and a **Namespace**.
- **Wage types and collectors** are loaded from the same payroll (PayrollRepositoryWageTypeCommand, PayrollRepositoryCollectorCommand); each **DerivedWageType** / **DerivedCollector** has a **RegulationId** and script fields (**ValueScript**, **ResultScript**, **StartScript**, **ApplyScript**, **EndScript**).
- When running a script, **PayrunProcessorRegulation** and the script controllers (**WageTypeScriptController**, **CollectorScriptController**, **PayrunScriptController**) receive a **Namespace** from `context.DerivedRegulations.FirstOrDefault(x => x.Id == wageType.RegulationId)?.Namespace` (or the collector’s regulation). The **FunctionHost** / script runtime uses this namespace to resolve and execute the regulation implementation (e.g. compiled C# from **payroll-engine-regulation-France**).
- **payroll-engine-regulation-France** provides:
  - **Scripts/Composite/** (e.g. `WageTypeValue.cs`, `CollectorStart.cs`, `CollectorApply.cs`, `CollectorEnd.cs`, `PayrunStart.cs`, `PayrunEnd.cs`, employee start/end, case build/validate, etc.).
  - **Scripts/Register/** (e.g. `WageTypeValue.Register.cs`, `CollectorStart.Register.cs`) that wire the function base class to the **FR** (France) composite types and expose helpers like `company`, `employee`, `MyRegulation`.
- So the “call into France” is: same **PayrunProcessor** and **PayrunProcessorRegulation** flow; the **Namespace** for a France regulation causes the script host to run the France assembly’s WageTypeValue / CollectorStart / CollectorApply / CollectorEnd (and other) implementations.

---

## 8. Data read from the database (summary)

- **Tenant:** `ITenantRepository.GetAsync`.
- **User:** `IUserRepository.GetAsync` / `ExistsAsync`.
- **Payrun:** `IPayrunRepository.GetAsync` / `ExistsAsync`.
- **Payroll:** `IPayrollRepository.GetAsync` / `ExistsAsync`; **GetDerivedRegulationsAsync**, **GetDerivedWageTypesAsync**, **GetDerivedCollectorsAsync**, **GetDerivedCasesAsync**, **GetDerivedCaseFieldsAsync** (via PayrollRepository*Command classes).
- **Division:** `IDivisionRepository.GetAsync`.
- **Employees:** `IEmployeeRepository.QueryAsync` with `DivisionQuery`.
- **Case values:** `IGlobalCaseValueRepository`, `INationalCaseValueRepository`, `ICompanyCaseValueRepository`, `IEmployeeCaseValueRepository` (wrapped in `CaseValueCache`).
- **Calendar:** `ICalendarRepository.GetByNameAsync`.
- **Regulation sharing:** `IRegulationShareRepository.GetAsync` (during validation).
- **Existing payrun jobs:** `IPayrunJobRepository.QueryAsync` (draft check; retro date).
- **Incremental comparison:** `IPayrollConsolidatedResultRepository.GetCollectorResultsAsync`, `GetWageTypeResultsAsync`.

---

## 9. Data written to the database (summary)

- **PayrunJob:** insert via `PayrunJobRepository.CreateAsync` (table `PayrunJob`); columns include Name, Owner, PayrunId, PayrollId, DivisionId, CreatedUserId, ParentJobId, Tags, Forecast, RetroPayMode, JobResult, CycleName, CycleStart, CycleEnd, PeriodName, PeriodStart, PeriodEnd, EvaluationDate, CreatedReason, JobStart, Message, TotalEmployeeCount, etc. Updates via `UpdateAsync` (JobStart, Message, ProcessedEmployeeCount, JobEnd, JobStatus, etc.).
- **PayrunJobEmployee:** insert in `PayrunJobRepository.OnCreatedAsync` via `JobEmployeeRepository.CreateAsync`.
- **PayrollResultSet (PayrollResult table):** insert via `PayrollResultSetRepository.CreateAsync`; columns include PayrollId, PayrunId, PayrunJobId, EmployeeId, DivisionId, CycleName, CycleStart, CycleEnd, PeriodName, PeriodStart, PeriodEnd.
- **WageTypeResult:** insert via `WageTypeResultSetRepository.CreateAsync` or `CreateBulkAsync` (child of PayrollResultSet).
- **CollectorResult:** insert via `CollectorResultSetRepository.CreateAsync` or `CreateBulkAsync` (child of PayrollResultSet).
- **PayrunResult:** insert via `PayrunResultRepository.CreateAsync` or `CreateBulkAsync` (child of PayrollResultSet).

---

## 10. Key types and files (quick reference)

| Concern | Class / type | File (payroll-engine-backend unless noted) |
|--------|----------------|-------------------------------------------|
| API entry | `PayrunJobController.StartPayrunJobAsync` | `Backend.Controller/PayrunJobController.cs`, `Api/Api.Controller/PayrunJobController.cs` |
| Invocation model | `PayrunJobInvocation` | `Api/Api.Model/PayrunJobInvocation.cs` |
| Processor | `PayrunProcessor.Process` | `Domain/Domain.Application/PayrunProcessor.cs` |
| Context/repos | `PayrunContext`, `PayrunProcessorRepositories` | `Domain/Domain.Application/PayrunProcessorRepositories.cs` |
| Regulation (wage/collector) | `PayrunProcessorRegulation` | `Domain/Domain.Application/PayrunProcessorRegulation.cs` |
| Scripts (payrun/employee) | `PayrunProcessorScripts` | `Domain/Domain.Application/PayrunProcessorScripts.cs` |
| Job factory | `PayrunJobFactory.CreatePayrunJob` | `Domain/Domain.Model/PayrunJobFactory.cs` |
| Calculator | `IPayrollCalculatorProvider` (e.g. `DefaultPayrollCalculatorProvider`) | `Api/Api.Core/ApiFactory.cs` |
| Payrun job persistence | `PayrunJobRepository` | `Persistence/Persistence/PayrunJobRepository.cs` |
| Result persistence | `PayrollResultSetRepository` | `Persistence/Persistence/PayrollResultSetRepository.cs` |
| Derived regulations | `PayrollRepository.GetDerivedRegulationsAsync` | `Persistence/Persistence/PayrollRepository.cs` → PayrollRepositoryRegulationCommand |
| Derived wage types/collectors | `PayrollRepository.GetDerivedWageTypesAsync` / `GetDerivedCollectorsAsync` | `Persistence/Persistence/PayrollRepository.cs` → PayrollRepositoryWageTypeCommand / PayrollRepositoryCollectorCommand |
| France wage type value | `FR.Composite.WageTypeValue`, `WageTypeValueFunction` | payroll-engine-regulation-France: `Scripts/Composite/WageTypeValue.cs`, `Scripts/Register/WageTypeValue.Register.cs` |
| France collectors / payrun | `Scripts/Register/CollectorStart.Register.cs`, etc.; `Scripts/Composite/*.cs` | payroll-engine-regulation-France: `Scripts/` |

---

*Document generated from analysis of payroll-engine-backend, payroll-engine-core, and payroll-engine-regulation-France source code. All class and method names refer to the actual .cs implementations.*
