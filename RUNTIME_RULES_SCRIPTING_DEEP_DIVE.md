# Runtime Rules / Scripting: Deep Dive

This document covers:

1. **How runtime rules/scripting work today** in the .NET engine.
2. **Options to replicate** in Java and TypeScript.
3. **Whether runtime rules/scripting are needed at all** — and how the engine would work without them.
4. **How to build the engine in Java and TypeScript without runtime rules/scripting.**

Related: `COUNTRY_REGULATION_INGESTION_FLOW.md`, `EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md`, `DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md`, `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`.

---

## Part 1: How It Currently Works in the .NET Engine

### 1.1 Overview

Regulation logic (wage type values, collector start/end, payrun lifecycle) is implemented as **C# code** stored in the database. **Compilation does not happen at payrun time.** Instead:

1. **Import phase 1**: When **Rules.json** (or similar) is imported, script **source** (e.g. full Rules.cs) is stored in the **Script** table (Value column).
2. **Import phase 2**: When **WageTypes.json** (or other JSONs that reference functions in those scripts via valueExpression) is imported, the backend **compiles** the scripts with **Roslyn** into binaries and stores them on the script objects (WageType.Binary, Collector.Binary, etc.).
3. **At payrun time**: The backend loads **precompiled binary** from the DB (or AssemblyCache) and **invokes** lifecycle methods (e.g. `WageTypeValue`, `CollectorStart`) via reflection—**no compilation at payrun**.

So: **runtime rules/scripting** = “compile user C# at **import** time, load binary and run at **payrun** time.”

---

### 1.2 Where Scripts Live

