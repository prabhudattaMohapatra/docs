# POC: Precompiled JAR and External Regulation Service — Scope

**POC:** Regulation execution via (1) **precompiled JAR** and (2) **external regulation service** (Java). The engine supports both paths and obtains wage type results from either.  
**Related:** [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md) | [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) (Options 2 & 3)

This document states **in scope** and **out of scope** only. For steps, deliverables, and implementation details, see the main POC doc.

---

## Execution paths (both in scope)

The engine can obtain wage type results in two ways:

1. **JAR path:** Resolve regulation by (id, version) → load JAR from plugin directory → instantiate `RegulationEvaluator` → call `evaluateWageType(wageTypeNumber, context)` in process.
2. **External service path:** Resolve regulation to a **service endpoint** → call external regulation service (HTTP/gRPC) with context → receive wage type value in response.

Engine configuration (or in-memory registry) determines which path is used per regulation (e.g. by regulation id or by “regulation type” JAR vs service).

---

## In scope

| Area | Scope |
|------|--------|
| **Engine** | Minimal “payrun” that (1) **JAR path:** resolves regulation by id/version, loads a JAR from a plugin directory, obtains `RegulationEvaluator`, and calls `evaluateWageType(wageTypeNumber, context)`; (2) **Service path:** resolves regulation to a service endpoint, calls the external regulation service (HTTP or gRPC) with context (e.g. tenant, employee, period, wage type number, case values), and gets the wage type value from the response. Engine supports both paths and branches per regulation (or per config). |
| **Contract (JAR)** | Java interfaces: `RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`. Enough to return a wage type value; optionally one collector lifecycle method. No need to mirror full .NET semantics. |
| **Contract (external service)** | Request/response schema for the regulation service (e.g. POST body: tenantId, regulationId, employeeId, period, wageTypeNumber, caseValues; response: value or error). Versioned API (e.g. path or header). Engine HTTP/gRPC client with timeouts and optional retries. |
| **Sample regulation JAR** | One JAR (e.g. poc-regulation) that implements the contract: a handful of wage type numbers with trivial or simplified logic. One collector start/end if useful. |
| **Stub regulation service** | A minimal HTTP (or gRPC) service that implements the external-service contract: accepts evaluate request, returns wage type value (trivial logic for POC). Used to validate the service path end-to-end. |
| **Versioning / resolution** | Engine configuration or in-memory map: (regulation id, version) → JAR path **or** service endpoint. Enables “load by version” for JAR and “call by endpoint” for service. Optionally rollback (switch version or endpoint). |
| **Caching** | **JAR path:** Load JAR / instantiate evaluator once per (regulationId, version); reuse across payrun invocations. **Service path:** No evaluator cache; optional HTTP client/connection pooling. Document approach and classloader considerations for JAR. |
| **Failure handling** | **JAR path:** Missing JAR, wrong version, exception in evaluator — document behaviour. **Service path:** Timeout, 4xx/5xx, service unavailable — retries and/or fail payrun or wage type; document behaviour. |
| **Tests** | Unit tests for contract, JAR loader, and minimal payrun (JAR path). Unit and/or integration tests for engine calling stub regulation service (service path). Optional: benchmark for both paths (latency, throughput). |
| **Documentation** | POC report: design choices for both paths, metrics (JAR load time, service latency), failure behaviour, recommendations (proceed / refine / abandon). |

---

## Out of scope

| Area | Not in scope |
|------|------------------|
| **Payrun lifecycle** | Full payrun lifecycle (PayrunStart, EmployeeStart/End, multiple collectors, report generation). |
| **Regulation logic** | Real France/India/Swiss regulation logic; only simplified/stub logic. |
| **Persistence** | Real DB for Regulation/WageType; in-memory or minimal stub is enough. |
| **Security** | Production-grade security (e.g. signed JARs, sandboxing); note requirements for production only. |
| **Multi-tenant** | Multi-tenant isolation beyond “one regulation version per resolution”. |
| **Migration** | Comparison with .NET engine behaviour or migration tooling. |

---

## Repos and boundaries

- **Repos:** **payroll-engine-poc** (engine + regulation-api module; engine implements both JAR loader and regulation service client), **payroll-regulations-poc** (sample regulation JAR). The **stub regulation service** may live in a separate module within the engine repo or in regulations repo for POC; engine loads JAR from plugin directory and calls the service by configured endpoint.
- **Success:** (1) **JAR path:** Engine loads a regulation JAR by (regulationId, version), runs a minimal payrun that evaluates N wage types and produces results. (2) **Service path:** Engine calls the external regulation service with context, receives wage type values, and produces the same result shape. Both paths documented with metrics and failure behaviour.
