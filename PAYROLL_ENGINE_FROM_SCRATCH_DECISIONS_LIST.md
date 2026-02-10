# Payroll Engine from Scratch — Decisions List

When building a **new payroll engine from scratch** in a different language and challenging the existing architecture, the following decisions can or must be taken. Each item is a **decision area**: you choose an option (or define your own) and document it (e.g. in an RFC like `rfc_runtime_rules_scripting_options.md`).

Use this list to:
- Ensure no major design choice is left implicit.
- Drive RFCs or design docs per area.
- Track “decided” vs “open” and dependencies between decisions.

**Convention:**  
- **Must** = required for a coherent engine; at least one explicit choice (or “we defer”) is needed.  
- **Can** = optional but recommended to decide early; affects scope and consistency.

---

## 1. Architecture and product shape

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 1.1 | **Core vs dynamic separation** | One generic engine + ingested/pluggable regulation content vs monolithic (all logic in code) vs separate product per country vs config-only. | **Must** | `rfc_core_dynamic_separation_alternatives.md`, `PAYROLL_ENGINE_CORE_DYNAMIC_SEPARATION_ALTERNATIVES.md` |
| 1.2 | **Separate product per country** | If not “one engine + dynamic content”: one deployable per country vs one shared deployable with routing. | Can | `SEPARATE_PRODUCT_PER_COUNTRY_DETAILED.md` |
| 1.3 | **Language and platform** | Primary implementation language (e.g. Java, TypeScript) and runtime; affects scripting, tooling, and hiring. | **Must** | `NEW_PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_AND_REUSE_ANALYSIS.md`, `PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md` |

---

## 2. Runtime rules and regulation execution

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 2.1 | **Runtime rules / scripting model** | How regulation logic runs: runtime scripting (compile/run user code), precompiled (JAR/JS bundle), external regulation service, expression/formula DSL only, or hybrid. | **Must** | `rfc_runtime_rules_scripting_options.md`, `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md` |
| 2.2 | **Script/expression language** | If runtime scripting or hybrid: which language (Java, JavaScript, Groovy, etc.) and which compiler/runtime (Janino, Graal, isolate-vm, etc.). | Must (if 2.1 = scripting or hybrid) | Inside `rfc_runtime_rules_scripting_options.md` (Option 1) |
| 2.3 | **Expression DSL design** | If expression-only or hybrid: grammar, whitelist of functions, storage (string vs JSON AST), and limits (no loops, no user-defined functions, etc.). | Must (if 2.1 = expression or hybrid) | Inside `rfc_runtime_rules_scripting_options.md` (Option 4, 5) |
| 2.4 | **Regulation service contract** | If external regulation service: API shape (REST/gRPC), request/response schema, versioning, auth, and batching strategy. | Must (if 2.1 = external service or hybrid) | Inside `rfc_runtime_rules_scripting_options.md` (Option 3) |
| 2.5 | **Precompiled regulation contract** | If precompiled JAR/JS: interface (e.g. RegulationEvaluator), version resolution, and loading (classpath vs plugin registry vs dynamic import). | Must (if 2.1 = precompiled or hybrid) | Inside `rfc_runtime_rules_scripting_options.md` (Option 2) |
| 2.6 | **Script lifecycle and hooks** | Which lifecycle hooks to support: PayrunStart/End, EmployeeStart/End, WageTypeAvailable, WageTypeValue, WageTypeResult, CollectorStart/Apply/End, CaseAvailable/Build/Validate, ReportStart/Build/End. | **Must** | `PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md` (Component 6) |
| 2.7 | **Script sandbox and limits** | No file/network in script; timeout per invocation; memory limits; optional approval workflow for script changes. | **Must** | `rfc_runtime_rules_scripting_options.md` (Observability); build plan Component 14 |

---

