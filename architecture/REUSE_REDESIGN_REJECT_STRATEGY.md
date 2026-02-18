# Payroll Engine: Reuse, Redesign & Reject Strategy

This document defines what to **reuse**, what to **redesign**, and what to **reject** when building a new payroll engine inspired by the existing .NET engine.

Related: `PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md` (language choice and POC).

---

## 1. Reuse — Carry Over

These are **core payroll concepts** and patterns that are language-agnostic and should be carried over into the new engine.

### 1.1 Domain model (reuse conceptually)

- **Tenant** → **Division** → **Payroll** (with **Regulation** layers) → **Payrun** → **PayrunJob**.
- **Employee** (per division); **Case** / **CaseValue** (global, national, company, employee).
- **WageType** (number, name, value type, collector links); **Collector** (name, collect mode, negated).
- **PayrunJob** (period, cycle, evaluation date); **PayrollResultSet** (WageTypeResults, CollectorResults, PayrunResults).
- **Regulation** as a versioned set of WageTypes, Collectors, Cases, Lookups, and script/expression logic.

**Reuse**: Entity names, relationships, and lifecycle. Reimplement in Java or TypeScript with your own types and persistence.

### 1.2 Calculation flow (reuse)

- **Case fields** (inputs) → **Wage types** (per-wage-type value) → **Collectors** (aggregate from wage types) → **Net / Payment** (formulas from collectors).
- Order: **Collector Start** → **Wage type value** (for each) → **Collector Apply** (per wage type) → **Collector End**.
- **PayrunStart** → **EmployeeStart** → [wage types + collectors] → **EmployeeEnd** → **PayrunEnd**.

**Reuse**: Sequence and dependencies; reimplement the orchestration (e.g. your own PayrunProcessor-like loop).

### 1.3 Lifecycle hooks (reuse)

- PayrunStart, PayrunEnd.
- EmployeeStart, EmployeeEnd.
- WageTypeAvailable, WageTypeValue.
- CollectorStart, CollectorApply, CollectorEnd.
- CaseAvailable, CaseBuild, CaseValidate (if you support case rules).

**Reuse**: **Names and order** of hooks; signatures can be adapted (e.g. context object instead of multiple args).

### 1.4 Exchange / API shape (reuse as reference)

- REST (or equivalent) for: tenants, payrolls, regulations, payruns, jobs, employees, case values, results.
- **Exchange format** (JSON) for import/export: tenants, regulations, wage types, collectors, cases, lookups, scripts.

**Reuse**: Resource names and payload shape where it helps compatibility or migration; you can simplify or version (e.g. v2 API).

### 1.5 Regulation structure (reuse)

- Regulation = **metadata** (JSON/YAML) + **logic** (scripts or expressions).
- Wage type: optional **value expression/script**; links to collectors.
- Collector: optional **start/end/apply** logic.
- Lookups for rates, thresholds, etc.

**Reuse**: Separation of “data” (wage type number, collector name, case names) from “behavior” (how value is computed).

### 1.6 Calendar and periods (reuse)

- Payroll calendar (period type, cycle); **evaluation period** and **evaluation date**; **cycle start/end** for YTD etc.

**Reuse**: Concepts; reimplement or use a library (e.g. Java: ThreeTen Extra; TypeScript: date-fns or similar).

---

## 2. Redesign — Do Differently

These are implementation choices that are .NET-specific or worth improving; keep the **intent** but change the **mechanism**.

### 2.1 Runtime compilation of arbitrary C# (redesign)

- **Current**: Roslyn compiles C# source from DB into an in-memory assembly; reflection to invoke.
- **Issue**: Tight coupling to .NET; complex and heavy; security and versioning of script dependencies.
- **Redesign**:
  - **Java**: Janino (or Groovy) for Java snippets; or GraalVM for a small DSL/JS.
  - **TypeScript**: No in-process TypeScript compiler; use **JavaScript** in an isolate, or **precompiled** rule bundles, or an **external rule service** (HTTP/gRPC). Document the trade-off (flexibility vs simplicity).
  - **Or**: Avoid runtime scripting entirely (expressions, precompiled regulation, or external service). See `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md`.

### 2.2 Storing full script source in the main DB (redesign)

- **Current**: Script content (e.g. Rules.cs) in `Script.Value`; compiled on demand.
- **Issues**: Hard to version like code; hard to test in CI; big blobs in DB.
- **Redesign**:
  - **Versioned artifacts**: Scripts live in repos (or artifact store); engine references **regulation version** (e.g. France v2.1) and loads compiled/bundled logic from cache or blob store.
  - **DB** stores only **references** (regulation id, script name, version hash), not full source. Optionally keep source in DB for audit/display only.

### 2.3 Monolith backend (redesign)

- **Current**: One process does API + payrun processing + script compilation.
- **Redesign**:
  - **Separate workers** for payrun execution (scale independently, isolate CPU/memory).
  - Optional **regulation service** (separate process or Lambda) that only evaluates wage type/collector; engine calls it. See `EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md`.

