# Payroll Engine: Pros/Cons of Current Architecture and Alternative Architectures

This document sets out **pros and cons of the current architecture** (constant core + dynamic ingested scripts, compiled at import) and **alternative architectures** with concrete examples and their own pros and cons. It supports decisions on whether to keep, evolve, or replace the current design (e.g. when building a new engine).

---

## 1. Current Architecture: Short Recap

- **Constant core**: Backend + libraries define shapes, lifecycle order, script contract, persistence schema, and API. No country-specific logic in code.
- **Dynamic content**: Regulation metadata (wage types, collectors, cases, lookups) and **script source** (e.g. C# in `Script.Value`) are **ingested** via Exchange import and stored in the DB.
- **Execution**: Scripts are **compiled during import** (when JSONs like WageTypes.json that reference them are imported via PayrollImport); binaries are stored on script objects (WageType.Binary, etc.). At payrun time the core loads regulation data and **precompiled binaries** from DB (or AssemblyCache) and **invokes** script methods in a fixed lifecycle order—**no compilation at payrun**.

So: **ingested source + compile at import + load binary and run at payrun**.

---

## 2. Pros and Cons of the Current Architecture

### 2.1 Pros

| Pro | Detail |
|-----|--------|
| **No backend redeploy for regulation changes** | New or updated regulations (and script source) are imported via API. Next payrun uses the new content. Ideal for multi-tenant and multi-country with frequent rule changes. |
| **Full expressiveness** | Regulation authors write real C# (or whatever the compiler supports). Complex logic, lookups, retro jobs, and edge cases are possible without extending the engine. |
| **Single codebase for the engine** | The core is one deployable; no per-country DLLs or services to version and deploy. Operations stay simple. |
| **Same API and schema for all tenants/countries** | One persistence model, one REST API. Adding France vs Switzerland is “just” different data and scripts, not different code paths. |
| **Auditability of source** | Script source lives in DB (or in regulation repos that feed import). You can trace “what ran” to a concrete script version. |
| **In-process execution** | No network calls for regulation logic. Latency and failure modes are simpler than with an external service. |
| **Compile-once, run-many** | Compilation happens at import; binary is stored in DB and cached in AssemblyCache. So cost of Roslyn is paid at import, not on the payrun path. |

### 2.2 Cons

| Con | Detail |
|-----|--------|
| **Compilation dependency at import** | Import of wage types/collectors that reference scripts requires Roslyn. That means import can be heavier (CPU, time) and the backend needs .NET compiler tooling available at import time—but not on the payrun path. |
| **Security and isolation** | Scripts run in the same process as the core. A bug or malicious script can affect the whole process. Sandboxing is limited (e.g. no OS-level isolation). |
| **Version coupling** | Scripts are compiled against core types and templates (Client.Scripting). A core upgrade that changes those can break existing script source until regulations are updated and re-imported. |
| **Debugging and observability** | Failures in regulation code show as script exceptions; debugging often requires logs, stack traces, and knowledge of the generated code. No “edit and re-run” in production. |
| **Large DB content** | Storing script source (and optionally binary) per regulation can make DB large and backups heavier. |
| **Single language** | Regulation logic is C# (or whatever the compiler accepts). Teams that prefer Python, Java, or a DSL cannot author “native” scripts without a separate conversion or codegen step. |

---

## 3. Alternative Architectures: Description, Example, Pros, Cons

### 3.1 Pre-compiled assemblies (no runtime compile)

**Description**: Regulation logic is compiled **before** payrun time (e.g. at CI or at import). The backend stores only a **binary** (or a reference to a blob); it **loads** the assembly and invokes it. No Roslyn in the payrun path.

**Example**:  
- Build pipeline: Regulation repo (France) is built into `PayrollEngine.Regulation.France.dll`. CI publishes it to blob storage (e.g. `s3://bucket/regulations/FR/v2.dll`).  
- Import: Backend API stores regulation “France” with `BinaryReference = "s3://bucket/regulations/FR/v2.dll"` (no Script.Value).  
- Payrun: PayrunProcessor loads regulation “France”; ScriptProvider resolves reference → downloads DLL (or uses local cache) → AssemblyCache loads from byte[] → same reflection invoke as today. No compile.

**Pros**

- No compile at payrun; faster and more predictable first-run latency.
- Production can run without Roslyn; smaller attack surface and simpler runtime.
- Versioning is explicit (DLL version or blob path); easy to roll back.
- Possible to sign and enforce “only approved assemblies.”

**Cons**

- New or changed regulation = new build + publish + update reference (and possibly re-import). No “edit script in DB and re-run.”
- Build pipeline must have same .NET (or runtime) as backend; version skew can break loading.
- If binary is referenced by URL, payrun depends on blob storage availability (unless cached aggressively).

---

### 3.2 External regulation service (API / serverless)

**Description**: The payroll backend **does not** execute regulation code. It calls an **external service** (REST, gRPC, or serverless) with context (tenant, regulation, employee, period, case values, wage type id, etc.) and uses the returned result (e.g. wage type value, collector result). Regulation logic lives entirely in the other service.

**Example**:  
- Backend: For wage type 1000 (Monthly Salary), PayrunProcessor calls `POST https://regulation-api/evaluate/wage-type` with body `{ tenantId, regulationId: "FR", employeeId, periodStart, periodEnd, wageTypeNumber: 1000, caseValues: [...] }`.  
- Regulation service: Implemented in Python/Go/Java; looks up case values, applies formula, returns `{ "value": 5500.00 }`.  
- Backend: Creates WageTypeResult from response and continues. No script load or compile in backend.

**Pros**

- No script storage or compilation in payroll DB; backend stays “data + orchestration only.”
- Regulation can be in any language and scale independently (e.g. separate team, separate deploy).
- Clear boundary: backend owns payrun flow and persistence; regulation service owns logic.

**Cons**

- **Latency**: Many round-trips per payrun (per wage type, per collector, etc.) unless you batch (e.g. “evaluate whole employee” in one call).
- **Availability**: Payrun depends on regulation service; need retries, timeouts, and possibly fallback or degradation.
- **Contract versioning**: Backend and service must agree on request/response shape; changes require coordination.
- **Data duplication or sync**: Service needs case values, lookups, etc.; either sent in each request or replicated to regulation service’s store.

---

### 3.3 Rules-as-data / formula DSL (no code in DB)

**Description**: Regulation is **data only**: rules, conditions, and expressions (e.g. JSON/YAML or a formula DSL). A **generic rules engine** or **expression interpreter** inside the backend evaluates this data at payrun time. No C# (or other) script source stored; no binary.

**Example**:  
- Wage type “Gross” is stored as: `{ "wageTypeNumber": 1000, "expression": "CaseValue('EmployeeStatistics.ContractualMonthlyWage')", "collectors": ["GrossSalary"] }`.  
- Or: `{ "conditions": [{ "case": "EmployeeStatistics.Contract", "eq": "indefiniteSalaryMth" }], "then": "Lookup('MonthlyRate', CaseValue('EmployeeStatistics.ContractualMonthlyWage'))" }`.  
- Backend: RulesEngine loads rule set for the regulation, passes in case values and lookups, evaluates expression/conditions, returns value. No compile; no script table.

**Pros**

- No code in DB; no Roslyn; no assembly load. Safer and easier to audit (“this wage type is this expression”).
- Rules are versionable as data (e.g. in same Exchange import); no separate script lifecycle.
- Same engine for all countries; behaviour is entirely data-driven.

**Cons**

- **Expressiveness**: Complex logic (retro jobs, multi-step calculations, special cases) can become verbose or require many rules; some cases may need an “escape hatch” (e.g. a small script or external call).
- **DSL design**: The formula/rule language must be rich enough for all countries; otherwise you end up with a mini-language that approaches a full language.
- **Performance**: Interpretation can be slower than compiled code for hot paths; may need caching or compilation of expressions.

---

### 3.4 Packaged regulation as library (DLL/JAR, reference in DB)

**Description**: Regulation is implemented as a **normal library** (e.g. .NET DLL or Java JAR) that is **deployed with the app** or loaded from a known path/package. The DB stores only a **reference** (e.g. regulation id = “France” → assembly “PayrollEngine.Regulation.France”). The core has a **registry** (id → type or factory) and calls a **fixed interface** (e.g. `EvaluateWageType(context)`).

**Example**:  
- Backend ships with (or loads from a known dir) `PayrollEngine.Regulation.France.dll`.  
- Regulation row: `Name = "France", RegulationPackageId = "PayrollEngine.Regulation.France"`. No Script table for France.  
- Payrun: PayrunProcessor resolves “France” to `FranceRegulationCalculator`, calls `GetWageTypeValue(1000, context)`; logic is inside the DLL. No import of source or binary from DB.

**Pros**

- Full language (C#/Java); no runtime compile; no code or binary in DB.
- Type-safe API between core and regulation; easy to refactor with compiler.
- Versioning via package/deploy (e.g. NuGet, Maven); familiar to developers.

**Cons**

- **Deploy coupling**: New country or regulation change = new build and deploy (or at least new package version and config). Not “import and run.”
- **Multi-tenant/country**: Either one assembly per regulation (many DLLs) or one multi-tenant assembly that dispatches by regulation id; both have operational implications.
- **Core–regulation contract**: Interface (e.g. `IRegulationCalculator`) is in the core; regulation assemblies must target that. Core upgrades can break existing packages.

---

### 3.5 Interpreted scripting (e.g. JavaScript/Lua in-process)

**Description**: Regulation logic is written in an **interpreted language** (e.g. JavaScript, Lua, or Python) and stored as **source** in DB (or referenced). The backend embeds an **interpreter** (e.g. V8, Lua, IronPython) and executes the script in-process. No C# compilation; no .NET assembly for regulation.

**Example**:  
- Script.Value = JavaScript: `function getWageTypeValue(wageTypeNumber, context) { return context.caseValues['EmployeeStatistics.ContractualMonthlyWage'] || 0; }`.  
- Backend: Has a JS engine (e.g. V8); loads script from DB, calls `getWageTypeValue(1000, context)` with a serialized context; gets back value. Same lifecycle as today, but no Roslyn.

**Pros**

- No Roslyn; no .NET compiler in runtime. Some languages (e.g. JS) are familiar to more people.
- Scripts can be edited in DB and re-run without a .NET build (still need to reload script in engine).
- Sandboxing is often easier (e.g. limit globals, timeouts) than with full .NET.

**Cons**

- **Performance**: Interpreted is usually slower than compiled C# for tight loops and heavy logic.
- **Two languages**: Core is C#; regulation is JS/Lua. Context must be marshalled; debugging spans two runtimes.
- **API surface**: Scripts get a restricted API (what you expose to the interpreter); complex rules may hit limits.
- **Dependency**: Backend depends on the chosen interpreter (e.g. V8, Lua); version and security updates are on you.

---

### 3.6 Hybrid: Core + optional pre-compiled plugins (no DB script source)

**Description**: Like “packaged regulation as library,” but plugins are **optional** and **loaded at runtime** from a configured path or package store (not from DB). DB stores only regulation id and **plugin id** (e.g. “France” → plugin “France.v2”). No script source or binary in DB; binaries live in file system or artifact store.

**Example**:  
- Regulation “France” has `PluginId = "France.v2"`. Backend config: plugin dir = `/app/plugins` or NuGet feed.  
- On first use, backend loads `France.v2.dll` from dir/feed, caches it, resolves to `IRegulationPlugin`, calls `EvaluateWageType(...)`.  
- To update France: build new DLL, publish to dir/feed, update regulation row to `France.v3` (or replace file). No import of source.

**Pros**

- No runtime compile; no script source in DB. Clear separation: core vs plugin.
- Update regulation by deploying new plugin version and pointing regulation to it; no full backend redeploy.
- Can enforce “only load signed plugins” or “only from internal feed.”

**Cons**

- Operational complexity: plugin storage, versioning, and rollout (e.g. when to switch regulation to v3).
- Core–plugin contract and .NET version must stay compatible; breaking changes require coordinated release.

---

## 4. Comparison Summary

| Architecture | Regulation change without backend redeploy? | Code in DB? | Runtime compile? | Full expressiveness? | Latency / complexity |
|-------------|---------------------------------------------|-------------|------------------|----------------------|------------------------|
| **Current (ingested source + compile at import)** | Yes (re-import) | Yes (source) | At import (not payrun) | Yes (C#) | In-process; no compile at payrun |
| **Pre-compiled assemblies** | Yes (publish new DLL + update ref) | No (ref only) | No | Yes (C#) | In-process; no compile spike |
| **External regulation service** | Yes (deploy service) | No | No | Yes (any language) | Network; batching helps |
| **Rules-as-data / formula DSL** | Yes (re-import data) | No (data only) | No | Limited (DSL) | In-process; interpreter cost |
| **Packaged library (DLL/JAR)** | No (new deploy/package) | No | No | Yes (C#/Java) | In-process; simple |
| **Interpreted scripting (JS/Lua)** | Yes (re-import or reload) | Yes (source) | No | Medium (language + API) | In-process; interpreter cost |
| **Hybrid (plugins, no DB source)** | Yes (new plugin + config) | No | No | Yes (C#) | In-process; plugin management |

---

## 5. When to Prefer Which

- **Keep current** if: You need “change regulation without redeploy,” full C# expressiveness, and can accept Roslyn at import time and script source in DB. Payrun path is already “load binary and run” (no compile at payrun).
- **Pre-compiled assemblies** if: You want to remove runtime compile and keep C# and in-process execution; you are fine with a build/publish step for regulation changes.
- **External regulation service** if: You want regulation in another language or team, or to scale regulation independently; you can accept network latency and contract versioning.
- **Rules-as-data / DSL** if: You want no code in DB, maximum auditability, and can express (or limit) logic to a DSL; accept lower expressiveness or a richer DSL design.
- **Packaged library** if: You have few, stable regulations and prefer type-safe, deploy-time binding over “import and run.”
- **Interpreted scripting** if: You want to avoid Roslyn and allow “edit script and run” in a non-.NET language; accept interpreter performance and a restricted API.
- **Hybrid plugins** if: You want no DB script source and no runtime compile but still want to update regulations without full backend redeploy via plugin versioning.

---

## 6. References

- **PAYROLL_ENGINE_CORE_VS_DYNAMIC_ARCHITECTURE.md** — Short summary of core vs dynamic.
- **PAYROLL_ENGINE_FUNDAMENTAL_ARCHITECTURE_DETAILED.md** — Detailed description of the current architecture.
- **EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md** — External regulation service design.
- **DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md** — Pre-compiled assembly options.
- **EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md** — Rules-as-data, packaged library, external service, expression DSL.
