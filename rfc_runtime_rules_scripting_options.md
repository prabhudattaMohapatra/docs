# RFC: Runtime Rules / Scripting — Option Selection for New Payroll Engine

**Status:** in progress / ready for review / approved

**Author:**

**Open Date:**

**Closing Date for Comments:**

---

## Motivation

The new payroll engine (Java or TypeScript) must decide how regulation logic (wage type values, collector start/apply/end, payrun/employee lifecycle) is executed. The existing .NET engine uses **runtime rules/scripting**: C# stored in the DB, compiled (Roslyn) on demand or from stored binary, and invoked at payrun time. Replicating this approach in Java/TypeScript is one option; alternatives (precompiled artifacts, external service, expression DSL, hybrid) exist and have different trade-offs. This RFC exists to capture each option in a consistent format so stakeholders can evaluate and select the execution model for the new engine.

**References:** `RUNTIME_RULES_SCRIPTING_DOTNET_DETAILED.md`, `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md`.

---

## Context

- **Current system:** The .NET payroll engine (payroll-engine-backend, payroll-engine-client-scripting, payroll-engine-regulation-France, etc.) implements regulation logic as C# code. Script source lives in **[Script].Value**; wage types have **valueExpression** (e.g. `return social_security_ceiling();`) that call methods defined in regulation scripts (e.g. FR.Rules = Rules.cs). The engine compiles (Roslyn) template + expression + script content into one assembly per script object, stores **Binary** and **ScriptHash** on WageType/Collector/etc., and at payrun loads the assembly and invokes methods via reflection.
- **Target system:** A new payroll engine built from scratch in **Java** or **TypeScript**, taking inspiration from the .NET engine. The engine will need to support payrun orchestration (PayrunStart → EmployeeStart → wage types + collectors → EmployeeEnd → PayrunEnd) and must obtain wage type values and collector behaviour from some execution model.
- **Problem space:** The execution model for regulation rules is not fixed. Options range from “replicate .NET-style runtime scripting” (compile/run user code at payrun) to “no user code at all” (expressions, precompiled libraries, or external service). Each option has implications for security, auditability, performance, operability, and migration.

---

## Problem Statement

**Goal:** Select and specify the **runtime rules/scripting** (or non-scripting) approach for the new payroll engine so that:

1. Regulation behaviour (wage type value, collector start/apply/end, lifecycle hooks) can be implemented and executed in a well-defined way.
2. The choice is explicit and documented with enough detail to implement it (APIs, storage, flow, observability).
3. Trade-offs (security, audit, performance, simplicity, flexibility) are clear for each option.

This is primarily a **design/architecture** requirement with strong **non-functional** implications (security, performance, operability).

---

## Design

- **Functional:** The engine must support the same conceptual payrun lifecycle and results (wage type results, collector results, case values). The **source** of those results may be: (1) compiled user script, (2) precompiled library, (3) external API, (4) interpreted expression, or (5) a mix (hybrid).
- **Non-functional:** Security (no unintended code execution), auditability (versioned/data-driven where required), performance (predictable latency, no compile spikes if avoidable), operability (observability, deploy model).
- **SCORP (five pillars):** Consider **Security** (sandbox vs no-code-in-DB), **Cost** (compute for compile vs external calls), **Operational excellence** (one service vs two, cache lifecycle), **Reliability** (dependency on external service, classloader/isolate stability), **Performance** (compile/load vs interpret vs RPC).

---

## Solution Highlights

Five options are detailed below. Summary:

| Option | Description | Runtime compile? | Code in DB? | Deploy for rule change? |
|--------|-------------|------------------|-------------|-------------------------|
| **1. Runtime rules/scripting** | Compile and run user code (Java/JS) at payrun (Janino, JS isolate, etc.) | Yes | Yes (source) or optional binary | No (ingest/update script) |
| **2. Precompiled (JAR / JS bundle)** | Regulation in a library; engine calls fixed interface | No | No (ref/version only) | Yes (new artifact) |
| **3. External regulation service** | Engine calls HTTP/gRPC; separate service runs rules | No (in service) | No (endpoint) | Service deploy/config |
| **4. Expression / formula DSL only** | Rules as expressions; engine interprets | No (interpret) | Yes (data only) | No (update data) |
| **5. Hybrid** | Simple = expression; complex = precompiled or service | Depends | Mixed | Depends |

**Recommendation (default for new greenfield):** Precompiled (JAR/JS) with optional expression DSL for simple wage types, unless requirements force “change rules without deploy” or “regulation owned by another team” (then runtime scripting or external service).