### 2.4 Tight coupling of “engine core” and “regulation script API” (redesign)

- **Current**: Scripts extend base classes and use a large runtime (Employee, Case, Collector, Lookup, etc.) from the same process.
- **Redesign**:
  - **Narrow, explicit API** for rules: e.g. “context” with only `getCaseValue(caseName, fieldName)`, `getCollector(name)`, `setWageTypeResult(number, value)`, `getLookup(name, key)`. Easier to sandbox and to reimplement in another language later.
  - Consider **expression-first** for simple rules (e.g. “WageType 1000 = CaseValue(ContractualMonthlyWage)”) and script only when needed.

### 2.5 In-memory assembly cache keying (redesign)

- **Current**: AssemblyCache caches compiled assembly; invalidation can be coarse.
- **Redesign**:
  - Cache key = **hash of script content + engine version** (or regulation version). Clear cache when regulation or engine is deployed.
  - Or: no long-lived in-process cache; load from artifact store per job (if cold start is acceptable).

### 2.6 Synchronous “run full payrun in one request” as only option (redesign)

- **Current**: Sync possible; async via queue + worker.
- **Redesign**:
  - **First-class async**: “Start payrun” returns job id; “Get job status/results” is the main flow. Sync can be a thin wrapper (wait for job). Easier to scale and to add retries/timeouts.

---

## 3. Reject — Do Not Reuse

These should **not** be carried over; use language- and stack-appropriate alternatives instead.

### 3.1 .NET-specific types and serialization (reject)

- **Current**: `decimal`, `DateTime`, custom JSON converters, `DataSet` for reports.
- **Reject**: Do not mimic .NET types in Java/TypeScript.
- **Instead**:
  - **Java**: `BigDecimal`, `LocalDate`/`ZonedDateTime`; standard JSON (Jackson).
  - **TypeScript**: decimal library; `Date` or string (ISO) for dates; standard JSON. Don’t try to mimic `DataSet`; use simple DTOs or report-specific structures.

### 3.2 Roslyn / in-process C# compilation (reject)

- **Current**: Microsoft.CodeAnalysis (Roslyn) compiles C# in-process; reflection to invoke.
- **Reject**: Do not port Roslyn or “compile C# in Java/TS”.
- **Instead**: Use Janino/Groovy (Java), JS isolate/precompiled (TypeScript), or no runtime scripting (expressions, precompiled regulation, external service).

### 3.3 Script table storing full source and binary (reject as primary model)

- **Current**: Script table with `Value` (C# source) and `Binary` (compiled byte[]); compile on first use and persist.
- **Reject**: Do not make “store and compile user code in DB” the primary model in the new engine.
- **Instead**: Versioned artifacts (repos/artifact store) and DB references; or expression-only; or external regulation service.

### 3.4 Exact persistence schema (reject)

- **Current**: SQL Server schema (tables, FKs, column names) as in the .NET engine.
- **Reject**: Do not copy the exact schema.
- **Instead**: Design for your DB (e.g. PostgreSQL) and engine; keep **concepts** (tenant, payroll, payrun, job, results, case values) and relationships, but tables and columns can differ.

### 3.5 FastReport / .NET reporting stack (reject)

- **Current**: FastReport and .NET report generation.
- **Reject**: Do not depend on .NET reporting.
- **Instead**: Use Java/TypeScript reporting libs (e.g. iText/POI in Java; pdfkit/exceljs in TypeScript).

---

## 4. Summary Table

| Area | Reuse | Redesign | Reject |
|------|--------|----------|--------|
| **Domain entities** | Tenant, Payroll, Payrun, Employee, WageType, Collector, Case, Result (concepts and names) | — | .NET-specific types; use Java/TS types. |
| **Calculation flow** | Case → WageType → Collector → Net; lifecycle order | — | — |
| **Lifecycle hooks** | Names and order (PayrunStart, EmployeeStart, WageTypeValue, …) | Signature style (use context object) | — |
| **Exchange/API** | Resource model and payloads as reference | Exact URLs/formats if you want a clean v2 | — |
| **Regulation structure** | Metadata + logic; wage type ↔ collector links | Versioned artifacts instead of full source in DB | Storing full C# in DB as primary model |
| **Script execution** | Need *some* way to run dynamic rules (if you keep them) | Janino/JS isolate/external service; or expression/precompiled only | Roslyn; in-process C# compile |
| **Persistence** | Concepts (job, results, case values) | Worker model; cache keying | Exact schema; script source/binary in DB as primary |
| **Reporting** | Concept of “report from results” | — | FastReport/.NET stack |

---

## 5. Suggested Next Steps

1. **Adopt** the reuse list (domain, flow, lifecycle, regulation structure, calendar) in your new design.
2. **Apply** the redesigns (versioned regulation, narrow rule API, async-first, separate workers) where they fit your scope.
3. **Avoid** the reject list (no .NET types, no Roslyn, no “script source in DB as primary”, no exact schema, no .NET reporting).
4. Use **PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md** to choose Java vs TypeScript and to scope your POC; then design v1 and implement.
