# Payroll Engine Build Plan from Scratch

A component-wise plan for building a full payroll engine from scratch. **The codebase (payroll-engine, payroll-engine-backend, payroll-engine-console, payroll-engine-xxxx) is the main source**: each component references actual files, classes, interfaces, and flows in the workspace. Use this as a roadmap and checklist; reimplement in any language by mirroring these structures and flows.

---

## Part 1: Overall Phases

| Phase | Purpose | In this codebase |
|-------|--------|-------------------|
| **Discovery** | Scope, constraints, domain | Domain entities in `payroll-engine-backend/Domain/Domain.Model/`; regulation examples in `payroll-engine-regulation-France/`, `payroll-engine-regulations-swiss/`, `payroll-engine-template/`. |
| **Design** | Architecture, data model, contracts | Domain.Model + Repository interfaces; Api.Model (DTOs); Domain.Scripting (script contract). |
| **Implementation** | Build in dependency order | Backend (Domain → Persistence → Api → Backend.Server); Console (Commands → PayrollConsole); Client libs (Core → Scripting → Services). |
| **Validation** | E2E and ops readiness | Examples in `payroll-engine/Examples/`; Tests in `payroll-engine/Tests/`, regulation Scripts.Tests. |

---

## Part 2: Component-Wise Breakdown (Codebase as Source)

Each component lists **codebase reference** first (where to look), then decisions, design (as implemented), activities, and implementation order.

---

### Component 1: Domain Model (Core Concepts)

**Codebase reference**:
- **Entities**: `payroll-engine-backend/Domain/Domain.Model/`
  - `Tenant.cs` (Identifier, Culture, Calendar, Attributes)
  - `Payroll.cs` (Name, DivisionId, RegulationReferences, ClusterSets, …)
  - `RegulationReference.cs`, `RegulationShare.cs`
  - `Division` (via repository: `IDivisionRepository.cs`)
  - `Employee.cs`, `User.cs`
  - `Payrun.cs`, `PayrunJob.cs`, `PayrunJobInvocation.cs`, `PayrunJobEmployee.cs`
  - `PayrunJobFactory.cs` (create/update job from invocation + calculator)
  - `RetroPayrunJob.cs`
- **Base**: `DomainObjectBase.cs`, `IDomainObject.cs`, `IIdentifiableObject.cs`
- **Value types**: Period/cycle from `IPayrollCalculator`, `PayrollCalculator.cs`; `Calendar.cs`; period types (`CalendarMonthPayrollPeriod.cs`, `WeekPayrollPeriod.cs`, etc.)

**Decisions** (as in codebase):
- Tenant is top-level; no “organization” above.
- Payroll has `DivisionId` (1:1 division per payroll); payroll has regulation references and cluster sets (wage type/collector/case filtering).
- Payrun: one payrun per period; contains many **payrun jobs** (one job can cover many employees in sync mode, or one job per employee in async mode; see `PayrunJob`, `PayrunJobEmployee`).
- Regulation sharing: payroll references multiple regulations via `RegulationReference`; **derived** wage types/collectors/scripts come from `IPayrollRepository.GetDerivedWageTypesAsync`, `GetDerivedCollectorsAsync`, etc., with regulation date and override type.

**Design (as implemented)**:
- Tenant → Regulations, Payrolls, Divisions, Employees (all tenant-scoped).
- Payroll → DivisionId; payroll references regulations; derived regulation children loaded by `PayrollRepository` with regulation date and evaluation date.
- Payrun → PayrollId; PayrunJob → PayrunId, optional EmployeeId (sync: one job for all employees; async: one job per employee). PayrunJob has ParentJobId for retro.
- Invariants: `PayrollValidator.ValidateRegulations`; period from `IPayrollCalculator.GetPayrunPeriod`.

**Activities**:
1. Implement Tenant, Regulation, Payroll, Division, Employee, User, Payrun, PayrunJob (and RetroPayrunJob if needed).
2. Implement PayrunJobFactory: from PayrunJobInvocation + calculator → create/update PayrunJob (period, cycle, evaluation date).
3. Define regulation derivation: how payroll resolves “effective” wage types, collectors, scripts for a given regulation date (see `GetDerivedWageTypesAsync`, `GetDerivedCollectorsAsync`).

**Implementation**: Start with domain POJOs/entities; no DB. Unit tests for PayrunJobFactory and period logic.

**Dependencies**: None.

---

### Component 2: Case and Case Value Model

**Codebase reference**:
- **Entities**: `Domain.Model/Case.cs`, `CaseField.cs`, `CaseValue.cs`, `CaseSet.cs`, `CaseFieldSet.cs`
- **Case value**: `CaseValue.cs` — DivisionId, EmployeeId, CaseName, CaseFieldName, CaseSlot, ValueType, Value, Start, End, Forecast, Tags, Attributes
- **Scope**: Separate repositories per scope: `IGlobalCaseValueRepository`, `INationalCaseValueRepository`, `ICompanyCaseValueRepository`, `IEmployeeCaseValueRepository`
- **Resolution**: `CaseValueCache` (e.g. `Domain.Model/Repository/CaseValueCache.cs`) — loads case values for a parent (tenant/national/company/employee), division, evaluation date, forecast. Used as “case value repository” for scripts.
- **Provider**: `Domain.Scripting/CaseValueProvider.cs`, `CaseValueProviderCalculation.cs` — aggregates global, national, company, employee caches; provides `GetValue(caseName, caseFieldName, slot)` etc. for script API.
- **Stored procedures**: `GetGlobalCaseValues.sql`, `GetNationalCaseValues.sql`, `GetCompanyCaseValues.sql`, `GetEmployeeCaseValues.sql` (Persistence.SqlServer) — effective-dated queries.

**Decisions (as in codebase)**:
- Scopes: Global (tenant), National (tenant), Company (division), Employee (employee + division). Stored in separate tables/repos.
- Effective dating: CaseValue has Start and End; “valid at date” = start ≤ date and (end null or end ≥ date). Queries in stored procs filter by evaluation date.
- CaseSlot: optional; for multi-slot case fields (e.g. multiple insurance codes).
- ValueType: enum (String, Boolean, Integer, Decimal, DateTime, etc.); Value stored as JSON string; NumericValue derived for numeric types.

