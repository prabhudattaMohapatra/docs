# Payroll Engine End-to-End Learning Pathway

A suggested pathway to understand the payroll engine from end to end using the repos and codebases in the workspace (`payroll-engine`, `payroll-engine-backend`, `payroll-engine-console`, and all `payroll-engine-xxxx` libraries).

---

## 1. High-Level Map

| Layer | Repos | Role |
|-------|--------|------|
| **Foundation** | `payroll-engine-core`, `payroll-engine-client-core` | Shared types, API models, exchange format, query builders |
| **Scripting** | `payroll-engine-client-scripting` | Script templates, function base classes, used by backend to compile/run rules |
| **Backend** | `payroll-engine-backend` | REST API, persistence, **PayrunProcessor** (orchestrates payrun, loads scripts, runs them) |
| **Console** | `payroll-engine-console` | CLI: import/export, DSL convert, payrun triggers via API |
| **Regulations** | `payroll-engine-rules`, `payroll-engine-regulation-France`, `payroll-engine-regulations-INDIA`, `payroll-engine-regulations-swiss`, etc. | Country rules: WageTypes, Collectors, Cases, **C# scripts** (e.g. Rules.cs) |
| **Supporting** | `payroll-engine-client-services`, `payroll-engine-serilog`, `payroll-engine-document`, `payroll-engine-jsonSchemaBuilder`, `payroll-engine-dbQueryTool` | HTTP client, logging, reports, schemas, DB tooling |
| **Orchestration / examples** | `payroll-engine` | Commands, examples, setup scripts, docs |

Calculation flow (conceptually): **Case fields → Wage types → Collectors → Net/Payment**.  
Execution flow: **Backend PayrunProcessor** loads regulation + scripts from DB, compiles with Roslyn, runs payrun/employee/wage-type/collector lifecycle; **Console** talks to backend via **Client.Services** and can run payruns, import regulation, etc.

---

## 2. Suggested Learning Path (Step by Step)

### Phase 1: Foundation and Data Model (no execution yet)

1. **`payroll-engine-core`**  
   - Types, exceptions, serialization, `DataSet`, config.  
   - **Focus**: `Core/` (Date, DatePeriod, Query, ValueType, Data/, Serialization/).  
   - **Goal**: Understand shared primitives and how payroll “values” and periods are represented.

2. **`payroll-engine-client-core`**  
   - API models, exchange format, HTTP client, query builders.  
   - **Focus**: `Client.Core/` — entities (Tenant, Regulation, Payroll, Payrun, Employee, Case, WageType, Collector, Script, etc.), exchange read/write, API endpoints.  
   - **Goal**: Understand the **domain model** and how data is sent/received (import/export, API payloads).  
   - **Docs**: `docs/REQUIRED_REPOSITORIES.md` (dependency graph); `docs/PAYROLL_FLOW_DIAGRAM.md`, `docs/PAYROLL_CALCULATION_FLOW_ANALYSIS.md` (calculation concepts).

3. **Calculation concepts (docs only)**  
   - Read `docs/PAYROLL_FLOW_DIAGRAM.md` and `docs/PAYROLL_CALCULATION_FLOW_ANALYSIS.md`.  
   - **Goal**: Case fields → wage types → collectors → gross → deductions → net → payment; which case fields feed which wage types/collectors.

---

### Phase 2: Where Rules Live and How They’re Stored

4. **Regulation structure (one country)**  
   - Pick one, e.g. **`payroll-engine-regulation-France`** or **`payroll-engine-regulations-swiss`**.  
   - **Focus**:  
     - `Regulation/` or `DSLOutput/` — JSON for WageTypes, Collectors, Cases, Lookups, Regulation.  
     - `Scripts/` or `DSLOutput/.../Rules/` — **C#** (e.g. `Rules.cs`, wage type value functions).  
   - **Goal**: Regulation = metadata (JSON) + C# scripts; script names and function types (WageTypeValue, CollectorStart/End, PayrunStart, etc.).

5. **`payroll-engine-template`**  
   - Template regulation + script stubs (composite objects, wage types, collectors, payrun lifecycle).  
   - **Goal**: Canonical script “shapes” and how they plug into the engine.