**POC plan:** Two options will be validated via Java POCs before final selection: **Precompiled JAR** ([poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md)) and **External Regulation Service** ([poc_runtime_rules_external_service_java.md](poc_runtime_rules_external_service_java.md)). Evaluation criteria and decision process: [poc_runtime_rules_evaluation_and_decision.md](poc_runtime_rules_evaluation_and_decision.md).

---

## Solution Details

For each option, the following is specified: flow, storage/DB, APIs/contracts, infra/ops, pros/cons, and when to use.

---

### Option 1: Runtime Rules / Scripting

**Description:** Replicate .NET-style behaviour: regulation logic is stored as **source** (e.g. Java or JavaScript); the engine **compiles** (or evaluates) it at build/import or on first use, then **loads** the compiled artifact and **invokes** it at payrun time (e.g. `getValue()`, `collectorStart()`).

**Flow:**

1. **Ingestion:** Regulation payload includes script source (e.g. Rules.java or Rules.js) and wage type **valueExpression** (e.g. `return social_security_ceiling();`). Client/API persists script in DB and wage types with expression.
2. **Compile (or eval):** When a script object (e.g. WageType) is first built for execution: merge template + expression + script content → **Janino** (Java) or **isolate/vm** (JS) → bytecode/script. Store **Binary** and **ScriptHash** on the entity (or in cache).
3. **Payrun:** For each wage type, resolve script object → get assembly/script by (Type, ScriptHash) from cache or DB → create instance with **EvaluationContext** (case values, lookups, period, employee) → invoke `getValue()` (or equivalent). Result → WageTypeResult.

**Additional details:**

- **Java:** Janino compiles Java source to bytecode; engine uses a dedicated ClassLoader (or CollectibleAssemblyLoadContext-style) to load the class and invoke methods via reflection. Alternative: Groovy (script language) or GraalVM (run JS from Java). Cache key = (e.g. WageTypeValueFunction class, ScriptHash); first miss triggers compile and persist of Binary.
- **TypeScript:** Rules are JavaScript at runtime (no TS compiler in process). Engine uses **isolated-vm** or **vm2** to run script in a V8 isolate; context object (getCaseValue, setValue, getLookup) is injected. No bytecode stored; script string is re-compiled in isolate unless the library caches compiled script by hash.
- **Lifecycle coverage:** Same pattern for PayrunStart, PayrunEnd, EmployeeStart, EmployeeEnd, CollectorStart, CollectorApply, CollectorEnd, WageTypeAvailable, CaseAvailable, etc.—each has a template + optional user expression/script, compiled or evaluated, then invoked.
- **Failure modes:** Compile failure (syntax error in script) → fail at build/import or first use; runtime exception in script → fail that wage type or payrun step; cache/classloader leak → monitor and evict by timeout or LRU.

**Storage / DB:**

- **[Script]** table: **Value** (NVARCHAR(MAX)) = full script source. No Binary on Script in core model; Binary on **WageType** / **Collector** / **Case** / **Report** / **Payrun**.
- **WageType**: ValueExpression, optional Script reference, **Binary**, **ScriptHash** (after compile).
- Same pattern as current .NET engine (see `FRANCE_REGULATION_INGESTION_RUNTIME_AND_DB.md`).

**APIs / Contracts:**

- Regulation API: POST/PUT scripts (source), wage types (valueExpression, collector links).
- Engine internal: `ScriptCompiler` (Janino or equivalent), `ScriptProvider.GetBinaryAsync`, `FunctionHost.GetObjectAssembly(type, scriptObject)`, `WageTypeValueRuntime.EvaluateValue(wageType)`.

**Infra / Ops:**

- Engine process must host compiler (Janino) or JS isolate; memory and CPU for compile/cache. Cache eviction policy (e.g. by ScriptHash, timeout). No separate regulation service.

**Pros:**

- **Maximum flexibility:** Change rules by ingesting new script or updating Script.Value; no engine or artifact deploy.
- **Power-user friendly:** Full language (Java/JS); complex branching, loops, and helpers (e.g. France Rules.cs) live in one place.
- **Multi-tenant customisation:** Per-tenant or per-regulation scripts possible if the ops and security model allow.
- **Conceptual parity with .NET:** Easiest mental model when migrating or comparing; same flow (valueExpression + Rules script → compile → invoke).
- **Single deploy surface:** Only the engine is deployed; regulations are data + script content.

**Cons:**