**Design (as implemented)**:
- CaseValue: TenantId (implicit via parent), DivisionId, EmployeeId (for employee scope), CaseName, CaseFieldName, CaseSlot, ValueType, Value, Start, End, Forecast.
- CaseValueCache: built from one of the four case value repos + parentId, divisionId, evaluationDate, forecast; used by CaseValueProvider.
- CaseValueProvider: takes global, national, company, employee caches + settings (DbContext, Calculator, CaseFieldProvider, EvaluationPeriod, EvaluationDate, RetroDate); scripts call into it for case values.

**Activities**:
1. Implement Case, CaseField, CaseValue; implement four case value repositories (or one with scope filter).
2. Implement CaseValueCache: load case values for (parentId, divisionId, evaluationDate) with start/end filtering.
3. Implement CaseValueProvider: given four caches, resolve value by case name + field name + slot (scope priority: employee > company > national > global; date: valid at evaluation date).

**Implementation**: Unit tests: same field at different scopes; overlapping Start/End; missing value.

**Dependencies**: Domain model (Tenant, Division, Employee).

---

### Component 3: Wage Types and Collectors (Calculation Model)

**Codebase reference**:
- **Entities**: `Domain.Model/WageType.cs`, `Domain.Model/Collector.cs` (and audit types)
- **WageType**: WageTypeNumber, Name, Collectors (list of names), CollectorGroups, ValueExpression, ResultExpression, ValueActions, ResultActions, ValueType, Calendar, Culture, Clusters; implements script track (ValueScript, ResultScript, GetFunctionTypes, GetFunctionScript, GetFunctionActions)
- **Collector**: Name, CollectMode, Negated, ValueType, etc.; script hooks for CollectorStart, CollectorApply, CollectorEnd
- **Derived**: `DerivedWageType.cs`, `DerivedCollector.cs` — regulation-resolved wage type/collector with namespace and attributes
- **Results**: `WageTypeResult.cs`, `CollectorResult.cs`, `PayrollResultSet.cs`, `WageTypeResultSet.cs`, `CollectorResultSet.cs`
- **Ordering**: Derived wage types and collectors come from `PayrollRepository.GetDerivedWageTypesAsync`, `GetDerivedCollectorsAsync`; wage types grouped by WageTypeNumber (ILookup<decimal, DerivedWageType>), collectors by name (ILookup<string, DerivedCollector>). Evaluation order: **collectors first (CollectorStart)**, then **wage types in derived order** (each wage type: WageTypeValue → add to collectors via CollectorApply), then **CollectorEnd** per collector. See `PayrunProcessor.CalculateEmployeeAsync`.

**Decisions (as in codebase)**:
- Wage type identity: decimal WageTypeNumber per regulation; “derived” wage type includes regulation id and namespace.
- Collector identity: string Name per regulation; derived collector has name and namespace.
- Feed: WageType.Collectors and WageType.CollectorGroups (list of names); collector does not list wage types. “Is collector available for wage type” = `PayrunProcessorRegulation.IsCollectorAvailable(derivedWageType, derivedCollector)` (wage type’s Collectors/CollectorGroups contain collector name).
- Evaluation: (1) CollectorStart for all collectors (init collector result). (2) For each derived wage type: if WageTypeAvailable then CalculateWageTypeValue → WageTypeResult; then for each collector that this wage type feeds, CollectorApply(wage type result) → update collector value. (3) CollectorEnd for all collectors. Order of wage types = order of context.DerivedWageTypes (from repository).

**Design (as implemented)**:
- WageTypeResult: WageTypeId, WageTypeNumber, Value, Start, End, Attributes, CustomResults; CollectorResult: CollectorId, CollectorName, Value, CollectMode, Negated, …
- PayrollResultSet: WageTypeResults, CollectorResults, PayrunResults; attached to employee and period.
- Result persistence: `ResultProvider` (Domain.Scripting/ResultProvider.cs) + `IPayrollResultRepository`, `IPayrollConsolidatedResultRepository`; results written after each employee in `PayrunProcessor` (StoreResultsAsync).

**Activities**:
1. Implement WageType and Collector metadata; implement DerivedWageType/DerivedCollector (or equivalent) and derivation from payroll + regulation date.
2. Implement “collector available for wage type” (wage type’s Collectors/CollectorGroups vs collector name).
3. Implement WageTypeResult and CollectorResult; implement PayrollResultSet and result persistence (ResultProvider + repositories).

**Implementation**: Order of execution is fixed in PayrunProcessor: CollectorStart → [foreach wage type: WageTypeValue, then CollectorApply for each collector] → CollectorEnd. See `PayrunProcessor.cs` lines ~782–906.

**Dependencies**: Domain model (Regulation, Payroll). Case value provider needed for WageTypeValue scripts.

---

### Component 4: Lookups

**Codebase reference**:
- **Entities**: `Domain.Model/LookupSet.cs`, `LookupData.cs` (lookup values); regulation lookups via `IRegulationLookupProvider`
- **Provider**: `Domain.Scripting/RegulationLookupProvider.cs` — built from DbContext, PayrollRepository, PayrollQuery (TenantId, PayrollId, RegulationDate, EvaluationDate), RegulationRepository, LookupSetRepository. Scripts get lookup value by lookup name + key (and optional range).
- **Persistence**: `GetDerivedLookups.sql`, `GetDerivedLookupValues.sql`, `GetLookupRangeValue.sql` — derived lookups by regulation date; range value by key and date.

**Decisions (as in codebase)**:
- Lookups are per regulation; “derived” lookups resolved for payroll at regulation date.
- Lookup values can have range (start/end); `GetLookupRangeValue` returns value valid at a given date.
- Script API: regulation lookup provider is passed into script controller; scripts call e.g. lookup by name and key.

**Design (as implemented)**:
- LookupSet (name, etc.); lookup values with key and value/attributes, optional range.
- RegulationLookupProvider implements resolution using PayrollRepository + RegulationRepository + LookupSetRepository; used in PayrunScriptController and WageTypeScriptController, etc.

**Activities**:
1. Implement LookupSet and LookupValue (or equivalent); implement derived lookup loading for payroll + regulation date.
2. Implement range resolution: get value for (lookup name, key, date).
3. Expose lookup provider in script context so rules can call “lookup(name, key)” or “lookup(name, key, date)”.

