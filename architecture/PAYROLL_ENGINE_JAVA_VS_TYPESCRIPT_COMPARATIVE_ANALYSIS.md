# Payroll Engine: Java vs TypeScript Comparative Analysis

This document supports choosing between **Java** and **TypeScript** for building a new payroll engine inspired by the existing .NET engine. It provides evaluation dimensions, pain points, and POC recommendations so you can run a proof-of-concept and decide.

Related: `REUSE_REDESIGN_REJECT_STRATEGY.md` (what to carry over vs change). Also `TYPESCRIPT_MIGRATION_PROBLEMS.md`, `JAVA_MIGRATION_BENEFITS.md`, `JAVA_MIGRATION_NEGATIVES.md`.

---

## 1. Evaluation Dimensions (for POC)

Use these dimensions to score each language and design POC experiments.

| Dimension | What to measure | POC relevance |
|-----------|-----------------|---------------|
| **Runtime rules / scripting** | Can regulation logic (wage type value, collector start/end, payrun lifecycle) run as dynamic code? How fast and how safe? | Critical — current engine compiles C# at import, runs binaries at payrun (no compile at payrun). |
| **Performance** | Payrun duration for N employees × M wage types; memory per job; cold vs warm. | Critical for batch payruns. |
| **Decimal / financial math** | Correct rounding, no float errors for money. | Critical. |
| **Typing & domain model** | Strong typing for Tenant, Payroll, WageType, Collector, Case, Result; refactor safety. | High. |
| **Persistence** | ORM/migrations, transactions, connection pooling; fit with existing DB or new. | High. |
| **Reporting** | PDF/Excel generation for payslips and reports. | Medium–high. |
| **Concurrency** | Multiple payrun jobs in parallel; thread/worker safety. | High. |
| **Ecosystem** | Libraries for calendar, expressions, validation, API, logging. | Medium. |
| **Deployment** | Containers, serverless, cold start. | Medium. |
| **Team & hiring** | Skills available; time to productivity. | Strategic. |
| **Security** | Sandboxing of user scripts; no arbitrary code execution. | Critical. |

---

## 2. Runtime Rules / Scripting (Critical)

The current engine compiles **C# regulation scripts** during **import** (when JSONs like WageTypes.json that reference scripts are imported via PayrollImport); the backend compiles with Roslyn and stores binaries on script objects. At **payrun time** only precompiled binaries are loaded and invoked (WageTypeValue, CollectorStart/End, etc.). So there is no compilation at payrun—only load and run. Replicating “compile at import, run at payrun” is the main design point.

### 2.1 Java — pain points and options

| Aspect | Pain point | Evaluation / POC |
|--------|------------|------------------|
| **Runtime compilation** | No built-in “compile string to class” in JDK. | **Options**: Janino (Java source → Class), Groovy (script → run), GraalVM (JS/other → run). Janino is closest to Roslyn: compile small Java snippets in-memory. |
| **Script language** | If you use Java for rules: same language as engine (good for types). If Groovy: two languages, looser typing. | POC: Implement one wage type + one collector with **Janino** (embed user Java in template class) and measure compile + run time for 100 wage type evaluations. |
| **Sandboxing** | Running user code in-process is risky. | Use a dedicated classloader, no `System.exit`, no reflection to engine internals; or run rules in a separate process/worker. POC: define sandbox policy and test escape attempts. |
| **Cold start** | First compilation per regulation/script set can be 50–200 ms. | POC: Measure first-call latency; consider caching compiled classes by script hash (like current AssemblyCache). |

**Java summary**: Compilation at import (or on first use) is **feasible and proven** (Janino, Groovy). Main POC: “Regulation = metadata + Java (or Groovy) snippets” with compile-at-import (or compile-on-first-use) and cache; at payrun only load binary and run.

### 2.2 TypeScript — pain points and options

| Aspect | Pain point | Evaluation / POC |
|--------|------------|------------------|
| **No runtime TS compiler** | TypeScript is compile-time only. You cannot “compile a string of TS” in the process like Roslyn/Janino. | **Options**: (1) Run **JavaScript** in-process (V8 isolate, vm2, quickjs) — then rules are JS, not TS. (2) Precompile TS to JS in a build step and load bundles — not “dynamic” in the same sense. (3) External rule service (API that evaluates; engine in any language). |
| **eval / Function** | `eval()` or `new Function()` in Node: security risk, no type safety, hard to give rules a typed API (Employee, Case, Collector). | POC: If you choose JS rules, use **isolated-vm** or **vm2** (or Node’s experimental vm with context) and expose a minimal, typed facade (e.g. `getCaseValue(name)`, `getCollector(name)`). Measure overhead and safety. |
| **Typed API for rules** | In .NET, rules get strongly typed `Employee`, `Case`, etc. In JS, you typically pass a plain object; typings are documentation only at runtime. | POC: Design a rule API (e.g. one function `wageTypeValue(wageTypeNumber, context)`) and implement it in JS with JSDoc or a thin TS layer that compiles to JS; measure developer experience and refactor safety. |
| **Performance** | V8 is fast, but crossing JS ↔ native for every wage type call can add overhead. | POC: 10k wage type evaluations in a tight loop (JS function calling back into engine for case values / collectors); compare to Java/Janino. |

