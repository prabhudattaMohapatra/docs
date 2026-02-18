# Different Execution Model: Pre-Compiled Assemblies

This document details the **pre-compiled assembly** execution model for regulation scripts: when and where compilation happens, where binaries are stored, how the runtime loads them, and design options you can adopt.

---

## 1. Current Model (Compile-on-Demand + Binary Cache)

### 1.1 Flow Today

| Step | Where | What |
|------|--------|------|
| 1 | **Ingestion** | PayrollImport stores **source only**: Regulation + Script rows with `Value` = C# source (e.g. Rules.cs). No `Binary` at import. |
| 2 | **First use (payrun)** | PayrunProcessor needs a script object (e.g. Payrun for payrun start/end). Repository loads the object; if **Binary** is null, it runs **ScriptCompiler** (Roslyn), gets `ScriptCompileResult.Binary`, assigns `scriptObject.Binary` and `ScriptHash`, and **persists** them (e.g. `ScriptTrackChildDomainRepository` / `ScriptChildDomainRepository`). |
| 3 | **Assembly load** | **FunctionHost.GetObjectAssembly(type, scriptObject)** → **AssemblyCache.GetObjectAssembly**. Cache key = `(type, scriptObject.ScriptHash)`. If not cached: binary = `scriptObject.Binary` ?? **ScriptProvider.GetBinaryAsync(context, scriptObject)** (reads Binary from DB by Id + ScriptHash). Then **CollectibleAssemblyLoadContext.LoadFromBinary(binary)**. Result is cached. |
| 4 | **Execution** | PayrunProcessorScripts (and controllers) use the loaded assembly via reflection (e.g. PayrunStart, EmployeeStart, WageTypeValue). |

So today: **first** payrun for a given script compiles and stores Binary; **later** payruns use the stored Binary (and in-memory AssemblyCache). Compilation is deferred to first use and then cached in DB and in process.

### 1.2 Relevant Types

- **ScriptCompiler**: Builds full C# (object codes + function codes + DB scripts), calls **CSharpCompiler** (Roslyn), returns **ScriptCompileResult** (includes `Binary`).
- **AssemblyCache**: Key = `(Type, ScriptHash)`. Gets binary from `scriptObject.Binary` or **IScriptProvider.GetBinaryAsync**; loads via **CollectibleAssemblyLoadContext**; caches by key; evicts by timeout.
- **IScriptProvider** (e.g. **ScriptProviderRepository**): **GetBinaryAsync** only **reads** `Binary` from DB (by script object Id + ScriptHash). Does not compile.
- **ScriptTrackDomainObject**: Has `Script`, `ScriptVersion`, **Binary**, **ScriptHash**. Repositories set Binary after compile and persist.

---

## 2. Different Execution Model: What Changes

The idea is to **avoid compile at payrun time** and/or **move compile to a controlled moment** (ingestion, CI, or a dedicated “build regulation” step). Execution stays the same: **AssemblyCache** still loads from **binary**; only the **source** of that binary and **when** it is produced change.

### 2.1 Goals

- **Faster payrun**: No Roslyn compile on first use; lower CPU and latency.
- **Controlled build**: Compile once in CI or at import; same binary for all payruns until regulation is updated.
- **Stricter control**: Option to run only signed or approved assemblies; no ad-hoc compilation in production.
- **Clear versioning**: Binary tied to regulation/script version and runtime (.NET version).

### 2.2 What Stays the Same

- **Execution path**: PayrunProcessor → script object (with Binary) → FunctionHost → AssemblyCache → LoadFromBinary → reflection. No change to PayrunProcessorScripts or controllers.
- **AssemblyCache and CollectibleAssemblyLoadContext**: Still load from byte[] and cache by (Type, ScriptHash).
- **IScriptProvider.GetBinaryAsync**: Still the fallback when `scriptObject.Binary` is null; only the **implementation** can change (e.g. resolve from blob by reference).

---

## 3. Design Options

### Option A: Compile at Ingestion (Backend)

- **When**: During **PayrollImport** (or a dedicated “build regulation” API): after upserting Regulation + Scripts (source), backend runs **ScriptCompiler** for each script object that has source, gets `Binary`, and writes it (and ScriptHash) to the same DB row (or to a separate Binary store).
- **Storage**: Same as today: **Binary** (and ScriptHash) on the script object in SQL Server. Optionally keep or drop **Value** (source) for production.
- **Execution**: Unchanged. First payrun already sees Binary; AssemblyCache loads from DB via ScriptProvider.
- **Pros**: No compile on first payrun; single place (import) for build.  
- **Cons**: Import takes longer; import pipeline must have Roslyn and same .NET version as runtime.

### Option B: Compile in CI / Build Job; Store Binary in DB via API

- **When**: CI or a build job (e.g. after DSL convert): build process compiles regulation scripts (same logic as ScriptCompiler/CSharpCompiler), produces byte[] per “script unit,” then calls a **backend API** (e.g. “PATCH regulation script with binary”) to store **Binary** and **ScriptHash** for the relevant script(s). Source can be stored at import; binary filled in by this step.
- **Storage**: Still **Binary** (and ScriptHash) in DB. Source can live only in source control if you prefer.
- **Execution**: Unchanged. Payrun loads Binary from DB.
- **Pros**: Compile in CI; backend stays simple (no Roslyn at import).  
- **Cons**: Two-step flow (import metadata/source, then “upload binary”); versioning and consistency (script version vs binary) must be clear.

### Option C: Pre-Compiled Assemblies in Blob Storage; Reference in DB