- **Security surface:** Executing code from DB requires sandboxing (isolate, ClassLoader boundaries, or process boundary) and review of who can write scripts; risk of malicious or buggy script.
- **Complexity:** Compiler (Janino/Groovy) or isolate runtime, cache lifecycle, ScriptHash/versioning, and classloader or isolate teardown to avoid leaks.
- **Performance variability:** First-time compile (or first load of Binary) can add latency; cache invalidation on script change can cause short spikes; large numbers of script objects can increase memory.
- **Audit and compliance:** Rules as “code in DB” are harder to treat as pure data; auditors may expect code review and change control similar to application code.
- **Debugging:** Stack traces and logs reference compiled/generated code; mapping back to original script and line numbers requires tooling.

**When to use:** Need “change rules without deploy” and accept security/ops cost; or direct parity with existing .NET engine.

---

### Option 2: Precompiled (JAR / JS Bundle)

**Description:** Regulation logic lives in a **versioned artifact** (JAR for Java, JS bundle for TypeScript). The engine does **not** compile or evaluate user code at runtime. It **loads** the artifact (by regulation id/version) and calls a **fixed interface** (e.g. `evaluateWageType(wageTypeNumber, context)`).

**Flow:**

1. **Build (CI):** France/India/Swiss regulations are implemented as Java/TypeScript projects → JAR or JS bundle. Each implements e.g. `RegulationEvaluator`: `evaluateWageType`, `collectorStart`, `collectorEnd`, etc. Artifact is versioned (e.g. `fr-regulation:1.2.3`).
2. **Storage:** DB stores **Regulation** with **regulationPackageId** (or version). **WageType** / **Collector** store metadata only (number, name, collector links); **no** ValueExpression or script source.
3. **Payrun:** Engine resolves regulation → loads evaluator (from classpath or plugin registry by package id) → for each wage type calls `evaluator.evaluateWageType(wageTypeNumber, context)` → result → WageTypeResult. No compile at payrun.

**Additional details:**

- **Java:** JAR is on classpath or loaded via a plugin SPI (e.g. `ServiceLoader` or custom registry). One class per regulation implements `RegulationEvaluator`; engine caches evaluator instance per (regulationId, version). Context object carries tenant, employee, period, case values, lookups—evaluator is stateless relative to engine.
- **TypeScript:** JS bundle is a file or URL; engine uses dynamic `import(regulationModuleUrl)` or `require(path)` once per regulation and caches the module. Module exports a single object with `evaluateWageType`, `collectorStart`, etc. Build pipeline: TS source → tsc/esbuild → JS; no runtime TS.
- **Versioning:** DB stores e.g. `regulationPackageId=fr-regulation`, `version=1.2.3`. Engine resolves artifact from classpath `fr-regulation-1.2.3.jar` or from a registry (URL/artifact repo). Rollback = point regulation to previous version and redeploy or reconfigure.
- **Failure modes:** Missing JAR or module → fail at regulation load or first use; exception inside evaluator → fail that wage type/step; version mismatch (engine expects interface v2, JAR is v1) → fail at load with clear error.

**Storage / DB:**

- **Regulation**: id, name, **regulationPackageId** (e.g. `fr-regulation`), **version** (e.g. `1.2.3`). No Script table for code.
- **WageType** / **Collector**: metadata only (name, number, collector links, value type). No ValueExpression, no Binary.

**APIs / Contracts:**

- **RegulationEvaluator** (Java): `BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context)`, `void collectorStart(String collectorName, EvaluationContext context)`, etc.
- **Regulation module** (TS): exports `evaluateWageType(wageTypeNumber, context)`, `collectorStart(collectorName, context)`. Engine loads via `import()` or `require()` by regulation id/version.
- Regulation API: CRUD regulations with packageId/version; wage types/collectors as metadata only.

**Infra / Ops:**

- Artifacts deployed with engine (classpath) or to a registry/URL. Engine loads once per regulation (or caches). No compiler or script cache in process.

**Pros:**

- **No code in DB:** No script source or binary stored; eliminates “execute arbitrary code from DB” risk and simplifies DB schema and backups.
- **Clear security boundary:** Engine only loads and calls known interfaces; regulation code is built and tested in CI like any application code.
- **Versioning and audit:** Regulation = artifact version (e.g. `fr-regulation:1.2.3`); reproducible builds, full change history in VCS and artifact repo; easy to answer “what logic ran for payrun X?”.
- **Full language and tooling:** Java/TypeScript with types, unit tests, IDE, refactoring; no custom script debugger needed.
- **Simpler engine:** No ScriptCompiler, no script cache, no ClassLoader/isolate lifecycle; engine is “orchestration + plugin loader”.
- **Predictable performance:** No compile at payrun; load artifact once per regulation; execution is plain method calls.

**Cons:**