## 3. Domain model

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 3.1 | **Tenant and hierarchy** | Tenant as top-level; whether “organization” or other level exists above tenant; strict tenant scoping for all data. | **Must** | Build plan Component 1, 14 |
| 3.2 | **Payroll and division** | One division per payroll (1:1) vs many; how payroll references regulations (regulation references, cluster sets). | **Must** | Build plan Component 1 |
| 3.3 | **Payrun and payrun job model** | One payrun per period; one job per payrun vs one job per employee (sync vs async); PayrunJobFactory and period/cycle from calculator. | **Must** | Build plan Component 1, 10 |
| 3.4 | **Regulation derivation** | How “effective” wage types, collectors, scripts, lookups are resolved for a payroll at a given regulation date and evaluation date (derived vs denormalized storage). | **Must** | Build plan Component 1, 3, 4, 7 |
| 3.5 | **Wage type and collector identity** | WageTypeNumber (decimal) and Collector name per regulation; derived wage type/collector with namespace and attributes. | **Must** | Build plan Component 3 |
| 3.6 | **Collector–wage type relationship** | Wage type lists Collectors/CollectorGroups (feed direction); “collector available for wage type” rule; no reverse list on collector. | **Must** | Build plan Component 3 |

---

## 4. Case and case values

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 4.1 | **Case value scopes** | Global (tenant), National (tenant), Company (division), Employee (employee + division); separate repos/tables vs single table with scope column. | **Must** | Build plan Component 2 |
| 4.2 | **Effective dating** | CaseValue Start/End; “valid at date” = start ≤ date and (end null or end ≥ date); query pattern for resolution. | **Must** | Build plan Component 2, 7 |
| 4.3 | **Case slot and value types** | Optional CaseSlot for multi-slot case fields; value types (String, Boolean, Integer, Decimal, DateTime, etc.) and storage (e.g. JSON string + NumericValue). | **Must** | Build plan Component 2 |
| 4.4 | **Case value resolution and provider** | CaseValueCache per scope; CaseValueProvider aggregating global/national/company/employee with priority and date; API exposed to rules (getCaseValue(name, field, slot)). | **Must** | Build plan Component 2 |

---

## 5. Lookups

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 5.1 | **Lookup structure** | LookupSet and lookup values per regulation; derived lookups for payroll at regulation date. | **Must** | Build plan Component 4 |
| 5.2 | **Range and date resolution** | Lookup values with optional range (start/end); get value for (lookup name, key, date). | **Must** | Build plan Component 4 |
| 5.3 | **Lookup provider in rules** | API for rules: lookup(name, key) or lookup(name, key, date); injected via RegulationLookupProvider or equivalent. | **Must** | Build plan Component 4 |

---

## 6. Periods and calendars

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 6.1 | **Period types** | Calendar month, week, bi-month, semi-month, quarter, year; which to support first. | **Must** | Build plan Component 5 |
| 6.2 | **Calculator and calendar source** | Calendar name from Division/Tenant/Employee; IPayrollCalculator (or equivalent) per tenant/user/culture/calendar; GetPayrunPeriod(date), GetPeriods(year). | **Must** | Build plan Component 5 |
| 6.3 | **Cycle and retro** | Period vs cycle (start/end, name) on PayrunJob; use for YTD and retro (retro periods within cycle). | **Must** | Build plan Component 5, 10 |

---

## 7. Persistence and data layer

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 7.1 | **Schema and tenant isolation** | One schema with tenant_id (or equivalent) on all tenant-scoped tables; no cross-tenant queries. | **Must** | Build plan Component 7, 14 |
| 7.2 | **Derived data strategy** | Derived wage types/collectors/scripts/lookups via queries (e.g. stored procs) with regulation date vs denormalized stored tables. | **Must** | Build plan Component 7 |
| 7.3 | **Result storage** | WageTypeResult, CollectorResult under PayrollResult; consolidated (YTD) in separate tables or same; transaction scope (e.g. one transaction per payrun job). | **Must** | Build plan Component 7, 10 |
| 7.4 | **Database and access pattern** | SQL Server vs PostgreSQL vs other; repository pattern; DbContext/transaction scope for processor. | **Must** | Build plan Component 7; backend Persistence.SqlServer |

---

