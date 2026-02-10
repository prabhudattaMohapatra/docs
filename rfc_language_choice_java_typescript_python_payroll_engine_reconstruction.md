# RFC: Theoretical Comparison (Java vs TypeScript vs Python) to Choose Language for Payroll Engine POC

**Status:** in progress / ready for review / approved

**Author:**  

**Open Date:**  

**Closing Date for Comments:**  

---

## Motivation

Before building a full payroll engine reconstruction, we will run a **proof-of-concept (POC)** in one (or at most two) language(s). We need to choose **which language to use for that POC first**. This RFC does **not** decide the final implementation language for the full reconstruction—that decision will follow after POC results. Here we perform a **theoretical comparison** among **Java**, **TypeScript**, and **Python** and recommend which language to use for the first POC, so the team can agree on POC scope and success criteria without committing to the long-term reconstruction language yet.

---

## Context

- The **existing engine** is .NET (C#). It compiles regulation C# scripts at **import** (or on first use), stores binaries, and at **payrun** only loads and invokes them. There is no compile step at payrun.
- **Reconstruction scope** (from `PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md` and the codebase) includes: domain model (Tenant, Regulation, Payroll, WageType, Collector, Case, CaseValue, results); case value resolution (four scopes, effective dating); regulation derivation; **rules execution** (lifecycle hooks—WageTypeValue, CollectorStart/End, etc.—either as runtime scripting, precompiled artifacts, or external service); persistence; REST API; async payrun worker; CLI; decimal correctness; concurrency; optional reporting.
- The **rules execution** model is the most language-sensitive part; the rest is standard backend + persistence + API + worker. This RFC is **theoretical only** (no POC results assumed); it is based on inherent language/runtime and ecosystem fit. The outcome is a recommendation for **which language to use for the first POC**, not a binding choice for the full reconstruction.

**Related:** `PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md`, `PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md`, `rfc_runtime_rules_scripting_options.md`, `JAVA_PAYROLL_ENGINE_FROM_SCRATCH_KNOWLEDGE.md`, `PAYROLL_ENGINE_RECONSTRUCTION_JAVA_VS_TYPESCRIPT_VS_PYTHON.md`.

---

## Problem Statement

**Goal:** Choose **which language to use for the first POC** (one primary language; optionally a second for comparison). The POC will validate feasibility and inform the **later** decision on the implementation language for the full payroll engine reconstruction.

- **Functional (for POC scope):** The POC language must be able to implement a meaningful subset of the build plan (e.g. domain model, case resolution, rules execution path, persistence, one payrun flow) so that we can assess fit.
- **Non-functional (for comparison):** We compare Java, TypeScript, and Python on type safety, decimal/financial math, CPU performance and concurrency, rules execution options (runtime scripting vs precompiled), script sandboxing, and ecosystem—so that the POC is done in a language that is a plausible candidate for the full reconstruction.

The problem is **specific**: which of Java, TypeScript, or Python should we use for the **first POC**, based on theoretical fit, when team preference is neutral—and under what conditions we should run the POC in TypeScript or Python instead. The **final** language for the full engine will be decided after the POC (out of scope for this RFC).

---

## Design

The design is a **comparison framework** and **decision criteria**, not an implementation design. Considerations:

- **Functional:** Rules execution (runtime scripting vs precompiled vs external), decimal handling, typing/domain model, persistence, API, worker, reporting.
- **Non-functional:** CPU performance and concurrency, memory, cold start/deployment size, security (script sandbox), ecosystem, team/hiring.
- **SCORP-style pillars (interpreted for language choice):**
  - **Security:** Safe execution of regulation logic (sandbox or precompiled/external); no arbitrary code execution from stored scripts where avoidable.
  - **Cost / Operational excellence:** Deployment footprint, cold start, operational familiarity; team productivity and hiring cost.
  - **Reliability:** Type safety and decimal correctness reduce defects; mature ecosystems reduce operational risk.
  - **Performance:** Throughput and latency for payrun jobs; ability to run multiple jobs in parallel without undue overhead.

The solution is to adopt the evaluation dimensions and recommendation below to **select the language for the first POC**. The POC itself (scope, success criteria, and follow-up decision on full reconstruction language) will be defined separately.

---

## Solution Highlights

- **Recommendation for first POC (theoretical):** Use the comparison below to pick **one** language for the initial POC. No commitment yet to the full reconstruction language—that will be decided after POC results.
- **Default: run the first POC in Java** when there is no strong organizational bias toward TypeScript or Python. Java has the strongest theoretical fit for rules execution (Janino/Groovy, “compile-at-import, run at payrun”), decimal (`BigDecimal`), typing, and CPU/concurrency, and aligns with payroll/HR backend ecosystem.
- **Run the first POC in TypeScript (Node)** when the team is TS/JS-first, when regulation rules in the POC can be JavaScript (or external), and when small image and fast cold start are in scope.
- **Run the first POC in Python** when the platform is Python-first, when the POC does not require in-process runtime scripting (precompiled or external rules), and when multiprocessing and type-hint discipline are acceptable.
- **Override:** If the team is already committed to trying one language first, that language is a valid POC choice; the comparison below still helps set expectations and POC scope. The full reconstruction language remains a separate decision after the POC.

---

## Solution Details

### Scope of reconstruction (reference for POC relevance)

The POC need not implement all of this; the table describes what the **full** engine would cover, so the theoretical comparison is grounded in real demands. The POC should exercise at least: a slice of domain + rules execution path + decimal handling + one payrun flow.

| Area | Demand |
|------|--------|
| Domain model | Tenant, Regulation, Payroll, Division, Employee, Payrun, PayrunJob; Case, CaseValue (effective-dated, multi-scope); WageType, Collector; Script; LookupSet, LookupValue; results (WageTypeResult, CollectorResult). |
| Case value resolution | Four scopes; effective dating; CaseValueCache + CaseValueProvider used by rules. |
| Regulation derivation | Derived wage types, collectors, scripts, lookups by payroll + regulation date. |
| Rules execution | Lifecycle hooks; runtime scripting **or** precompiled artifacts **or** external regulation service. |
| Persistence | Relational DB; repositories; effective-dated, tenant-scoped queries; transactions. |
| REST API | CRUD; import/export (Exchange); payrun create and async start (enqueue). |
| Background worker | Queue; worker runs processor, persists results. |
| CLI | Import, export, payrun start, results (HTTP client). |
| Decimal | Exact decimal for money; regulation-specific rounding. |
| Concurrency | Multiple payrun jobs in parallel; safe shared caches (e.g. compiled scripts). |

### Evaluation dimensions (summary)

| Dimension | Java | TypeScript (Node) | Python |
|-----------|------|-------------------|--------|
| Runtime rules / scripting | Strong: Janino, Groovy, GraalJS; compile-at-import; same language for engine and rules. | Moderate: JS in isolate or precompiled bundles; no TS at runtime. | Moderate: RestrictedPython, GraalPython, precompiled modules, or external service; no dominant safe in-process story. |
| Decimal / financial math | Strong: `BigDecimal`. | Moderate: decimal.js/big.js; discipline required. | Strong: `decimal.Decimal` in stdlib. |
| Typing & domain model | Strong: compile- and runtime; refactor-safe. | Strong: compile-time; runtime untyped. | Moderate: type hints + mypy/pyright; optional at runtime. |
| Persistence | Strong: JPA, Spring Data, Flyway/Liquibase. | Strong: TypeORM, Prisma, Drizzle. | Strong: SQLAlchemy, Alembic. |
| REST API & worker | Strong: Spring Boot; ExecutorService, SQS. | Strong: Express/Fastify/NestJS; Bull/BullMQ. | Strong: FastAPI; Celery/RQ. |
| Performance (CPU) | Strong: JIT; threads for parallel jobs. | Good: V8; single-threaded; offload to workers. | Weaker: GIL; multiprocessing for parallel jobs. |
| Concurrency | Threads + executors; shared caches straightforward. | Event loop + worker threads/processes. | Multiprocessing or multiple processes; asyncio for I/O. |
| Cold start / deployment | Larger JRE image; GraalVM native smaller. | Small Node image; serverless-friendly. | Medium; serverless cold start slower than Node. |
| Security (script sandbox) | ClassLoader isolation; separate process option. | Isolated-vm or separate process. | RestrictedPython or subprocess; less standard. |
| Ecosystem / hiring | Mature for payroll/enterprise. | Strong for APIs; payroll backend less typical. | Strong for data/APIs; payroll core less typical. |

### Recommendation matrix

| Priority | Prefer | Reason |
|----------|--------|--------|
| Closest replication of current engine (compile-at-import, same language) | Java | Janino/Groovy; single type system; script cache and ClassLoader. |
| Runtime scripting in same language as engine | Java (Java rules) or TypeScript (JS rules) | Java: one language. TS: engine in TS, rules in JS. |
| No runtime scripting (precompiled or external) | Any | All three can orchestrate precompiled artifacts or external service. |
| Decimal correctness with least friction | Java or Python | BigDecimal and Decimal in stdlib. |
| Strong typing and refactor safety | Java or TypeScript | Compile-time (and runtime for Java). |
| Highest CPU throughput in one process | Java | JIT and threads; no GIL. |
| Smallest image and fastest cold start | TypeScript (Node) | Small runtime; serverless-friendly. |
| Team already strong in one language | That language | Build plan implementable in all three. |
| External regulation service (engine thin) | Any | API, queue, worker, persistence, HTTP client. |
| Python-first platform (data/ML/tooling) | Python | Precompiled or external rules; multiprocessing and type-hint discipline. |

### Summary (theoretical fit)

| Criterion | Java | TypeScript | Python |
|-----------|------|------------|--------|
| Runtime scripting (compile/run stored code) | ★★★ | ★★ (JS only) | ★★ |
| Precompiled / external rules | ★★★ | ★★★ | ★★★ |
| Decimal / money | ★★★ | ★★ | ★★★ |
| Typing & domain model | ★★★ | ★★★ | ★★ |
| Persistence | ★★★ | ★★★ | ★★★ |
| REST API & worker | ★★★ | ★★★ | ★★★ |
| CPU performance & concurrency | ★★★ | ★★ | ★★ |
| Cold start / deployment size | ★★ | ★★★ | ★★ |
| Security (script sandbox) | ★★★ | ★★★ | ★★ |
| Ecosystem for payroll/enterprise | ★★★ | ★★ | ★★ |

**Legend:** ★★★ strong fit, ★★ adequate with trade-offs, ★ possible but weaker.

### Recommendation for POC language (from solution highlights)

- **Default for first POC: Java** — Strongest theoretical fit for rules execution, decimal, typing, performance, and ecosystem when team preference is neutral.
- **First POC in TypeScript** — When team is TS/JS-first, POC rules can be JS or external, and small/fast deployment is in scope.
- **First POC in Python** — When platform is Python-first, POC does not require in-process runtime scripting, and multiprocessing + type-hint discipline are acceptable.
- **Override** — Committed team preference to try TypeScript or Python first is a valid reason to run the POC in that language. The **final** language for full reconstruction is decided after the POC, not by this RFC.

### Caveats

- **Theoretical only:** No POC or benchmarks are assumed. This RFC only recommends which language to use for the first POC; it does not decide the full reconstruction language. Validate with a small POC (e.g. one payrun, 100 employees, 50 wage types, one regulation) in the top candidate(s).
- **Rules execution:** If the POC assumes “no runtime scripting” (precompiled or external), the gap between the three languages narrows; any of them can be a reasonable POC choice.
- **Single language for POC:** This RFC assumes one primary language for the first POC (optionally a second for comparison). Mixed-stack or multi-language reconstruction is out of scope here.

---

## Observability

This RFC does not define runtime observability of a future engine. For **the POC**, the following metrics are relevant to validate the chosen language and inform the later decision on full reconstruction:

- **Functional:** POC payrun duration (end-to-end); per-employee or per-wage-type latency; correctness (e.g. comparison to .NET engine output for same inputs where applicable).
- **Infra / non-functional:** Memory at peak during POC payrun; cold start (if relevant); CPU utilization; throughput if running multiple payrun jobs in the POC.

POC scope and exact metrics should be agreed when defining the POC (e.g. in a separate POC plan or RFC).

---

## Compatibility, Deprecation, and Migration Plan (if any)

- **Compatibility:** This RFC does not change the current .NET engine or any production behaviour. It only recommends a language for the **first POC**. The current engine remains in use.
- **Deprecation / Migration:** N/A. The decision on full reconstruction language and any future migration will be made **after** the POC, in a separate process.

---

## Future Iterations / Enhancements

- **Define and run the POC:** Once this RFC is approved, define POC scope (e.g. one payrun, subset of employees and wage types, one regulation), success criteria, and metrics. Run the POC in the language recommended above.
- **Optional: second language POC:** If needed, run a second POC in another language (e.g. TypeScript or Python) for comparison before locking the full reconstruction language.
- **Runtime scripting in POC:** If the POC must validate dynamic regulation code, implement a minimal “compile-at-import, run at payrun” path (e.g. Janino in Java, or JS isolate in TypeScript) and assess security and performance.
- **Decide full reconstruction language:** After the POC, use its results (performance, correctness, developer experience) plus the theoretical comparison to decide the implementation language for the full payroll engine reconstruction. That decision is out of scope for this RFC.