- **Deploy for every change:** New country or any rule change requires new artifact version, build, and deploy (or at least drop-in of new JAR/JS and config update).
- **Less “instant” customisation:** No “edit script in UI and run”; changes go through build pipeline and release process.
- **Engine must support plugin loading:** Classpath or plugin registry (JAR) or dynamic import (JS); version resolution and error handling when artifact is missing or incompatible.
- **Multi-tenant custom code:** If each tenant needs different logic, either one JAR per tenant (many artifacts) or branching inside a shared JAR (complexity and versioning).

**When to use:** Regulated environments, auditability, rule changes via release process.

---

### Option 3: External Regulation Service

**Description:** The engine does **not** run regulation logic. It calls an **external API** (HTTP/gRPC) with context (tenant, regulation, employee, period, case values, wage type id); the **regulation service** evaluates and returns the result (e.g. wage type value). The service can be implemented in any language.

**Flow:**

1. **Storage:** **Regulation**: id, name, **serviceEndpoint** (URL or Lambda ARN). WageType/Collector: metadata only.
2. **Payrun:** For each wage type (or batched per employee), engine calls **POST /evaluate/wage-type** (or gRPC equivalent) with body: `{ tenantId, regulationId, employeeId, periodStart, periodEnd, wageTypeNumber, caseValues, lookups }`. Service returns `{ value: 1234.56 }`. Engine persists WageTypeResult and continues. Same for collector start/end and lifecycle hooks if needed.

**Additional details:**

- **Batching:** To reduce round-trips, engine can send one request per employee with all wage types and collector lifecycle calls (e.g. `POST /evaluate/employee` with `wageTypeNumbers[]`, `collectorCalls[]`); service returns a map of results. Trade-off: smaller requests vs fewer network calls.
- **Implementation of the service:** Service can use Option 1 (scripting), Option 2 (JAR/bundle), or Option 4 (DSL) internally; engine is agnostic. Enables polyglot (e.g. regulation service in Python or Go) and independent scaling (e.g. regulation service on GPU or separate fleet).
- **Versioning:** Request schema and service behaviour are versioned (e.g. `Accept: application/vnd.payroll.regulation.v2+json` or URL path `/v2/evaluate/wage-type`). Engine stores `serviceEndpoint` and optionally `serviceVersion`; backward compatibility and rollback require coordination.
- **Failure modes:** Network timeout or 5xx → retry then fail payrun or mark wage type as error; 4xx (e.g. invalid request) → fail fast; service bug → trace by request id and service logs; dependency on service availability and latency.

**Storage / DB:**

- **Regulation**: **serviceEndpoint**, optional **serviceVersion** or **apiKey**. No Script rows.
- **WageType** / **Collector**: metadata only.

**APIs / Contracts:**

- **External:** Regulation service exposes e.g. `POST /evaluate/wage-type`, `POST /evaluate/collector-start`, etc. Request/response schema versioned. Auth (API key, OAuth) and tenant isolation.
- **Engine:** HTTP/gRPC client; retries, circuit breaker, timeouts. No script execution inside engine.

**Infra / Ops:**

- Two (or more) services: engine + regulation service(s). Deploy, monitor, secure both. Latency and availability of the service affect payrun duration and reliability.

**Pros:**

- **No scripting in engine:** Engine is pure orchestration and persistence; no compiler, no script cache, no code-from-DB; smallest engine attack surface.
- **Polyglot:** Regulation service can be implemented in any language (Java, TypeScript, Python, Go); different teams can own different regulations.
- **Independent scaling and evolution:** Scale regulation service separately (e.g. burst for batch payruns); upgrade regulation logic without touching engine deploy.
- **Clear boundary:** Contract is HTTP/gRPC; easy to mock for tests and to swap implementations (e.g. stub vs real regulation service).
- **Centralised regulation logic:** All regulation for a tenant or region can live in one service; simplifies compliance and audit of “where rules run”.

**Cons:**

- **Latency:** Every wage type (or batch) requires a network call; payrun duration is bounded by regulation service latency and throughput; need batching and timeouts to avoid long payruns.
- **Availability:** Payrun depends on regulation service being up; requires SLA, health checks, and possibly fallback or queue-and-retry.
- **Versioning and auth:** API versioning, backward compatibility, and tenant-scoped auth (API key, OAuth) must be designed and maintained; key rotation and multi-tenant isolation add ops.
- **Operational complexity:** Two (or more) deploy pipelines, monitoring, logging, and incident response; cross-service tracing (e.g. trace id from engine to service) is essential for debugging.
- **Cross-service debugging:** Failures can be in engine, network, or service; need correlated logs and clear error propagation (e.g. return error code and message in response body).

**When to use:** Regulation owned by another team/product; regulation must scale or evolve independently.

---