**Implementation**: Unit tests: exact key; range; missing key. Integration: persist lookup values, resolve in processor.

**Dependencies**: Domain model (Regulation); PayrollRepository (derived regulation).

---

### Component 5: Periods and Calendars

**Codebase reference**:
- **Model**: `Domain.Model/Calendar.cs`; period types: `CalendarMonthPayrollPeriod.cs`, `WeekPayrollPeriod.cs`, `BiMonthPayrollPeriod.cs`, `SemiMonthPayrollPeriod.cs`, `QuarterPayrollPeriod.cs`, `YearPayrollPeriod.cs`
- **Calculator**: `Domain.Model/IPayrollCalculator.cs`, `PayrollCalculator.cs`, `DefaultPayrollCalculatorProvider.cs` — GetPayrunPeriod(date), GetPeriods(year), etc.
- **Usage**: PayrunContext has Calculator; PayrunJob has PeriodStart, PeriodEnd, CycleName, CycleStart, CycleEnd, EvaluationDate; PayrunJobFactory sets these from calculator and invocation.

**Decisions (as in codebase)**:
- Calendar name is on Division and Tenant (fallback); employee can override. Calculator is obtained per (tenant, user, culture, calendar name).
- Period and cycle: PayrunJob has period (start/end) and cycle (start/end, name). Used for YTD and retro (e.g. retro periods within cycle).

**Design (as implemented)**:
- IPayrollCalculator: GetPayrunPeriod(date), GetPayrunPeriods(periodStart, periodEnd), etc. Multiple period types (month, week, etc.) implement period boundaries.
- PayrunJobFactory.CreatePayrunJob / UpdatePayrunJob: set PeriodStart, PeriodEnd, CycleStart, CycleEnd, PeriodName, CycleName, EvaluationDate from job invocation and calculator.

**Activities**:
1. Implement Calendar (metadata) and at least one period type (e.g. calendar month).
2. Implement IPayrollCalculator (or equivalent) that returns period start/end and cycle for a given date.
3. Use in PayrunJob creation so each job has a well-defined period and evaluation date.

**Implementation**: Unit tests: period for date; list of periods in year. Used by processor for context.EvaluationPeriod and retro loop.

**Dependencies**: None (can be parallel to domain).

---

### Component 6: Rules Engine (Scripting)

