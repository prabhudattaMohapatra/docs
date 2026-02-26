# Payrun Execution Flow — End-to-End

> Source of truth: the `payroll-engine-*` repositories.
> Generated 2026-02-23 from code inspection of the backend, core, client-scripting, client-services, console, and regulation repos.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Repository Map](#2-repository-map)
3. [Domain Model Hierarchy](#3-domain-model-hierarchy)
4. [Database Tables Involved in a Payrun](#4-database-tables-involved-in-a-payrun)
5. [API Entry Point](#5-api-entry-point)
6. [Payrun Execution — Full Sequence](#6-payrun-execution--full-sequence)
   - 6.1 [Phase 1 — Validation and Setup](#61-phase-1--validation-and-setup)
   - 6.2 [Phase 2 — Job Creation (DB Write)](#62-phase-2--job-creation-db-write)
   - 6.3 [Phase 3 — Regulation Loading (DB Reads)](#63-phase-3--regulation-loading-db-reads)
   - 6.4 [Phase 4 — Employee Loading (DB Reads)](#64-phase-4--employee-loading-db-reads)
   - 6.5 [Phase 5 — Payrun Start Script](#65-phase-5--payrun-start-script)
   - 6.6 [Phase 6 — Employee Processing Loop](#66-phase-6--employee-processing-loop)
   - 6.7 [Phase 7 — Payrun End Script](#67-phase-7--payrun-end-script)
   - 6.8 [Phase 8 — Job Completion (DB Write)](#68-phase-8--job-completion-db-write)
7. [Per-Employee Calculation Detail](#7-per-employee-calculation-detail)
   - 7.1 [Case Value Resolution](#71-case-value-resolution)
   - 7.2 [Collector Start](#72-collector-start)
   - 7.3 [Wage Type Loop](#73-wage-type-loop)
   - 7.4 [Collector End](#74-collector-end)
   - 7.5 [Case Values as Payrun Results](#75-case-values-as-payrun-results)
   - 7.6 [Incremental Result Handling](#76-incremental-result-handling)
   - 7.7 [Result Persistence (DB Write)](#77-result-persistence-db-write)
8. [Retro Pay Processing](#8-retro-pay-processing)
9. [Payrun Job Lifecycle and Status Transitions](#9-payrun-job-lifecycle-and-status-transitions)
10. [Webhook Dispatch](#10-webhook-dispatch)
11. [Scripting Integration](#11-scripting-integration)
12. [Calendar and Period Calculation](#12-calendar-and-period-calculation)
13. [Console-Driven Payrun (Exchange Import)](#13-console-driven-payrun-exchange-import)
14. [Regulation Structure (France Example)](#14-regulation-structure-france-example)
15. [Stored Procedures](#15-stored-procedures)
16. [Complete DB Operation Sequence](#16-complete-db-operation-sequence)

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Client / Console                              │
│  (payroll-engine-console, payroll-engine-client-services)            │
│                                                                      │
│  POST /api/tenants/{tenantId}/payruns/jobs  (PayrunJobInvocation)    │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTP
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   payroll-engine-backend                              │
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────┐ │
│  │ Backend.Server    │  │ Backend.Controller│  │ Api.Controller     │ │
│  │ (ASP.NET host,   │  │ (HTTP routes)    │  │ (business logic)   │ │
│  │  DI, Swagger)    │  │                  │  │                    │ │
│  └──────────────────┘  └────────┬─────────┘  └────────┬───────────┘ │
│                                 │                      │             │
│                                 ▼                      ▼             │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Domain.Application                               │   │
│  │  PayrunProcessor  │  PayrunProcessorRegulation                │   │
│  │  PayrunProcessorScripts  │  PayrunProcessorRepositories       │   │
│  └──────────────────────────────┬───────────────────────────────┘   │
│                                 │                                    │
│          ┌──────────────────────┼───────────────────────┐           │
│          ▼                      ▼                       ▼           │
│  ┌──────────────┐  ┌───────────────────┐  ┌────────────────────┐   │
│  │Domain.Model   │  │Domain.Scripting    │  │Persistence         │   │
│  │(entities,     │  │(C# script compile  │  │(repositories,      │   │
│  │ calculators)  │  │ and execution)     │  │ Dapper, SQL Server)│   │
│  └──────────────┘  └───────────────────┘  └────────────────────┘   │
│                                                      │              │
└──────────────────────────────────────────────────────┼──────────────┘
                                                       │
                                              ┌────────▼────────┐
                                              │  SQL Server DB   │
                                              └─────────────────┘
```

The payrun runs **synchronously** within the HTTP request — there is no background job queue or hosted service.

---

## 2. Repository Map

| Repository | Role |
|---|---|
| `payroll-engine-backend` | ASP.NET host, API controllers, domain application (PayrunProcessor), persistence layer, DB scripts |
| `payroll-engine-core` | Shared enums (PayrunJobStatus, RetroPayMode, CollectMode, CaseType, ValueType, etc.), base interfaces, date/period types, data utilities |
| `payroll-engine-client-core` | Domain model interfaces (IPayrunJob, IWageType, ICollector, etc.), service interfaces, API endpoint definitions |
| `payroll-engine-client-services` | HTTP client services (PayrunService, PayrunJobService, etc.) |
| `payroll-engine-client-scripting` | Script base classes (WageTypeValueFunction, CollectorApplyFunction, PayrunStartFunction, etc.) |
| `payroll-engine-console` | CLI tool for import, export, payrun tests, DSL conversion |
| `payroll-engine-regulation-France` | Example regulation: cases, wage types, collectors, scripts |
| `payroll-engine` | Integration repo: examples, schemas, docs |

---

## 3. Domain Model Hierarchy

```
Tenant
├── User
├── Calendar
├── Division
│   └── Employee (assigned to divisions)
├── Regulation
│   ├── Case (Global | National | Company | Employee)
│   │   └── CaseField (timeType, valueType, aggregation)
│   ├── WageType (wageTypeNumber, valueExpression, collectors)
│   ├── Collector (collectMode, start/apply/end expressions)
│   ├── Lookup / LookupValue
│   ├── Script (function types: WageType, Collector, Case, Payrun, etc.)
│   └── Report
├── Payroll
│   ├── PayrollLayer → links to Regulation(s) with level + priority
│   └── ClusterSets (including retro clusters)
├── Payrun (startExpression, endExpression, employeeStart/End, retro settings)
│   └── PayrunParameter
└── PayrunJob (execution instance)
    ├── PayrunJobEmployee
    └── PayrollResult (per employee)
        ├── WageTypeResult → WageTypeCustomResult
        ├── CollectorResult → CollectorCustomResult
        └── PayrunResult
```

**Regulation inheritance**: A Payroll has one or more PayrollLayers, each pointing to a Regulation at a given level and priority. Derived wage types and collectors are resolved by walking the regulation hierarchy — higher-level regulations override lower-level ones.

---

## 4. Database Tables Involved in a Payrun

### Tables read during payrun

| Table | Purpose |
|---|---|
| `Tenant` | Tenant configuration, culture, calendar |
| `User` | Creator/processor user identity |
| `Payrun` | Payrun definition (expressions, retro settings) |
| `PayrunParameter` | Payrun parameters |
| `Payroll` | Payroll definition, cluster sets |
| `PayrollLayer` | Regulation layers linked to payroll |
| `Division` | Division for payrun scope |
| `Calendar` | Cycle/period time units, week mode |
| `Employee` | Employee master data |
| `EmployeeDivision` | Employee-to-division mapping |
| `Regulation` | Regulation definitions |
| `RegulationShare` | Shared regulation access |
| `WageType` | Wage type definitions |
| `Collector` | Collector definitions |
| `Case` | Case definitions |
| `CaseField` | Case field definitions |
| `GlobalCaseValue` | Global scope case values |
| `NationalCaseValue` | National scope case values |
| `CompanyCaseValue` | Company scope case values |
| `EmployeeCaseValue` | Employee scope case values |
| `LookupValue` | Lookup data for scripts |
| `Script` | Compiled C# scripts |
| `PayrunJob` (prior jobs) | For retro date calculation |

### Tables written during payrun

| Table | Operation | When |
|---|---|---|
| `PayrunJob` | INSERT | Job creation at start |
| `PayrunJobEmployee` | INSERT (per employee) | Job creation |
| `PayrunJob` | UPDATE | TotalEmployeeCount, ProcessedEmployeeCount increments, JobEnd on completion |
| `PayrollResult` | INSERT (per employee) | After employee calculation |
| `WageTypeResult` | INSERT (per wage type) | After employee calculation |
| `WageTypeCustomResult` | INSERT | When scripts add custom results |
| `CollectorResult` | INSERT (per collector) | After employee calculation |
| `CollectorCustomResult` | INSERT | When scripts add custom results |
| `PayrunResult` | INSERT (per case value) | After employee calculation |

---

## 5. API Entry Point

**Endpoint:** `POST /api/tenants/{tenantId}/payruns/jobs`

**Controller chain:**

```
Backend.Controller.PayrunJobController        (HTTP route binding)
  └── Api.Controller.PayrunJobController      (business logic)
        └── PayrunProcessor.Process()         (orchestration)
```

**Request body** — `PayrunJobInvocation`:

```json
{
  "payrunId": 1,
  "userId": 1,
  "name": "January 2026",
  "periodStart": "2026-01-01T00:00:00",
  "evaluationDate": "2026-01-31T00:00:00",
  "reason": "Monthly payroll",
  "jobStatus": "Draft",
  "retroPayMode": "None",
  "employeeIdentifiers": ["EMP001", "EMP002"],
  "forecast": null,
  "attributes": {}
}
```

**Response:** `201 Created` with the `PayrunJob` object.

---

## 6. Payrun Execution — Full Sequence

### 6.1 Phase 1 — Validation and Setup

**Source:** `PayrunJobController.StartPayrunJobAsync` → `PayrunProcessor.Process`

1. **Load tenant** — `TenantRepository.GetAsync(tenantId)` → DB read `Tenant`
2. **Load user** — `UserRepository.GetAsync(userId)` → DB read `User`
3. **Load payrun** — `PayrunRepository.GetAsync(payrunId)` → DB read `Payrun`
4. **Load payroll** — `PayrollRepository.GetAsync(payrollId)` → DB read `Payroll`
5. **Load division** — `DivisionRepository.GetAsync(divisionId)` → DB read `Division`
6. **Resolve calendar** — from division, then tenant, then system default → DB read `Calendar`
7. **Resolve culture** — from division, then tenant, then system default
8. **Create PayrollCalculator** — `PayrollCalculatorProvider.CreateCalculator(calendar, culture)` — cached per calendar name
9. **Determine retro date** — if `RetroPayMode != None`, queries prior `PayrunJob` records to find the latest evaluation date

### 6.2 Phase 2 — Job Creation (DB Write)

**Source:** `PayrunJobFactory.CreatePayrunJob` → `PayrunJobRepository.CreateAsync`

1. `PayrunJobFactory.CreatePayrunJob(invocation, divisionId, payrollId, calculator)`:
   - Resolves cycle and period from `invocation.PeriodStart` using the calculator
   - Sets: PayrunId, PayrollId, DivisionId, CreatedUserId, ParentJobId, Name, Owner, Tags, Forecast, RetroPayMode, JobResult
   - Sets cycle/period names and date ranges
   - Sets EvaluationDate and CreatedReason
   - Sets `JobStatus` = `Forecast` if forecast, otherwise from invocation (typically `Draft`)

2. **DB Write:** `INSERT INTO PayrunJob (...)` → returns new job ID

3. **DB Write:** For each employee in the invocation, `INSERT INTO PayrunJobEmployee (PayrunJobId, EmployeeId)`

### 6.3 Phase 3 — Regulation Loading (DB Reads)

**Source:** `PayrunProcessorRepositories.LoadDerivedRegulationsAsync` → `PayrollRepository.GetDerived*Async`

1. **Load derived regulations** — `PayrollRepository.GetDerivedRegulationsAsync(payrollId, regulationDate, evaluationDate)`:
   - Walks the `PayrollLayer` hierarchy
   - Returns regulations active as of `periodEnd` and `evaluationDate`
   - DB reads: `PayrollLayer`, `Regulation`, `RegulationShare`

2. **Load derived wage types** — `PayrollRepository.GetDerivedWageTypesAsync(payrollQuery, clusterSet, overrideType)`:
   - Returns wage types from all regulations in the payroll, resolved by level and priority
   - If retro job, uses `ClusterSetWageTypeRetro` instead
   - DB reads: `WageType` (joined across regulation hierarchy)

3. **Load derived collectors** — `PayrollRepository.GetDerivedCollectorsAsync(payrollQuery, clusterSet, overrideType)`:
   - Same pattern as wage types
   - DB reads: `Collector` (joined across regulation hierarchy)

4. **Load case value caches** — `CaseValueProvider` loads and caches:
   - `GlobalCaseValue` — DB read
   - `NationalCaseValue` — DB read
   - `CompanyCaseValue` — DB read
   - (Employee case values loaded per employee later)

### 6.4 Phase 4 — Employee Loading (DB Reads)

**Source:** `PayrunProcessor.SetupEmployees`

- If `employeeIdentifiers` provided: load those specific employees
- Otherwise: load all active employees in the division
- DB reads: `Employee`, `EmployeeDivision`
- **DB Write:** `UPDATE PayrunJob SET TotalEmployeeCount = {count}`

### 6.5 Phase 5 — Payrun Start Script

**Source:** `PayrunProcessorScripts.PayrunStart`

- Executes `Payrun.StartExpression` if defined
- Uses `PayrunScriptController` → compiles and runs C# script
- Script has access to: case values, lookups, runtime values, regulation data
- Script can abort the payrun by returning false

### 6.6 Phase 6 — Employee Processing Loop

**Source:** `PayrunProcessor.ProcessAllEmployeesAsync`

```
for each employee:
    ProcessEmployeeAsync(employee)
    UPDATE PayrunJob SET ProcessedEmployeeCount += 1
```

Each employee is processed independently — see [Section 7](#7-per-employee-calculation-detail) for full detail.

### 6.7 Phase 7 — Payrun End Script

**Source:** `PayrunProcessorScripts.PayrunEnd`

- Executes `Payrun.EndExpression` if defined
- Script has access to all accumulated results

### 6.8 Phase 8 — Job Completion (DB Write)

**Source:** `PayrunProcessor.CompleteJobAsync`

- **DB Write:** `UPDATE PayrunJob SET JobEnd = {now}, Message = {summary}`

If an error occurs at any point, `AbortJobAsync` is called instead:
- **DB Write:** `UPDATE PayrunJob SET JobStatus = 'Abort', JobEnd = {now}, ErrorMessage = {error}`

---

## 7. Per-Employee Calculation Detail

**Source:** `PayrunProcessor.ProcessEmployeeAsync` → `PayrunProcessor.CalculateEmployeeAsync`

```
┌─────────────────────────────────────────────────────────────────┐
│                    ProcessEmployeeAsync                          │
│                                                                 │
│  1. Load employee case values (DB read)                         │
│  2. Create CaseValueProvider                                    │
│  3. EmployeeStart script                                        │
│  4. CalculateEmployeeAsync (Setup phase)                        │
│  │   ├── Resolve employee culture/calendar                      │
│  │   ├── CollectorStart (all collectors)                        │
│  │   ├── Wage Type Loop (with restart support)                  │
│  │   │   ├── IsWageTypeAvailable                                │
│  │   │   ├── CalculateWageTypeValue (script)                    │
│  │   │   ├── WageTypeResult script                              │
│  │   │   └── CollectorApply (applicable collectors)             │
│  │   ├── CollectorEnd (all collectors)                          │
│  │   ├── Case values → PayrunResults                            │
│  │   └── Incremental: remove unchanged results                  │
│  5. Retro processing (if retro jobs scheduled)                  │
│  6. CalculateEmployeeAsync (Reevaluation phase, if retro ran)   │
│  7. Store PayrollResultSet (DB write)                           │
│  8. EmployeeEnd script                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 7.1 Case Value Resolution

**Source:** `CaseValueProvider` (Domain.Scripting)

- **DB Read:** `EmployeeCaseValue` — per employee, cached in `CaseValueCache`
- Resolution by `CaseFieldTimeType`:
  - `Timeless` — latest created value
  - `Moment` — latest value before the evaluation date
  - `Period` / `CalendarPeriod` — value active during the payrun period, with aggregation (First, Last, Summary)
- **Pro-rating:** `PayrollCalculator.CalculateCasePeriodValue` handles partial periods using working days or calendar days based on `CalendarWeekMode`
- **Scope chain:** Employee → Company → National → Global (determined by `CaseType`)

### 7.2 Collector Start

**Source:** `PayrunProcessorRegulation.CollectorStart`

For each derived collector:
1. `Collector.Reset()` — clears accumulated values from previous employee
2. Execute `CollectorStartRuntime` if the collector has a `StartScript`
3. DB reads: none (collectors are in memory from Phase 3)

### 7.3 Wage Type Loop

**Source:** `PayrunProcessorRegulation.CalculateWageTypeValue`

The wage type loop supports **restarts** — if a wage type script sets `ExecutionRestartRequest`, the entire loop restarts (up to `PayrunMaxExecutionCount` times).

For each derived wage type:

1. **Optional per-wage-type calculator** — if the wage type uses a different calendar, create a separate `PayrollCalculator`
2. **Availability check** — `PayrunScriptController.IsWageTypeAvailable(payrun.WageTypeAvailableExpression)`:
   - Runs the payrun's `WageTypeAvailableExpression` if defined
   - Returns true/false
3. **Value calculation** — `WageTypeScriptController.GetValue`:
   - Compiles the wage type's `ValueExpression` (C# script)
   - Executes `WageTypeValueFunction.GetValue()` within `WageTypeValueRuntime`
   - Script accesses case values, lookups, other wage type results, runtime values
   - Returns `(decimal? value, List<RetroPayrunJob> retroJobs, bool executionRestart)`
4. **Result script** — `WageTypeResultRuntime.Result`:
   - Runs in **reverse inheritance order** (most-derived first)
   - Can modify the wage type value
5. **Record wage type result** — added to in-memory `PayrollResultSet`
6. **Collector apply** — for each collector linked to this wage type:
   - Check `IsCollectorAvailable` (wage type can disable specific collectors)
   - Execute `CollectorApplyRuntime` if the collector has an `ApplyScript`
   - If apply script returns null, use the wage type value as-is
   - `collector.AddValue(value)` — accumulates the value
   - Returns `(collector.Result, retroJobs)`
   - Collector result computed from `CollectMode`: Summary, Minimum, Maximum, Average, Range, Count

### 7.4 Collector End

**Source:** `PayrunProcessorRegulation.CollectorEnd`

For each derived collector:
1. Execute `CollectorEndRuntime` if the collector has an `EndScript`
2. Apply constraints: `MinResult`, `MaxResult`, `Threshold`, `Negated`
3. Record final collector result to in-memory `PayrollResultSet`

### 7.5 Case Values as Payrun Results

**Source:** `PayrunProcessor.GetCaseValuePayrunResultsAsync`

- Iterates case values from the `CaseValueProvider`
- Each case value becomes a `PayrunResult` record with: Name, Slot, ValueType, Value, Start, End, Tags

### 7.6 Incremental Result Handling

When `JobResult = Incremental`:
- Compares new results against prior results
- Removes unchanged wage type results, collector results, and payrun results
- Only deltas are persisted

### 7.7 Result Persistence (DB Write)

**Source:** `PayrollResultSetRepository.CreateAsync`

This is the atomic write that persists all results for one employee:

```
INSERT PayrollResult                     ← 1 row per employee
  ├── INSERT WageTypeResult              ← 1 row per wage type
  │     └── INSERT WageTypeCustomResult  ← 0+ rows per wage type
  ├── INSERT CollectorResult             ← 1 row per collector
  │     └── INSERT CollectorCustomResult ← 0+ rows per collector
  └── INSERT PayrunResult                ← 1 row per case value
```

- Uses bulk insert when there are no custom results
- Row-by-row insert when custom results exist (to link child rows to parent IDs)

---

## 8. Retro Pay Processing

**Source:** `PayrunProcessor.ProcessEmployeeAsync` (retro section)

### When retro is triggered

Retro jobs are scheduled by wage type or collector scripts calling `ScheduleRetroPayrun(scheduleDate)`. The `CaseValueProvider` also checks for case value changes after the retro date via `UpdateRetroCaseValue`.

### Retro flow

```
1. Collect RetroPayrunJob list from wage type/collector scripts
2. Determine target retro date = min(retro job scheduleDate, caseValueProvider.RetroCaseValue)
3. Apply RetroTimeType constraint:
   - Anytime → no time limit
   - Cycle → clamp to current cycle start
4. For each period from retro date to current period:
   a. Create new PayrunProcessor (child)
   b. Create PayrunJobInvocation:
      - ParentJobId = current job ID
      - RetroPayMode = None (no recursive retro)
      - JobResult = Incremental
      - Same employee only
   c. child.Process(retroJobInvocation, payrunSetup)
      → Full payrun execution for that one period/employee
      → Writes retro results to DB
5. Clear RuntimeValueProvider.EmployeeValues
6. Re-run CalculateEmployeeAsync with PayrunExecutionPhase = Reevaluation
7. Apply retro result tags to current period results
```

### DB operations during retro

Each retro period generates:
- INSERT `PayrunJob` (with `ParentJobId` set)
- INSERT `PayrunJobEmployee`
- INSERT `PayrollResult`, `WageTypeResult`, `CollectorResult`, `PayrunResult` (incremental deltas only)
- UPDATE `PayrunJob` (completion)

---

## 9. Payrun Job Lifecycle and Status Transitions

### PayrunJobStatus enum

| Status | Value | Description |
|---|---|---|
| `Draft` | 0 | Job created, not yet released |
| `Release` | 1 | Released for processing |
| `Process` | 2 | Being processed |
| `Complete` | 3 | Successfully completed |
| `Forecast` | 4 | Forecast/simulation run |
| `Abort` | 5 | Aborted due to error |
| `Cancel` | 6 | Cancelled during processing |

### Valid state transitions

```
Draft ──────► Release ──────► Process ──────► Complete
  │                │              │
  │                │              └──────────► Cancel
  │                │
  └────────────────┴──────────────────────────► Abort
```

- `Draft → Release` — user approves the job
- `Draft → Abort` — user or system aborts
- `Release → Process` — user triggers processing
- `Release → Abort` — user or system aborts
- `Process → Complete` — processing finished successfully
- `Process → Cancel` — user cancels during processing

### How status changes

- **During `PayrunProcessor.Process`:** The job is created at the status specified in the invocation (typically `Draft`). If processing fails, `AbortJobAsync` sets status to `Abort`.
- **Via API:** `POST /api/tenants/{tenantId}/payruns/jobs/{jobId}/status` calls `ChangePayrunJobStatusAsync`:
  - Updates `Released`/`Processed`/`Finished` timestamps and user IDs
  - Dispatches webhooks on Release→Process and Process→Complete/Cancel transitions

### DB writes per status change

```sql
-- PatchPayrunJobStatusAsync
UPDATE PayrunJob SET
  JobStatus = @status,
  Released = @now,          -- when status = Release
  ReleasedUserId = @userId,
  ReleasedReason = @reason
WHERE Id = @jobId

UPDATE PayrunJob SET
  JobStatus = @status,
  Processed = @now,         -- when status = Process
  ProcessedUserId = @userId,
  ProcessedReason = @reason
WHERE Id = @jobId

UPDATE PayrunJob SET
  JobStatus = @status,
  Finished = @now,          -- when status = Complete, Abort, or Cancel
  FinishedUserId = @userId,
  FinishedReason = @reason
WHERE Id = @jobId
```

---

## 10. Webhook Dispatch

**Source:** `Api.Controller.PayrunJobController.ChangePayrunJobStatusAsync`

Webhooks are dispatched **only on explicit status changes** via the API — not during `PayrunProcessor.Process`.

| Transition | Webhook Action |
|---|---|
| Release → Process | `WebhookAction.PayrunJobProcess` |
| Process → Complete | `WebhookAction.PayrunJobFinish` |
| Process → Cancel | `WebhookAction.PayrunJobFinish` |

The webhook payload is the serialized `PayrunJob` JSON.

`IWebhookDispatchService` is also passed into script contexts so regulation scripts can access webhook capabilities if needed.

---

## 11. Scripting Integration

### Script controller hierarchy

| Controller | Scripts executed |
|---|---|
| `PayrunScriptController` | PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd, IsEmployeeAvailable, IsWageTypeAvailable |
| `WageTypeScriptController` | GetValue (ValueExpression), Result (ResultExpression) |
| `CollectorScriptController` | Start (StartScript), Apply (ApplyScript), End (EndScript) |
| `CaseScriptController` | Build, Validate, Available |

### Script base classes (payroll-engine-client-scripting)

```
Function (base)
  └── PayrollFunction
        │   - CaseValue[fieldName], GetLookup<T>(), period/cycle access
        └── PayrunFunction
              │   - WageType[number], Collector[name], runtime values, results
              ├── WageTypeValueFunction    → override GetValue(): decimal?
              ├── WageTypeResultFunction   → override Result(decimal value): decimal
              ├── CollectorStartFunction   → override Start(): bool?
              ├── CollectorApplyFunction   → override ApplyValue(): decimal?
              ├── CollectorEndFunction     → override End(): decimal?
              ├── PayrunStartFunction      → override Start(): bool?
              ├── PayrunEndFunction        → override End(): bool?
              ├── PayrunEmployeeStartFunction
              └── PayrunEmployeeEndFunction
```

### APIs available to scripts

| Category | Methods |
|---|---|
| Case values | `CaseValue[name]`, `GetCaseValue()`, `GetPeriodCaseValue()`, `GetPeriodCaseValues()` |
| Lookups | `GetLookup<T>(name, key)`, `GetRangeLookup<T>(name, rangeValue, key)` |
| Results | `WageType[number]`, `Collector[name]`, `GetWageTypeResults()`, `GetCollectorResults()` |
| Historical | `GetConsolidatedWageTypeResults()`, `GetConsolidatedCollectorResults()` |
| Runtime values | `Get/Set PayrunRuntimeValue(key)`, `Get/Set EmployeeRuntimeValue(key)` |
| Custom results | `AddCustomResult(source, name, value)` |
| Retro | `IsRetroPayrun`, `ScheduleRetroPayrun(scheduleDate)` |
| Period | `Period`, `PeriodStart`, `PeriodEnd`, `GetPeriod(offset)`, `GetCycle()` |
| Employee | `GetEmployeeAttribute()`, employee identifiers |

### Script compilation

- Scripts are compiled via Roslyn (`Domain.Scripting`)
- Compiled assemblies are cached with configurable timeout (`AssemblyCacheTimeout`)
- Script source comes from the `Script` table (linked to regulation via `Scripts.json`)

---

## 12. Calendar and Period Calculation

**Source:** `PayrollCalculator` (Domain.Model), `IPayrollPeriod` (Core)

### Calendar model

| Field | Description |
|---|---|
| `CycleTimeUnit` | Year, SemiYear, Quarter, etc. — the larger grouping |
| `PeriodTimeUnit` | CalendarMonth, SemiMonth, BiWeek, Week, etc. — the pay period |
| `WeekMode` | `Week` (calendar days) or `WorkWeek` (working days only) |
| `FirstDayOfWeek` | Monday, Sunday, etc. |
| `TimeMap` | Period or Cycle — controls annualization |
| `PeriodDayCount` | Fixed day count for period (optional) |

### How periods are calculated

1. `PayrollCalculator.GetPayrunCycle(periodStart)` → returns `DatePeriod` for the cycle containing `periodStart`
2. `PayrollCalculator.GetPayrunPeriod(periodStart)` → returns `IPayrollPeriod` for the period containing `periodStart`
3. Concrete implementations: `YearPayrollPeriod`, `SemiYearPayrollPeriod`, `QuarterPayrollPeriod`, `CalendarMonthPayrollPeriod`, `SemiMonthPayrollPeriod`, `BiWeekPayrollPeriod`, `WeekPayrollPeriod`, etc.

### Example: monthly payroll

```
Calendar: CycleTimeUnit = Year, PeriodTimeUnit = CalendarMonth
PeriodStart = 2026-01-01 → Period = Jan 1–31, Cycle = Jan 1 – Dec 31
PeriodStart = 2026-06-15 → Period = Jun 1–30, Cycle = Jan 1 – Dec 31
```

---

## 13. Console-Driven Payrun (Exchange Import)

**Source:** `payroll-engine-console`

### Flow: JSON exchange to payrun execution

```
┌───────────────────────┐
│  Exchange JSON / ZIP   │  Contains: tenant, regulations, employees,
│  (or DSL output)       │  payrolls, case changes, payrun job invocations
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│  ExchangeImport        │  For each object type, calls the backend API:
│                        │
│  1. Setup Tenant       │  POST /api/tenants
│  2. Setup Users        │  POST /api/tenants/{id}/users
│  3. Setup Calendars    │  POST /api/tenants/{id}/calendars
│  4. Setup Divisions    │  POST /api/tenants/{id}/divisions
│  5. Setup Regulations  │  POST /api/tenants/{id}/regulations
│     - Cases            │    POST .../regulations/{id}/cases
│     - CaseFields       │    POST .../cases/{id}/fields
│     - WageTypes        │    POST .../regulations/{id}/wagetypes
│     - Collectors       │    POST .../regulations/{id}/collectors
│     - Lookups          │    POST .../regulations/{id}/lookups
│     - Scripts          │    POST .../regulations/{id}/scripts
│  6. Setup Employees    │  POST /api/tenants/{id}/employees
│  7. Setup Payrolls     │  POST /api/tenants/{id}/payrolls
│     - PayrollLayers    │    POST .../payrolls/{id}/layers
│  8. Setup Payruns      │  POST /api/tenants/{id}/payruns
│  9. Setup CaseChanges  │  POST .../payrolls/{id}/cases
│     (sets case values) │
│ 10. Start PayrunJobs   │  POST /api/tenants/{id}/payruns/jobs
│                        │    ← triggers full payrun execution
└───────────────────────┘
```

### DSL pipeline (for regulation development)

```
1. DSLPrepare     → Download regulation files from S3
2. DSLConvert     → Convert Model/Rule/Instruction files to Exchange JSON
3. PayrollImport  → Import Exchange JSON to backend via API
```

### Test workflow

```
PayrunTest command:
  1. Import exchange JSON (regulations, employees, case values)
  2. Execute payrun job invocations
  3. Compare calculated results vs expected results in the JSON
  4. Report pass/fail per wage type, collector, payrun result
```

---

## 14. Regulation Structure (France Example)

**Source:** `payroll-engine-regulation-France`

### Cases

| Case | Type | Fields (examples) |
|---|---|---|
| `FR.company` | Company | siren, company_name, headcount, is_large_company |
| `FR.employee` | Employee | first_name, last_name, date_of_birth, social_security_number, contract_type, base_monthly_salary, gross_salary, transport_allowance, meal_allowance, individualized_tax_rate, monthly_ss_ceiling, is_alsace_moselle, work_percentage, etc. (40+ fields) |

### Collectors (accumulate across wage types)

| Collector | CollectMode | Purpose |
|---|---|---|
| `FR.total_gross_pay` | Summary | Total gross pay |
| `FR.total_employee_contributions` | Summary | All employee social contributions |
| `FR.total_employer_contributions` | Summary | All employer social contributions |
| `FR.total_deductions` | Summary | Total deductions |
| `FR.taxable_income` | Summary | Taxable income base |
| `FR.total_employee_urssaf_contributions` | Summary | Employee URSSAF |
| `FR.total_employer_urssaf_contributions` | Summary | Employer URSSAF |
| `FR.total_employee_pension_contributions` | Summary | Employee pension |
| `FR.total_employer_pension_contributions` | Summary | Employer pension |

### Wage types (50+, key examples)

| Number | Name | Collectors | Logic |
|---|---|---|---|
| 1000 | gross_salary | FR.total_gross_pay | `return gross_salary();` |
| 1100 | social_security_ceiling | — | Ceiling calculation |
| 2xxx | URSSAF contributions | FR.total_employee_urssaf, FR.total_employer_urssaf | Rate × base |
| 3xxx | Pension contributions | FR.total_employee_pension, FR.total_employer_pension | AGIRC-ARRCO rates |
| 4xxx | Tax (PAS) | FR.taxable_income | Withholding tax calculation |
| 5xxx | Net calculations | FR.total_deductions | Net salary, net social |
| 9xxx | DSN output | — | Emit values for reporting |

### Script organization

| Function Type | Script Names |
|---|---|
| WageType value | FR.WageTypeBase, FR.WageTypeValue |
| WageType result | FR.WageTypeResult |
| Collector start/apply/end | FR.CollectorBase, FR.CollectorStart, FR.CollectorApply, FR.CollectorEnd |
| Payrun start/end | FR.PayrunStart, FR.PayrunEnd |
| Employee start/end | FR.PayrunEmployeeStart, FR.PayrunEmployeeEnd |
| Case build/validate | FR.CaseBuild, FR.CaseValidate |

---

## 15. Stored Procedures

| Procedure | Purpose | Used by |
|---|---|---|
| `DeletePayrunJob` | Cascading delete: PayrunResult, WageTypeCustomResult, WageTypeResult, CollectorCustomResult, CollectorResult, PayrollResult, PayrunJobEmployee, PayrunJob | `PayrunJobRepository.DeleteAsync` |
| `GetWageTypeResults` | Retrieves wage type results with filters (tenant, employee, division, period, wage type numbers, status) | `PayrollResultRepository.GetWageTypeResultsAsync` |
| `GetCollectorResults` | Retrieves collector results with same filter pattern | `PayrollResultRepository.GetCollectorResultsAsync` |
| `GetConsolidatedWageTypeResults` | Consolidated (aggregated) wage type results across periods | `PayrollConsolidatedResultRepository` |
| `GetConsolidatedCollectorResults` | Consolidated collector results across periods | `PayrollConsolidatedResultRepository` |
| `GetConsolidatedPayrunResults` | Consolidated payrun results across periods | `PayrollConsolidatedResultRepository` |
| `GetPayrollResultValues` | Pivot-style result values for reporting | `PayrollResultRepository.QueryResultValuesAsync` |

---

## 16. Complete DB Operation Sequence

Here is the exact sequence of database operations during a single payrun execution with N employees:

```
── PHASE 1: VALIDATION ──────────────────────────────────────────────
READ  Tenant                           (1 query)
READ  User                             (1 query)
READ  Payrun                           (1 query)
READ  Payroll                          (1 query)
READ  Division                         (1 query)
READ  Calendar                         (1 query)
READ  PayrunJob (prior jobs)           (1 query, for retro date)

── PHASE 2: JOB CREATION ───────────────────────────────────────────
WRITE INSERT PayrunJob                 (1 row)
WRITE INSERT PayrunJobEmployee         (N rows, one per employee)

── PHASE 3: REGULATION LOADING ──────────────────────────────────────
READ  PayrollLayer                     (1 query)
READ  Regulation (hierarchy)           (1+ queries)
READ  RegulationShare                  (1 query)
READ  WageType (derived)               (1 query)
READ  Collector (derived)              (1 query)
READ  Script (regulation scripts)      (1+ queries)
READ  GlobalCaseValue                  (1 query)
READ  NationalCaseValue                (1 query)
READ  CompanyCaseValue                 (1 query)

── PHASE 4: EMPLOYEE LOADING ───────────────────────────────────────
READ  Employee                         (1 query)
READ  EmployeeDivision                 (1 query)
WRITE UPDATE PayrunJob                 (TotalEmployeeCount)

── PHASE 5: PAYRUN START SCRIPT ────────────────────────────────────
(no DB operations — script runs in memory)

── PHASE 6: PER EMPLOYEE (× N) ────────────────────────────────────
│
│  READ  EmployeeCaseValue             (1 query per employee)
│  READ  LookupValue                   (0+ queries, as scripts request)
│  READ  PayrollResult (prior)         (0+ queries, for retro/historical)
│
│  (Collector Start, Wage Type Loop, Collector End — all in memory)
│
│  WRITE INSERT PayrollResult          (1 row)
│  WRITE INSERT WageTypeResult         (W rows, one per wage type)
│  WRITE INSERT WageTypeCustomResult   (0+ rows)
│  WRITE INSERT CollectorResult        (C rows, one per collector)
│  WRITE INSERT CollectorCustomResult  (0+ rows)
│  WRITE INSERT PayrunResult           (R rows, one per case value)
│  WRITE UPDATE PayrunJob              (ProcessedEmployeeCount)
│
│  ── IF RETRO (per retro period) ──────────────────────────────────
│  │  WRITE INSERT PayrunJob           (retro child job)
│  │  WRITE INSERT PayrunJobEmployee   (1 row)
│  │  READ  (same regulation/case reads as above)
│  │  WRITE INSERT PayrollResult       (incremental deltas)
│  │  WRITE INSERT WageType/Collector/PayrunResult (deltas)
│  │  WRITE UPDATE PayrunJob           (retro job completion)
│  └────────────────────────────────────────────────────────────────

── PHASE 7: PAYRUN END SCRIPT ──────────────────────────────────────
(no DB operations — script runs in memory)

── PHASE 8: JOB COMPLETION ─────────────────────────────────────────
WRITE UPDATE PayrunJob                 (JobEnd, Message)
```

### Total DB operations (approximate, no retro, N employees, W wage types, C collectors, R case values)

| Operation | Count |
|---|---|
| SELECT queries | ~12 + N (employee case values) + lookup queries |
| INSERT PayrunJob | 1 |
| INSERT PayrunJobEmployee | N |
| INSERT PayrollResult | N |
| INSERT WageTypeResult | N × W |
| INSERT CollectorResult | N × C |
| INSERT PayrunResult | N × R |
| UPDATE PayrunJob | N + 2 (employee count + total count + completion) |

---

## Appendix A: Table Schemas

### PayrunJob

```sql
CREATE TABLE [dbo].[PayrunJob] (
  [Id]                     INT IDENTITY(1,1) PRIMARY KEY,
  [Status]                 INT NOT NULL,
  [Created]                DATETIME2(7) NOT NULL,
  [Updated]                DATETIME2(7) NOT NULL,
  [TenantId]               INT NOT NULL,
  [PayrunId]               INT NOT NULL,
  [PayrollId]              INT NOT NULL,
  [DivisionId]             INT NOT NULL,
  [ParentJobId]            INT NULL,
  [CreatedUserId]          INT NOT NULL,
  [ReleasedUserId]         INT NULL,
  [ProcessedUserId]        INT NULL,
  [FinishedUserId]         INT NULL,
  [RetroPayMode]           INT NOT NULL,
  [JobStatus]              INT NOT NULL,
  [JobResult]              INT NOT NULL,
  [Name]                   NVARCHAR(128) NOT NULL,
  [Owner]                  NVARCHAR(128) NULL,
  [Forecast]               NVARCHAR(128) NULL,
  [CycleName]              NVARCHAR(128) NOT NULL,
  [CycleStart]             DATETIME2(7) NOT NULL,
  [CycleEnd]               DATETIME2(7) NOT NULL,
  [PeriodName]             NVARCHAR(128) NOT NULL,
  [PeriodStart]            DATETIME2(7) NOT NULL,
  [PeriodEnd]              DATETIME2(7) NOT NULL,
  [EvaluationDate]         DATETIME2(7) NOT NULL,
  [Released]               DATETIME2(7) NULL,
  [Processed]              DATETIME2(7) NULL,
  [Finished]               DATETIME2(7) NULL,
  [CreatedReason]          NVARCHAR(MAX) NOT NULL,
  [ReleasedReason]         NVARCHAR(MAX) NULL,
  [ProcessedReason]        NVARCHAR(MAX) NULL,
  [FinishedReason]         NVARCHAR(MAX) NULL,
  [TotalEmployeeCount]     INT NOT NULL,
  [ProcessedEmployeeCount] INT NOT NULL,
  [JobStart]               DATETIME2(7) NOT NULL,
  [JobEnd]                 DATETIME2(7) NULL,
  [Message]                NVARCHAR(MAX) NULL,
  [ErrorMessage]           NVARCHAR(MAX) NULL,
  [Tags]                   NVARCHAR(MAX) NULL,
  [Attributes]             NVARCHAR(MAX) NULL
);
```

### PayrollResult

```sql
CREATE TABLE [dbo].[PayrollResult] (
  [Id]          INT IDENTITY(1,1) PRIMARY KEY,
  [Status]      INT NOT NULL,
  [Created]     DATETIME2(7) NOT NULL,
  [Updated]     DATETIME2(7) NOT NULL,
  [TenantId]    INT NOT NULL,
  [PayrollId]   INT NOT NULL,
  [PayrunId]    INT NOT NULL,
  [PayrunJobId] INT NOT NULL,
  [EmployeeId]  INT NOT NULL,
  [DivisionId]  INT NOT NULL,
  [CycleName]   NVARCHAR(128) NOT NULL,
  [CycleStart]  DATETIME2(7) NOT NULL,
  [CycleEnd]    DATETIME2(7) NOT NULL,
  [PeriodName]  NVARCHAR(128) NOT NULL,
  [PeriodStart] DATETIME2(7) NOT NULL,
  [PeriodEnd]   DATETIME2(7) NOT NULL
);
```

### WageTypeResult

```sql
CREATE TABLE [dbo].[WageTypeResult] (
  [Id]                         INT IDENTITY(1,1) PRIMARY KEY,
  [Status]                     INT NOT NULL,
  [Created]                    DATETIME2(7) NOT NULL,
  [Updated]                    DATETIME2(7) NOT NULL,
  [PayrollResultId]            INT NOT NULL,
  [WageTypeId]                 INT NOT NULL,
  [WageTypeNumber]             DECIMAL(28,6) NOT NULL,
  [WageTypeName]               NVARCHAR(128) NOT NULL,
  [WageTypeNameLocalizations]  NVARCHAR(MAX) NULL,
  [ValueType]                  INT NOT NULL,
  [Value]                      DECIMAL(28,6) NOT NULL,
  [Culture]                    NVARCHAR(128) NOT NULL,
  [Start]                      DATETIME2(7) NOT NULL,
  [StartHash]                  INT NOT NULL,
  [End]                        DATETIME2(7) NOT NULL,
  [Tags]                       NVARCHAR(MAX) NULL,
  [Attributes]                 NVARCHAR(MAX) NULL
);
```

### CollectorResult

```sql
CREATE TABLE [dbo].[CollectorResult] (
  [Id]                          INT IDENTITY(1,1) PRIMARY KEY,
  [Status]                      INT NOT NULL,
  [Created]                     DATETIME2(7) NOT NULL,
  [Updated]                     DATETIME2(7) NOT NULL,
  [PayrollResultId]             INT NOT NULL,
  [CollectorId]                 INT NOT NULL,
  [CollectorName]               NVARCHAR(128) NOT NULL,
  [CollectorNameHash]           INT NOT NULL,
  [CollectorNameLocalizations]  NVARCHAR(MAX) NULL,
  [CollectMode]                 INT NOT NULL,
  [Negated]                     BIT NOT NULL,
  [ValueType]                   INT NOT NULL,
  [Value]                       DECIMAL(28,6) NOT NULL,
  [Culture]                     NVARCHAR(128) NOT NULL,
  [Start]                       DATETIME2(7) NOT NULL,
  [StartHash]                   INT NOT NULL,
  [End]                         DATETIME2(7) NOT NULL,
  [Tags]                        NVARCHAR(MAX) NULL,
  [Attributes]                  NVARCHAR(MAX) NULL
);
```

### PayrunResult

```sql
CREATE TABLE [dbo].[PayrunResult] (
  [Id]                 INT IDENTITY(1,1) PRIMARY KEY,
  [Status]             INT NOT NULL,
  [Created]            DATETIME2(7) NOT NULL,
  [Updated]            DATETIME2(7) NOT NULL,
  [PayrollResultId]    INT NOT NULL,
  [Source]             NVARCHAR(128) NOT NULL,
  [Name]               NVARCHAR(128) NOT NULL,
  [NameLocalizations]  NVARCHAR(MAX) NULL,
  [Slot]               NVARCHAR(128) NULL,
  [ValueType]          INT NOT NULL,
  [Value]              NVARCHAR(MAX) NULL,
  [NumericValue]       DECIMAL(28,6) NULL,
  [Culture]            NVARCHAR(128) NOT NULL,
  [Start]              DATETIME2(7) NULL,
  [StartHash]          INT NOT NULL,
  [End]                DATETIME2(7) NULL,
  [Tags]               NVARCHAR(MAX) NULL,
  [Attributes]         NVARCHAR(MAX) NULL
);
```