### Option 4: Expression / Formula DSL Only

**Description:** No user script. Wage types and collectors are defined by **expressions** (formula string or JSON expression tree). The engine has an **expression evaluator**; at payrun it **interprets** the expression with context (case values, lookups, collector results, period, employee).

**Flow:**

1. **Storage:** **WageType**: **ValueExpression** = e.g. `"CaseValue('BaseSalary') * 0.2"` or JSON AST. **Collector**: optional StartExpression, EndExpression (or empty). Lookups and cases as today. No Script table for code.
2. **Payrun:** For each wage type, `expressionEvaluator.evaluate(wageType.valueExpression, context)` → decimal. Collector start/end: evaluate optional expressions or no-op. No compile; no loading of user code.

**Storage / DB:**

- **WageType**: **ValueExpression** (NVARCHAR or JSON). CollectorLinks, Attributes. No Binary, no Script.
- **Collector**: optional StartExpression, ApplyExpression, EndExpression. No script source.
- **Lookups**, **Cases**: unchanged. No Script table (or only for metadata).

**APIs / Contracts:**

- **Expression evaluator:** Context exposes `getCaseValue(name)`, `getCollector(name)`, `getLookup(name, key)`, period, employee. Parser + AST or small VM; **no** `eval(arbitraryCode)`.
- **Java:** e.g. Aviator, JEL, Spring Expression, or custom parser. **TypeScript:** e.g. expr-eval (whitelist), or custom AST.

**Infra / Ops:**

- Single engine; no compiler, no isolate. Simple deploy. Expression language must be designed and maintained.

**Additional details:**

- **Grammar and whitelist:** Expression language is a closed set: literals, arithmetic, `CaseValue(name)`, `Collector(name)`, `Lookup(name, key)`, `Min`, `Max`, `Round`, period/employee fields, etc. **No** general-purpose loops, user-defined functions, or file/system access. Parser produces AST; evaluator walks AST with context—no `eval(arbitraryCode)`.
- **Java:** Aviator, JEL, Spring Expression Language, or a custom grammar (e.g. ANTLR). **TypeScript:** expr-eval (with whitelist of allowed functions), or custom parser (e.g. nearley). Expressions can be stored as string and parsed once per payrun (or parsed at ingestion and stored as JSON AST for speed).
- **Collector lifecycle:** Collector start/end can be “expression only” (e.g. set initial accumulator) or empty (no-op). Complex collector logic may not fit; then either extend DSL with more primitives or treat as limitation and use Option 1/2 for those regulations.
- **Failure modes:** Parse error (invalid expression) → fail at ingestion or first use; runtime error (e.g. missing case value, division by zero) → fail that wage type with clear message; infinite recursion or huge expression → guard with timeout or step limit.

**Pros:**

- **No code execution:** Only a fixed set of operators and functions; no arbitrary code from DB; easy to explain to security and compliance; no sandbox or isolate needed.
- **Auditable data:** Rules are data (expression strings or JSON); versioned in DB; diff and review like config; “what formula ran?” is explicit in stored expression.
- **Simple engine:** Parser + evaluator only; no ClassLoader, no script cache, no compiler; fast to implement and maintain.
- **Fast to evaluate:** Interpret AST or bytecode from a small VM; predictable cost per expression; no compile step at payrun.
- **Testable:** Expression evaluator can be unit-tested with mock context; regression tests are expression + context → expected value.

**Cons:**

- **Limited expressiveness:** No loops, no custom functions (unless added as DSL primitives); complex rules can become long or clumsy expressions, or may require many primitives.
- **DSL design and maintenance:** Grammar, documentation, and migration when extending (e.g. new function `ProRata(days, amount)`); risk of “expression language creep” toward a full script language.
- **Escape hatch:** Some regulations may need logic that does not fit the DSL; then either “no support” or hybrid (Option 5) with script/JAR for those cases.
- **Debugging expressions:** Less familiar than full code; may need expression-specific debug view (e.g. show evaluated sub-expressions) and error messages that point to the failing part of the expression.

**When to use:** Simple to medium complexity rules; strong compliance/audit; rules mostly formulas + lookups.

---

### Option 5: Hybrid (Expression + Precompiled or Service)

**Description:** **Simple** wage types: **expression** only (e.g. `CaseValue('BaseSalary') * 0.2`). **Complex** regulations: **precompiled** JAR/JS or **external service**. Engine branches: if WageType has ValueExpression, evaluate; else if Regulation has RegulationPackageId or ServiceEndpoint, call library or service.

**Flow:**

