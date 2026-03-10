# POC: External Regulation Service Option (Java)

**Status:** Draft  
**Related RFC:** [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) — Option 3: External Regulation Service  
**Language / stack:** Java (engine as client; regulation service can be Java or other)

---

## 1. Purpose and objectives

Validate the **external regulation service** execution model: the engine does **not** run regulation logic; it calls an external API (HTTP or gRPC) with context (tenant, regulation, employee, period, wage type id, case values) and the **regulation service** evaluates and returns the result (e.g. wage type value). Engine is pure orchestration and persistence.

**Objectives:**

- Prove that the engine can **invoke** a regulation service over HTTP (or gRPC) and obtain wage type results with a well-defined request/response contract.
- Validate **latency and throughput** for per-wage-type and (optionally) batched calls; document impact on payrun duration.
- Exercise **failure handling**: timeouts, 4xx/5xx, retries, circuit breaker (optional for POC).
- Identify **implementation choices** (client library, auth, versioning header, batching schema).
- Document **operational aspects**: two services, tracing/correlation, and deployment.

---

## 2. POC scope

### In scope

- **Engine (Java):** Minimal “payrun” that: (1) resolves regulation to a **service endpoint** (URL from config or in-memory “DB”), (2) for each of N wage types, builds a request (tenantId, regulationId, employeeId, period, wageTypeNumber, caseValues), (3) calls **POST /evaluate/wage-type** (or equivalent), (4) parses response (value or error), (5) collects results into WageTypeResult list. Optional: **batch** request (e.g. one request per employee with all wage types) and parse batch response.
- **Contract:** Define request/response schema (JSON). Example request: `{ "tenantId", "regulationId", "employeeId", "periodStart", "periodEnd", "wageTypeNumber", "caseValues", "lookups" }`. Response: `{ "value": 1234.56 }` or `{ "error": "code", "message" }`. Document schema and version (e.g. Accept header or URL path /v1/).
- **Regulation service (stub):** A small HTTP service (Java Spring Boot, Micronaut, or simple servlet) that implements the contract. For POC, logic can be trivial (e.g. return fixed value or `caseValues.get("BaseSalary") * 0.2`). No need for full France/India logic. Service must return valid JSON and handle at least one wage type number; optional: return 500 or timeout for failure-mode tests.
- **Engine HTTP client:** Use a Java HTTP client (e.g. Java 11+ HttpClient, or OkHttp, or RestTemplate) with configurable base URL, timeouts, and optional retries. Optional: circuit breaker (Resilience4j) for POC; at least document recommended production behaviour.
- **Configuration:** Engine gets regulation’s **serviceEndpoint** (and optionally apiKey or version) from config or in-memory map. No real DB required.
- **Tests:** Unit tests for client (with mock server or WireMock), and an integration test: engine + real stub service run; engine executes “payrun” and asserts results. Optional: latency test (e.g. 100 or 1000 wage type calls) to compare per-call vs batch.
- **Documentation:** POC report covering design choices, metrics (latency, throughput), failure handling, and recommendations (proceed / refine / abandon).

### Out of scope

- Full payrun lifecycle (PayrunStart, EmployeeStart/End, multiple collectors); only “evaluate wage type” path.
- Real regulation logic in the service; stub/simplified only.
- Production auth (OAuth, API key validation); optional simple API key in header for POC.
- gRPC (unless chosen instead of HTTP for POC); HTTP + JSON is enough to validate the model.
- Production deployment (Kubernetes, service mesh); local or single-host run is sufficient.
- Comparison with .NET engine or migration tooling.

---

## 3. POC steps

### Phase 1: Contract and stub service (Days 1–2)

1. **Define API contract**
   - Request: POST body with tenantId, regulationId, employeeId, periodStart, periodEnd, wageTypeNumber, caseValues (map), optional lookups. Use JSON; document field types and required vs optional.
   - Response: success `{ "value": <decimal> }`; error `{ "errorCode": "...", "message": "..." }`. HTTP status: 200 for success, 4xx/5xx for errors.
   - Optional: version in path (e.g. `/v1/evaluate/wage-type`) or header. Document in README or OpenAPI snippet.

2. **Implement stub regulation service**
   - Create a small Java web app (e.g. Spring Boot or Micronaut) with one endpoint: POST /evaluate/wage-type (or /v1/evaluate/wage-type).
   - Implement trivial logic: e.g. for wageTypeNumber 1001 return 100.00; for 1002 return caseValues["BaseSalary"] * 0.2. Return JSON per contract.
   - Optional: add a “chaos” query param or header to simulate 500 or delay for failure tests.
   - Run locally; verify with curl or Postman.

3. **Verify**  
   - Call service with sample request; assert response shape and value.

### Phase 2: Engine client and minimal payrun (Days 3–4)