**TypeScript summary**: You are effectively choosing between **“engine in TypeScript, rules in JavaScript”** (dynamic but no TS in runtime) or **“precompiled rule bundles / external service”** (no true runtime compilation in-process). POC should prove: (a) acceptable latency and security for JS rules, or (b) that a “no dynamic scripts” or “external evaluator” design is acceptable.

---

## 3. Performance

| Criterion | Java | TypeScript (Node) |
|-----------|------|-------------------|
| **CPU-bound calculation** | JVM JIT, excellent for long-running loops (wage types × employees). | Node single-threaded; CPU-bound work blocks event loop unless offloaded to workers or native. |
| **Memory** | Higher baseline (heap, metaspace). Tuning (Xmx, GC) required. | Lower baseline; V8 heap. Less tuning. |
| **Cold start** | JVM warmup (seconds) unless using GraalVM native image (faster start, more build complexity). | Node starts in milliseconds. |
| **Concurrency** | Threads + ExecutorService; good for parallel payrun jobs. | Worker threads or separate processes; or “one job per container” in serverless. |

**POC**: Run the **same** payrun algorithm (e.g. 100 employees, 50 wage types, 20 collectors, no I/O) in Java and in TypeScript (Node). Measure: time to complete, P95 latency per employee, memory at peak. If TypeScript, test both single-threaded and with worker threads.

---

## 4. Decimal / Financial Math

| Language | Option | Pain point |
|----------|--------|------------|
| **Java** | `BigDecimal` | Standard, immutable, correct. Slightly verbose. Use everywhere for money. |
| **TypeScript** | No built-in decimal. | Use **decimal.js** or **big.js**. Risk: developers use `number` by mistake. Enforce “money is Decimal” in types and APIs. |

**POC**: Implement one gross-to-net calculation (e.g. 10 wage types, 5 collectors) in both; compare results to .NET engine output for the same inputs (same rounding rules).

---

## 5. Typing & Domain Model

| Aspect | Java | TypeScript |
|--------|------|------------|
| **Strength** | Compile-time and runtime types; refactoring is safe. | Compile-time only; runtime is untyped. Good for API boundaries and DTOs. |
| **Domain model** | Classes (Tenant, Payroll, WageType, Collector, Case, Result) map naturally. | Interfaces + classes or plain objects. Same concepts, different style. |
| **Null safety** | Optional / nullability in modern Java. | `strictNullChecks`; optional chaining. |

Both can implement the same **domain concepts** (tenant, payroll, payrun, wage type, collector, case, result). POC: Model a minimal “PayrunJob + WageTypeResult + CollectorResult” in both and ensure serialization/deserialization (e.g. for API and queue) is clear and consistent.

---

## 6. Persistence

| Aspect | Java | TypeScript |
|--------|------|------------|
| **ORM** | JPA/Hibernate, Spring Data; mature, many dialects. | TypeORM, Prisma, Drizzle; good but younger. |
| **Migrations** | Flyway, Liquibase. | Same or framework-specific. |
| **Transactions** | Well understood; connection pooling (HikariCP) standard. | Same with pg/mysql clients; ensure transaction boundaries around payrun. |

**POC**: Implement “save payrun job + results” and “load payroll + derived wage types/collectors” in both; compare code size, clarity, and transaction behavior.

---

## 7. Reporting (PDF / Excel)

| Aspect | Java | TypeScript |
|--------|------|------------|
| **PDF** | iText, Apache PDFBox, JasperReports. | pdfkit, puppeteer (HTML→PDF). |
| **Excel** | Apache POI. | exceljs, xlsx. |

Current .NET engine uses FastReport etc.; both Java and TypeScript have viable alternatives. POC only if reports are in scope for v1 (e.g. one payslip PDF and one summary Excel).

---

## 8. Concurrency

| Aspect | Java | TypeScript |
|--------|------|------------|
| **Model** | Threads; shared state with care. | Single-threaded event loop; workers or separate processes for CPU work. |
| **Payrun jobs** | One thread per job or bounded executor; natural. | Queue + workers (or one process per job in Kubernetes). |
| **Shared caches** | In-memory cache (e.g. compiled scripts) with concurrency. | Same idea; ensure no shared mutable state across worker boundaries. |

**POC**: Run 5 payrun jobs in parallel (same tenant, different payruns or employees); measure throughput and correctness (no cross-job leakage).