1. **Storage:** **WageType**: optional **ValueExpression**. **Regulation**: optional **regulationPackageId** or **serviceEndpoint**. Same tables as Options 2, 3, 4 as needed.
2. **Payrun:** For each wage type: if ValueExpression present → expression path (Option 4). Else resolve regulation → if packageId present → precompiled path (Option 2); if endpoint present → external service path (Option 3).

**Storage / DB:**

- Mixed: WageType may have ValueExpression and/or regulation may have packageId or endpoint. No Script table for code for precompiled/service path; expressions stored as data for expression path.

**APIs / Contracts:**

- Engine implements both (or all three) paths: ExpressionEvaluator, RegulationEvaluator (JAR/JS), RegulationServiceClient (HTTP/gRPC). Single PayrunProcessor with branching.

**Infra / Ops:**

- Depends on which paths are used: expression-only (simple); precompiled (artifact deploy); service (two services). Multiple code paths to maintain and test.

**Additional details:**

- **Branching logic:** PayrunProcessor (or equivalent) branches per wage type: (1) if WageType has non-empty ValueExpression → ExpressionEvaluator.evaluate(...); (2) else get Regulation for wage type → if regulation has regulationPackageId → load RegulationEvaluator and call evaluateWageType(...); (3) else if regulation has serviceEndpoint → HTTP/gRPC call to regulation service. Priority can be defined (e.g. expression overrides package if both present) and documented.
- **Per-regulation vs per-wage-type:** Typically “expression vs precompiled vs service” is decided per **regulation** (e.g. France = precompiled, generic = expression), and all wage types under that regulation use the same path. Alternatively, a few wage types could override (e.g. one wage type has ValueExpression, rest use regulation package); schema must support that without ambiguity.
- **Consistency and testing:** Same “wage type value” concept is produced by three different mechanisms; tests must cover all paths and ensure result shape (e.g. WageTypeResult) and error handling are consistent (e.g. same retry/rollback semantics). Documentation should spell out which path applies when.
- **Failure modes:** Per-path failures as in Options 2, 3, 4; plus “no path matched” if a wage type has no ValueExpression and its regulation has neither packageId nor endpoint → fail with clear configuration error.

**Pros:**

- **Right tool per case:** Simple rules (e.g. `BaseSalary * 0.2`) stay as expressions (auditable, no deploy); complex regulations (e.g. France social security) use JAR or service without forcing everything into one model.
- **Incremental adoption:** Start with expression-only for new regulations; add precompiled or service when complexity demands it, without rewriting simple wage types.
- **Escape hatch for complex rules:** When expression DSL is insufficient, avoid “expression language creep”; instead delegate to a full implementation (JAR or service) for that regulation.
- **Migration friendliness:** Migrating from .NET: map simple wage types to expressions first; reimplement complex regulations as JAR or service in phases.
- **Flexibility:** Different countries or products can use different paths (e.g. product A = expression only, product B = expression + France JAR).

**Cons:**

- **Two or three code paths:** Engine must implement and maintain expression evaluator, plugin loader (JAR/JS), and optionally HTTP/gRPC client; more branches, more tests, more docs.
- **Consistency:** Same concept (e.g. “evaluate wage type”) implemented differently; behaviour and error handling must be aligned so that callers (UI, reports) do not see path-dependent quirks.
- **Operational mix:** If both precompiled and external service are used, ops must handle artifact deploy and service SLA; troubleshooting requires knowing which path was used for a given payrun.
- **Schema and precedence:** Need clear rules for “when expression vs when package vs when service” and for overrides (e.g. wage-type-level expression overriding regulation package); can become complex if overused.

**When to use:** Mixed portfolio (simple + complex regulations); migration from current engine (some behaviour as expressions, some as precompiled/service).

---

## Comparison of options

