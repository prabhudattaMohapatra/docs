# Payroll Engine: Fundamental Architecture (Detailed)

This document describes the fundamental split between the **constant core** (the engine that never changes per country or tenant) and the **dynamic components** (regulations and scripts that are ingested or plugged in and can change without redeploying the engine). It expands on the short summary in **PAYROLL_ENGINE_CORE_VS_DYNAMIC_ARCHITECTURE.md**.

---

## 1. Design principle

The payroll engine is built around one principle:

- **The core is a generic execution engine.** It knows *how* to run a payrun: in what order to call scripts, how to resolve case values, how to aggregate wage types into collectors, how to persist results. It does **not** know *what* wage types or *what* formulas exist for France, India, or any tenant.
- **Country and tenant specifics are data and code.** They are supplied as regulation metadata (wage types, collectors, cases, lookups) and scripts (C# or other). The core loads and executes them at runtime. Changing or adding a regulation does not require changing the core.

So: **core = mechanism; dynamic = content.**

---

## 2. The constant core (detailed)

### 2.1 Where it lives

| Layer | Repository / path | Role |
|-------|-------------------|------|
| **Backend** | `payroll-engine-backend` | Host, API, domain application, persistence. |
| **Backend.Api** | `Api/` | REST controllers, API model (DTOs), mapping, core infra (e.g. PayrunJobWorkerService). |
| **Backend.Domain** | `Domain/` | Domain model, scripting (compilation, FunctionHost), application (PayrunProcessor, services). |
| **Backend.Persistence** | `Persistence/` | Repository interfaces and implementations (SQL Server). |
| **Backend.Server** | `Backend.Server/` | ASP.NET host; wires Api + Persistence. |
| **Libraries** | `payroll-engine-core` | Shared types, exceptions, serialization, query, data tables, configuration. |
| **Libraries** | `payroll-engine-client-core` | API client types, exchange format, query builders, endpoints. |
| **Libraries** | `payroll-engine-client-scripting` | Script templates and base classes used when compiling regulation scripts. |

The core also depends on supporting libraries (e.g. `payroll-engine-serilog`, `payroll-engine-document`) for logging and reporting infrastructure; those are still “constant” in the sense that they are not per-regulation.

### 2.2 What the core defines (shapes and behaviour)

The core **defines**:

- **Domain entity shapes**: Tenant, Division, Payroll, Regulation, Payrun, PayrunJob, Employee, Case, CaseValue, WageType, Collector, Script, LookupSet, PayrollResult, etc. (types, properties, relationships in Domain.Model and API model).
- **Lifecycle and order of execution**: PayrunStart → EmployeeStart → Collector Start → (per wage type: WageTypeValue, Collector Apply) → Collector End → EmployeeEnd → PayrunEnd. This order is fixed in PayrunProcessor and PayrunProcessorRegulation.
- **Script invocation contract**: For each hook (e.g. WageTypeValue, CollectorStart), the core passes a well-defined context (tenant, payroll, payrun job, case value provider, regulation provider, result provider, lookup provider, etc.) and expects a well-defined return (e.g. decimal?, list of retro jobs). The **script controllers** (PayrunScriptController, WageTypeScriptController, CollectorScriptController) in Domain.Scripting implement this contract; regulation scripts are compiled to satisfy the same contract.
- **Persistence schema**: Tables for tenants, regulations, scripts, wage types, collectors, cases, lookups, case values, payrun jobs, results, etc. The schema is part of the core; the **rows** (e.g. which wage types exist) come from imported regulations.
- **API surface**: REST endpoints and payloads for CRUD and operations (e.g. start payrun, get results). Same API for every tenant and regulation set.

So the core fixes **structure and process**; it does not fix **content**.

### 2.3 What the core does not define (content)

The core does **not** hard-code:

- Which wage type numbers or names exist.
- Which collectors exist or how they aggregate.
- Which cases and case fields exist.
- The formula for a wage type (that is in the script or wage type ValueScript).
- The logic for collector start/apply/end (in scripts).
- Lookup tables (e.g. tax rates); they are stored as regulation data and read by scripts.
- Payrun start/end or employee start/end logic (optional scripts on the Payrun object).

All of that is **supplied at runtime** via regulation metadata and scripts stored in the database (or otherwise loaded via the same contracts).

### 2.4 Core components that touch “dynamic” content

These parts of the core **load** or **execute** dynamic content; they do not **define** it:

- **PayrollRepository (and related)**: `GetDerivedRegulationsAsync`, `GetDerivedWageTypesAsync`, `GetDerivedCollectorsAsync`, etc. They read from persistence the regulation/wage type/collector **rows** that were created by import. The core defines the **query and shape**; the data is dynamic.
- **ScriptProvider (IScriptProvider)**: Interface used to obtain script **binary**. The default implementation reads the precompiled **Binary** from the database (WageType/Collector/etc. or Script table). Compilation does **not** happen at payrun time; scripts are compiled during **import** when JSONs that reference them (e.g. WageTypes.json with valueExpression referencing Rules.cs) are imported via PayrollImport, and the resulting binaries are stored on the script objects. So the core owns **when** to compile (at import) and **how** to load/run (at payrun); the script **text** is dynamic.
- **ScriptCompiler, FunctionHost, AssemblyCache**: Take regulation script source + embedded templates (from Client.Scripting), compile with Roslyn, load into a collectible assembly context, and invoke methods. The **mechanism** is fixed; the **source** is per regulation.
- **CaseValueProvider**: Resolves case values from caches (global, national, company, employee) that were filled from persistence. The **resolution logic** (periods, forecast, slots) is in the core; the **case and field names** and **values** come from regulation and tenant data.

So the boundary is clear: **core = “how to load and run”**; **dynamic = “what to load and run”.**

### 2.5 Contracts and extension points

The core exposes a small set of extension points so that dynamic content can plug in:

- **Exchange format (JSON schema)**: The core (via Client.Core and backend API) understands the Exchange model. Any regulation that conforms to this format can be imported. The schema defines the **structure** of regulations, wage types, collectors, cases, scripts, lookups; the **values** are dynamic.
- **Script function signatures**: Regulation scripts are compiled against base types and a known set of method names/signatures (e.g. WageTypeValue, CollectorStart). The templates in Client.Scripting provide the “wrapper”; the regulation provides the body. So the **signature** is fixed; the **implementation** is dynamic.
- **IScriptProvider**: Allows the host to customize where script binary (or source) comes from. Default is DB; alternative could be external store or precompiled assemblies.

There are no plugin DLLs or “country modules” in the core; “plugging in” is done by **importing data and script source** and having the core compile and run it.

---

## 3. The dynamic components (detailed)

### 3.1 What counts as “dynamic”

Everything that can change **without changing the backend binary or configuration code**:

- **Regulation metadata**: One or more Regulation entities per tenant. Each regulation has a name, namespace, and validity; it is the parent of wage types, collectors, cases, lookups, reports, and scripts.
- **Wage types**: Number, name, value type, collector attachments, value/result expressions (script names or inline), attributes. Different per country (e.g. France vs Switzerland).
- **Collectors**: Name, collect mode, negated, value type, start/apply/end script references, attributes. Define how wage type results are aggregated.
- **Cases and case fields**: Case names, field names, value types, slots. Define the “input” structure for employees and company (e.g. salary, tax code, insurance code).
- **Lookups and lookup values**: Named lookup sets and their rows (e.g. tax rate by code and canton). Scripts read these at runtime.
- **Scripts**: Named script objects (e.g. “Rules”) with **Value** = C# source. Stored per regulation (e.g. in Script table). Contain the actual logic for wage type value, collector start/apply/end, payrun/employee lifecycle, case availability, etc.
- **Reports**: Report definitions and templates; report scripts if any. Often less central than wage type/collector logic but still per-regulation.

### 3.2 Where dynamic content is authored

- **Regulation repositories**: e.g. `payroll-engine-regulation-France`, `payroll-engine-regulations-INDIA`, `payroll-engine-regulations-swiss`, `payroll-engine-rules`. They contain:
  - **JSON** (or YAML): WageTypes.json, Collectors.json, Cases, Lookups, Regulation.json, etc., often under a structure like `Regulation/` or `DSLOutput/FR/Rules/`.
  - **C# (or other) source**: e.g. `Rules.cs`, or per–wage type / per–collector script files. These implement the functions the core will call.
- **DSL pipelines**: Some repos use a DSL (e.g. YAML) that is **converted** to the engine’s JSON + C# (e.g. payroll-engine-console-dsl, DslConvert). The DSL output is still “dynamic content” from the core’s point of view; the conversion step is outside the backend.
- **Hand-authored Exchange**: Any valid Exchange JSON/zip (with or without scripts) can be imported. So dynamic content can also come from custom tools or one-off builds.

### 3.3 How dynamic content reaches the core

- **Import path**: Console (or another client) runs **PayrollImport** (single file) or **PayrollImportFromDsl** (directory of JSON/zip). The client reads the Exchange file(s), then calls the backend REST API to upsert tenants, regulations, wage types, collectors, cases, lookups, **scripts**, etc. So the backend **persistence** layer stores the dynamic content; the backend **does not** read directly from regulation repos or file system at payrun time (except insofar as ScriptProvider might be customized).
- **Storage**: Regulation metadata and script **source** live in the database (Regulation, WageType, Collector, Case, Script, LookupSet, etc.). Script **source** (e.g. Rules.cs) is stored in the **Script** table when Rules.json is imported; when **WageTypes.json** (or other JSONs containing valueExpression/script references) is imported, the backend **compiles** those scripts into binaries and stores them on the script objects (WageType.Binary, Collector.Binary, etc.). At payrun time, the core **only loads precompiled binaries** (via ScriptProvider/AssemblyCache) and executes them—no compilation at payrun.
- **Update**: To change a regulation, re-import the updated Exchange (or call API to update specific entities). Next payrun uses the new content. No backend redeploy.

So the **contract** between “dynamic” and “core” is: **Exchange format for import**, and **script API (signatures + context)** for execution.

### 3.4 Versioning and consistency

- Regulations can have **validity** (e.g. regulation date); payroll “derives” wage types and collectors for a given **regulation date** and **evaluation date**. So multiple versions of a regulation can coexist; the core chooses the right one by date.
- **OverrideType** and **layering** (base regulation + derived payroll layers) allow further customization per tenant or payroll without changing the core.
- Scripts are stored per regulation; when script source is updated and re-imported, the next compilation (or cache expiry) picks up the new code. So “versioning” of scripts is effectively “last write wins” per regulation/script name, unless you use multiple regulations or validity.

---

## 4. Boundary between core and dynamic

### 4.1 Compilation boundary

- **Core**: Owns **ScriptCompiler**, **FunctionHost**, **AssemblyCache**. It decides how to combine regulation script source with embedded templates (Client.Scripting), how to compile (Roslyn), and how to invoke (reflection / delegates). It also defines the **context** type passed into each script function (e.g. case value provider, regulation provider, result provider).
- **Dynamic**: Supplies the **script source** (e.g. Rules.cs body) and the **names** of scripts to run (via wage type/collector/payrun script references). Scripts must comply with the **signatures and context** the core expects; otherwise compilation or runtime fails.

So the boundary is: **core compiles at import (when script objects are saved) and loads/calls at payrun; dynamic supplies source and references.**

### 4.2 Data boundary

- **Core**: Owns the **schema** (tables, columns, FKs). It defines what a Regulation, WageType, Collector, Script, etc. **look like** (e.g. Script has Name, Value, FunctionTypeMask).
- **Dynamic**: Supplies the **rows**. Which regulations exist, which wage types, what their ValueScript or CollectorStart script name is, what the Script.Value (source code) is. The core never “invents” these rows; they come from import or API writes.

So the boundary is: **core = schema and repositories; dynamic = data.**

### 4.3 API boundary

- **Core**: Exposes REST API for tenants, regulations, payrolls, payruns, employees, case values, results, and for **starting a payrun** (which enqueues a job for PayrunProcessor). The API is the same for all tenants and regulations.
- **Dynamic**: Consumed via the same API. Import uses POST/PUT to create/update regulations and scripts. No separate “regulation API”; regulation is just another resource the core knows how to store and load.

So the boundary is: **one API for both “core” resources (e.g. tenants, divisions) and “dynamic” resources (regulations, scripts).**

---

## 5. End-to-end flow: ingestion to execution

### 5.1 Ingestion (dynamic → core)

1. Author regulation (JSON + C#) in a regulation repo or produce Exchange JSON/zip (e.g. via DSL convert).
2. Run **PayrollImport** or **PayrollImportFromDsl** (Console or client). Client reads Exchange, then calls backend API (e.g. `POST /tenants/{id}/regulations`, `POST /tenants/{id}/regulations/{id}/scripts`, and equivalent for wage types, collectors, cases, lookups).
3. **Two phases in import**: (a) When **Rules.json** (or similar) is imported, the backend stores script **source** (e.g. full Rules.cs) in the **Script** table (Value column). (b) When **WageTypes.json** (or other JSONs that reference functions in those scripts via valueExpression) is imported, the backend **compiles** the scripts (via ScriptCompiler/Roslyn) into binaries and stores them on the script objects (WageType.Binary, Collector.Binary, etc.). So compilation happens **at import time** for better payrun performance.

### 5.2 Execution (core runs dynamic content)

1. User or system **starts a payrun** (API or Console). Backend creates a payrun job and enqueues it.
2. **PayrunJobWorkerService** dequeues the job, builds **PayrunProcessorSettings** (repositories, ScriptProvider, etc.), creates **PayrunProcessor**, calls **Process(JobInvocation)**.
3. **PayrunProcessor** loads payrun, payroll, **derived regulations** (from persistence), then **derived wage types** and **derived collectors** (from persistence, by regulation date and evaluation date). So the **set** of wage types and collectors for this payrun is determined by **dynamic data** already in the DB.
4. For each employee, the processor builds **CaseValueProvider** (from case value caches filled from DB). It runs **PayrunStart**, **EmployeeStart**, then for each collector **CollectorStart** (script), then for each wage type **WageTypeValue** (script) and **CollectorApply** (script), then **CollectorEnd** (script), then **EmployeeEnd**, **PayrunEnd**. Each of these script calls goes through **FunctionHost**: the core **loads the precompiled binary** (from AssemblyCache or via ScriptProvider from DB) and **invokes** the corresponding method—**no compilation at payrun**. So at execution time, the core is **running code that was compiled at import time** from dynamic content.
5. Results (wage type results, collector results) are written back via **ResultProvider** and persistence. The core defines **where** to write (e.g. PayrollResultSet); the **values** come from the regulation scripts.

So end-to-end: **dynamic content is ingested into core storage, then the core execution pipeline loads and runs that content at payrun time.**

---

## 6. Implications

### 6.1 For keeping the engine stable

- **Adding a new country** = author a new regulation (JSON + scripts), import it. No change to backend or core libraries.
- **Changing a formula** = change the script or wage type expression in the regulation repo, re-import. Backend unchanged.
- **Fixing a core bug or adding a core feature** (e.g. new lifecycle hook, new context property) = change backend/libraries and redeploy; regulations may need to be updated if they rely on the new contract.

### 6.2 For building a new engine from scratch

- You can **reuse the same split**: a constant core (orchestration, lifecycle, persistence schema, script runtime contract) and dynamic content (regulation metadata + scripts). The new engine’s “core” would implement the same **contracts** (lifecycle order, script signatures, context shape) if you want to reuse existing regulation repos or migrate them.
- You can **change the format** of dynamic content (e.g. different script language, different JSON shape) as long as you define an **ingestion path** (e.g. import step that maps your format into your new core’s storage) and a **script execution contract** that your core expects.

### 6.3 For documentation and onboarding

- **Core** documentation: PayrunProcessor, script compilation, persistence schema, API. Same for every deployment.
- **Dynamic** documentation: Per-regulation (e.g. France wage types, Swiss collectors) or per-tenant. The core stays the single source of truth for “how the engine works”; regulations are the source of truth for “what this country/tenant does.”

---

## 7. Summary diagram (detailed)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  CONSTANT CORE                                                                   │
│  • payroll-engine-backend (Api, Domain, Persistence) + payroll-engine-core,      │
│    payroll-engine-client-core, payroll-engine-client-scripting                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Defines:                                                                        │
│  • Entity shapes (Tenant, Regulation, WageType, Collector, Script, Case, …)     │
│  • Lifecycle order (PayrunStart → … → Collector End → PayrunEnd)                  │
│  • Script invocation contract (context, signatures)                             │
│  • Persistence schema (tables, FKs)                                              │
│  • REST API                                                                      │
│  Does not define:                                                                │
│  • Which wage types/collectors exist  • Formula logic  • Lookup data             │
├─────────────────────────────────────────────────────────────────────────────────┤
│  At runtime (payrun):                                                             │
│  • Loads regulation/wage type/collector/script rows from DB (written by import)  │
│  • Loads precompiled binaries (from DB/cache); no compilation at payrun           │
│  • Invokes script methods in fixed order; persists results                        │
└───────────────────────────────┬─────────────────────────────────────────────────┘
                                 │
                                 │  Import (Exchange → API → DB; scripts compiled into binaries when WageTypes etc. imported)
                                 │  Execution (DB → load binary + run; no compile at payrun)
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  DYNAMIC COMPONENTS                                                               │
│  • Regulation repos (e.g. France, India, Swiss) or any Exchange producer         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Content:                                                                         │
│  • Regulation metadata (name, namespace, validity)                               │
│  • Wage types (number, name, value/result script refs, collector links)           │
│  • Collectors (name, start/apply/end script refs)                                │
│  • Cases and case fields                                                         │
│  • Lookups and lookup values                                                     │
│  • Scripts (C# source: Rules.cs, wage type value, collector start/apply/end, …)   │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Reaches core via:                                                                │
│  • PayrollImport / PayrollImportFromDsl → backend API → persistence              │
│  • Stored in same DB as core data; no backend redeploy to add/change regulation   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

**One sentence**: The **core** is the generic payroll execution engine (shapes, lifecycle, persistence, API); the **dynamic** part is the regulation metadata and script source that are ingested and stored. Scripts are **compiled during import** (when JSONs like WageTypes.json that reference them are imported); at **payrun time** only precompiled binaries are loaded and executed—so the engine stays constant while country and tenant logic change freely, with no compilation on the payrun path.
