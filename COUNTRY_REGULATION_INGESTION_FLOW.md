# Country Regulation Ingestion: Current Flow, Storage, Execution & Alternatives

**Summary**: Country regulation is ingested via the **PayrollImport** command (or its wrapper **PayrollImportFromDsl**). Regulation data—including scripts (e.g. Rules.cs)—is sent as **Exchange JSON/zip** to the backend API, stored in **SQL Server** under **Regulation** and **Script** tables, and **executed at payrun time** by the backend’s **PayrunProcessor** using **Roslyn**-compiled C#.

---

## 1. Current Flow

### 1.1 Entry Points

| Command | Role |
|--------|------|
| **PayrollImport** | Imports payroll data from a **single JSON or ZIP file** (Exchange format). Reads file → `ExchangeReader.ReadAsync()` → `ExchangeImport.ImportAsync()` → HTTP calls to backend API. |
| **PayrollImportFromDsl** | Wrapper that runs **PayrollImport** for each `.json`/`.zip` in a **DSL output directory**. Resolves file list from `dslsettings` (e.g. `Import.Paths`) or fallback to first .json/.zip in directory. |
| **DSLImport** | Runs **DslConvertModel** / **DslConvertRule** (if configured), then **PayrollImportFromDsl** on the output directory. |
| **DslPrepareAndImport** | Runs **DSLPrepare** (e.g. S3 download), then **PayrollImportFromDsl**. |

So “country regulation” is ingested when that regulation is represented inside the **Exchange JSON** that **PayrollImport** sends to the API. That JSON can come from:

- A **pre-built** Exchange JSON/zip (e.g. from a regulation repo build), or  
- **DSL conversion output**: YAML/DSL → DslConvert (Model/Rule) → JSON in `DSLOutput/...` → **PayrollImportFromDsl** → **PayrollImport** for each file.

### 1.2 Step-by-Step Flow (Regulation via PayrollImport)

1. **Source**  
   - Either: pre-built Exchange JSON/zip, or  
   - DSL repo (e.g. `payroll-engine-regulation-France`) → **DslConvert** (Model + Rule) → JSON under `DSLOutput/...` (e.g. `DSLOutput/FR/Rules/Rules.json`, `WageTypes.json`, `Regulation/Scripts.json`).

2. **Console**  
   - User runs **PayrollImport** \<file\> or **PayrollImportFromDsl** \<DSL directory\>.  
   - **PayrollImportFromDsl** resolves files from config (e.g. `dslsettings-France.json` → `Import.Paths`), sets CWD per file so relative paths in JSON (e.g. Rules.cs) resolve, then invokes **PayrollImport** for each `.json`/`.zip`.

3. **PayrollImport**  
   - `ExchangeReader.ReadAsync(fileName)` → **Exchange** object (Tenants, Regulations, Scripts, WageTypes, Collectors, Cases, Lookups, etc.).  
   - `ExchangeImport` (with `PayrollHttpClient`) visits the exchange graph and calls backend REST APIs (upserts).