| Criterion | Option 1: Runtime scripting | Option 2: Precompiled (JAR/JS) | Option 3: External service | Option 4: Expression DSL | Option 5: Hybrid |
|-----------|-----------------------------|---------------------------------|----------------------------|--------------------------|------------------|
| **Rule change without deploy** | Yes (ingest script) | No (new artifact + deploy) | No (service deploy) | Yes (ingest expression) | Depends on path (expression yes; JAR/service no) |
| **Code in DB** | Yes (script source; optional Binary) | No | No | No (expressions only) | No for JAR/service; expressions as data for expression path |
| **Security** | Highest risk (execute from DB); needs sandbox | Low (load known artifacts only) | Low (engine does not run rules) | Lowest (closed DSL, no arbitrary code) | Per path: expression lowest; JAR low; service low |
| **Engine complexity** | High (compiler, cache, classloader/isolate) | Medium (plugin loader, version resolution) | Low (orchestration + HTTP/gRPC client) | Low (parser + evaluator) | High (multiple paths, branching, consistency) |
| **Ops / infra** | Single service; compiler memory/CPU; cache policy | Single service; artifact deploy/registry | Two+ services; SLA, latency, auth | Single service; DSL maintenance | Mixed: one service but multiple code paths and possibly artifact + service |
| **Expressiveness** | Full language (Java/JS) | Full language (Java/TS) | Full (service implements any logic) | Limited (formulas + whitelist functions) | Full where JAR/service used; limited where expression only |
| **Auditability** | Harder (code in DB; script versioning) | Strong (artifact version in VCS/registry) | Strong (service version, API contract) | Strong (expressions as data; diffable) | Per path; expression path strong; JAR/service as in 2/3 |
| **Performance** | Variable (compile/cache; first-use cost) | Predictable (load once; method calls) | Network-bound (latency, batching) | Predictable (interpret AST) | Depends on path used per payrun |
| **Best for** | “Change rules without deploy”; parity with .NET | Regulated env; release-based rule changes | Regulation owned by other team; independent scale | Simple–medium rules; strong compliance | Mixed portfolio; migration; escape hatch for complex |

---

## Suggested diagrams

The following diagram ideas support quicker understanding and stakeholder alignment. Mermaid source for the first five is in **`docs/diagram/`**:

| # | File | Description |
|---|------|-------------|
| 1 | `diagram/01_five_option_flow_comparison.md` | Five-option flow comparison |
| 2 | `diagram/02_option1_ingestion_compile_payrun.md` | Option 1: Ingestion → Compile → Payrun (sequence) |
| 3 | `diagram/03_option2_build_deploy_run.md` | Option 2: Build → Deploy → Run (pipeline) |
| 4 | `diagram/04_option3_engine_regulation_service.md` | Option 3: Engine ↔ Regulation service (component + sequence) |
| 5 | `diagram/05_option4_expression_ast_result.md` | Option 4: Expression → AST → result |

Add further diagrams as Mermaid (in-repo), draw.io, or exported images where indicated below.

### 1. **Five-option flow comparison (one diagram)**

**Purpose:** Show at a glance where rule logic lives and how it is executed in each option.

**Content:** Five horizontal “flows” (one per option), each with:
- **Source of rules:** DB (script/expression), Artifact store (JAR/JS), or External service.
- **Where execution happens:** Inside engine (compile+run, load+call, interpret) vs in regulation service.
- **Payrun step:** Engine → [obtain value] → WageTypeResult.

**Placement:** After **Solution Highlights** (before Solution Details), or immediately after the **Comparison of options** table.

**Sketch:**  
`[Option 1] API/Client → DB(script) → Engine(compile→cache→invoke) → result`  
`[Option 2] CI → Artifact(JAR/JS) ← Engine(load→invoke) ; DB(metadata only)`  
`[Option 3] Engine → HTTP/gRPC → Regulation Service → response → result`  
`[Option 4] DB(expression) → Engine(parse→evaluate) → result`  
`[Option 5] Branch: expression path | JAR path | service path → result`

---

### 2. **Option 1: Ingestion → Compile → Payrun (sequence)**

**Purpose:** Clarify the three phases and where DB, compiler, and cache sit.

**Content:** Sequence diagram: **Client/API** → persist script + wage types to **DB**; **Engine (build)** reads script + expression → **Compiler** (Janino/isolate) → **Cache** (Binary, ScriptHash); **Payrun** loads from cache/DB → inject **EvaluationContext** → invoke → **WageTypeResult**.

**Placement:** In **Option 1** section, right after the Flow list.

---

### 3. **Option 2: Build → Deploy → Run (pipeline)**

**Purpose:** Show that code never touches the engine DB; it flows from repo to artifact store to engine.

**Content:** Pipeline: **VCS** (France/India/Swiss repo) → **CI build** → **Artifact registry** (JAR/JS by version). **Engine** reads **DB** (Regulation.regulationPackageId, version) → **Plugin loader** resolves and loads artifact → **RegulationEvaluator** invoked at payrun. No Script table.

**Placement:** In **Option 2** section, after the Flow list.

---

### 4. **Option 3: Engine ↔ Regulation service (component + sequence)**

**Purpose:** Make the process boundary and request/response explicit.

**Content:**
- **Component diagram:** Box “Payroll Engine” (orchestration, DB, no rule execution); box “Regulation Service” (implements rules); arrow “HTTP/gRPC” with label “evaluate wage type / collector / …”.
- **Optional sequence:** Engine sends request (tenant, regulation, employee, period, wageTypeNumber, caseValues) → Service evaluates → response (value or error). Optional batch request/response.

