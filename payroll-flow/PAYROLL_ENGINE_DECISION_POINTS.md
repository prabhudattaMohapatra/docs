# Payroll Engine from Scratch — Decision Points & Questions

> When building a payroll engine in another language from scratch, these are the key decisions and questions to answer. Each section includes context, options, and trade-offs.

---

## Table of Contents

1. [Language & Runtime](#1-language--runtime)
2. [Compute Model: Server vs Serverless](#2-compute-model-server-vs-serverless)
3. [Execution Environment: Lambda vs Fargate vs EC2/Container](#3-execution-environment-lambda-vs-fargate-vs-ec2container)
4. [Database Choice](#4-database-choice)
5. [Regulations: Packaging & Deployment](#5-regulations-packaging--deployment)
6. [Scripting & Rule Execution](#6-scripting--rule-execution)
7. [Payrun Execution Model: Sync vs Async](#7-payrun-execution-model-sync-vs-async)
8. [API Design & Versioning](#8-api-design--versioning)
9. [Multi-Tenancy & Isolation](#9-multi-tenancy--isolation)
10. [Data Model & Schema Strategy](#10-data-model--schema-strategy)
11. [Calendar, Period & Time Handling](#11-calendar-period--time-handling)
12. [Testing & Validation](#12-testing--validation)
13. [Security, Compliance & Audit](#13-security-compliance--audit)
14. [Observability & Operations](#14-observability--operations)
15. [Decision Checklist Summary](#15-decision-checklist-summary)

---

## 1. Language & Runtime

**Question:** What language (and runtime) should the core engine use?

**Why it matters:** Payroll logic is numeric, date-heavy, and often regulation-specific. The language affects scripting, regulation packaging, hiring, and cloud integration.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Java / JVM (Kotlin, Scala)** | Strong typing, decimal/money libs, mature ecosystem; JAR-based regulations; long-running processes; good serverless support (Lambda, custom runtimes). | Heavier cold start (Lambda); more verbose. | Regulations as JARs; enterprise stack; Fargate/ECS. |
| **TypeScript / Node.js** | Fast iteration; same language as many frontends; good Lambda fit (small bundle, cold start). | Decimal handling needs care (use decimal.js); long CPU-bound payruns can block event loop. | API-first; serverless; team is JS/TS. |
| **Python** | Readable rules/scripts; strong data/date libs; easy DSLs. | GIL limits CPU parallelism; slower than JVM/Node for heavy number crunching; packaging regulations is less standard. | Data-heavy pipelines; ML/AI rules; internal tools. |
| **Go** | Fast, single binary, good for CLI and services; no JAR story for “plugins.” | Fewer payroll-specific libs; regulation logic often expressed differently (config/DSL vs code). | High-throughput API; container-first; simple regulation model. |
| **C# / .NET** | Reference implementation is .NET; excellent decimal, dates, async; NuGet for regulations. | Ecosystem smaller in some clouds; Lambda support is good but less default than Node/Java. | Aligning with existing .NET engine or Windows shops. |

**Sub-questions:**
- Do we need **compile-time safety** for regulation code (Java/C#/TypeScript) or is interpreted/DSL enough (Python, Lua)?
- How will we handle **decimal/money**? (No floats — use decimal types or dedicated money libraries.)
- What is the **team’s primary language** and willingness to maintain this stack long-term?

---

## 2. Compute Model: Server vs Serverless

**Question:** Should the payrun execution run on a long-lived server (or container) or in a serverless function?

**Why it matters:** Payruns can run from seconds (small tenant, one period) to many minutes (large headcount, retro pay). Timeouts, cost, and cold starts differ.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Server (always-on)** | No cold start; no timeout ceiling (you control); easy to add queues, background jobs. | You manage scaling, patching, cost of idle. | Predictable load; need >15 min runs; existing app server. |
| **Serverless (Lambda, etc.)** | Scale to zero; pay per run; managed scaling. | Execution time limits (e.g. 15 min Lambda); cold start; need to split work or use step functions for long jobs. | Variable load; short jobs; event-driven. |

**Sub-questions:**
- What is the **maximum expected payrun duration** per request? (If >15 min, Lambda alone is not enough without chunking.)
- Do we need **true scale-to-zero** or is a small always-on fleet acceptable?
- Are we okay **chunking** payruns (e.g. by employee batch) and orchestrating with Step Functions / SQS to stay within Lambda limits?

---

## 3. Execution Environment: Lambda vs Fargate vs EC2/Container

**Question:** Where does the payrun code actually run — Lambda, Fargate (or ECS), or EC2/VMs?

**Why it matters:** This drives timeout, memory, regulation packaging (e.g. JAR size), and how we do “scripting” (no persistent process in Lambda).

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Lambda** | No servers to manage; auto-scale; fine-grained billing. | 15 min max (configurable up to 15); 10 GB RAM max; 250 MB deployment package (unzipped); cold start; no long-lived in-memory state. | Short payruns; event-driven; cost-sensitive. |
| **Fargate / ECS** | No server management; container abstraction; no hard timeout; larger memory/CPU. | More expensive per hour than Lambda for bursty work; still need to manage container image and scaling. | Long payruns; regulations as containers or big artifacts; need >15 min. |
| **EC2 / VM** | Full control; no timeout; any runtime, any size. | You manage OS, scaling, capacity. | Legacy or strict compliance; maximum control. |
| **Hybrid** | API on Lambda/Fargate; heavy payrun on Fargate or queue workers. | Two runtimes to maintain; need job queue and worker contract. | API always fast; payrun can be long and batch. |

**Sub-questions:**
- Do regulations ship as **large artifacts** (e.g. 100+ MB JAR)? Lambda has package size limits; Fargate/ECS is more flexible.
- Do we need **persistent in-process state** during a payrun (e.g. compiled script cache)? Lambda is stateless; containers can keep state per task.
- What **memory/CPU** do we expect per payrun? (Drives Lambda memory and Fargate task size.)

---

## 4. Database Choice

**Question:** What database do we use for tenant data, payroll definitions, case values, and results?

**Why it matters:** Payrun flow does many reads (regulations, employees, case values, lookups) and then a burst of writes (job, job employees, result sets). Transactions and reporting matter.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **SQL (PostgreSQL, SQL Server, MySQL)** | ACID; joins for regulations/employees; stored procedures; mature reporting. | Scaling write bursts may need tuning; operational overhead unless managed (RDS, Aurora). | Strong consistency; complex queries; existing SQL skills. |
| **Aurora PostgreSQL/MySQL** | SQL + managed, read replicas, auto-scaling storage. | Cost; still need to design schema and indexing. | AWS-native; want SQL with less DB ops. |
| **DynamoDB** | Serverless; scale to very high throughput; single-digit ms latency. | No joins; design around access patterns; less natural for reporting/analytics. | High scale; simple access patterns; OK to stream to analytics DB. |
| **Document (MongoDB, DocumentDB)** | Flexible schema; good for regulation JSON. | Weaker transactional story for multi-document payrun writes; reporting often needs another store. | Regulation-as-document; schema evolution. |
| **Hybrid** | e.g. PostgreSQL for core + Redis for cache, or DynamoDB + RDS for reporting. | More moving parts. | When one DB doesn’t fit both transactional and analytical needs. |

**Sub-questions:**
- Do we need **multi-row transactions** for a single payrun (job + N result sets)? If yes, SQL or a DB with multi-doc transactions is safer.
- Do we need **stored procedures** for deletes/aggregations (e.g. “delete payrun job and all results”)? SQL is a natural fit.
- What are **compliance/audit** requirements? (Encryption, retention, point-in-time recovery — all influence choice.)

---

## 5. Regulations: Packaging & Deployment

**Question:** How do we package and deploy regulation logic (wage types, collectors, cases, scripts)?

**Why it matters:** Regulations change per country/jurisdiction and over time. Packaging affects versioning, security, and where code runs.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Precompiled artifact (JAR, NuGet, DLL)** | Type-safe; versioned; can be loaded at runtime in same process. | Requires engine and regulations in same language/runtime; release process per regulation. | Java/C# engine; regulations as code; strong versioning. |
| **Separate microservice per regulation** | Independent deploy; any language per regulation. | Network latency per wage type/collector call; harder consistency; operational complexity. | Different teams per country; strict service boundaries. |
| **Embedded in main app (monolith)** | Simple deployment; no version mismatch. | All regulations in one codebase; release coupling. | Small set of regulations; single team. |
| **Database-stored scripts (e.g. C# source in DB)** | No artifact deploy; change via data. | Security (code injection); need sandboxing and compilation pipeline; harder to test in CI. | Reference engine pattern; dynamic script updates. |
| **DSL / config (YAML, JSON) + interpreter** | Non-developers can edit; version as data. | Complex logic is awkward; may need “escape hatch” to code. | Simple rules; config-driven; low-code. |

**Sub-questions:**
- Who **changes regulations**? (Developers → code/artifact; ops/config → DSL or DB-stored.)
- Do we need **multiple regulations active** for one payroll (e.g. base + country overlay)? That implies a resolution strategy (layers, priority) regardless of packaging.
- How do we **test** regulations? (Unit tests in repo vs. integration tests against engine API.)

---

## 6. Scripting & Rule Execution

**Question:** How do we execute regulation “scripts” (wage type value, collector apply, payrun start/end)?

**Why it matters:** The reference engine compiles C# and runs it in-process. Other languages have different options and security implications.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Same-language eval (eval, Roslyn, etc.)** | Full language power; same types as engine. | Security (sandbox); need to control what scripts can access. | Same language as engine; need full expressiveness. |
| **Embedded scripting (Lua, JavaScript/V8, Python)** | Sandboxed; small surface; often fast. | Different language from engine; marshalling. | Security-critical; want to limit what scripts do. |
| **DSL interpreter (custom or expression lang)** | No arbitrary code; auditable. | Limited expressiveness; may need escape to code. | Simple rules; compliance/audit. |
| **Precompiled only (no runtime scripts)** | No runtime code execution; safest. | All logic in artifacts; slower to change. | Strict “no dynamic code” policy. |
| **Remote call to regulation service** | Isolation per regulation. | Latency; availability; complexity. | Regulations as separate services. |

**Sub-questions:**
- Do scripts need to **call back** into the engine (e.g. “get wage type 1000”, “get case value”, “schedule retro”)? That defines the engine API exposed to scripts.
- What **sandboxing** is required? (No file system, no network, CPU/memory limits?)
- How do we handle **script errors**? (Fail payrun, fail one employee, or collect and continue?)

---

## 7. Payrun Execution Model: Sync vs Async

**Question:** When the client starts a payrun, do we run it to completion in the same request (sync) or return immediately and process in the background (async)?

**Why it matters:** Long payruns can hit HTTP timeouts and leave clients waiting. Async needs job identity, status API, and possibly webhooks.

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Synchronous** | Simple; client gets results in response; no polling. | Timeouts; client must wait; not suitable for very long runs. | Short payruns; internal/same-VPC clients. |
| **Asynchronous (job queue)** | No request timeout; client polls or gets webhooks; can retry. | Need job storage, status API, idempotency; client complexity. | Long payruns; many employees; external clients. |
| **Hybrid** | Sync for “small” (e.g. single employee or forecast); async for full run. | Two code paths; clear definition of “small”. | Mixed usage; want simple path for quick runs. |

**Sub-questions:**
- What is the **typical and max** payrun size (employees × periods)? Drives timeout risk.
- Do clients need **webhooks** on job complete/fail? If yes, we need event delivery (and possibly retries).
- Do we support **cancellation** of a running payrun? Easier with async + worker that checks “cancel requested” flag.

---

## 8. API Design & Versioning

**Question:** How do we expose “start payrun”, “get results”, “get job status”, and how do we version the API?

**Why it matters:** Multiple clients (UI, console, integrations); regulations and engine evolve; we need stability and clear contracts.

| Decision | Options | Notes |
|----------|---------|------|
| **Style** | REST vs GraphQL | REST is common for CRUD + “start job”; GraphQL if clients need flexible result shapes. |
| **Versioning** | URL path (/v1/...) vs header vs query | Path is explicit and easy to route; header keeps URL stable. |
| **Idempotency** | Idempotency key header for “start payrun” | Avoids duplicate jobs on retry; store key with job. |
| **Pagination** | Cursor vs offset | Cursor better for large result sets and consistency. |
| **Representation** | JSON vs Protocol Buffers | JSON for ease; Protobuf for performance and strict schema. |

**Sub-questions:**
- Do we need **backward compatibility** for at least N versions? That implies deprecation policy and possibly adapter layer.
- Who are the **API consumers**? (Web app, CLI, other services — may influence auth and rate limits.)

---

## 9. Multi-Tenancy & Isolation

**Question:** How do we isolate data and execution between tenants?

**Why it matters:** Security, compliance, and blast radius (one tenant’s bug or load shouldn’t affect others).

| Option | Pros | Cons | Best when |
|--------|------|------|-----------|
| **Shared DB, tenant ID in every table** | Simple; one schema; row-level security possible. | Must enforce tenant ID in every query; risk of cross-tenant leak if bug. | Same schema for all; strong code review and RLS. |
| **Schema per tenant** | Strong isolation; easy to backup/restore per tenant. | Schema migration × tenants; more connections. | Enterprise; compliance; smaller tenant count. |
| **Database per tenant** | Maximum isolation. | Operational cost; migration and reporting across tenants harder. | SaaS with strict isolation; larger tenants. |
| **Separate process/container per tenant** | Process isolation. | Resource and operational cost. | Rare; only for highest isolation. |

**Sub-questions:**
- Do we need **tenant-specific regulation versions** (e.g. Tenant A on France 2024, Tenant B on France 2025)? That affects how we store and resolve regulation references.
- What **rate limiting** do we apply per tenant? (Per-tenant queues or throttling.)

---

## 10. Data Model & Schema Strategy

**Question:** How do we model tenants, employees, payrolls, payruns, jobs, and results? Single schema or multiple bounded contexts?

**Why it matters:** The reference engine has a clear hierarchy (Tenant → Payroll → Regulation → WageType/Collector/Case; PayrunJob → PayrollResult → WageTypeResult/CollectorResult/PayrunResult). Replicating it guides consistency and reporting.

| Decision | Options | Notes |
|----------|---------|-------|
| **Normalization** | Highly normalized vs. denormalized result snapshots | Normalized for definitions; results can snapshot names/numbers for audit. |
| **Result storage** | One row per wage type/collector per employee per job vs. blob | Rows enable SQL reporting and deletes; blob can be faster write. |
| **Case values** | Separate tables per scope (Global, National, Company, Employee) vs. one with scope column | Reference uses separate; one table with scope is simpler. |
| **Regulation inheritance** | Tables for Regulation, WageType, Collector with layer/priority vs. flattened at read time | Layer/priority allows “derived” wage types; flatten for execution. |
| **Migrations** | Code-first vs. SQL scripts vs. hybrid | Important for zero-downtime and rollback. |

**Sub-questions:**
- Do we need **full history** of result values (e.g. for retro and audit)? That affects whether we overwrite or insert new rows per run.
- How do we **purge** old jobs/results? (Soft delete, partition by date, or hard delete with retention policy.)

---

## 11. Calendar, Period & Time Handling

**Question:** How do we represent pay periods, cycles, and working vs. calendar days?

**Why it matters:** Tax and social security often depend on period boundaries and working days. One bug can affect many employees.

| Decision | Options | Notes |
|----------|---------|-------|
| **Timezone** | Store all dates in UTC vs. tenant timezone vs. “date only” | Recommendation: store period boundaries in UTC or explicit timezone; avoid “date only” for moment-in-time. |
| **Period type** | Enum (Month, SemiMonth, Week, BiWeek) vs. configurable calendar | Configurable (like reference Calendar) supports more countries. |
| **Working days** | Calendar table vs. rule (e.g. Mon–Fri) vs. external source | Needed for pro-rating; keep in tenant/division config. |
| **Decimal vs. float** | Always decimal for money and rates | Non-negotiable for payroll. |

**Sub-questions:**
- Do we support **multiple calendars** per tenant (e.g. monthly vs. weekly)? Reference supports calendar per division/employee.
- How do we handle **period boundaries** for case values (e.g. “salary effective from 15th”)? Reference uses Start/End and aggregation (First, Last, Summary).

---

## 12. Testing & Validation

**Question:** How do we test the engine and regulations?

**Why it matters:** Payroll bugs have financial and legal impact. We need unit tests, regression tests, and ideally golden-file comparisons.

| Area | Options | Notes |
|------|---------|-------|
| **Engine unit tests** | In-repo tests; mock repositories | Test payrun flow with in-memory regulation and employees. |
| **Regulation tests** | Per-regulation repo tests; exchange JSON + expected results | Reference: “PayrunTest” runs exchange import then compares results. |
| **Golden files** | Check in expected result JSON/CSV; diff on change | Prevents accidental regressions; review diffs when rules change. |
| **Property-based tests** | Generate random employees/periods; assert invariants | Good for date and decimal edge cases. |
| **CI** | Run full payrun tests on every PR | Required for safe refactors. |

**Sub-questions:**
- Do we have **certified result sets** from authorities or auditors? Those become golden data.
- How do we test **retro pay** and **incremental** result logic? Need fixtures with prior jobs and case value changes.

---

## 13. Security, Compliance & Audit

**Question:** What security and compliance requirements do we have?

**Considerations:**

| Topic | Questions |
|-------|-----------|
| **Authentication** | OAuth2, API keys, or both? Per-tenant or global? |
| **Authorization** | Role-based (e.g. “run payrun”, “view results”)? Resource-level (per division/payroll)? |
| **Encryption** | At rest (DB, backups) and in transit (TLS)? Who manages keys? |
| **Audit log** | Who started which job, when, and what changed? Store in DB or external system? |
| **PII** | Where is employee PII stored? Retention and deletion policy? |
| **Regulation code** | Who can deploy regulation artifacts? Signed artifacts? No eval of untrusted code? |

---

## 14. Observability & Operations

**Question:** How do we observe and operate the engine in production?

| Area | Decisions |
|------|-----------|
| **Logging** | Structured logs (JSON); correlation ID per payrun/job; log level (info vs. debug). |
| **Metrics** | Payrun duration, employee count, DB latency, script errors; per tenant optional. |
| **Tracing** | Distributed trace per request (and per payrun) if we have multiple services. |
| **Alerting** | On failure rate, timeout rate, queue depth (if async). |
| **Deployment** | Blue/green or canary; rollback strategy; DB migrations before/after code. |
| **Secrets** | DB credentials, API keys in secret manager; not in code or config in repo. |

---

## 15. Decision Checklist Summary

Use this as a short checklist; answer each with your chosen option and one-line rationale.

| # | Decision | Your choice | Rationale |
|---|----------|-------------|-----------|
| 1 | **Language** | Java / TypeScript / Python / Go / C# / other | |
| 2 | **Compute model** | Server / Serverless / Hybrid | |
| 3 | **Execution environment** | Lambda / Fargate / EC2 / Hybrid | |
| 4 | **Database** | PostgreSQL / SQL Server / Aurora / DynamoDB / Other | |
| 5 | **Regulation packaging** | JAR (or equivalent) / Separate service / Monolith / DB scripts / DSL | |
| 6 | **Script execution** | Same-language eval / Embedded script engine / DSL only / Precompiled only | |
| 7 | **Payrun execution** | Sync / Async / Hybrid | |
| 8 | **API versioning** | Path / Header / Other | |
| 9 | **Multi-tenancy** | Shared DB + tenant ID / Schema per tenant / DB per tenant | |
| 10 | **Result storage** | Row per result / Blob / Hybrid | |
| 11 | **Money/decimals** | Library and type (e.g. decimal in DB and code) | |
| 12 | **Timezone strategy** | UTC / Tenant TZ / Date-only | |
| 13 | **Testing** | Golden files / Property-based / Certified data | |
| 14 | **Auth** | OAuth2 / API key / Both | |
| 15 | **Audit** | Full job/result audit log (where, format) | |

---

## References

- [PAYRUN_EXECUTION_FLOW.md](./PAYRUN_EXECUTION_FLOW.md) — End-to-end flow and DB operations of the reference .NET engine.
- Your existing POC and production comparison docs (e.g. `POC_VS_PRODUCTION_COMPARISON.md`) for current environment choices.