| Layer | What | Where |
|-------|------|--------|
| **Ingestion** | Regulation + scripts (e.g. Rules.cs) in Exchange JSON | Console **PayrollImport** / **PayrollImportFromDsl** → Backend API |
| **API** | `POST/PUT` to `/tenants/{tenantId}/regulations/{regulationId}/scripts` | Upsert script; `Value` = C# source |
| **Persistence** | **Script** table | `RegulationId`, `Name`, `FunctionTypeMask`, **Value** (C# source), **Binary** (optional), **ScriptHash** |
| **Wage type / collector** | Each can have **ValueExpression** (inline) or reference **Script** (e.g. Rules) | Stored in WageType/Collector rows; script content in Script table |

Script **source** is in `Script.Value`. Script **binary** (compiled byte[]) is in `Script.Binary` (optional; filled on first compile or at import). **ScriptHash** identifies the script version for cache keys.

---

### 1.3 Compilation Flow

**When is compilation triggered?**

- When a **script object** (Payrun, WageType, Collector, Case, etc.) is **created or updated** during **import** (e.g. when WageTypes.json is imported via PayrollImport): the persistence layer’s `CreateAsync` / `UpdateAsync` calls **SetupBinaryAsync**, which calls **ScriptCompiler** and then stores **Binary** and **ScriptHash** on the script object. So compilation happens **at import time** when wage types (or collectors, etc.) that reference scripts are saved—**not** at payrun time.
- **Where**: `ScriptTrackChildDomainRepository` / `ScriptChildDomainRepository` (in Persistence). In `CreateAsync` and `UpdateAsync` they call `SetupBinaryAsync`, which calls `ScriptCompiler` with:
  - **scriptObject**: The entity (e.g. WageType) implementing `IScriptObject`.
  - **functionScripts**: Map of `FunctionType` → script **content** (e.g. wage type value expression, or content from Script table).
  - **scripts**: Optional list of **Script** rows (e.g. Rules.cs) to include in the same assembly.
  - **embeddedScriptNames**: Optional embedded resource names (base templates).

**Steps inside ScriptCompiler:**

1. **BuildObjectCodes**: Load **embedded C# templates** from `CodeFactory` (from PayrollEngine.Client.Scripting). These are base classes / partial class stubs (e.g. `WageTypeValueFunction`, `CollectorStartFunction`).
2. **BuildActionResults**: Parse **actions** (e.g. from wage type metadata) into `ActionResult` code snippets.
3. **BuildFunctionCodes**: For each function type (e.g. WageTypeValue), take the **embedded template** for that function, then:
   - **Inject** action code into `#region Action` / `#region ActionInvoke`.
   - **Inject** user expression or script into `#region Function` (the “return …” or method body).
4. **Collect codes**: Object codes (templates) + function codes (one per function type, with injected user code) + **Scripts** (e.g. Rules.cs content as full C# files).
5. **CSharpCompiler.CompileAssembly(codes)**:
   - **Roslyn**: `CSharpCompilation.Create` with all code as `SyntaxTree`s, references to System, System.Linq, System.Text.Json, etc.
   - **Emit** to a byte[] (in-memory DLL).
   - Return **ScriptCompileResult** (Script source string, **Binary** byte[]).

**Result**: One assembly per “script object” (e.g. per wage type or per regulation script set). The assembly contains the merged base classes + user code (expressions + Rules.cs). The repository then sets `scriptObject.Binary = result.Binary` and `scriptObject.ScriptHash = result.Script.ToPayrollHash()` and persists.

---

### 1.4 Loading and Caching the Assembly

**When is the assembly loaded?**

- When the **engine needs to run** a script (e.g. wage type value): the **FunctionHost** is asked for the assembly for a given type and script object.
- **FunctionHost.GetObjectAssembly(type, scriptObject)** delegates to **AssemblyCache.GetObjectAssembly(type, scriptObject)**.

**AssemblyCache:**

- **Key**: `(Type, scriptObject.ScriptHash)`.
- **Value**: Loaded assembly (and its `CollectibleAssemblyLoadContext` for unloading).
- If **not in cache**:
  - **Binary** = `scriptObject.Binary` ?? **ScriptProvider.GetBinaryAsync(context, scriptObject)** (reads Binary from DB by Id + ScriptHash).
  - **CollectibleAssemblyLoadContext.LoadFromBinary(binary)** → Assembly.
  - Cache by key; optionally evict after a timeout (timer-based cleanup).
- So: first time for a given (type, scriptHash) we load from DB (binary); later we use cache. **No compilation at this step** — compilation happened at **import time** when the script object was created/updated (ScriptTrackChildDomainRepository.CreateAsync/UpdateAsync).

---

### 1.5 Execution Flow (Wage Type Value Example)

1. **PayrunProcessor** runs **CalculateEmployeeAsync**.
2. For each **DerivedWageType**, **PayrunProcessorRegulation.CalculateWageTypeValue** is called.
3. **WageTypeScriptController.GetValue** builds a **WageTypeValueRuntime** and calls **EvaluateValue(wageType)**.
4. **WageTypeValueRuntime.EvaluateValue**:
   - **CreateScript(typeof(WageTypeValueFunction), wageType)**:
     - Resolves the **script object** (the wage type with its scripts and optional Rules).
     - **FunctionHost.GetObjectAssembly(typeof(WageTypeValueFunction), scriptObject)** → Assembly (from cache or load from binary).
     - **Create instance** of the compiled type (reflection: get type from assembly, `Activator.CreateInstance(type, runtime)`), where `runtime` is the IRuntime (context: tenant, payroll, case values, etc.).
   - **script.GetValue()** → invokes the compiled method (user expression or Rules.cs logic).
   - **ScriptValueConvert.ToDecimalValue** converts result to decimal.
5. The returned value becomes the **WageTypeResult**; collector apply and other lifecycle steps follow.

**Same pattern** for PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd, CollectorStart, CollectorEnd, WageTypeAvailable, CaseAvailable, etc.: a **ScriptController** builds a **Runtime**, gets the assembly from **FunctionHost**, creates an instance of the compiled type, and invokes the corresponding method.

---

### 1.6 Summary: .NET Runtime Scripting

| Step | Component | What happens |
|------|-----------|--------------|
| **Storage** | Script table, WageType/Collector rows | C# source in `Script.Value`; optional `Script.Binary` and `ScriptHash`. |
| **Compile** | Persistence (ScriptTrackChildDomainRepository) + **ScriptCompiler** + **CSharpCompiler** (Roslyn) | **At import**: when script object (WageType, Collector, etc.) is created/updated: merge templates + user code + Rules.cs → compile → byte[]. Persist Binary and ScriptHash on the script object. **Not at payrun.** |
| **Load** | **FunctionHost** → **AssemblyCache** | Key = (Type, ScriptHash). If not cached: load binary from DB (ScriptProvider), LoadFromBinary, cache. |
| **Execute** | **ScriptController** (e.g. WageTypeScriptController) + **Runtime** (e.g. WageTypeValueRuntime) | Get assembly from FunctionHost, create instance of compiled type with IRuntime, invoke method (GetValue, Start, End, etc.). |

So: **runtime rules/scripting** in .NET = **compile C# at import (when script objects are saved), load binary and assembly at payrun, invoke by reflection.**

---

## Part 2: Options to Replicate in Java and TypeScript

### 2.1 Java Options

#### Option A: Janino (Java source → Class at runtime)

- **What**: Compile **Java source** (string or small files) into a `Class<?>` at runtime. Similar to Roslyn for C#.
- **Flow**: Script content (e.g. Java snippet or full class) → **Janino** → bytecode → load with a **ClassLoader** → instantiate and call method (e.g. `getValue()`).
- **Pros**: Same language as engine; full Java; fast compile (~ms); small footprint. No script in DB if you precompile in CI and store bytecode or reference.
- **Cons**: User code is Java (must be valid Java); sandboxing is classloader + security manager or process boundary.
- **Replication**: Regulation = metadata + **Java** snippets (or one “Rules” class). Engine has a “ScriptCompiler” that uses Janino to produce a class; cache by content hash; invoke via reflection. No need to store source in DB — could build at import or from artifact.

#### Option B: Groovy (script → run)

- **What**: **Groovy** scripts (or classes) compiled/interpreted at runtime. More flexible syntax; can be easier for rule authors.
- **Flow**: Script content → **GroovyShell** / **GroovyClassLoader** → run or get class → invoke.
- **Pros**: Dynamic, concise; good for DSLs. Mature.
- **Cons**: Different language from engine; slightly slower than Janino; dependency on Groovy.
- **Replication**: Regulation = metadata + **Groovy** scripts. Engine evaluates Groovy with a shared binding (context: case values, collectors, etc.). Sandbox via binding (expose only what you want).

#### Option C: GraalVM (JavaScript or other language)

- **What**: **GraalVM** can run JavaScript (or Python, R, etc.) in the same process. You could write rules in **JavaScript** and run them from a Java engine.
- **Flow**: Rule script (JS) → **Context.eval("js", code)** → value or callback.
- **Pros**: Polyglot; JS is familiar; no separate Node process.
- **Cons**: GraalVM dependency; different from “Java only”; sandboxing is Graal’s responsibility.
- **Replication**: Regulation = metadata + **JS** snippets. Engine creates a Graal context, injects a context object (getCaseValue, getCollector, etc.), evaluates the snippet, gets result.

#### Option D: Precompiled Java/Kotlin regulation JARs (no runtime compile)

- **What**: Regulation is a **normal JAR** (e.g. `fr-regulation-1.0.jar`) built in CI. Engine loads the JAR and calls a **fixed interface** (e.g. `RegulationEvaluator.evaluateWageType(wageTypeId, context)`).
- **Flow**: No compile at runtime. DB stores only regulation id + **version** (or JAR ref). Classpath or **Plugin** loader loads the JAR; engine calls the implementation.
- **Pros**: No Janino/Groovy; no code in DB; full Java; versioning via artifact. See “without runtime scripting” below.
- **Cons**: New country or change = new JAR and deploy (or new version).

---

### 2.2 TypeScript Options

#### Option A: JavaScript in an isolate (e.g. isolated-vm, vm2)

- **What**: Rules are **JavaScript** (not TypeScript at runtime). Engine runs in **Node**; rule code runs in an **isolate** (V8 isolate or vm2) with a **context object** (getCaseValue, getCollector, setResult, etc.).
- **Flow**: Rule script (JS string) → **isolated-vm** or **vm2** → compile and run in isolate with context → return value.
- **Pros**: True “run user code at runtime” in TS/Node stack; no separate process.
- **Cons**: Rules are JS (no TS types at runtime); sandboxing is critical (isolate must not escape); performance and memory of isolates.
- **Replication**: Regulation = metadata + **JS** snippets. Engine creates isolate, injects context, evaluates, gets result. Cache compiled script by hash if the library supports it.

#### Option B: Precompile TypeScript to JavaScript bundles

- **What**: Rules are written in **TypeScript** in a repo; **build step** compiles them to **JavaScript** (one bundle per regulation or per script). At runtime the engine **loads and runs** the JS (e.g. `require` or `import()` of a file, or `eval` of the bundle in a sandbox).
- **Flow**: TS source → **tsc** (or esbuild) in CI/build → JS bundle. Runtime: load JS (from disk or DB or URL), run in VM/isolate with context.
- **Pros**: Authors write TS; no runtime TS compiler. Same as “precompiled” in spirit.
- **Cons**: Not “compile a string at runtime”; it’s “load a prebuilt script”. Dynamic updates require a new build and deploy (or a way to serve new bundles).

#### Option C: External regulation service (any language)

- **What**: Engine (Java or TypeScript) does **not** run rules. It calls an **external API** (or serverless function) with context (tenant, regulation, employee, wage type id, case values, etc.); the service returns the result (e.g. wage type value). The service can be implemented in **TypeScript**, Python, Go, etc.
- **Flow**: PayrunProcessor → HTTP/gRPC call to regulation service → service evaluates (e.g. with its own TS + JS or Python) → returns value. No script execution inside the engine process.
- **Pros**: No runtime scripting in the main engine; polyglot; regulation can be updated independently. See `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`.
- **Cons**: Latency, versioning, auth, and availability.

#### Option D: Expression / formula only (no full scripts)

- **What**: No “script” at all. Wage types and collectors are defined by **expressions** (e.g. a formula DSL or JSON expression tree). Engine has an **expression evaluator** (e.g. parser + AST or a small VM) and evaluates with context (case values, lookups, collector results).
- **Flow**: Wage type has `expression = "CaseValue('BaseSalary') * 0.2"` or `{ "op": "*", "left": { "caseValue": "BaseSalary" }, "right": 0.2 }`. Engine interprets at payrun time. No compile of user code.
- **Pros**: No runtime scripting; auditable data; safe. See “without runtime scripting” below.
- **Cons**: Limited expressiveness; complex rules may need many expressions or a richer DSL.

---

### 2.3 Comparison

| Approach | Java | TypeScript | Runtime compile? | Code in DB? |
|----------|------|------------|-------------------|-------------|
| **Janino** | ✅ | N/A | Yes (Java) | Optional |
| **Groovy** | ✅ | N/A | Yes (Groovy) | Optional |
| **GraalVM JS** | ✅ | N/A | Eval JS in Java | Optional |
| **JS isolate** | N/A | ✅ | Eval JS | Optional |
| **Precompiled JAR** | ✅ | N/A | No | No (ref only) |
| **Precompiled JS bundle** | N/A | ✅ | No (load script) | No or ref |
| **External service** | ✅ | ✅ | No (in service) | No (endpoint) |
| **Expression DSL** | ✅ | ✅ | No (interpret) | Yes (data) |

---

## Part 3: Do We Even Need Runtime Rules/Scripting?

### 3.1 Short Answer

**No.** The engine can work without compiling or executing user-supplied code at runtime. Regulation behaviour can be achieved by:

- **Data-driven rules**: Expressions, formulas, lookups, decision tables (see `EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md`).
- **Precompiled regulation**: Logic in a library (JAR/DLL) or JS bundle, built in CI; engine calls a fixed interface or loads a fixed script. No code in DB; no compile at payrun.
- **External regulation service**: Engine calls an API; the service does the “scripting” in its own process. No scripting inside the engine.

So: **runtime rules/scripting are optional.** They give maximum flexibility (change rules without redeploy) but add complexity, security surface, and dependency on Roslyn/Janino/JS engine. Many engines are built without them.

---

### 3.2 When Runtime Scripting Is Useful

- **Frequently changing** regulation logic that must not require a deploy.
- **Power users** who can write (and you can review) C#/Java/JS rules.
- **Multi-tenant** setups where each tenant can have custom expressions or scripts (and you accept the security and ops model).

---

### 3.3 When to Avoid Runtime Scripting

- **Strict compliance and audit**: Easier to audit **data** (expressions, lookups) and **versioned libraries** than arbitrary code in DB.
- **Security and ops**: No code execution from DB reduces risk; precompiled or external service keeps a clear boundary.
- **Simplicity**: One language (Java or TypeScript), no Roslyn/Janino/isolate in the critical path.
- **Performance and stability**: No compile or load at payrun; no cache invalidation or classloader leaks.

---

### 3.4 How the Engine Works Without Runtime Scripting

The **payrun lifecycle** stays the same: PayrunStart → EmployeeStart → [wage types + collectors] → EmployeeEnd → PayrunEnd. Only the **source of the result** for each step changes:

| Step | With runtime scripting | Without runtime scripting |
|------|-------------------------|----------------------------|
| **Wage type value** | Call compiled script (e.g. `GetValue()`) | Evaluate **expression** (DSL), or call **precompiled** function (JAR/JS/service), or call **external API**. |
| **Collector start/end** | Call compiled script | Same: expression, precompiled, or API. |
| **Payrun/employee start/end** | Call compiled script | Same: optional hooks as expressions or precompiled or API. |

So: **engine orchestration** (order of steps, case values, collectors, results) is unchanged. Only the **implementation** of “get wage type value” and “run collector start/end” is swapped from “load and run user script” to one of:

- **Expression interpreter** (formula DSL).
- **Precompiled regulation** (library or bundle with fixed interface).
- **External regulation service** (HTTP/gRPC).

See Part 4 for concrete designs.

---

## Part 4: Building the Engine Without Runtime Rules/Scripting

### 4.1 Core Idea

- **Orchestration**: Keep the same **PayrunProcessor**-style flow (context, employees, wage types, collectors, lifecycle).
- **No script table for code**: No `Script.Value` or `Script.Binary`; no Roslyn/Janino/isolate.
- **Regulation behaviour** comes from: **(1) data** (expressions, lookups, steps) or **(2) precompiled code** (JAR/JS with fixed API) or **(3) external service**.

---

### 4.2 Option A: Expression / Formula DSL Only

**Stored in DB:**

- **WageType**: e.g. `ValueExpression = "CaseValue('BaseSalary') * 0.2"` or a JSON expression tree. Optional: `CollectorLinks`, `Attributes`.
- **Collector**: e.g. `StartExpression`, `EndExpression` (or empty). No full script.
- **Lookups**: Same as today (name, key, value).
- **Cases**: Structure and case values; no C# script.

**Execution:**

- Engine has an **ExpressionEvaluator** (parser + AST or a small VM). Context: case values, collector results, lookups, period, employee.
- For each wage type: **evaluate(ValueExpression, context)** → decimal. For collector start/end: evaluate optional expressions or no-op.
- **Java**: Use a library (e.g. **Aviator**, **JEL**, **Spring Expression**, or a small custom parser). **TypeScript**: Use a safe expression evaluator (e.g. **expr-eval** with a whitelist, or a custom AST).

**Pros**: No code in DB; auditable; no compile. **Cons**: Limited to what the expression language can do; complex rules may need many expressions or a richer DSL.

---

### 4.3 Option B: Precompiled Regulation (Library or Bundle)

**Stored in DB:**

- **Regulation**: id, name, **regulationPackageId** (e.g. `fr-regulation` or `France.v2`). No Script rows for code.
- **WageType / Collector**: Metadata (number, name, collector links); **no** ValueExpression or script. Behaviour is defined **inside** the package.

**Execution:**

- **Java**: At startup or on first use, **load a JAR** (from classpath or from a registry by `regulationPackageId`). JAR contains a class implementing e.g. `RegulationEvaluator`: `BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context)`, `void collectorStart(String collectorName, EvaluationContext context)`, etc. PayrunProcessor resolves regulation → evaluator instance → calls methods. No compile at runtime.
- **TypeScript**: **Load a JS module** (file or URL) per regulation. Module exports e.g. `evaluateWageType(wageTypeNumber, context)`, `collectorStart(collectorName, context)`. Engine calls these. No eval of arbitrary strings; only `import()` or `require()` of a known module. Build: TS → JS in CI; deploy the JS bundle.

**Pros**: Full language (Java/TS); no code in DB; versioning via artifact. **Cons**: New country or change = new build/deploy (or new package version).

---

### 4.4 Option C: External Regulation Service

**Stored in DB:**

- **Regulation**: id, name, **serviceEndpoint** (URL or Lambda ARN). No Script rows.
- **WageType / Collector**: Metadata only; behaviour lives in the service.

**Execution:**

- PayrunProcessor, for each wage type (or batch per employee), calls **POST /evaluate/wage-type** (or similar) with context (tenant, regulation, employee, period, case values, wage type id). Service returns `{ value: 1234.56 }`. Engine persists result and continues. Same for collector start/end and payrun/employee lifecycle if needed.
- **Java or TypeScript** engine: same; both use HTTP/gRPC client. Regulation service can be implemented in any language (e.g. TypeScript, Python).

**Pros**: No scripting in engine; regulation can be updated independently; polyglot. **Cons**: Latency, versioning, auth, availability. See `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`.

---

### 4.5 Option D: Hybrid (Expression + Precompiled or Service)

- **Simple** wage types: **expression** only (e.g. `CaseValue('BaseSalary') * 0.2`).
- **Complex** regulation: **precompiled** JAR/JS or **external service** for that regulation.
- DB: WageType has optional `ValueExpression`; Regulation has optional `RegulationPackageId` or `ServiceEndpoint`. Engine chooses: if expression present, evaluate; else if package/endpoint present, call library or service.

---

### 4.6 Concrete Steps for Java (No Runtime Scripting)

1. **Domain**: Same concepts (Tenant, Payroll, Payrun, Employee, WageType, Collector, Case, Result). Use **BigDecimal** for money.
2. **PayrunProcessor**: Same orchestration (lifecycle, order of wage types and collectors). Replace “get script assembly and invoke GetValue()” with:
   - **Expression path**: `expressionEvaluator.evaluate(wageType.getValueExpression(), context)`.
   - **Precompiled path**: `regulationEvaluator.evaluateWageType(wageType.getNumber(), context)` (e.g. from a JAR loaded by regulation id).
3. **Expression evaluator**: Implement or use a library (Aviator, JEL, etc.); context exposes `getCaseValue(name)`, `getCollector(name)`, `getLookup(name, key)`. No `eval` of arbitrary Java.
4. **Regulation packages**: Build France/India/Swiss as JARs implementing a single interface; deploy to classpath or load from a registry; DB stores only regulation id + version (or package id).
5. **Persistence**: No Script table (or only for metadata/audit). WageType/Collector store expressions or references only.

---

### 4.7 Concrete Steps for TypeScript (No Runtime Scripting)

1. **Domain**: Same concepts; use **decimal.js** (or similar) for money. Same lifecycle.
2. **PayrunProcessor**: Same orchestration. Replace “run script in isolate” with:
   - **Expression path**: `expressionEvaluator.evaluate(wageType.valueExpression, context)`.
   - **Precompiled path**: `await regulationModule.evaluateWageType(wageType.number, context)` (module loaded by regulation id from disk or URL).
3. **Expression evaluator**: Use **expr-eval** or a small custom parser; context is a plain object with `getCaseValue`, `getCollector`, `getLookup`. No `eval(code)` of user strings.
4. **Regulation modules**: Build France/India/Swiss as TS projects → JS bundles; deploy as files or serve from a URL. Engine loads the module once per regulation (or caches); DB stores only regulation id + version (or bundle path).
5. **Persistence**: No Script table for code. WageType/Collector store expressions or references only.

---

## Summary Table

| Topic | .NET today | Java replication | TypeScript replication | Without runtime scripting |
|-------|------------|------------------|------------------------|----------------------------|
| **Where scripts live** | Script.Value, WageType/Collector expressions | Same idea (optional); or no script in DB | Same | No script in DB; expressions or refs only |
| **Compile** | **At import** (when WageTypes etc. with script refs are saved); not at payrun | Janino / Groovy / GraalVM at import or first use | JS in isolate or precompile TS→JS | No compile; expression interpret or precompiled/service |
| **Load** | AssemblyCache, LoadFromBinary | ClassLoader, cache by hash | Isolate or require/import | N/A or load JAR/JS module once |
| **Execute** | Reflection on compiled type | Reflection on Janino/Groovy class or Graal eval | Isolate run or module call | Expression evaluator or library/service call |
| **Need runtime scripting?** | Yes (current design) | Optional (Janino/Groovy) or skip | Optional (JS isolate) or skip | **No** — engine works with expressions, precompiled, or service |

Use this doc to decide: **(1)** replicate .NET-style runtime scripting in Java/TS, or **(2)** build without it using expressions, precompiled regulation, or an external service, and to implement the chosen option in Java or TypeScript.

---

## Part 5: Comparison and Best Option

This section compares the main execution models and recommends the best option by context.

### 5.1 Options at a Glance

| Option | What it is | Runtime compile? | Code in DB? | Deploy for rule change? |
|--------|------------|------------------|-------------|-------------------------|
| **1. Runtime rules/scripting** | Compile user code at **import** (or first use), run binary at payrun (Roslyn, Janino, JS isolate) | At import / first use, not payrun | Yes (source) or optional binary | No (ingest/update script) |
| **2. Precompiled (JAR / JS bundle)** | Regulation logic in a library (JAR or JS module); engine calls fixed interface | No | No (ref/version only) | Yes (new artifact version) |
| **3. External regulation service** | Engine calls HTTP/gRPC; separate service evaluates rules (any language) | No (in service) | No (endpoint/version) | Service deploy or config |
| **4. Expression / formula DSL only** | Wage types and collectors defined by expressions (formula or JSON AST); engine interprets | No (interpret) | Yes (data only) | No (update expression data) |
| **5. Hybrid** | Simple cases: expression; complex: precompiled or external service | Depends on path | Mixed | Depends on path |

---

### 5.2 Pros and Cons by Option

#### Option 1: Runtime rules/scripting (current .NET style)

| Pros | Cons |
|------|------|
| **Maximum flexibility**: Change rules without redeploy (ingest new script or update DB). | **Security surface**: Execute code from DB; sandbox/isolate and review required. |
| **Power-user friendly**: Full language (C#/Java/JS); complex logic in one place. | **Complexity**: Compiler (Roslyn/Janino), cache, classloader/isolate lifecycle, versioning. |
| **Multi-tenant customisation**: Per-tenant scripts possible (if ops model allows). | **Performance**: First-time compile or load; cache invalidation; possible memory/leaks. |
| **Familiar to current .NET engine**: Easiest conceptual parity. | **Audit**: Harder to treat “rules” as pure data; code review vs data review. |

**Best for**: Teams that need “change regulation without deploy” and accept security/ops cost; or direct parity with existing .NET engine.

---

#### Option 2: Precompiled (JAR / JS bundle)

| Pros | Cons |
|------|------|
| **No runtime scripting**: No compile or eval in engine; clear security boundary. | **Deploy for every change**: New country or rule change = new artifact + deploy (or version). |
| **Full language**: Java/TypeScript; types, tests, IDE, CI/CD. | **Less “instant” customisation**: No “edit script in UI and run”; requires build pipeline. |
| **Versioning via artifact**: Regulation = `fr-regulation:1.2.3`; reproducible, auditable. | **Engine must load plugins**: Classpath or plugin loader (JAR) or dynamic import (JS). |
| **Performance**: Load once per regulation; no compile at payrun. | **Multi-tenant custom code**: Harder (one JAR per tenant or branching inside JAR). |
| **Simpler engine**: No Script table for code, no compiler, no script cache. | |

**Best for**: Regulated environments, auditability, and when rule changes go through a release process anyway.

---

#### Option 3: External regulation service

| Pros | Cons |
|------|------|
| **No scripting in engine**: Engine is pure orchestration + HTTP/gRPC client. | **Latency**: Network call per wage type or per batch; must design for throughput. |
| **Polyglot**: Service can be Java, TypeScript, Python, etc. | **Availability**: Engine depends on service; need retries, circuit breaker, fallback. |
| **Independent scaling**: Scale regulation service separately. | **Versioning and auth**: Contract versioning, API keys, tenant isolation. |
| **Regulation updated independently**: Deploy service without touching engine. | **Operational complexity**: Two (or more) services to deploy, monitor, secure. |
| **Clear boundary**: Engine = context + call; service = all rule logic. | **Debugging**: Cross-service tracing and context. |

**Best for**: When regulation is owned by a different team or product, or when regulation must scale/evolve independently.

---

#### Option 4: Expression / formula DSL only

| Pros | Cons |
|------|------|
| **No code execution**: Only interpret expressions; minimal security surface. | **Limited expressiveness**: Complex rules need many expressions or a rich DSL. |
| **Auditable data**: Rules are data (formulas, lookups, steps); easy to version and diff. | **DSL design**: Must design and maintain expression language and semantics. |
| **No compile, no JAR**: Simple engine; expression evaluator + context. | **Power users**: Cannot write arbitrary code; may need “escape hatch” later. |
| **Fast to evaluate**: Parse once or use AST; evaluate with context. | **Edge cases**: Some regulations may need branching or logic that is clumsy in DSL. |

**Best for**: Simple to medium complexity rules, strong compliance/audit needs, and when rules are mostly formulas + lookups.

---

#### Option 5: Hybrid (expression + precompiled or service)

| Pros | Cons |
|------|------|
| **Right tool per case**: Simple = expression; complex = JAR/JS or service. | **Two (or three) code paths**: Engine must branch and maintain all paths. |
| **Incremental adoption**: Start with expressions; add precompiled or service where needed. | **Consistency**: Same “wage type” concept may be expression in one regulation and code in another. |
| **Escape hatch**: Complex regulations don’t force “everything in DSL”. | **Operational mix**: Some regulations deploy with engine, others via service. |

**Best for**: Mixed portfolio (simple + complex regulations) or migration from current engine (keep some behaviour as expressions, others as precompiled/service).

---

### 5.3 Comparison Matrix

| Criteria | Runtime scripting | Precompiled (JAR/JS) | External service | Expression DSL | Hybrid |
|----------|-------------------|----------------------|------------------|----------------|--------|
| **Change rules without deploy** | ✅ Yes | ❌ No | ⚠️ Service deploy | ✅ Yes (data) | ⚠️ Depends |
| **Security (no execute-from-DB)** | ❌ No | ✅ Yes | ✅ Yes | ✅ Yes | ⚠️ Depends |
| **Auditability (data / versioned artifact)** | ⚠️ Code in DB | ✅ Artifact version | ⚠️ API version | ✅ Data only | ⚠️ Mixed |
| **Full language (complex logic)** | ✅ Yes | ✅ Yes | ✅ Yes (in service) | ❌ No | ✅ For complex path |
| **Engine simplicity** | ❌ Compiler, cache | ✅ Load plugin | ✅ HTTP client | ✅ Evaluator | ⚠️ Multiple paths |
| **Performance (no compile at payrun)** | ⚠️ Cache dependent | ✅ Yes | ⚠️ Network | ✅ Yes | ⚠️ Depends |
| **Operational complexity** | High | Medium | High | Low | Medium–High |
| **Best fit** | Max flexibility, accept risk | Compliance, versioned releases | Separate ownership, scale | Simple rules, audit | Mixed portfolio |

---

### 5.4 Recommendation: Best Option by Context

**There is no single “best” option for every context.** The best choice depends on requirements.

| If you need… | Best option | Rationale |
|--------------|-------------|-----------|
| **Strict compliance, audit, no code-from-DB** | **Precompiled (JAR/JS)** or **Expression DSL** | No runtime code execution; versioned artifacts or data-only rules. |
| **Change rules without any deploy** | **Runtime scripting** or **Expression DSL** | Script/expression update in DB or ingest; no artifact deploy. |
| **Simplest engine, lowest risk** | **Expression DSL** or **Precompiled** | No compiler, no sandbox; either interpret data or call a known library. |
| **Regulation owned by another team / product** | **External regulation service** | Clear contract; engine only orchestrates and calls API. |
| **Mix of simple and complex regulations** | **Hybrid** | Expressions for simple; JAR/JS or service for complex. |
| **Parity with current .NET engine** | **Runtime scripting** (Java: Janino; TS: JS isolate) | Closest behaviour and data model to existing engine. |
| **New greenfield, want to avoid .NET pain** | **Precompiled** or **Expression + Precompiled** | Avoid “compile in production”; use CI-built artifacts and optional expressions. |

---

### 5.5 Single Recommendation When Forced to Choose

If forced to pick **one** default for a **new** payroll engine (Java or TypeScript):

- **Default recommendation: Precompiled (JAR for Java, JS bundle for TypeScript)** with optional **expression DSL** for simple wage types (e.g. `CaseValue('X') * 0.2`).

**Why:**

1. **Security and audit**: No execution of code stored in DB; regulation is a versioned artifact (e.g. `fr-regulation:1.2.3`).
2. **Simplicity**: No Roslyn/Janino/isolate in the engine; no script cache or classloader lifecycle.
3. **Performance**: Load regulation once; no compile at payrun.
4. **Full language**: Complex logic stays in Java/TypeScript with normal testing and CI/CD.
5. **Escape for simple cases**: Expression evaluator for formula-only wage types keeps the engine flexible without putting code in DB.

**When not to choose this:**

- You must support “change rules without deploy” and cannot use a release process → lean toward **runtime scripting** or **expression DSL**.
- Regulation is a separate product or team → **external regulation service**.
- Rules are mostly formulas and lookups → **expression DSL only** may be enough.