4. **Backend API**  
   - **Regulation**: `SetupRegulationAsync` → `RegulationApiEndpoints.RegulationsUrl(tenant.Id)` (upsert regulation).  
   - **Scripts**: `SetupScriptAsync` → `RegulationApiEndpoints.RegulationScriptsUrl(tenant.Id, regulation.Id)` (upsert script; `script.Name` e.g. “Rules”, `script.Value` = C# source).  
   - Similarly: lookups, cases, wage types, collectors, reports, payrolls, payruns, etc.

5. **Persistence**  
   - Regulation → **Regulation** table (per tenant).  
   - Scripts → **Script** table (per regulation): `RegulationId`, `Name`, `Value` (C# code), `FunctionTypeMask`, etc.  
   - Other regulation children (wage types, collectors, cases, lookups, etc.) in their own tables, linked to regulation/tenant.

So **country regulation is ingested** by turning it into Exchange JSON (either by hand, by a regulation repo build, or by DSL convert) and then running **PayrollImport** (directly or via **PayrollImportFromDsl**). There is no separate “regulation-only” import; regulation is part of the general payroll Exchange model.

---

## 2. Where It Is Stored

| Layer | What | Where |
|-------|------|--------|
| **API** | Regulation resource | `POST/PUT` to `/tenants/{tenantId}/regulations` (upsert). |
| **API** | Script resource | `POST/PUT` to `/tenants/{tenantId}/regulations/{regulationId}/scripts` (upsert). |
| **Persistence** | Regulation | **Regulation** table (e.g. `DbSchema.Tables.Regulation`); keyed by tenant. |
| **Persistence** | Scripts | **Script** table (`DbSchema.Tables.Script`); `RegulationId` FK; columns include `Name`, `Value` (full C# source), `FunctionTypeMask`, `OverrideType`. |
| **Persistence** | Wage types, collectors, cases, lookups, etc. | Their own tables with regulation/tenant FKs (e.g. WageType, Collector, Case, LookupSet, etc.). |

Scripts are **stored as source text** in `Script.Value`. At payrun time the backend loads these by regulation (and function type), compiles them together with wage-type expressions and embedded templates, and executes the compiled assembly.

---

## 3. Where / How It Is Executed

- **When**: During **payrun processing**, inside the backend **PayrunProcessor** (e.g. when a payrun job is executed by the worker).  
- **How**:  
  1. **PayrunProcessor** builds a **PayrunProcessorRegulation** (and derived wage types/collectors) for the payrun’s payroll.  
  2. For script execution it creates **PayrunProcessorScripts** (with `FunctionHost`, settings, context, result provider, tenant, payrun).  
  3. Regulation **scripts** for that payroll are loaded via **ScriptRepository** (e.g. `GetFunctionScriptsAsync(regulationId, functionTypes, evaluationDate)`).  
  4. **ScriptCompiler** (Domain.Scripting) takes:  
     - **ScriptObject** (embedded base),  
     - **function scripts** (e.g. wage type expressions),  
     - **scripts** from the DB (e.g. Rules.cs content from `Script.Value`),  
     - optional **embedded script names**.  
  5. It builds C# source (object codes + function codes + script contents), compiles with **Roslyn** (`CSharpCompiler`) into an in-memory assembly (loadable via **CollectibleAssemblyLoadContext**), cached in **AssemblyCache**.  
  6. **FunctionHost** resolves the compiled type and **PayrunProcessorScripts** invokes payrun/employee/wage-type/collector lifecycle methods (PayrunStart, EmployeeStart, WageTypeValue, CollectorStart/End, PayrunEnd, etc.) on that type.

So **execution** is: **DB Script rows (regulation C#)** + wage-type expressions + embedded templates → **ScriptCompiler** → **Roslyn** → **AssemblyCache** → **FunctionHost** → reflection invoke in **PayrunProcessor**.

---

## 4. Alternatives

### 4.1 Keep Current Flow, Change Source of Exchange JSON

- **CI/CD build**: Regulation repo (e.g. France, India) produces Exchange JSON/zip as build artifact; **PayrollImport** or **PayrollImportFromDsl** is run from pipeline (or manually) against that artifact.  
- **No change** to where data is stored or how it is executed; only the **origin** of the file (DSL convert vs pre-built JSON) changes.

### 4.2 Direct API / Admin UI

- **Skip the console** for ingestion: call backend APIs directly (e.g. from scripts, Postman, or an admin app) to create/update regulations and scripts.  
- **Same storage and execution** as today; only the **ingestion path** changes (no Exchange file, no PayrollImport).

### 4.3 Backend “Regulation Import” Endpoint

- Add an API that accepts **one** Exchange document (or a regulation-only subset) and performs the same upserts **inside** the backend (tenant + regulation + scripts + wage types + …).  
- **Single HTTP call** instead of many; still writes to the same Regulation/Script (and related) tables; **execution** unchanged.

### 4.4 Store Scripts Outside DB (e.g. Blob / File System)

- Store script **source** in object storage or files, and store only a **reference** (e.g. URL/path) in `Script` (or an extended attribute).  
- Backend would need to **resolve** script content from that reference before passing to **ScriptCompiler**.  
- **Execution** path stays the same (ScriptCompiler + Roslyn); only **storage** of the script text and **loading** logic change.

### 4.5 Different Execution Model (e.g. Pre-compiled Assemblies)

- **Ingestion**: Still use **PayrollImport** (or API) to create Regulation + Script records; optionally also build and store **compiled** assemblies (e.g. in blob storage) and reference them from regulation/script metadata.  
- **Execution**: Backend loads **pre-compiled** assembly by reference instead of compiling from `Script.Value` at payrun time.  
- **Pros**: Faster payrun (no compile), stricter control of what runs. **Cons**: Versioning, signing, and compatibility (runtime/.NET version) must be managed.  
- **Detailed design**: See **[DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md](./DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md)** for current flow, options (compile at ingestion, CI build, blob storage, hybrid), and implementation hooks.

### 4.6 Regulation as “Package” (NuGet / Container)

- Package a country regulation as a **NuGet package** or **container image** containing Exchange JSON (and optionally pre-compiled scripts or metadata).  
- Ingestion = **install package** + run **PayrollImport** (or a dedicated importer) on the unpacked JSON.  
- **Storage and execution** can stay as today; this is mainly a **distribution and versioning** alternative.

### 4.7 DSL-Only Ingestion (No Exchange JSON in Between)

- Extend the backend (or a service) to accept **DSL/YAML** directly and perform **convert + persist** in one step (no intermediate Exchange file on disk).  
- **Storage** remains Regulation/Script (and related) tables; **execution** unchanged.  
- **Benefit**: Single pipeline step; no need to manage JSON files.

---

## 5. Summary Table

| Aspect | Current |
|--------|--------|
| **Ingestion** | PayrollImport (Exchange JSON/zip) → backend API; optionally via PayrollImportFromDsl / DSLImport / DslPrepareAndImport. |
| **Storage** | SQL Server: Regulation table; Script table (Name, Value = C# source, FunctionTypeMask, etc.); related WageType, Collector, Case, Lookup, etc. |
| **Execution** | PayrunProcessor → ScriptRepository (GetFunctionScriptsAsync) → ScriptCompiler (scripts + wage expressions + embedded) → Roslyn → AssemblyCache → FunctionHost → reflection. |

Alternatives above vary **ingestion** (who calls what), **storage** (DB only vs external script blobs), or **execution** (compile-on-demand vs pre-compiled), but the **current** design is: **ingest via PayrollImport (Exchange) → store in Regulation + Script (and related) tables → execute at payrun via Roslyn compilation and FunctionHost**.
