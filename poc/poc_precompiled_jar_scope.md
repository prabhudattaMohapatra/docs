# POC: Precompiled JAR — Scope

**POC:** Minimal payroll engine (Java) with regulation execution via **precompiled JAR** only. Full payrun from start through to **payrun results**.  
**Related:** [poc_precompiled_jar_objectives.md](poc_precompiled_jar_objectives.md) | [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md) | [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) (Option 2)

This document states **in scope** and **out of scope** only.

---

## In scope

| Area | Scope |
|------|--------|
| **Regulation execution** | **Precompiled JAR only.** Engine resolves regulation by (id, version), loads JAR from plugin directory, obtains `RegulationEvaluator`, and calls `evaluateWageType(wageTypeNumber, context)` (and collector lifecycle methods as needed). No external regulation service in this POC. |
| **Full payrun to results** | **Full payrun** from start through to **payrun results**: PayrunStart → (per employee) EmployeeStart → wage type evaluation + collector lifecycle (start/apply/end) → EmployeeEnd → PayrunEnd → **payrun results** (e.g. wage type results, collector results, aggregated or per-employee as needed for a minimal result set). Flow and data shape inspired by the .NET engine; implementation minimal but complete. |
| **Contract** | Java interfaces: `RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`, and whatever is needed for collector lifecycle (e.g. collector start/end). Enough to drive wage type values and collectors through the payrun. |
| **Sample regulation JAR** | One JAR (e.g. poc-regulation) implementing the contract: wage type logic and collector behaviour (start/end) with trivial or simplified logic for POC. |
| **Versioning / resolution** | Engine configuration or in-memory map: (regulation id, version) → JAR path. Load by version; optionally demonstrate rollback (switch version and reload). |
| **Caching** | Load JAR / instantiate evaluator once per (regulationId, version); reuse across the payrun. Document approach and classloader considerations. |
| **Persistence for payrun** | Persistence or in-memory storage sufficient to run the full payrun and produce **payrun results** (e.g. wage type results, collector results). May be in-memory for POC; schema/format should support “payrun results” as an outcome. |
| **Failure handling** | Missing JAR, wrong version, exception in evaluator; behaviour documented (fail payrun or step, retry, etc.). |
| **Tests** | Unit tests for contract, JAR loader, and payrun steps. Integration test: full payrun through to payrun results. Optional: benchmark. |
| **Documentation** | POC report: design choices, payrun flow, metrics, failure behaviour, recommendations. |

---

## Out of scope

| Area | Not in scope |
|------|------------------|
| **External regulation service** | Calling an external HTTP/gRPC regulation service; that is a separate POC or later phase. |
| **Regulation logic** | Real France/India/Swiss regulation logic; only simplified/stub logic in the sample JAR. |
| **Production persistence** | Production DB, full schema, or production-grade persistence; minimal storage for POC is enough. |
| **Security** | Production-grade security (e.g. signed JARs, sandboxing); note for production only. |
| **Multi-tenant** | Multi-tenant isolation beyond what is needed to run one payrun. |
| **Reports / reporting** | Report generation beyond what is needed to define “payrun results” (e.g. no PDF/Excel report generation). |
| **Migration** | Detailed comparison with .NET engine or migration tooling. |

---

## Repos and boundaries

- **Repos:** **payroll-engine-poc** (engine + regulation-api module), **payroll-regulations-poc** (sample regulation JAR). Regulation JAR loaded at runtime from engine plugin directory.
- **Success:** Engine runs a **full payrun** (PayrunStart → employees → wage types + collectors → PayrunEnd) using a **precompiled JAR** for regulation logic and produces **payrun results**, with documented flow and behaviour.
