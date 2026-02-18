# Payroll Engine from Scratch — Open Questions

This document lists **open questions** (decision areas still to be resolved) when building a new payroll engine from scratch. It complements [PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md](PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md) (component plan) and [PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md](PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md) (full decisions list). Use this for tracking what remains **open** vs decided.

---

## 1. Platform and deployment

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 1.1 | **Server vs serverless** | Long-running server (e.g. ECS, App Service) vs serverless (Lambda, Azure Functions). Affects: cold start, payrun job duration limits, queue model, DB connection pooling, script compilation cache. | Build plan Component 9, 11; infra docs |
| 1.2 | **Where does the engine run?** | Same process as API vs separate worker service vs fully serverless (API + Lambda per job). Affects deployment topology and scaling. | Build plan Component 10, 11 |
| 1.3 | **Scaling and queue** | In-process queue vs DB-backed queue vs external (SQS, Service Bus). Worker scale-out and retries. | Build plan Component 11 |

---

## 2. Language and platform

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 2.1 | **Java vs TypeScript (vs Python)** | Primary implementation language for engine, API, and tooling. Affects: scripting story, hiring, reuse of existing .NET code (none vs adapt). | `rfc_language_choice_java_typescript_python_payroll_engine_reconstruction.md`, `PAYROLL_ENGINE_RECONSTRUCTION_JAVA_VS_TYPESCRIPT_VS_PYTHON.md`, `NEW_PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_AND_REUSE_ANALYSIS.md`, `PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md` |
| 2.2 | **Reuse vs rewrite** | Reuse existing .NET packages (Core, Client.*) via interop or adapters vs full rewrite in chosen language. | `REUSE_REDESIGN_REJECT_STRATEGY.md`; migration-analysis folder |
| 2.3 | **Migration path** | Big-bang rewrite vs strangler / incremental (e.g. new engine behind feature flag, country-by-country). | `MIGRATION_ANALYSIS.md`, `MIGRATION_COMPARISON_MATRIX.md` |

---

## 3. Core vs dynamic and product shape

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 3.1 | **Core vs dynamic separation** | One generic engine + ingested/pluggable regulation content vs monolithic (all logic in code) vs separate product per country vs config-only. | `rfc_core_dynamic_separation_alternatives.md`, `PAYROLL_ENGINE_CORE_DYNAMIC_SEPARATION_ALTERNATIVES.md`, `PAYROLL_ENGINE_CORE_VS_DYNAMIC_ARCHITECTURE.md` |
| 3.2 | **Separate product per country** | If not “one engine + dynamic content”: one deployable per country vs one shared deployable with routing. | `SEPARATE_PRODUCT_PER_COUNTRY_DETAILED.md` |

---

## 4. Runtime rules and scripting

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 4.1 | **Runtime rules / scripting model** | Runtime scripting (compile/run user code) vs precompiled (JAR/JS bundle) vs external regulation service vs expression/formula DSL only vs hybrid. | `rfc_runtime_rules_scripting_options.md`, `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md`; execution-and-rules folder |
| 4.2 | **Script/expression language** | If scripting or hybrid: which language (C#, Java, JavaScript, Groovy, etc.) and compiler/runtime (Roslyn, Janino, Graal, etc.). | `rfc_runtime_rules_scripting_options.md`; `JAVA_RULES_IMPLEMENTATION.md`; `RUNTIME_RULES_SCRIPTING_DOTNET_DETAILED.md` |
| 4.3 | **Expression DSL** | If expression-only or hybrid: grammar, whitelist of functions, storage (string vs JSON AST), limits (no loops, no user-defined functions). | `rfc_runtime_rules_scripting_options.md` (Option 4, 5) |
| 4.4 | **External regulation service** | If external service: API shape (REST/gRPC), request/response schema, versioning, auth, batching. | `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`; poc guides (external service variant) |
| 4.5 | **Precompiled regulation contract** | If precompiled: interface (e.g. RegulationEvaluator), version resolution, loading (classpath vs plugin directory vs dynamic import). | `DIFFERENT_EXECUTION_MODEL_PRE_COMPILED_ASSEMBLIES.md`, `EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md`; poc folder |
| 4.6 | **Script lifecycle and hooks** | Which hooks to support: PayrunStart/End, EmployeeStart/End, WageTypeAvailable, WageTypeValue, WageTypeResult, CollectorStart/Apply/End, CaseAvailable/Build/Validate, Report*. | Build plan Component 6 |
| 4.7 | **Script sandbox and limits** | No file/network; timeout per invocation; memory limits; approval workflow for script changes. | Build plan Component 6, 14 |

---

## 5. Persistence and data

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 5.1 | **Database choice** | SQL Server vs PostgreSQL vs other; compatibility with existing schema and tooling. | Build plan Component 7 |
| 5.2 | **Derived data strategy** | Derived wage types/collectors/scripts/lookups via queries (e.g. stored procs) with regulation date vs denormalized stored tables. | Build plan Component 7 |
| 5.3 | **Result storage and YTD** | WageTypeResult, CollectorResult under PayrollResult; consolidated (YTD) in separate tables or same; transaction scope. | Build plan Component 7, 10 |

---

## 6. API and execution model

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 6.1 | **API style** | REST with JSON vs gRPC vs both; tenant in path; resource set and versioning. | Build plan Component 9 |
| 6.2 | **Payrun execution** | Sync (block until done) vs async (start → enqueue jobs → poll status). Serverless may force async + polling. | Build plan Component 9, 10, 11 |
| 6.3 | **Auth and multi-tenancy** | API key, OAuth, OIDC; tenant from path vs token; no cross-tenant access. | Build plan Component 9, 14; `OIDC-SETUP-REQUIRED.md` |

---

## 7. Import/export and ingestion

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 7.1 | **Exchange and import path** | Exchange JSON shape; import via CLI (ExchangeReader → HTTP) vs backend API accepting Exchange and persisting directly. | Build plan Component 8 |
| 7.2 | **Rules ingestion pipeline** | How regulation content is ingested: API only vs file drop vs CI/CD artifact upload; link to rules execution model. | `RULES_CS_IMPORT_AND_EXECUTION.md`; `COUNTRY_REGULATION_INGESTION_FLOW.md` |

---

## 8. Non-functional and operations

| # | Open question | Options / notes | Related doc |
|---|----------------|-----------------|-------------|
| 8.1 | **Multi-tenancy enforcement** | Strict tenant scoping in every repo call and API handler; tenant in path vs token validation. | Build plan Component 14 |
| 8.2 | **Audit** | Audit tables and services for key entities (script, wage type, regulation, case value) or not. | Build plan Component 14 |
| 8.3 | **Performance and caching** | Cache derived regulation and compiled scripts; DB indexes; connection pooling; script cache invalidation. | Build plan Component 6, 14; testing folder |

---

## How to use this doc

- **Track status**: Mark each question as **Open** | **In progress** | **Decided** (and add a one-line decision or link to RFC).
- **Drive RFCs**: Use open questions to create or update RFCs (see `rfc/rfc_format.md`).
- **Cross-check**: When a decision is made, update [PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md](PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md) and this doc so the decisions list is the single place for “decided” and this doc stays focused on what’s still open.

Related: [PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md](PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md) | [PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md](PAYROLL_ENGINE_FROM_SCRATCH_DECISIONS_LIST.md) | [README.md](../README.md) (docs index).