## 8. Exchange format and import/export

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 8.1 | **Exchange model** | Single root (e.g. Exchange with Tenants); each tenant subtree (payrolls, regulations, wage types, collectors, cases, lookups, scripts, employees, case values); regulation shares at root. | **Must** (if core+dynamic) | Build plan Component 8 |
| 8.2 | **References and import order** | By identifier; import creates/updates by identifier; dependency order (tenant → regulation → script/wage type/collector → employees → case values). | **Must** (if import) | Build plan Component 8 |
| 8.3 | **Import path** | Client (CLI) reads file → ExchangeReader → ExchangeImport via HTTP to backend API vs backend API accepting Exchange JSON and persisting directly. | **Must** (if import) | Build plan Component 8 |
| 8.4 | **Script storage in exchange** | Script source inlined (Script.Value) vs referenced (e.g. file path); binary/cache not in exchange. | Can | Build plan Component 8; `rfc_runtime_rules_scripting_options.md` |
| 8.5 | **Rules ingestion pipeline** | How regulation content (scripts, expressions, or artifact references) is ingested: API only vs file drop vs CI/CD artifact upload; link to rules execution model (2.1). | Can | `rfc_rules_ingestion.md` (template); `RULES_CS_IMPORT_AND_EXECUTION.md` |

---

## 9. API and integration

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 9.1 | **API style** | REST with JSON vs gRPC vs both; tenant in path (/tenants/{id}/…); resource set (tenants, regulations, payrolls, divisions, employees, payruns, payrun jobs, case values, wage types, collectors, scripts, lookups, results). | **Must** | Build plan Component 9 |
| 9.2 | **Auth and multi-tenancy** | API key, OAuth, or other; tenant resolved from path vs token; no cross-tenant access. | **Must** | Build plan Component 9, 14 |
| 9.3 | **Payrun execution model** | Sync (block until done) vs async (start → enqueue jobs → poll status); “start payrun” = create/update jobs + enqueue; result endpoints by payrun job id. | **Must** | Build plan Component 9, 10, 11 |
| 9.4 | **Query and filtering** | OData-style filter/order/select vs simple query params; pagination. | Can | Build plan Component 9; backend OData.md |
| 9.5 | **Versioning** | API version in URL vs header (e.g. X-Version); compatibility and deprecation policy. | Can | Backend README (API Versioning) |

---

## 10. Payrun orchestration and execution

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 10.1 | **Lifecycle sequence** | PayrunStart → [per employee: EmployeeStart → CollectorStart → wage type loop (WageTypeValue + CollectorApply) → CollectorEnd → store results → EmployeeEnd] → PayrunEnd. | **Must** | Build plan Component 10 |
| 10.2 | **Wage type evaluation order** | Order of derived wage types (from repository); collector start before wage types; collector apply after each wage type; collector end after all wage types. | **Must** | Build plan Component 10 |
| 10.3 | **Retro payrun** | When CollectorStart/CollectorEnd (or script) returns retro jobs; create child payrun jobs for retro periods; process recursively or enqueue; loop until retro period reaches evaluation period start. | **Must** (if retro required) | Build plan Component 10 |
| 10.4 | **Failure and abort** | PayrunStart returns false or exception → abort job; per-employee or per–wage type exception → abort job vs partial results; context.Errors and AbortJobAsync. | **Must** | Build plan Component 10 |
| 10.5 | **Execution restart** | Optional “execution restart” from script (max N times) to re-run wage type loop; when and how to reset. | Can | Build plan Component 10 |

---

## 11. Background jobs and worker

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 11.1 | **Queue choice** | In-memory vs DB-backed vs external (SQS, RabbitMQ, etc.); one queue item = one payrun job vs one per payrun. | **Must** | Build plan Component 11 |
| 11.2 | **Worker model** | One worker process dequeues one item at a time; scale by multiple workers; retries and dead-letter policy. | **Must** | Build plan Component 11 |
| 11.3 | **Idempotency** | If job already completed, skip vs re-run; idempotency key or job id. | Can | Build plan Component 11 |

---

