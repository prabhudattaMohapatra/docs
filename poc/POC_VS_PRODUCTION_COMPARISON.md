# POC vs Production Engine — Feature Comparison

> Comparison of `payroll-engine-poc` + `payroll-regulations-poc` against the production `payroll-engine-backend` and its dependencies.
> Source of truth: code inspection of both codebases + `PAYRUN_EXECUTION_FLOW.md`.
> Generated 2026-02-23.

---

## Executive Summary

| Metric | Value |
|---|---|
| **Overall feature coverage** | **~20%** of production engine capabilities |
| **Computation engine coverage** | **~35%** of the core payrun calculation logic |
| **Regulation rules coverage** | **~40%** of France regulation wage type logic |
| **Infrastructure / deployment** | POC introduces capabilities not present in production (Fargate, S3, parallel processing, metrics) |

The POC faithfully reproduces the **innermost computation loop** — wage type evaluation, collector accumulation, and lookup resolution — while deliberately omitting the surrounding orchestration, persistence, scripting, retro pay, and API layers. This is consistent with its purpose: benchmarking raw computation performance on AWS Fargate.

---

## Feature-by-Feature Comparison

### 1. Payrun Orchestration (Phases 1–8)

| Feature | Production | POC | Status |
|---|---|---|---|
| **Phase 1 — Validation & Setup** | Load Tenant, User, Payrun, Payroll, Division, Calendar; resolve culture; create PayrollCalculator; determine retro date | Load stub JSON files from S3/local filesystem | Simplified |
| **Phase 2 — Job Creation** | INSERT PayrunJob + PayrunJobEmployee rows; status management | No job record; batch processing with run ID from timestamp | Not implemented |
| **Phase 3 — Regulation Loading** | Multi-layer regulation hierarchy via PayrollLayers; derived wage types/collectors by level+priority; cluster sets; override resolution | Single regulation JAR loaded via URLClassLoader; WageTypes.json defines evaluation order | Simplified |
| **Phase 4 — Employee Loading** | Query Employee + EmployeeDivision; filter by identifiers or division; employee availability script | Read `index.txt` file listing stub JSON filenames | Simplified |
| **Phase 5 — Payrun Start Script** | Execute Payrun.StartExpression (C# script); can abort payrun | Not implemented | Not implemented |
| **Phase 6 — Employee Processing Loop** | Sequential per-employee processing with DB writes per employee | Parallel processing via ExecutorService (thread pool = available CPUs) | Implemented (improved) |
| **Phase 7 — Payrun End Script** | Execute Payrun.EndExpression (C# script) | Not implemented | Not implemented |
| **Phase 8 — Job Completion** | UPDATE PayrunJob with JobEnd, Message; or AbortJobAsync on error | Log completion; upload results to S3 | Simplified |

**Coverage: ~15%** — The POC replaces the 8-phase orchestration with a linear flow: load data → process in parallel → write results.

---

### 2. Per-Employee Calculation

| Feature | Production | POC | Status |
|---|---|---|---|
| **Case value resolution** | 4 CaseTypes (Global/National/Company/Employee); 4 CaseFieldTimeTypes (Timeless/Moment/Period/CalendarPeriod); aggregation (First/Last/Summary); pro-rating via PayrollCalculator; scope chain | Flat `Map<String, String>` from stub JSON; no time types, no aggregation, no pro-rating | Simplified |
| **Collector Start** | Per-collector Reset() + optional StartScript (C# script) | Collectors initialized as empty HashMap in FranceRulesContextImpl | Simplified |
| **Wage type loop** | Ordered evaluation; per-WT availability check; C# script execution (ValueExpression); result script (ResultExpression, reverse inheritance); restart support (up to MaxExecutionCount) | Ordered evaluation from WageTypes.json; reflection-based dispatch to Java static methods; 12-month YTD simulation wrapper | Partially implemented |
| **Wage type availability check** | WageTypeAvailableExpression (C# script per payrun) | Not implemented; all wage types always evaluated | Not implemented |
| **Wage type restart** | ExecutionRestartRequest re-runs entire WT loop | Not implemented | Not implemented |
| **Wage type result script** | ResultExpression runs in reverse inheritance order; can modify value | Not implemented | Not implemented |
| **Collector Apply** | Per-collector ApplyScript (C# script); if null, use WT value as-is; collector.AddValue(value) | Automatic: if WT entry has collectors, value added to collector sum | Simplified |
| **Collector End** | Per-collector EndScript; constraints: MinResult, MaxResult, Threshold, Negated | Not implemented; raw sum returned | Not implemented |
| **CollectMode** | Summary, Minimum, Maximum, Average, Range, Count | Summary only | Partial (1 of 6) |
| **Case values as payrun results** | Each case value → PayrunResult record | Not implemented | Not implemented |
| **Incremental result handling** | Compare new vs prior results; persist only deltas | Not implemented | Not implemented |
| **Result persistence** | INSERT PayrollResult + WageTypeResult + CollectorResult + PayrunResult + custom results (DB) | Write one JSON file per employee to local disk; sync to S3 | Simplified |

**Coverage: ~25%** — The core wage-type-evaluation → collector-accumulation loop is present but without scripting, constraints, incremental handling, or rich case value resolution.

---

### 3. Wage Type Rules (France Regulation)

| Feature | Production | POC | Status |
|---|---|---|---|
| **Wage type count** | 50+ (C# scripts in payroll-engine-regulation-France) | 60 original + 145 synthetic = 205 total (Java static methods) | Implemented (expanded) |
| **Rule logic** | C# scripts compiled via Roslyn; access to full API surface (case values, lookups, results, historical, runtime values, custom results, retro, period, employee) | Java static methods; access to FranceRulesContext (field values, slabs, range lookups, collectors) | Partially implemented |
| **Rule categories implemented** | Gross salary, SS ceiling, health insurance, old age, family, unemployment, CSG/CRDS, Alsace-Moselle, CSA, FNAL, AGS, vocational training, apprenticeship, social dialogue, construction, AGIRC-ARRCO, CEG, CET, APEC, death insurance, general reduction, standard deduction, withholding tax (metro + overseas), net salary, net social, SMIC check, DSN, emit collectors | All of the above (ported from C# to Java) + 145 synthetic rules in 5 families (social, pension, tax, benefit, deduction) | Implemented |
| **YTD simulation** | Real YTD via case value aggregation and multi-period processing | Synthetic 12-month loop per wage type (simulateYtd in evaluator) | Synthetic approximation |

**Coverage: ~40%** — Rule logic is faithfully ported for France. Missing: script API richness, historical results access, runtime values, custom results, retro scheduling from scripts.

---

### 4. Collector System

| Feature | Production | POC | Status |
|---|---|---|---|
| **Collector definitions** | DB-stored with CollectMode, Start/Apply/End expressions, constraints | Defined inline in WageTypes.json as string arrays per wage type entry | Simplified |
| **CollectMode** | Summary, Minimum, Maximum, Average, Range, Count | Summary only | Partial (1 of 6) |
| **Collector start/apply/end scripts** | C# scripts for each lifecycle phase | Not implemented; automatic summation | Not implemented |
| **Constraints (Min/Max/Threshold/Negated)** | Applied at CollectorEnd | Not implemented | Not implemented |
| **Collector count** | 9 (France regulation) | 8 original + 5 synthetic = 13 | Implemented (expanded) |

**Coverage: ~20%** — Basic accumulation works; script lifecycle and advanced modes are absent.

---

### 5. Lookup System

| Feature | Production | POC | Status |
|---|---|---|---|
| **Lookup storage** | DB-backed: Lookup + LookupValue tables; derived across regulation hierarchy | Static JSON files bundled in JAR (FranceRates.json, Constants2025.json, WithholdingTax*.json) | Simplified |
| **Key-value lookups** | `GetLookup<T>(name, key)` from DB | `FranceRatesLoader.get(key)` — 45+ rates from JSON | Implemented |
| **Range lookups** | `GetRangeLookup<T>(name, rangeValue, key)` from DB | `NavigableMap.floorEntry()` for withholding tax tables | Implemented |
| **Constant lookups** | Via lookup tables | `FranceLookups.getConstant("Constants2025", key)` | Implemented |
| **Interpolation** | Not in standard engine (scripts can implement) | Linear interpolation for Guadeloupe/Guiana withholding tables | Implemented (POC addition) |
| **Lookup resolution across regulation layers** | Derived lookups by level/priority | Single flat lookup per name | Simplified |

**Coverage: ~35%** — Functional lookup system but without DB backing, derivation, or dynamic updates.

---

### 6. Calendar and Period Calculation

| Feature | Production | POC | Status |
|---|---|---|---|
| **CycleTimeUnit** | Year, SemiYear, Quarter, Month, etc. | Not implemented (single monthly period) | Not implemented |
| **PeriodTimeUnit** | CalendarMonth, SemiMonth, BiWeek, Week, etc. (7+ types) | Hardcoded monthly from stub data (periodStart/periodEnd) | Not implemented |
| **PayrollCalculator** | Resolves cycles/periods, handles pro-rating, working days | Not implemented | Not implemented |
| **Pro-rating** | CalculateCasePeriodValue with calendar/working days | Not implemented | Not implemented |
| **WeekMode** | Week (calendar days) or WorkWeek (working days) | Not implemented | Not implemented |
| **Multi-period processing** | Retro recalculation across multiple periods | Not implemented | Not implemented |

**Coverage: ~10%** — Period dates are passed through from stub data but no calendar logic exists.

---

### 7. Scripting Engine

| Feature | Production | POC | Status |
|---|---|---|---|
| **Script language** | C# compiled via Roslyn | Java static methods (compile-time, no runtime scripting) | Replaced |
| **Script types** | 10+ (WageTypeValue, WageTypeResult, CollectorStart/Apply/End, PayrunStart/End, EmployeeStart/End, CaseAvailable/Build/Validate) | 1 equivalent: wage type value via Method.invoke() | Simplified |
| **Script compilation & caching** | Roslyn compilation; AssemblyCacheTimeout; assembly cache | Not applicable (static methods) | Not implemented |
| **Script API surface** | Case values, lookups, results, historical, runtime values, custom results, retro scheduling, period/cycle access, employee attributes, webhooks | FranceRulesContext: field values, slabs, range lookups, collectors | Simplified |
| **Script inheritance** | ResultExpression runs in reverse inheritance order | Not implemented | Not implemented |

**Coverage: ~15%** — Computation is performed but without runtime script flexibility, rich API access, or dynamic compilation.

---

### 8. Retro Pay Processing

| Feature | Production | POC | Status |
|---|---|---|---|
| **RetroPayMode** | None, and retro modes | Not implemented | Not implemented |
| **Retro scheduling** | Scripts call ScheduleRetroPayrun(scheduleDate) | Not implemented | Not implemented |
| **Multi-period recalculation** | Recursive child payrun jobs per retro period | Not implemented | Not implemented |
| **Incremental results** | Compare and persist only deltas for retro periods | Not implemented | Not implemented |
| **RetroTimeType** | Anytime, Cycle (clamp to cycle start) | Not implemented | Not implemented |
| **Reevaluation phase** | Current period re-evaluated after retro runs | Not implemented | Not implemented |

**Coverage: 0%** — Retro pay is entirely absent from the POC.

---

### 9. Regulation Inheritance

| Feature | Production | POC | Status |
|---|---|---|---|
| **PayrollLayers** | Multiple regulations at different levels and priorities | Single regulation | Not implemented |
| **Derived resolution** | Walk hierarchy; higher-level overrides lower-level | Not applicable | Not implemented |
| **OverrideType** | On CaseField, Collector, etc. | Not implemented | Not implemented |
| **ClusterSets** | Scope regulations by cluster (normal + retro variants) | Not implemented | Not implemented |
| **RegulationShare** | Share regulations across tenants | Not implemented | Not implemented |

**Coverage: ~5%** — Single-regulation loading exists but no hierarchy or derivation.

---

### 10. Domain Model

| Feature | Production | POC | Status |
|---|---|---|---|
| **Tenant** | Full entity with culture, calendar | tenantId string in stub data | Simplified |
| **User** | Identity, permissions | Not present | Not implemented |
| **Calendar** | Entity with CycleTimeUnit, PeriodTimeUnit, WeekMode | Not present | Not implemented |
| **Division** | Organizational grouping, employee assignment | Not present | Not implemented |
| **Employee** | Full entity with master data | employeeId string + caseValues map | Simplified |
| **Regulation** | Entity with scripts, cases, wage types, collectors, lookups | regulations.json config pointing to JAR | Simplified |
| **Case / CaseField** | 4 types, time types, aggregation, value types | Not present (flat key-value map) | Not implemented |
| **WageType** | Entity with ValueExpression, ResultExpression, collectors | WageTypeEntry record (number, name, methodName, collectors) | Simplified |
| **Collector** | Entity with CollectMode, scripts, constraints | String name in WageTypeEntry.collectors | Simplified |
| **Lookup / LookupValue** | DB entities with derivation | Static JSON files | Simplified |
| **Payroll / PayrollLayer** | Entity linking regulations at levels | Not present | Not implemented |
| **Payrun / PayrunParameter** | Entity with expressions, retro settings | Not present | Not implemented |
| **PayrunJob** | Execution instance with lifecycle | Run ID from timestamp | Simplified |
| **PayrollResult / WageTypeResult / CollectorResult** | DB entities with full schema | JSON file output (employeeId, wageTypes map) | Simplified |
| **Custom results** | WageTypeCustomResult, CollectorCustomResult | Not present | Not implemented |

**Coverage: ~10%** — Minimal data model sufficient for batch computation.

---

### 11. API and HTTP Layer

| Feature | Production | POC | Status |
|---|---|---|---|
| **REST API** | 80+ controllers (CRUD for all entities + payrun execution) | No API; batch file processing triggered by Fargate task | Not implemented |
| **Synchronous payrun execution** | Runs within HTTP request | Runs as standalone JAR in container | Replaced |
| **Swagger / OpenAPI** | ASP.NET host with Swagger | Not applicable | Not implemented |

**Coverage: 0%** — Intentionally omitted; POC uses batch processing.

---

### 12. Database and Persistence

| Feature | Production | POC | Status |
|---|---|---|---|
| **Database** | SQL Server | None (file-based) | Not implemented |
| **ORM / Data access** | Dapper, custom repositories | Jackson ObjectMapper for JSON | Replaced |
| **Stored procedures** | 30+ (deletes, derived lookups, result queries, consolidation) | Not applicable | Not implemented |
| **Bulk inserts** | For result persistence | Not applicable | Not implemented |
| **Result schema** | PayrollResult → WageTypeResult → WageTypeCustomResult; CollectorResult → CollectorCustomResult; PayrunResult | Flat JSON file per employee | Simplified |

**Coverage: ~5%** — S3-based I/O replaces DB persistence for the POC's benchmarking purpose.

---

### 13. Webhook Dispatch

| Feature | Production | POC | Status |
|---|---|---|---|
| **Webhook on status change** | PayrunJobProcess, PayrunJobFinish | Not implemented | Not implemented |
| **Webhook in scripts** | IWebhookDispatchService available to scripts | Not implemented | Not implemented |

**Coverage: 0%**

---

### 14. POC-Only Capabilities (Not in Production)

These features exist in the POC but have no equivalent in the production engine:

| Feature | Description |
|---|---|
| **Parallel employee processing** | ExecutorService with thread pool sized to available CPUs; ThreadLocal evaluators for thread safety |
| **Containerized deployment** | Docker image on AWS Fargate; CloudFormation template (template.yaml) |
| **S3 data pipeline** | Stub data download from S3; results upload to S3; configurable prefixes |
| **Comprehensive metrics** | metrics.json with timing (total, S3 download, engine, S3 upload), CPU (utilization, delta), memory (usage, peak, RSS), network (rx/tx), cost estimation, throughput |
| **CI/CD** | GitHub Actions pipelines (main-build.yaml, deploy.yaml, build-regulation-jar.yaml, run-fargate-task.yaml) |
| **Infrastructure as Code** | SAM/CloudFormation template for ECS cluster, task definition, IAM roles, S3 bucket |
| **CodeArtifact integration** | SNAPSHOT-based regulation JAR publishing and consumption |
| **Synthetic workload scaling** | 145 synthetic wage types + 12-month YTD simulation for controlled complexity tuning |
| **ECR image management** | Container images versioned and stored in ECR |

---

## Coverage Summary by Category

| Category | Weight | POC Coverage | Weighted |
|---|---|---|---|
| Core computation (wage types + collectors + lookups) | 35% | ~35% | 12.3% |
| Employee processing flow | 15% | ~25% | 3.8% |
| Case value resolution | 10% | ~10% | 1.0% |
| Regulation loading / inheritance | 10% | ~15% | 1.5% |
| Calendar / period calculation | 5% | ~10% | 0.5% |
| Retro pay processing | 10% | 0% | 0.0% |
| Scripting engine | 10% | ~15% | 1.5% |
| Persistence / DB | 5% | ~5% | 0.3% |
| **Weighted Total** | **100%** | | **~20%** |

---

## Scaling Comparison

| Dimension | Production Engine | POC Engine |
|---|---|---|
| **Concurrency** | Single-threaded, synchronous within HTTP request | Multi-threaded (thread pool = vCPU count) |
| **I/O model** | SQL Server per-employee reads/writes during processing | Batch S3 download → in-memory processing → batch S3 upload |
| **Compute density** | I/O-bound (DB round trips dominate) | CPU-bound (all data in memory during computation) |
| **Horizontal scaling** | Not supported (single process) | Fargate task with configurable CPU/memory (256–4096 CPU units) |
| **Employee throughput** | Limited by DB I/O (~seconds per employee with full persistence) | 660+ employees/sec at Medium complexity on 2 vCPU |
| **Regulation complexity** | ~50 wage types with C# scripts, full API surface | 205 wage types with Java methods + 12-month YTD simulation |
| **Cost model** | Always-on server + SQL Server license | Pay-per-run Fargate pricing (~$0.003 per 10K-employee run) |

---

## What Matters for Benchmarking

The POC's **~20% overall coverage** is by design. For its stated purpose — benchmarking computation performance on Fargate — the critical path is:

1. **Wage type evaluation loop** — Implemented with 205 wage types and configurable complexity
2. **Collector accumulation** — Implemented (Summary mode)
3. **Lookup resolution** — Implemented (rate maps + range slabs)
4. **Parallel processing** — Implemented (exceeds production capabilities)
5. **Metrics collection** — Implemented (exceeds production capabilities)

The omitted features (retro pay, scripting, DB persistence, API layer, calendar, regulation inheritance) add I/O overhead and orchestration complexity in production but do not represent the **pure computation** the POC is designed to stress-test.

### Complexity Profiles

| Profile | Wage Types | Ops/Employee | BigDecimal Ops/WT | Periods | Collectors |
|---|---|---|---|---|---|
| **Light** (baseline) | 60 | ~300–800 | 5–15 | 1 (monthly) | 8 |
| **Medium** (current) | 205 | ~38,000–40,000 | 185–195 (incl. YTD) | 12 (simulated) | 13 |
| **Production France** (estimated) | ~50 | ~300–800 + DB I/O | 5–15 + script overhead | 1 (monthly, retro adds more) | 9 |