6. **Ingestion and storage**  
   - Read **`docs/COUNTRY_REGULATION_INGESTION_FLOW.md`**.  
   - **Console**: `PayrollImport`, `PayrollImportFromDsl` (exchange → backend API).  
   - **Backend**: API receives regulation + scripts; persistence stores them (e.g. Regulation + Script tables).  
   - **Goal**: From JSON/zip or DSL output → backend DB; scripts stored as source in `Script.Value`.

---

### Phase 3: Backend — API, Persistence, Payrun Execution

7. **`payroll-engine-backend` — structure**  
   - **Api**: `Api.Model` (DTOs), `Api.Core` (infra, **PayrunJobWorkerService**), `Api.Map`, `Api.Controller` → **Backend.Controller**.  
   - **Domain**: `Domain.Model` (domain entities + refs to Client.Scripting), `Domain.Scripting` (script compilation/Roslyn), **`Domain.Application`** (**PayrunProcessor**).  
   - **Persistence**: `Persistence`, `Persistence.SqlServer` (repos, DB).  
   - **Backend.Server**: Host; references Backend.Controller + Persistence.SqlServer.  
   - **Goal**: Request path (HTTP → Controller → Service/Processor) and where domain vs persistence live.

8. **Persistence**  
   - **Persistence**: repository interfaces and base impl.  
   - **Persistence.SqlServer**: SQL Server impl, `Database/` scripts.  
   - **Goal**: How tenants, regulations, payrolls, employees, case values, wage types, collectors, **scripts**, payruns, results are stored and loaded.

9. **PayrunProcessor (heart of execution)**  
   - **File**: `Domain/Domain.Application/PayrunProcessor.cs`.  
   - **Flow**:  
     - Load payrun, payroll, division, employees, case value caches.  
     - Build **PayrunProcessorRegulation** (wage types, collectors, etc.).  
     - For each employee/period: create **PayrunProcessorScripts** (FunctionHost, context, result provider).  
     - Load regulation scripts from DB; **ScriptCompiler** (Domain.Scripting) + **Client.Scripting** templates + Roslyn → compile to in-memory assembly.  
     - Run lifecycle: PayrunStart → EmployeeStart → wage type values & collector start/end → EmployeeEnd → PayrunEnd; write results via **ResultProvider**.  
   - **Trigger**: **Api.Core** `PayrunJobWorkerService` runs `processor.Process(jobInvocation)`.  
   - **Goal**: End-to-end payrun: who calls the processor, how scripts are loaded/compiled/executed, how results are persisted.