## 12. CLI and tooling

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 12.1 | **CLI role** | Thin client (commands call backend via HTTP) vs direct DB access for import/export. | **Must** | Build plan Component 12 |
| 12.2 | **Command set** | Import (Exchange file), Export, Payrun start, Payrun results, Case test, Report, Regulation import, Tenant delete, etc.; which are in scope for v1. | **Must** | Build plan Component 12; console README |
| 12.3 | **Config** | Base URL, API key, config file (e.g. appsettings/apisettings); optional DSL/config for file-based workflows. | **Must** | Build plan Component 12 |

---

## 13. Reporting and documents

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 13.1 | **Reporting in scope** | Report and ReportParameter, ReportTemplate; ReportProcessor with script (ReportStart, ReportBuild, ReportEnd) vs template-only; optional for minimal engine. | Can | Build plan Component 13 |
| 13.2 | **Document generation** | PDF/Excel generation (e.g. FastReport, NPOI); separate document service vs library in engine/CLI. | Can | Build plan Component 13; payroll-engine-document |

---

## 14. Non-functional

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 14.1 | **Multi-tenancy enforcement** | Tenant in every repository call and API handler; validate tenant in path vs token. | **Must** | Build plan Component 14 |
| 14.2 | **Audit** | Audit tables and audit services for key entities (script, wage type, regulation, case value); what to audit and retention. | Can | Build plan Component 14 |
| 14.3 | **Script security and limits** | No file/network in script API; timeout per invocation; optional memory limits; sandbox (isolate/ClassLoader). | **Must** | Build plan Component 14; `rfc_runtime_rules_scripting_options.md` |
| 14.4 | **Caching and performance** | Cache derived regulation and (if applicable) compiled scripts; cache eviction (timeout, LRU); DB indexes for case value and lookup resolution. | **Must** | Build plan Component 14 |
| 14.5 | **Observability** | Logging, metrics (payrun duration, queue depth, compile time, cache hit rate, regulation service latency), tracing (e.g. trace id from API to worker to regulation). | **Must** | `rfc_runtime_rules_scripting_options.md` (Observability); build plan Component 14 |

---

## 15. Migration and compatibility (if replacing existing engine)

| # | Decision | Description | Must/Can | Existing doc / RFC |
|---|----------|-------------|----------|--------------------|
| 15.1 | **Migration strategy** | Big-bang vs phased; reimplement regulations (C# → Java/TS or expression) vs wrap existing backend as regulation service. | Can | `rfc_runtime_rules_scripting_options.md` (Compatibility, Migration); `MIGRATION_ANALYSIS.md` |
| 15.2 | **Exchange and API compatibility** | Same Exchange JSON shape and API paths for import/export vs new contract and one-time migration tools. | Can | Build plan Component 8, 9 |

---

## Summary: Must-decide first

For a coherent from-scratch design, at least these should be decided early (many are interdependent):

1. **1.1** Core vs dynamic separation  
2. **1.3** Language and platform  
3. **2.1** Runtime rules / scripting model  
4. **2.6** Script lifecycle and hooks  
5. **3.1–3.6** Domain model (tenant, payroll, payrun job, derivation, wage type/collector identity and relationship)  
6. **4.1–4.4** Case value scopes, effective dating, resolution  
7. **5.1–5.3** Lookups structure and provider  
8. **6.1–6.3** Periods and calculator  
9. **7.1–7.4** Persistence (schema, derived strategy, results, DB)  
10. **9.1–9.3** API style, auth, payrun execution (sync/async)  
11. **10.1–10.4** Payrun lifecycle, ordering, retro, failure  
12. **11.1–11.2** Queue and worker  
13. **14.1, 14.3, 14.4, 14.5** Multi-tenancy, script security, caching, observability  

Then fill in the rest (exchange, CLI, reporting, audit, migration) according to scope.

---

*This list is derived from the payroll-engine workspace: `PAYROLL_ENGINE_BUILD_PLAN_FROM_SCRATCH.md`, `rfc_runtime_rules_scripting_options.md`, `rfc_core_dynamic_separation_alternatives.md`, and related docs in `docs/`.*