- **When**: CI or build job compiles and publishes .dll (or byte[]) to **blob storage** (e.g. S3, Azure Blob), with a stable key (e.g. tenant + regulation + script name + version/hash). DB stores only a **reference** (e.g. URL, or storage path + version).
- **Storage**: **Script** (or Regulation) row: optional **Value** (source); **Binary** = null; new field or attribute e.g. **BinaryReference** = `https://bucket/path/Regulation_X_Script_Y_v2.dll` (or similar). No large BLOB in DB.
- **Execution**: **IScriptProvider** implementation: if **BinaryReference** is set, resolve URL/path → download bytes → return byte[]. AssemblyCache and FunctionHost unchanged.
- **Pros**: DB stays small; binaries versioned and distributed via blob; can use CDN.  
- **Cons**: Network dependency at payrun (unless cached); versioning and invalidation (when to stop using an old DLL) must be defined.

### Option D: Hybrid – Prefer Binary, Fallback to Compile

- **When**: At payrun load: if **Binary** (or BinaryReference) is present and valid, use it; otherwise **compile from source** (current behavior) and persist Binary for next time.
- **Storage**: DB as today (Binary + ScriptHash + Value). Optionally add BinaryReference for “external” binaries.
- **Execution**: AssemblyCache already does: use scriptObject.Binary ?? ScriptProvider.GetBinaryAsync. Extend ScriptProvider: if reference set, fetch from blob; else read Binary from DB; if still null, trigger compile (or fail).
- **Pros**: Backward compatible; can migrate gradually to pre-compiled.  
- **Cons**: Two code paths; need clear policy (when to allow compile in prod).

---

## 4. Implementation Hooks in the Current Codebase

### 4.1 Where Binary Is Set (Today)

- **ScriptChildDomainRepository** / **ScriptTrackChildDomainRepository**: After compiling (ScriptCompiler), set `scriptObject.Binary = result.Binary` and `scriptObject.ScriptHash = result.Script.ToPayrollHash()`, then persist. So **compile-on-first-read** is in the repository layer when building script objects (e.g. Payrun, WageType, Collector).

### 4.2 Where Binary Is Read at Runtime

- **AssemblyCache.GetObjectAssembly**: Uses `scriptObject.Binary` if set; else `ScriptProvider.GetBinaryAsync(DbContext, scriptObject)`. So:
  - **Pre-fill Binary**: Ensure Binary (and ScriptHash) are set at ingestion or by a build step; repository will load them, and AssemblyCache will use them (no compile).
  - **External binary**: Implement **IScriptProvider.GetBinaryAsync** to resolve from blob when script object has a reference (e.g. new column or attribute); return byte[]; AssemblyCache unchanged.

### 4.3 Script Hash and Cache Key

- Cache key = `(Type, scriptObject.ScriptHash)`. **ScriptHash** must reflect the script content (or binary) so that when regulation is updated, a new key is used and a new assembly is loaded. Today ScriptHash is derived from script source. For pre-compiled binaries, hash should be part of the build (e.g. content hash of the DLL or of the source at build time) and stored with the script object so cache invalidation works.

### 4.4 Regulation-Level vs Script-Level Binary

- Today the **Payrun** (and WageType, Collector, etc.) is the **IScriptObject**: it carries the combined “script” (embedded templates + regulation scripts + wage-type expressions) and one **Binary** per object. So the unit of compilation is the **script object** (e.g. one Payrun, one WageType). Pre-compiled model can stay at that granularity: one binary per script object, stored or referenced on that object.

---

## 5. Versioning, Compatibility, Security

### 5.1 .NET Runtime Compatibility

- Pre-compiled assemblies must be built with the **same .NET version** (and compatible references) as the backend runtime. In CI, build with the same target framework (e.g. net9.0) and same PayrollEngine.* package versions.

### 5.2 Versioning and Rollback

- Store with the script object: **ScriptVersion** (or similar) and **ScriptHash**. When you deploy a new regulation version, new Binary + ScriptHash; AssemblyCache will load the new binary (new key). Rollback = restore previous regulation/script rows (and Binary or BinaryReference) and optionally clear AssemblyCache for that tenant/regulation.

### 5.3 Signing (Optional)

- For “only run approved code,” compile in a controlled pipeline and **sign** the assembly. At load time, verify signature before passing bytes to CollectibleAssemblyLoadContext. This requires an extra step in AssemblyCache or in a custom IScriptProvider.

### 5.4 Source Retention

- For audit and debugging, keep **Value** (source) in DB or in version control even when using pre-compiled Binary. Optionally restrict production to “Binary only” (no source, no compile) for tighter control.

---

## 6. Recommended Path (Short Term)

1. **Option A or B**:  
   - **A**: Add a “build script” step during or right after PayrollImport (same backend): compile all regulation scripts for the imported tenant/regulation, fill **Binary** and **ScriptHash**, persist.  
   - **B**: In CI, after producing Exchange (or DSL output), run a small build tool that compiles (reuse ScriptCompiler/CSharpCompiler logic), then call an API to attach **Binary** + **ScriptHash** to the relevant script(s).  
   Result: first payrun already gets Binary from DB; no compile in PayrunProcessor path.

2. **Keep execution as-is**: No change to FunctionHost, AssemblyCache, or PayrunProcessorScripts; they already support “binary only” when Binary is set.

3. **Option C** later if you want to move large binaries out of the DB: add **BinaryReference**, implement **IScriptProvider** to resolve from blob, and optionally clear **Binary** in DB for that row.

4. **ScriptHash**: Ensure build step sets ScriptHash from the compiled output (or source hash at build time) so cache keys update when regulation changes.

This gives you a **different execution model** (compile once at ingestion/build, run from binary at payrun) with minimal change to the current runtime and a clear path to external blob storage and stricter control later.