**Placement:** In **Option 3** section, after the Flow list.

---

### 5. **Option 4: Expression as data → AST → result**

**Purpose:** Emphasise “no code execution”: only parsed expression + context.

**Content:** Flow: **DB** (WageType.ValueExpression as string or JSON) → **Parser** → **AST** → **Evaluator** with **Context** (case values, collector, lookups, period, employee) → **Result**. Explicit “no eval(arbitraryCode)” callout.

**Placement:** In **Option 4** section, after the Flow list.

---

### 6. **Option 5: Hybrid decision flow**

**Purpose:** Show how the engine chooses expression vs precompiled vs service.

**Content:** Flowchart: Start → “WageType has ValueExpression?” → Yes → **Expression path** (Option 4) → Result. No → “Regulation has regulationPackageId?” → Yes → **Precompiled path** (Option 2) → Result. No → “Regulation has serviceEndpoint?” → Yes → **External service path** (Option 3) → Result. No → **Error** (no path matched).

**Placement:** In **Option 5** section, after the Flow list (or in Additional details).

---

### 7. **Component/context by option**

**Purpose:** Compare what lives inside the engine vs outside (DB, artifact store, external service).

**Content:** One diagram with four columns or four small diagrams: **Option 1** — Engine (compiler, cache, executor), DB (script + metadata). **Option 2** — Engine (plugin loader, executor), DB (metadata), Artifact store (JAR/JS). **Option 3** — Engine (client only), DB (metadata), Regulation Service. **Option 4** — Engine (parser, evaluator), DB (expressions + metadata). **Option 5** — Combination: Engine (all of the above as branches), DB + optional Artifact store + optional Service.

**Placement:** After **Comparison of options** table, or in **Solution Highlights**.

---

### 8. **Trade-off summary (2×2 or radar)**

**Purpose:** Visualise key trade-offs (e.g. flexibility vs security, or simplicity vs expressiveness) so stakeholders can see where each option sits.

**Content:**  
- **2×2:** Axes e.g. “Change rules without deploy” (yes/no) vs “Code in DB” (yes/no), or “Engine complexity” (low/high) vs “Expressiveness” (limited/full). Plot Options 1–5.  
- **Radar (optional):** Dimensions such as Security, Auditability, Performance, Ops simplicity, Flexibility; one polygon per option.

**Placement:** After **Comparison of options** table, or alongside the recommendation in **Solution Highlights**.

---

## Observability

- **Infra metrics:** Payrun duration, queue depth, worker utilisation. Per-option: **Runtime scripting** — compile duration, cache hit rate, assembly load errors. **Precompiled** — plugin load time, missing package errors. **External service** — call latency, error rate, circuit breaker state. **Expression** — evaluation time, parse errors. **Hybrid** — same as above per path.
- **Functional metrics:** Wage type results per payrun, collector results, failed payruns (with reason: script error, service timeout, expression error). Logs: regulation id, wage type number, option path (script/precompiled/service/expression), and on failure stack trace or service response.

---

## Compatibility, Deprecation, and Migration Plan

- **Compatibility:** The chosen option defines the **contract** for regulation packages (e.g. RegulationEvaluator interface, or expression schema, or regulation service API). Existing .NET regulations (e.g. France Rules.cs) do not run unchanged; they must be **reimplemented** as Java/TS artifact, or as expressions, or as a regulation service, depending on the selected option.
- **Deprecation:** N/A for new engine; this RFC selects the model for the new engine, not a change to the .NET engine.
- **Migration:** If migrating from .NET to new engine: (1) **Runtime scripting** — translate C# rules to Java/JS and ingest; (2) **Precompiled** — implement France/India/Swiss as JAR/JS and deploy; (3) **External service** — implement regulation service (e.g. wrap .NET or reimplement) and point engine to endpoint; (4) **Expression** — derive or hand-write expressions from current rules where possible; (5) **Hybrid** — combine expression for simple wage types and precompiled/service for complex.

---

## Future Iterations / Enhancements

- **Runtime scripting:** Add optional “script approval” workflow; improve cache eviction and diagnostics; support multiple script languages (e.g. Groovy in addition to Janino).
- **Precompiled:** Regulation registry (versioned JAR/JS); hot-reload of regulation packages without full engine restart.
- **External service:** Standardise regulation API (OpenAPI/gRPC); federation (multiple regulation services by country).
- **Expression DSL:** Extend DSL (e.g. conditionals, lookups, multi-step); tooling for generating expressions from existing rules.
- **Hybrid:** Unified “regulation behaviour” API so that expression, precompiled, and service are pluggable implementations of the same interface.