**Codebase reference**:
- **Script storage**: `Domain.Model/Script.cs` — Name, FunctionTypes (bitmask), Value (C# source), OverrideType. Scripts loaded per regulation via `GetDerivedScripts` (regulation date).
- **Compilation**: `Domain.Scripting/ScriptCompiler.cs` — takes IScriptObject (e.g. wage type, collector), function scripts (expressions/actions), optional Script list from DB, optional embedded script names. Builds object codes + action codes + function codes; concatenates script values; compiles with `CSharpCompiler` (Roslyn) into one assembly. `ScriptCompileResult` returns compiled type.
- **Cache**: `Domain.Scripting/AssemblyCache.cs` — caches compiled assembly by (script object type, script object); uses IScriptProvider to get script hash; timeout for cache expiry. `FunctionHost.GetObjectAssembly(type, scriptObject)` uses AssemblyCache.
- **Host**: `Domain.Scripting/FunctionHost.cs` — GetObjectAssembly(type, scriptObject); AddTask, AddLog (for script-side logging). Used by all script controllers.
- **Controllers**: `Domain.Scripting/Controller/` — PayrunScriptController (Start, End, EmployeeStart, EmployeeEnd), WageTypeScriptController (WageTypeValue, WageTypeResult), CollectorScriptController (CollectorStart, CollectorApply, CollectorEnd), CaseScriptController (CaseAvailable, etc.). Each builds a “runtime” (e.g. PayrunStartRuntime), gets compiled type from FunctionHost, invokes method with context.
- **Runtime base**: `Domain.Scripting/Runtime/` — PayrunStartRuntime, PayrunEndRuntime, PayrunEmployeeStartRuntime, PayrunEmployeeEndRuntime, WageTypeValueRuntime, WageTypeResultRuntime, CollectorStartRuntime, CollectorApplyRuntime, CollectorEndRuntime, etc. Each receives settings (DbContext, Tenant, Payroll, CaseValueProvider, RegulationLookupProvider, ResultProvider, …) and invokes the compiled script method.
- **Script object**: Wage types and collectors implement `IScriptObject` (GetFunctionTypes, GetFunctionScript, GetFunctionActions, GetEmbeddedScriptNames). Regulation scripts (e.g. “Rules”) are merged into the same compilation unit via ScriptCompiler constructor (scripts parameter).
- **Client.Scripting**: `payroll-engine-client-scripting` — provides base class/templates that the compiler merges with regulation script; e.g. WageTypeValue function signature and base code. Backend references package PayrollEngine.Client.Scripting.

**Decisions (as in codebase)**:
- Execution model: **dynamic C# scripts** stored in DB (Script.Value); compiled at runtime with Roslyn; cached per (script object type, script object) in AssemblyCache.
- Lifecycle: PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd, WageTypeAvailable, WageTypeValue, WageTypeResult, CollectorStart, CollectorApply, CollectorEnd, CaseAvailable, CaseBuild, CaseValidate, … (see FunctionType enum and Runtime folder).
- Script API: CaseValueProvider (case values), RegulationLookupProvider (lookups), ResultProvider (write wage type/collector results), Calculator (periods), Division, Employee, Payroll, Payrun, PayrunJob, Tenant, User. Scripts cannot do arbitrary I/O; they use these providers.
- Caching: AssemblyCache with timeout; script hash from IScriptProvider to invalidate when regulation scripts change.

**Design (as implemented)**:
- One compiled assembly per “script object” (e.g. per wage type or per regulation script set). ScriptCompiler combines: (1) object code (base class for wage type/collector), (2) action code (parsed from ValueActions/ResultActions), (3) function code (ValueExpression/ResultExpression + embedded), (4) regulation Script.Value (e.g. Rules.cs). CSharpCompiler compiles to in-memory assembly; CollectibleAssemblyLoadContext for unload.
- Invocation: PayrunScriptController.Start(context) → get PayrunScriptController type from FunctionHost for payrun → invoke Start method with runtime context. WageTypeScriptController.Value(derivedWageType, …) → get type for wage type → invoke WageTypeValue method. Same pattern for CollectorStart, CollectorApply, CollectorEnd.

**Activities**:
1. Choose scripting model: dynamic (like this) vs precompiled vs external service. If dynamic: choose language and compiler (C# Roslyn, JS Graal, Groovy, etc.).
2. Define lifecycle method set (PayrunStart, EmployeeStart, WageTypeValue, CollectorStart, CollectorApply, CollectorEnd, EmployeeEnd, PayrunEnd, WageTypeAvailable, CaseAvailable).
3. Implement “script object” contract (function types, script source per function); implement compiler that merges base + regulation script and produces invokable type.
4. Implement cache (key = regulation + script object identity; invalidate on script change). Implement invocation: build context (case values, lookups, result provider, employee, period, …), call compiled method.
5. Sandbox: no file/network in script; timeout per invocation (FunctionHostSettings, BackendScriptingSpecification).

**Implementation**: Mirror ScriptCompiler + CSharpCompiler + AssemblyCache + FunctionHost; mirror one controller (e.g. WageTypeScriptController) and its runtime (WageTypeValueRuntime). Unit test: mock context, one wage type script returns decimal.

**Dependencies**: CaseValueProvider (Component 2), Lookups (Component 4), Wage types/collectors (Component 3), Period (Component 5). ResultProvider for writing results.

---

### Component 7: Persistence (Data Layer)

**Codebase reference**:
- **Repository interfaces**: `Domain.Model/Repository/` — ITenantRepository, IRegulationRepository, IPayrollRepository, IDivisionRepository, IEmployeeRepository, IPayrunRepository, IPayrunJobRepository; IGlobalCaseValueRepository, INationalCaseValueRepository, ICompanyCaseValueRepository, IEmployeeCaseValueRepository; IWageTypeRepository, ICollectorRepository, IScriptRepository; ILookupSetRepository, ILookupValueRepository (or generic); IPayrollResultRepository, IPayrollConsolidatedResultRepository; IWageTypeResultRepository, ICollectorResultRepository; etc. See full list in `Domain.Model/Repository/`.
- **Persistence layer**: `payroll-engine-backend/Persistence/Persistence/` — repository implementations; `Persistence.SqlServer/` — SQL Server–specific (DbContext, stored procedures in StoredProcedures/, functions in Functions/).
- **Stored procedures**: e.g. GetGlobalCaseValues, GetEmployeeCaseValues, GetDerivedWageTypes, GetDerivedCollectors, GetDerivedScripts, GetDerivedLookups, GetDerivedLookupValues, GetLookupRangeValue; GetWageTypeResults, GetCollectorResults, GetConsolidatedWageTypeResults, GetConsolidatedCollectorResults; DeletePayrunJob, etc.
- **DbContext**: `Persistence.SqlServer/DbContext.cs` — tenant-scoped connection and transaction.

**Decisions (as in codebase)**:
- One schema; tenant_id (or equivalent) on all tenant-scoped tables. Repositories take TenantId (and parent ids) in every call.
- Derived data: wage types, collectors, scripts, lookups are not stored denormalized; they are “derived” via stored procs that join regulation layers and filter by regulation date and override type.
- Results: WageTypeResult and CollectorResult stored under PayrollResult; consolidated results for YTD in separate tables (GetConsolidatedWageTypeResults, etc.).
- Transaction: IDbContext; repositories accept context for transaction scope (e.g. one transaction per payrun job when storing results).

**Design (as implemented)**:
- Tables align with domain entities: Tenant, Regulation, Payroll, Division, Employee, User; Case, CaseField, CaseValue (per scope: global, national, company, employee); WageType, Collector, Script; LookupSet, LookupValue; Payrun, PayrunJob, PayrunJobEmployee; PayrollResult, WageTypeResult, CollectorResult; etc. Plus audit tables (e.g. WageTypeAudit).
- Effective-dated queries: case values and lookup values use Start/End in WHERE. Indexes on (tenant, parent, division, employee, case, field, start) for case values.

**Activities**:
1. Define schema (tables) for all entities; add tenant_id and FKs; add effective date columns where needed.
2. Implement repository interfaces and implementations; implement derived reads (GetDerivedWageTypes, GetDerivedCollectors, GetDerivedScripts, GetDerivedLookups) with regulation date and evaluation date.
3. Implement result repositories: add wage type and collector results for a payroll result (payrun job); support consolidated (YTD) queries if required.
4. Use DbContext (or equivalent) for transaction scope in processor when saving results.

**Implementation**: Migrations for schema; integration tests: insert tenant, regulation, payroll, case value; query derived wage types; insert payrun job and results.

**Dependencies**: Domain model (Components 1–4). No dependency on rules engine or processor.

---

### Component 8: Exchange Format and Import/Export

**Codebase reference**:
- **Exchange model**: `payroll-engine-client-core/Client.Core/Model/Exchange.cs` — root has Tenants (list of ExchangeTenant), RegulationShares, CreatedObjectDate, Schema. ExchangeTenant contains Payrolls, Regulations, Employees, etc., and each regulation contains WageTypes, Collectors, Cases, Lookups, Scripts, etc.
- **Read**: `Client.Core/Exchange/ExchangeReader.cs` — ReadAsync(fileName) → Exchange; supports JSON and ZIP (expands and reads JSON from ZIP).
- **Import**: `Client.Core/Exchange/ExchangeImport.cs` — visits Exchange graph and calls backend API (HTTP) to upsert tenants, regulations, payrolls, wage types, collectors, cases, lookups, scripts, employees, case values, etc. Uses Visitor pattern; import order and reference resolution (e.g. by identifier) are in the visitor/import logic.
- **Options**: `ExchangeImportOptions.cs`, `DataImportMode.cs`; export: `ExchangeExportOptions.cs`. Export is typically API GET that returns Exchange JSON (backend builds Exchange from repositories and serializes).

**Decisions (as in codebase)**:
- Single root object Exchange with Tenants; each tenant is a full subtree (payrolls, regulations, wage types, collectors, cases, lookups, scripts, employees, case values). Regulation shares at root level.
- References: by identifier (e.g. regulation identifier); import creates or updates by identifier; scripts can be inlined (Script.Value) or referenced (e.g. file path in exchange for large scripts).
- Import path: Console or client calls ExchangeReader.ReadAsync → ExchangeImport.ImportAsync with HttpClient → backend API endpoints (POST/PUT tenants, regulations, scripts, wage types, collectors, cases, lookups, employees, case values). Backend persists via repositories.

**Design (as implemented)**:
- Exchange.cs: Tenants (List<ExchangeTenant>), RegulationShares, CreatedObjectDate, Schema. ExchangeTenant: Identifier, Payrolls (each with Regulations, WageTypes, Collectors, Cases, Lookups, Scripts, Employees with CaseValues), etc.
- Import: traverse tenants → payrolls → regulations → wage types, collectors, cases, lookups, scripts → employees → case values; for each, HTTP PUT/POST to corresponding API (e.g. RegulationApiEndpoints.RegulationScriptsUrl(tenantId, regulationId)). Order respects FKs (tenant before regulation, regulation before script/wage type/collector).

**Activities**:
1. Define Exchange JSON shape (and optional JSON Schema); implement DTOs and (de)serialization.
2. Implement ExchangeReader: read JSON or ZIP and deserialize to Exchange.
3. Implement ExchangeImport: traverse Exchange and call backend API (or call repository layer directly if import runs in backend). Resolve references by identifier; upsert in dependency order.
4. Implement export: API or service that loads tenant (and children) from repositories, builds Exchange object, serializes to JSON.

**Implementation**: See Client.Core/Exchange/; backend API has import/export endpoints that use same Exchange shape. Integration test: export tenant → import into empty DB → export again and compare key fields.

**Dependencies**: Persistence (Component 7); domain/API model.

---

### Component 9: REST API

**Codebase reference**:
- **API layer**: `payroll-engine-backend/Api/` — Api.Model (DTOs), Api.Core (middleware, PayrunJobWorkerService, query, spec), Api.Map (domain ↔ API map), Api.Controller (HTTP controllers). Backend.Controller references Api.Controller; Backend.Server hosts the app and uses Persistence.SqlServer.
- **Controllers**: Under Api.Controller/ and Backend.Controller/ — tenant, regulation, payroll, division, employee, payrun, payrun job, case value, wage type, collector, script, lookup, result, report, etc. Endpoints follow pattern `/tenants/{tenantId}/regulations`, `/tenants/{tenantId}/payruns`, etc.
- **Payrun start**: Payrun is created; “start” enqueues payrun jobs (one or many). Worker (Api.Core/PayrunJobWorkerService) dequeues and runs PayrunProcessor.Process(jobInvocation). So “start payrun” = enqueue; processing is async.
- **Query**: Api.Core has query support (filter, order, select) and optional OData-style query.

**Decisions (as in codebase)**:
- REST with JSON; tenant in path; auth (e.g. API key) in middleware.
- Payrun execution: async. Start payrun → create/update payrun jobs and enqueue them; worker processes each job (PayrunProcessor.Process). API returns job id(s); client polls job status and gets results via result endpoints.

**Design (as implemented)**:
- Resources: Tenants, Regulations, Payrolls, Divisions, Employees, Payruns, PayrunJobs, CaseValues (global/national/company/employee), WageTypes, Collectors, Scripts, Lookups, Results (by payrun job or payroll result). Import/export endpoints for Exchange.
- Payrun: POST create payrun; POST start (enqueue jobs); GET payrun jobs and status; GET results for payrun job (wage type + collector results).

**Activities**:
1. Implement HTTP endpoints for all resources (CRUD); implement import/export (accept Exchange JSON, call import service; export: build Exchange from repos and return JSON).
2. Implement payrun create and payrun start (start = enqueue jobs to IPayrunJobQueue). Implement worker (Component 11) that dequeues and runs processor.
3. Implement result endpoints: get wage type and collector results by payrun job id (or payroll result id).

**Implementation**: Controllers call application services (Domain.Application *Service classes); services use repositories. Integration test: create tenant, create payrun, start payrun, poll until jobs complete, get results.

**Dependencies**: Persistence (7), Exchange (8). Payrun start depends on Processor (10) and Worker (11).

---

### Component 10: Payrun Processor (Orchestrator)

**Codebase reference**:
- **Entry**: `Domain.Application/PayrunProcessor.cs` — constructor (Tenant, Payrun, PayrunProcessorSettings). Process(PayrunJobInvocation) is the main entry.
- **Flow (summary)**:
  1. Load payrun, payroll, division; build PayrunContext (User, Payroll, Division, Calculator, EvaluationDate, EvaluationPeriod, PayrunJob, RetroDate, StoreEmptyResults).
  2. Resolve derived regulations, case field provider, global/national/company case value caches (CaseValueCache), RegulationLookupProvider, derived collectors, derived wage types.
  3. Resolve employees (from invocation or repository); add PayrunJobEmployee entries; validate payroll; create PayrunProcessorRegulation and PayrunProcessorScripts.
  4. **PayrunStart**: processorScript.PayrunStart(context) — PayrunScriptController.Start (once per payrun).
  5. For each employee: build employee case value cache; CaseValueProvider(global, national, company, employee caches); **EmployeeStart**: processorScripts.EmployeeStart(caseValueProvider, context). Then **CalculateEmployeeAsync** (see below). Then **EmployeeEnd**: processorScripts.EmployeeEnd(caseValueProvider, context).
  6. **PayrunEnd**: processorScript.PayrunEnd(context).
  7. Update job status and message; on error AbortJobAsync.
- **CalculateEmployeeAsync** (per employee):
  - Push culture and calendar from employee/division/tenant; set context.Calculator (employee-specific if needed).
  - Build PayrollResultSet (PayrollId, PayrunId, PayrunJobId, EmployeeId, DivisionId, period, cycle). SetupEmployeeCollectors: one CollectorResult per derived collector (initial value from collector.Result or 0).
  - **CollectorStart**: foreach derived collector, processorRegulation.CollectorStart(…) → invoke script; update collector result; collect retro jobs.
  - **Wage type loop**: foreach derived wage type — (optional) set wage type calendar; **IsWageTypeAvailable** (script); **CalculateWageTypeValue** (script) → WageTypeResultSet; if execution restart requested, reset and re-run loop (max N times); add wage type result to payroll result; **CollectorApply** for each collector that this wage type feeds → update collector value; collect retro jobs.
  - **CollectorEnd**: foreach derived collector, processorRegulation.CollectorEnd(…) → invoke script; update collector result; collect retro jobs.
  - Get case value payrun results (optional); remove unchanged results if incremental mode; **StoreResultsAsync** (ResultProvider); process retro payrun jobs (create child jobs and process them recursively with RetroPayMode.None).
  - Restore context.Calculator and culture.
- **Settings**: PayrunProcessorSettings (DbContext, repositories, PayrollCalculatorProvider, ScriptProvider, WebhookDispatchService, FunctionLogTimeout, etc.). PayrunProcessorRepositories wraps repos for processor use. ResultProvider = new ResultProvider(PayrollResultRepository, PayrollConsolidatedResultRepository).

**Decisions (as in codebase)**:
- One Process call can handle one payrun job (async: one job = one employee) or one payrun with many employees (sync: one job = all employees). Job invocation contains PayrunId, optional EmployeeIdentifiers, optional pre-created PayrunJobId.
- Retro: retro jobs created from CollectorStart/CollectorEnd (RetroPayrunJob list); processor creates child PayrunProcessor with same Tenant/Payrun, invokes Process with ParentJobId, RetroPayrunJobs, RetroPayMode.None, JobResult.Incremental, and period = retro period. Loops until retro period reaches evaluation period start.
- Failure: if PayrunStart returns false or exception, AbortJobAsync. If employee fails, context.Errors.Add(employee, exception); after all employees, if context.Errors.Any() then AbortJobAsync. Per–wage type exception propagates and aborts job.

**Design (as implemented)**:
- PayrunContext holds all context (User, Payroll, Division, PayrunJob, Calculator, EvaluationDate, EvaluationPeriod, DerivedRegulations, CaseFieldProvider, Global/National/Company/Employee case value caches, RegulationLookupProvider, DerivedWageTypes, DerivedCollectors, ExecutionPhase, etc.). Implements IRegulationProvider for script resolution.
- PayrunProcessorScripts: PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd — delegates to PayrunScriptController with context. PayrunProcessorRegulation: IsWageTypeAvailable, CalculateWageTypeValue, CollectorStart, CollectorApply, CollectorEnd — delegates to WageTypeScriptController and CollectorScriptController with context and case value provider.
- Result persistence: after each employee, StoreResultsAsync(payrollResult); ResultProvider stores wage type and collector results (and consolidated if needed).

**Activities**:
1. Implement PayrunProcessor: load job (or create from invocation), build context, load derived wage types and collectors, load case value caches and lookup provider.
2. Implement PayrunProcessorScripts and PayrunProcessorRegulation (or single orchestrator) that call script controllers with correct context.
3. Implement lifecycle sequence: PayrunStart → [per employee: EmployeeStart → CollectorStart → wage type loop (WageTypeValue + CollectorApply) → CollectorEnd → store results → EmployeeEnd] → PayrunEnd.
4. Implement retro: when CollectorStart/CollectorEnd return retro jobs, create child payrun jobs for retro periods and call processor recursively (or enqueue).
5. Wire ResultProvider to persistence (add wage type results and collector results for payroll result).

**Implementation**: Mirror PayrunProcessor.cs and PayrunProcessorRegulation.cs, PayrunProcessorScripts.cs; use Component 2 (CaseValueProvider), 3 (ordering and result model), 4 (lookup provider), 5 (calculator), 6 (script controllers), 7 (repositories). Integration test: one payrun job with one employee, one wage type, one collector, minimal regulation script; assert results stored.

**Dependencies**: All of 2–7 and 6 (rules engine).

---

### Component 11: Background Job / Worker

**Codebase reference**:
- **Queue**: `Domain.Application/PayrunJobQueue.cs` — interface IPayrunJobQueue; implementation likely in-memory or DB-backed. PayrunJobQueueItem contains TenantId, Payrun, PayrunJobId, JobInvocation (and Tenant, Payrun loaded for processor).
- **Worker**: `Api/Api.Core/PayrunJobWorkerService.cs` — BackgroundService; ExecuteAsync loop: DequeueAsync(stoppingToken) → ProcessJobAsync(queueItem). ProcessJobAsync: create scope; get IDbContext, IConfiguration, IScriptProvider, IWebhookDispatchService; build PayrunProcessorSettings; new PayrunProcessor(tenant, payrun, settings); await processor.Process(queueItem.JobInvocation); MarkJobCompletedAsync; send webhook. On exception: MarkJobAbortedAsync.
- **Enqueue**: When API “start payrun” is called, controller creates payrun jobs (if async) and enqueues one PayrunJobQueueItem per job (or one item for whole payrun in sync mode). Queue is injected (IPayrunJobQueue) into the service that starts the payrun.

**Decisions (as in codebase)**:
- One queue item = one payrun job (one employee in async mode). Worker processes one job at a time per worker instance; scale by multiple workers.
- Retries: not shown in snippet; typically mark job failed and optionally retry (e.g. re-enqueue) or dead-letter.
- Idempotency: processor loads job by id; if job already completed, could skip or re-run depending on flag.

**Design (as implemented)**:
- IPayrunJobQueue: DequeueAsync(CancellationToken), EnqueueAsync(…). PayrunJobQueueItem: TenantId, Payrun, PayrunJobId, JobInvocation (and loaded Tenant, Payrun for processor).
- PayrunJobWorkerService: long-running loop; each iteration dequeues one item, creates a new DI scope, builds PayrunProcessor with scoped services, runs Process, then marks job completed or aborted.

**Activities**:
1. Implement queue (in-memory or DB or external). Implement enqueue on “start payrun” (one item per job or one per payrun).
2. Implement worker: dequeue → build processor with scoped services → Process(jobInvocation) → mark completed/aborted.
3. Wire API “start payrun” to create jobs and enqueue items. Ensure processor receives correct Tenant, Payrun, and JobInvocation (with PayrunJobId and employee identifiers if async).

**Implementation**: See Api.Core/PayrunJobWorkerService.cs; Domain.Application/PayrunJobQueue.cs (or equivalent). Integration test: start payrun (async) → wait for worker to process → assert job status completed and results present.

**Dependencies**: Payrun Processor (10), Persistence (7). API (9) triggers enqueue.

---

### Component 12: CLI / Tooling

**Codebase reference**:
- **Entry**: `payroll-engine-console/PayrollConsole/Program.cs` — ConsoleProgram; CommandManager and CommandProvider; RegisterCommands; ExecuteAsync runs CommandManager.ExecuteAsync(HttpClient). Commands use PayrollEngine.Client.Services (HTTP client) to call backend.
- **Commands**: `payroll-engine-console/Commands/` — PayrollImportCommand, PayrollImportFromDslCommand, PayrollExportCommand, PayrollResultsCommand; DslConvertCommand, DslPrepareCommand, DslPrepareAndImportCommand; CaseTestCommand; ReportCommand, DataReportCommand, ReportTestCommand; TenantDeleteCommand; UserVariableCommand; etc. See Commands/ folder.
- **Import**: PayrollImportCommand uses ExchangeReader + ExchangeImport with HttpClient; PayrollImportFromDslCommand resolves files from DSL config (e.g. dslsettings-France.json) and runs PayrollImport for each file.
- **Config**: appsettings.json, apisettings.json, dslsettings*.json; base URL and API key for backend.

**Decisions (as in codebase)**:
- CLI is a thin client: commands parse args and call backend via HTTP (Client.Services). No direct DB access.
- Import: read Exchange JSON (or ZIP) from file or from DSL output directory; POST to backend import endpoint or call tenant/regulation/script/… endpoints in order (ExchangeImport does the latter with HttpClient).
- Export: GET export endpoint or aggregate GETs; write Exchange JSON to file.
- Payrun: “start payrun” = HTTP POST to start endpoint; “results” = GET payrun job results.

**Design (as implemented)**:
- Command pattern: each command has Parameters class and ExecuteAsync(context, parameters). Context has HttpClient, Console, Logger. Commands registered in CommandProvider; CLI invokes by name and args.
- HTTP client: configured from appsettings/apisettings; used by ExchangeImport and by result/export commands.

**Activities**:
1. Implement CLI entry (parse command name and args); implement command registry and dispatcher.
2. Implement Import command: read file → ExchangeReader → ExchangeImport with HttpClient (or call single import API if backend exposes it).
3. Implement Export command: HTTP GET export (or aggregate GETs) → write file.
4. Implement Payrun start and Payrun results commands: POST start, GET job status, GET results.

**Implementation**: See payroll-engine-console/PayrollConsole/Program.cs and Commands/*.cs. Integration test: import file → export → diff; start payrun → poll → get results.

**Dependencies**: REST API (9). Client library (e.g. Client.Services) for HTTP.

---

### Component 13: Reporting and Documents (Optional)

**Codebase reference**:
- **Model**: `Domain.Model/Report.cs`, `ReportParameter.cs`, `ReportTemplate.cs`, `ReportRequest.cs`, `ReportResponse.cs`
- **Application**: `Domain.Application/ReportService.cs`, `ReportBuilder.cs`, `ReportProcessor.cs`, `ReportTool.cs` — build report from template and payrun results/case values; run scripts (ReportStart, ReportBuild, ReportEnd) for custom reports.
- **Script**: `Domain.Scripting/Controller/ReportScriptController.cs`, Runtime/ReportStartRuntime, ReportBuildRuntime, ReportEndRuntime. Report can have script; ReportProcessor invokes script with context (result set, parameters).
- **Document**: payroll-engine-document (e.g. PDF/Excel generation); used by report commands and possibly webapp.

**Decisions (as in codebase)**:
- Reports are regulation or global; have parameters (e.g. payrun job id); can have script (start/build/end). ReportBuilder/ReportProcessor load data (results, case values) and run script; output can be data set or file (PDF/Excel via document library).
- Optional: store generated documents (e.g. payslip) and link from payrun job.

**Design (as implemented)**:
- Report, ReportParameter, ReportTemplate; ReportProcessor runs with PayrollResultSet and case values; invokes report script if present; uses ReportBuilder and document generation for output.

**Activities**:
1. Implement Report and ReportParameter, ReportTemplate (metadata). Implement ReportProcessor: load report, load data (results + case values), run report script (if any), produce output (dataset or file).
2. Optional: integrate document library (PDF/Excel). Optional: API endpoint to generate report (e.g. GET /payrun-jobs/{id}/report).

**Implementation**: See Domain.Application/Report*.cs and ReportScriptController. Optional for minimal engine.

**Dependencies**: Persistence (7) for results and case values.

---

### Component 14: Non-Functional (Multi-Tenancy, Audit, Security, Performance)

**Codebase reference**:
- **Tenant**: All repository methods take tenant id (and parent ids); API resolves tenant from path (e.g. /tenants/{id}/…). No cross-tenant queries.
- **Audit**: Audit types (e.g. WageTypeAudit, ScriptAudit) and I*AuditRepository; audit tables store history. Some services write audit on create/update (see *AuditService in Domain.Application).
- **Script**: Scripts run in-process; no file/network access from script API. Timeout: FunctionLogTimeout in settings; BackendScriptingSpecification may define limits. AssemblyCache has timeout for unload.
- **Performance**: Derived wage types/collectors/scripts loaded once per payrun (or per job); case value caches built per employee; AssemblyCache caches compiled scripts; connection pooling via DbContext.

**Decisions (as in codebase)**:
- Multi-tenancy: strict tenant scoping in repos and API path.
- Audit: optional audit tables and audit services for key entities (regulation, script, wage type, etc.).
- Security: script sandbox = no I/O in script API; optional timeout and memory limits.
- Performance: cache derived regulation and compiled scripts; batch or incremental result write as needed.

**Activities**:
1. Enforce tenant in every repository call and API handler; validate tenant in path vs token if needed.
2. Add audit logging for critical entities (script, wage type, regulation, case value) if required.
3. Enforce script timeout and no arbitrary I/O in script context.
4. Tune caching (regulation, scripts) and DB indexes (case value by tenant, employee, date).

**Implementation**: Apply across API and persistence; optional audit services; AssemblyCache and script timeout in FunctionHostSettings.

**Dependencies**: All components that touch data or scripts.

---

## Part 3: Dependency Graph and Build Order

```
Component 1 (Domain)          Component 5 (Periods/Calculator)
        |                              |
        v                              v
Component 2 (Case + CaseValueCache)  -->  Component 6 (Scripting)  <--  Component 3 (WageTypes/Collectors)
        |                              |                              Component 4 (Lookups)
        v                              v
Component 7 (Persistence: Repositories + SQL)
        |
        v
Component 8 (Exchange: Reader + Import)  -->  Component 9 (API)
        |                                          |
        v                                          v
Component 10 (PayrunProcessor)  -->  Component 11 (PayrunJobWorkerService)
        |                                          |
        v                                          v
Component 12 (Console Commands)           Component 13 (Reporting, optional)
        |
        v
Component 14 (Non-functional) — apply across all
```

**Suggested implementation order** (match codebase layering):
1. Domain model (1) — Tenant, Payroll, Regulation, Division, Employee, Payrun, PayrunJob, WageType, Collector, Case, CaseValue, Script, LookupSet, etc.
2. Periods and calculator (5) — IPayrollCalculator, PayrunJobFactory.
3. Case value caches and CaseValueProvider (2) — CaseValueCache, CaseValueProvider; repositories for case values.
4. Wage types, collectors, result model (3) — DerivedWageType, DerivedCollector, WageTypeResult, CollectorResult, PayrollResultSet.
5. Lookups (4) — RegulationLookupProvider, derived lookups.
6. Scripting (6) — ScriptCompiler, FunctionHost, AssemblyCache, script controllers (Payrun, WageType, Collector), runtimes.
7. Persistence (7) — all repository implementations, stored procs, DbContext.
8. Exchange (8) — Exchange model, ExchangeReader, ExchangeImport (with HTTP or direct repo).
9. Payrun processor (10) — PayrunProcessor, PayrunProcessorRegulation, PayrunProcessorScripts, ResultProvider; integrate caches, lookup provider, script controllers, repositories.
10. Queue and worker (11) — IPayrunJobQueue, PayrunJobWorkerService; enqueue on start payrun.
11. API (9) — controllers, payrun start (enqueue), result endpoints, import/export.
12. CLI (12) — commands (import, export, payrun start, results).
13. Reporting (13) — optional.
14. Non-functional (14) — tenant enforcement, audit, script timeout, caching.

---

## Part 4: Milestones and Deliverables

| Milestone | Deliverables | Codebase reference |
|-----------|--------------|---------------------|
| **M1 – Domain and resolution** | Domain entities; CaseValueCache + CaseValueProvider; WageType/Collector metadata + derivation; Lookups + RegulationLookupProvider; IPayrollCalculator | Domain.Model; CaseValueCache; PayrollRepository.GetDerived*; RegulationLookupProvider; PayrollCalculator |
| **M2 – Scripting and persistence** | ScriptCompiler, FunctionHost, AssemblyCache; one script controller (e.g. WageType); repositories and stored procs; effective-dated case value and lookup queries | Domain.Scripting; Persistence; StoredProcedures |
| **M3 – Processor** | PayrunProcessor.Process; PayrunProcessorRegulation (WageTypeValue, CollectorStart/Apply/End); PayrunProcessorScripts (PayrunStart/End, EmployeeStart/End); ResultProvider; StoreResultsAsync | PayrunProcessor.cs; PayrunProcessorRegulation.cs; PayrunProcessorScripts.cs; ResultProvider |
| **M4 – API and import/export** | REST API (CRUD, import, export, payrun create/start); ExchangeReader + ExchangeImport; worker enqueue on start | Api.Controller; Backend.Controller; ExchangeReader; ExchangeImport; PayrunJobWorkerService |
| **M5 – Worker and CLI** | PayrunJobWorkerService (dequeue, Process); CLI commands (import, export, payrun start, results) | Api.Core/PayrunJobWorkerService; Commands/*.cs |
| **M6 – Production readiness** | Tenant enforcement, audit (if needed), script timeout/cache, load test | Repository tenant scoping; AssemblyCache; BackendScriptingSpecification |

---

## Part 5: Summary Checklist (Codebase Anchors)

| Area | Key codebase locations |
|------|-------------------------|
| **Domain** | Domain.Model/*.cs (Tenant, Payroll, Payrun, PayrunJob, WageType, Collector, Case, CaseValue, Script, …) |
| **Case** | CaseValue.cs; CaseValueCache.cs; CaseValueProvider.cs; I*CaseValueRepository; Get*CaseValues.sql |
| **Wage types/collectors** | WageType.cs, Collector.cs; DerivedWageType, DerivedCollector; GetDerivedWageTypes, GetDerivedCollectors; PayrunProcessorRegulation (CalculateWageTypeValue, CollectorStart, CollectorApply, CollectorEnd) |
| **Lookups** | LookupSet, LookupValue; RegulationLookupProvider; GetDerivedLookups, GetLookupRangeValue |
| **Periods** | IPayrollCalculator, PayrollCalculator; PayrunJobFactory; Calendar*, *PayrollPeriod.cs |
| **Scripting** | Script.cs; ScriptCompiler, CSharpCompiler, AssemblyCache, FunctionHost; Controller/*.cs, Runtime/*.cs; Client.Scripting package |
| **Persistence** | Domain.Model/Repository/*.cs; Persistence/; Persistence.SqlServer/ (DbContext, StoredProcedures, Functions) |
| **Exchange** | Client.Core/Model/Exchange.cs; ExchangeReader.cs; ExchangeImport.cs |
| **API** | Api/ (Api.Model, Api.Core, Api.Map, Api.Controller); Backend.Controller; Backend.Server |
| **Processor** | PayrunProcessor.cs; PayrunProcessorRegulation.cs; PayrunProcessorScripts.cs; PayrunContext; CalculateEmployeeAsync |
| **Worker** | PayrunJobWorkerService.cs; IPayrunJobQueue; PayrunJobQueueItem |
| **CLI** | PayrollConsole/Program.cs; Commands/ (PayrollImport*, PayrollExport*, PayrollResults*, …); Client.Services |
| **Reporting** | ReportService, ReportBuilder, ReportProcessor; ReportScriptController |
| **Non-functional** | Tenant in repos and API; *Audit*; FunctionHostSettings; AssemblyCache |

Use this plan with the **codebase as the main source**: open the referenced files, follow the flows (e.g. PayrunProcessor.Process → CalculateEmployeeAsync → CollectorStart → wage type loop → CollectorEnd → StoreResultsAsync), and reimplement each component in your target language and stack.