10. **Domain.Scripting**  
    - Script compilation (C# from DB + embedded templates), Roslyn, **AssemblyCache**, **FunctionHost**.  
    - **Goal**: How script source becomes a type and how lifecycle methods are invoked (reflection / delegate).

11. **`payroll-engine-client-scripting`**  
    - Script **templates** and **base classes** (e.g. wage type value, collector start/end) that the compiler combines with regulation script content.  
    - **Goal**: What the compiled script extends and what APIs it has (Employee, Case, Collector, WageType, etc.).

---

### Phase 4: Console and Client Usage

12. **`payroll-engine-client-services`**  
    - High-level API client used by Console (and others).  
    - **Goal**: How the console (or any client) calls backend (tenant, regulation, payroll, payrun, results).

13. **`payroll-engine-console`**  
    - **PayrollConsole/Program.cs**: CommandManager, CommandProvider, HTTP client.  
    - **Commands/**: PayrollImport, PayrollImportFromDsl, DslConvert, PayrollExport, PayrollResults, Report, CaseTest, etc.  
    - **Goal**: Which commands hit which APIs; how import/export and payrun invocation work from CLI.

14. **Console DSL**  
    - **payroll-engine-console-dsl** (in repo): DSL convert (e.g. YAML → JSON/scripts).  
    - **dslsettings*.json**: Import paths, DSL config per country.  
    - **Goal**: How “DSL” (e.g. France/India/Swiss) becomes exchange + scripts and then import.

---

### Phase 5: End-to-End Flows and Execution Models

15. **Regulation ingestion (full path)**  
    - Regulation repo (e.g. France) → DSL output or pre-built JSON → Console **PayrollImportFromDsl** / **PayrollImport** → Backend API → Persistence.  
    - **Goal**: Trace one regulation from repo to DB.

16. **Payrun execution (full path)**  
    - Create/start payrun (API or console) → **PayrunJobWorkerService** dequeue → **PayrunProcessor.Process** → load regulation + scripts → compile → run lifecycle → **ResultProvider** → persistence.  
    - **Goal**: From “run payrun” to wage type/collector results in DB.

17. **Execution model docs**  
    - **`docs/EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`**: Backend calling an external regulation service instead of DB + Roslyn.  
    - **`docs/DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md`**, **`docs/EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md`**: Variants (precompiled, no DB scripts).  
    - **Goal**: How the current “DB + Roslyn” model compares to other possible designs.

---

### Phase 6: Deeper Dives (Optional)

18. **`payroll-engine-webapp`**  
    - Blazor UI; uses Client.Core / backend API; same domain concepts.

19. **`payroll-engine-adminApp`**  
    - Admin tooling; lighter than main engine.

20. **`payroll-engine` (main repo)**  
    - Commands (`.pecmd`), Examples (SimplePayroll, StartPayroll, ReportPayroll, etc.), Setup, Tests.  
    - **Goal**: How to run examples and tests; how “payroll-engine” repo orchestrates running backend/console with examples.

21. **Infra and pipelines**  
    - **gp-nova-payroll-engine-infra**: Infra as code.  
    - **docs/PIPELINE-EXECUTION-FLOW.md**: Build/deploy, ECR, ECS.  
    - **Goal**: How backend is built, packaged, and deployed.

22. **Other regulation repos**  
    - **payroll-engine-regulations-INDIA**, **payroll-engine-rules**, etc.: Same ideas as France/Swiss (metadata + C#), different country rules.

---

## 3. Dependency Chain (Build Order)

1. **payroll-engine-core**
2. **payroll-engine-client-core** (depends on Core)
3. **payroll-engine-client-scripting** (depends on Client.Core)
4. **payroll-engine-client-services** (depends on Client.Core)
5. **payroll-engine-serilog**, **payroll-engine-document**
6. **payroll-engine-backend** (Domain.Model → Core + Client.Scripting; Domain.Scripting → Client.Scripting; Domain.Application → Domain.Scripting; Api → Domain; Persistence → Domain; Server → Controller + Persistence.SqlServer)
7. **payroll-engine-console** (Commands + PayrollConsole; uses Client.Services, Document, Serilog; optional SwissExtension, payroll-engine-console-dsl)

Regulation repos are **data + scripts** consumed by backend via import; they don’t need to “build before” backend, but their JSON + C# must match what the engine expects.

---

## 4. Key Files to Read (in order)

| Order | Repo / doc | File / area |
|-------|------------|-------------|
| 1 | docs | `PAYROLL_FLOW_DIAGRAM.md`, `PAYROLL_CALCULATION_FLOW_ANALYSIS.md` |
| 2 | payroll-engine-client-core | README; Client.Core (API models, exchange) |
| 3 | docs | `COUNTRY_REGULATION_INGESTION_FLOW.md` |
| 4 | payroll-engine-backend | `Domain/Domain.Application/PayrunProcessor.cs` (start → ~line 250) |
| 5 | payroll-engine-backend | `Api/Api.Core/PayrunJobWorkerService.cs` |
| 6 | payroll-engine-backend | Domain.Scripting (ScriptCompiler, FunctionHost, C# compilation) |
| 7 | payroll-engine-client-scripting | Script templates / base classes |
| 8 | payroll-engine-regulation-France (or Swiss) | `DSLOutput/.../Rules/Rules.cs`, Regulation JSON |
| 9 | payroll-engine-console | `PayrollConsole/Program.cs`; Commands (PayrollImport*, Dsl*) |
| 10 | docs | `REQUIRED_REPOSITORIES.md`, `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md` |

---

## 5. One-Sentence Recap

**Core/Client.Core** define the model and exchange; **Client.Scripting** provides script templates; **Backend** stores regulation and scripts in DB and runs **PayrunProcessor**, which compiles and executes C# regulation scripts (Roslyn) to compute wage types and collectors; **Console** imports/exports data and triggers payruns via **Client.Services**; **regulation repos** supply the JSON and C# rules per country.