---

## 9. Deployment & Ops

| Aspect | Java | TypeScript |
|--------|------|------------|
| **Containers** | Larger image (JRE); GraalVM native image smaller, faster start. | Smaller Node image; fast start. |
| **Serverless** | Possible (Lambda with Java); cold start heavy. | Lambda/Node or Deno; cold start light. |
| **Observability** | Logging, metrics, tracing (e.g. Micrometer, OpenTelemetry) mature. | Same with pino, OpenTelemetry. |

---

## 10. Team & Hiring

| Aspect | Java | TypeScript |
|--------|------|------------|
| **Typical use** | Backend, enterprise, financial systems. | Full-stack, APIs, Node backends. |
| **Payroll context** | Many payroll/HR products are Java or .NET. | Less common for heavy backend payroll engines. |

Consider: existing team skills, hiring pool, and long-term ownership.

---

## 11. Security (Script Sandbox)

| Aspect | Java | TypeScript |
|--------|------|------------|
| **In-process** | Classloader isolation; deny certain packages (e.g. `java.lang.Runtime`). Policy must be strict and tested. | Isolated VM (e.g. isolated-vm) or separate process. `eval` in main process is unsafe. |
| **Out-of-process** | Rules run in separate JVM or service; engine calls via API. | Rules run in separate Node process or external service; engine calls via API. |

**POC**: Define “what can a rule do?” (read case values, read/write collector results, no file/network). Implement that in Java (Janino + policy) and in TypeScript (JS in isolate + facade); try to break out.

---

## 12. POC Recommendations to “Prove” One Stack

### Minimum POC scope (2–4 weeks)

1. **Domain**
   - Tenant, Payroll, Payrun, PayrunJob, Employee.
   - WageType, Collector (metadata only).
   - Case values (global, company, employee).
   - PayrollResultSet: WageTypeResults + CollectorResults.

2. **Calculation**
   - One **payrun lifecycle**: PayrunStart → for each employee: EmployeeStart → for each wage type: WageTypeValue → CollectorApply → CollectorEnd → EmployeeEnd → PayrunEnd.
   - At least **2 wage types** and **2 collectors** with **dynamic logic** (script or expression).
   - Use **decimal** for all money.

3. **Scripting**
   - **Java**: Janino; one Java class per “regulation script” with method `wageTypeValue(wageTypeNumber, context)` returning `BigDecimal`. Load and invoke via reflection. Optional: CollectorStart/End.
   - **TypeScript**: Either (a) JS function in isolated-vm with a context object, or (b) no dynamic script — hardcoded rules and compare “expression only” path. If (a), implement same 2 wage types in JS.

4. **Persistence**
   - Save/load PayrunJob and PayrollResultSet (e.g. PostgreSQL or existing DB). No need for full schema.

5. **API**
   - One endpoint: “Start payrun” (tenant, payrun, period, optional employee list). Sync or async (queue + worker). Return job id and status.

### Success criteria for “better”

- **Functionality**: Same inputs → same results (or documented differences) vs .NET engine for the 2 wage types + 2 collectors.
- **Performance**: Time for 100 employees × 2 wage types acceptable (e.g. &lt; 30 s); no OOM.
- **Scripting**: Safe, understandable, and maintainable (clear API for rules, no escape).
- **Code**: Clear separation of engine core vs regulation logic; easy to add a third wage type.

### Scoring matrix (example)

| Dimension | Weight | Java score (1–5) | TS score (1–5) | Notes |
|-----------|--------|------------------|----------------|-------|
| Runtime scripting | 25% | 5 (Janino) | 3 (JS isolate or no dynamic) | |
| Performance | 20% | 5 | 4 | Measure in POC |
| Decimal / correctness | 15% | 5 | 4 (library discipline) | |
| Typing / domain | 10% | 5 | 4 | |
| Persistence | 10% | 5 | 4 | |
| Security (sandbox) | 10% | 4 | 4 | |
| Team / hiring | 5% | 4 | 3 | Adjust to your context |
| Deployment | 5% | 3 | 5 | |

Use your own weights and scores after the POC.

---

## Suggested Next Steps

1. **Lock POC scope**: 2 wage types, 2 collectors, one payrun lifecycle, one DB table for results, one “start payrun” API.
2. **Implement POC in Java** (e.g. Spring Boot + Janino + PostgreSQL) and **in TypeScript** (Node + isolated-vm or no dynamic scripts + PostgreSQL). Same inputs, compare results to .NET and compare performance/maintainability.
3. **Score** using the matrix above and your own weights.
4. **Decide** reuse list from `REUSE_REDESIGN_REJECT_STRATEGY.md`; adopt “versioned regulation + narrow rule API + async-first” where applicable.
5. **Design v1** of the new engine (APIs, persistence, worker model) and then implement.