4. **HTTP client in engine**
   - Implement **RegulationServiceClient** (or equivalent): method `evaluateWageType(request) → WageTypeResult or exception`.
   - Config: base URL (from regulation’s serviceEndpoint), connect/read timeouts (e.g. 5s / 10s). Use Java HttpClient or OkHttp.
   - Parse response; map to WageTypeResult; on 4xx/5xx or timeout, throw or return error result (document chosen behaviour).

5. **Optional retries**
   - Retry on 5xx or timeout (e.g. max 2 retries, backoff 1s). Document and test.

6. **Minimal payrun**
   - “Payrun” that: (1) gets regulation and serviceEndpoint from config, (2) for each of N wage types (stub list), builds request with stub context (employee, period, case values), (3) calls client.evaluateWageType for each, (4) collects results. Measure total time for N = 10, 100, 500 (or 1000).
   - Optional: implement batch endpoint in stub (e.g. POST /evaluate/employee with array of wage type numbers); engine sends one request per “employee” and parses map of results. Compare total time vs per-call.

7. **Tests**
   - Unit test: client with WireMock (or mock server) returning 200 and 500; assert success and failure handling.
   - Integration test: start stub service, run engine payrun for N wage types, assert all results and no crash.

### Phase 3: Failure modes and evaluation (Days 5–6)

8. **Failure scenarios**
   - Service down: engine gets connection refused or timeout. Document: fail payrun, or fail wage type with error, or retry/queue (POC can just fail fast).
   - Service returns 500: test retry and then fail or return error result.
   - Service returns 400: invalid request; document that engine should not retry and should log request/response for debugging.
   - Optional: circuit breaker (e.g. Resilience4j) — after K failures, stop calling for T seconds; then half-open. Document if implemented.

9. **Tracing / correlation**
   - Add a request-id (or trace-id) header from engine to service; log it in both. Demonstrate correlation for debugging. No need for full distributed tracing stack.

10. **Documentation and report**
    - POC report: API contract, client design, metrics (latency per call, batch vs single, failure behaviour), operational notes (two services, config, tracing), and recommendation (proceed with external service / refine / abandon).
    - List open questions: auth in production, SLA, versioning, batching strategy.

---

## 4. Evaluation (during and after POC)

- **Feasibility:** Can the engine reliably call an external service and get wage type results? Any blocking issues (serialisation, timeouts, connection pooling)?
- **Performance:** Latency per single call; total time for 100/1000 wage types; impact of batching (if implemented). Compare with “in-process” expectation (precompiled JAR POC).
- **Reliability:** Behaviour under service failure, timeout, 5xx. Is “engine + service” acceptable for payrun SLA?
- **Operability:** Two deployables, two configs, tracing. How clear is debugging when something fails?
- **Risks:** Network dependency, version skew between engine and service API, auth and multi-tenant isolation in production.

Findings will be fed into the **Evaluation criteria and decision making** document for comparison with the Precompiled JAR POC.

---

## 5. Deliverables

| Deliverable | Description |
|-------------|-------------|
| API contract | Request/response schema (JSON), HTTP method and path, error format. Optional: OpenAPI YAML. |
| Stub regulation service | Small Java HTTP service with POST /evaluate/wage-type (and optional batch); trivial logic. |
| Engine HTTP client | RegulationServiceClient with evaluateWageType, timeouts, optional retries. |
| Minimal payrun | Code path: resolve endpoint → for each wage type call service → collect results. Optional batch path. |
| Tests | Unit tests (mock server), integration test (engine + stub service), optional latency comparison. |
| POC report | Document with contract, metrics, failure handling, tracing, recommendations, and open questions. |

---

## 6. Timeline and effort (indicative)

| Phase | Duration | Focus |
|-------|----------|--------|
| Phase 1 | 2 days | API contract, stub service, manual verification. |
| Phase 2 | 2 days | Engine client, minimal payrun, unit and integration tests. |
| Phase 3 | 1–2 days | Failure modes, optional circuit breaker, tracing, report. |
| **Total** | **~6 working days** | Single developer; adjust if batch or resilience is deeper. |

---

## 7. Success criteria (go/no-go for “proceed to full design”)

- Engine can call regulation service with a defined contract and receive wage type values.
- At least one full “minimal payrun” path runs (N wage type calls to service → results) without fundamental blockers.
- Latency and throughput are documented; impact of network calls on payrun duration is clear.
- At least two failure modes (e.g. timeout, 500) are tested and documented with recommended behaviour.
- POC report is written and shared for use in post-POC evaluation and decision.

---

## 8. References

- RFC: [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) — Option 3 (External Regulation Service).
- Diagram: [diagram/04_option3_engine_regulation_service.md](diagram/04_option3_engine_regulation_service.md).
