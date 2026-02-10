# Execution Model: External Regulation Service

Regulation logic runs in a **separate service** (REST API, gRPC, or serverless). The payroll backend does **not** store or execute scripts or binary; it only stores a **reference** (endpoint or ARN) and **calls the service** with context, then uses the returned results.

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Payroll Backend (PayrunProcessor)                              │
│  - Owns: tenants, employees, payruns, results, case values       │
│  - For regulation logic: only calls out                         │
└───────────────────────────┬─────────────────────────────────────┘
                             │ HTTP / gRPC / event
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Regulation Service (external)                                   │
│  - Owns: country regulation logic (France, India, …)             │
│  - Receives: context (tenant, regulation, employee, period, …)   │
│  - Returns: wage type values, collector results, flags, etc.     │
└─────────────────────────────────────────────────────────────────┘
```

- **Backend**: Orchestrates payrun (employees, period, sequence). For each “regulation decision” (wage type value, collector start/end, payrun start/end, etc.) it **calls the regulation service** with a request and applies the response (e.g. store wage type result).
- **Regulation service**: Stateless (or uses its own DB/cache). Implements the contract; can be one service per country or one multi-tenant API with `regulationId` in the request.

**Stored in payroll DB**: Regulation row has e.g. **RegulationId**, **Name**, **ServiceEndpoint** (URL or ARN). No Script table for this regulation; no Binary.

---

## 2. When the Backend Calls the Service

Today the payrun engine runs script hooks at specific points. With an external service, each of these becomes a **call** to the service (or a batch). Typical operations:

| Payrun phase | Current (scripts) | External service |
|--------------|-------------------|-------------------|
| Payrun start | PayrunStart(context) | POST `/evaluate/payrun-start` with context |
| Per employee start | EmployeeStart(caseValues, context) | POST `/evaluate/employee-start` |
| Wage type value | WageTypeValue(wageType, context) → decimal | POST `/evaluate/wage-type` → `{ value, ... }` |
| Collector start | CollectorStart(collector, context) → retro jobs, etc. | POST `/evaluate/collector-start` |
| Collector end | CollectorEnd(collector, context) | POST `/evaluate/collector-end` |
| Wage type available | IsWageTypeAvailable(wageType, context) → bool | POST `/evaluate/wage-type-available` |
| Case available | CaseAvailable(case, context) → bool | POST `/evaluate/case-available` |
| Employee end | EmployeeEnd(context) | POST `/evaluate/employee-end` |
| Payrun end | PayrunEnd(context) | POST `/evaluate/payrun-end` |

You can **coarsen** the contract (e.g. one “evaluate employee” call that returns all wage type + collector results for that employee) to reduce round-trips; see §4.

---

## 3. Contract (What Backend Sends, What Service Returns)

### 3.1 Context (Common in Every Request)

Backend sends a **context** object the service needs to evaluate rules. Typical fields:

| Field | Description |
|-------|-------------|
| **tenantId** | Tenant identifier |
| **regulationId** / **regulationName** | Which regulation (e.g. France) |
| **payrunId**, **payrunJobId** | Payrun and job |
| **employeeId** | Employee (for per-employee calls) |
| **divisionId** | Division |
| **evaluationPeriod** | Period (start/end) |
| **evaluationDate** | Single date when relevant |
| **culture** | e.g. fr-FR |
| **caseValues** | Global, national, company, employee case values (names, values, slots) |
| **existingResults** | Already computed wage type / collector results in this payrun (for collector end, etc.) |
| **attributes** | Wage type / collector / case attributes as needed |

(Exact shape can be JSON; avoid sending huge blobs; use IDs + minimal payload where possible.)

### 3.2 Wage Type Value

- **Request**: e.g. `POST /evaluate/wage-type`  
  Body: context + **wageTypeNumber**, **wageTypeName**, **wageTypeAttributes**, **collectorResultsSoFar** (if needed for formula).
- **Response**: e.g. `{ "value": 1234.56, "tags": ["tag1"], "attributes": { ... } }`  
  Backend creates a **WageTypeResult** from this.

### 3.3 Collector Start / End

- **Start**: Request = context + **collectorName**, **collectorAttributes**.  
  Response = e.g. `{ "retroJobs": [ { "scheduleDate": "2025-01-01", ... } ], "customResults": [ ... ] }`  
  Backend applies retro jobs and custom results.
- **End**: Request = context + **collectorName**, **collectorAttributes**, **accumulatedResults**.  
  Response = e.g. `{ "customResults": [ ... ] }`.

### 3.4 Payrun / Employee Lifecycle

- **Payrun start**: Request = context. Response = e.g. `{ "success": true }` or `{ "success": false, "message": "..." }`. Backend aborts payrun if not success.
- **Employee start**: Request = context + **caseValues**. Response = `{ "success": true }` or false (skip employee).
- **Employee end** / **Payrun end**: Request = context. Response = `{ "success": true }` (and optional side effects if you ever need them).

### 3.5 Availability Checks

- **Wage type available**: Request = context + wage type. Response = `{ "available": true }` or false.
- **Case available**: Request = context + case name. Response = `{ "available": true }` or false.

All of the above can be **one REST API** with different paths or a **single endpoint** with an `operation` field (e.g. `wage-type`, `collector-start`, `payrun-start`). Same idea for gRPC: one service with multiple RPCs.

---

## 4. Granularity: Per-Call vs Batch

- **Per-call**: One HTTP request per wage type, per collector start/end, etc. Simple contract, many round-trips (latency, cost).
- **Batch per employee**: One request per employee: “evaluate this employee” with full context; response = all wage type values + collector results + flags for that employee. Fewer calls, larger payload/response.
- **Batch per payrun**: One request per payrun: “evaluate whole payrun”; response = all results for all employees. Fewest calls; service must implement full payrun loop (and have all case values, etc.); backend becomes a “submit + poll” or “webhook when done” consumer.

**Recommendation**: Start with **per-employee batch** (one “evaluate employee” call per employee). Backend sends employee + period + case values + regulation id; service returns list of wage type results and collector results. Balances latency and implementation complexity.

---

## 5. Discovery: How Backend Knows Which Endpoint to Call

- **Regulation table**: Add **ServiceEndpoint** (URL) or **ServiceArn** (e.g. Lambda ARN). Optional: **ServiceAuth** (e.g. “use tenant API key”), **ServiceVersion**.
- **Resolve at payrun start**: PayrunProcessor loads regulation(s) for the payrun’s payroll; reads **ServiceEndpoint** (or ARN); uses that for all calls for that regulation. No script, no binary.

If you use **one service per country**: endpoint can be per regulation (e.g. `https://regulation-fr.internal/evaluate`). If you use **one multi-tenant service**: same base URL, regulation id in body or path (e.g. `POST /evaluate/FR/wage-type`).

---

## 6. Protocol Options

| Protocol | Typical use | Pros | Cons |
|----------|-------------|------|------|
| **REST (JSON)** | API Gateway + Lambda, or container | Simple, widely used, easy to debug | Many small requests if not batched |
| **gRPC** | Container / K8s service | Compact, streaming, strong typing | More setup, less “curl-friendly” |
| **Async (queue + worker)** | SQS + Lambda, or queue + worker | Decoupled, retries, scale | Payrun becomes async (submit job, poll or webhook for completion) |

For **synchronous** payrun (backend waits for result and then continues), **REST** or **gRPC** with a **per-employee** (or per-operation) call is the simplest. **Async** is useful if you want “submit payrun job → regulation service processes in background → callback or poll for results.”

---

## 7. Deployment Shapes for the Regulation Service

- **Lambda (or similar FaaS)**: One function per operation (e.g. `EvaluateWageType`) or one function with operation in the body. Backend calls via API Gateway or Lambda URL. Good for low/medium volume; cold start and timeout limits.
- **Container (e.g. ECS, K8s)**: One service (or one per country) exposing REST/gRPC. Backend calls via internal ALB or service mesh. Good for high volume and full control.
- **Single API + routing**: One “regulation API” that routes by `regulationId` (or path) to the right logic (France, India, …). Backend always calls the same host; service owns routing and versioning.

---

## 8. Versioning and Contract Evolution

- **API version in URL or header**: e.g. `/v1/evaluate/wage-type` or `Accept: application/vnd.regulation.v1+json`. Backend stores **ServiceEndpoint** with version (or service handles multiple versions).
- **Regulation version**: Optional **RegulationVersion** or **ContractVersion** in DB; backend sends it in the request so the service can run the right logic.
- **Backward compatibility**: New fields in request/response should be optional so old backends or old services still work.

---

## 9. Error Handling and Timeouts

- **Timeouts**: Backend should set a timeout (e.g. 30s per request). If the service does not respond, backend treats it as failure (retry or fail the payrun job).
- **4xx**: Validation error (e.g. unknown regulation). Backend logs and fails the job or skips that regulation.
- **5xx / network error**: Retry with backoff (e.g. 1–3 retries); then fail the job.
- **Response body error**: e.g. `{ "error": "code", "message": "..." }`. Backend maps to payrun error (e.g. add to job issues, abort employee or payrun).

---

## 10. Security and Auth

- **Network**: Prefer internal only (VPC, private API, no public internet). Backend and regulation service in same VPC or connected via private link.
- **Auth**: Service-to-service auth (e.g. IAM for Lambda/API Gateway, or shared secret in header, or JWT). Backend sends auth with every request; service validates before evaluating.
- **Tenant isolation**: Backend sends **tenantId** (and optionally **divisionId**); service must enforce that results are scoped to that tenant and must not leak data across tenants.
- **Input size**: Limit request size (e.g. cap case values or existing results) to avoid abuse and cost.

---

## 11. How Backend Changes (Pluggable “Regulation Provider”)

- **Provider abstraction**: Introduce e.g. **IRegulationEvaluator** (or **IRegulationServiceClient**) with methods such as **EvaluateWageTypeAsync(context, wageType) → WageTypeResult**, **EvaluateCollectorStartAsync(...)**, **EvaluateEmployeeAsync(...)** (if batch), etc.
- **Two implementations**:
  - **Current**: “Script” provider — uses existing ScriptCompiler / FunctionHost / in-process scripts (today’s behaviour).
  - **External**: “Service” provider — builds request from context, calls regulation service (REST/gRPC), maps response to **WageTypeResult** / collector result / boolean, returns to PayrunProcessor.
- **Choice per regulation**: When loading regulation, if **ServiceEndpoint** (or **ServiceArn**) is set, use **external** provider; otherwise use **script** provider. So you can migrate regulation-by-regulation to the external service.
- **No script/binary in DB** for regulations that use the external provider; only metadata and endpoint/ARN.

---

## 12. Pros and Cons (Recap)

| Pros | Cons |
|------|------|
| No scripts or binary in payroll DB | Extra latency per call (mitigate with batching) |
| Regulation logic in any language (Python, Go, etc.) | Service availability and SLA matter for payrun success |
| Deploy/update regulation without touching backend | Versioning and contract discipline required |
| Clear separation: backend = orchestration + data; service = rules | Auth, tenant isolation, and input validation in the service |
| Can scale regulation service independently | Need retries, timeouts, and clear error handling |

---

## 13. Minimal “Evaluate Employee” Contract (Example)

Single call per employee, service returns all wage type and collector results for that employee.

**Request** (e.g. `POST /v1/evaluate/employee`):

```json
{
  "tenantId": 1,
  "regulationId": 10,
  "payrunJobId": 100,
  "employeeId": 50,
  "divisionId": 2,
  "evaluationPeriod": { "start": "2025-01-01", "end": "2025-01-31" },
  "evaluationDate": "2025-01-15",
  "culture": "fr-FR",
  "caseValues": {
    "global": [],
    "national": [],
    "company": [],
    "employee": [
      { "caseName": "BaseSalary", "value": 3000, "slot": "2025-01-01" }
    ]
  },
  "wageTypes": [
    { "number": 100, "name": "Gross", "attributes": {} },
    { "number": 200, "name": "SocialSecurity", "attributes": {} }
  ],
  "collectors": [
    { "name": "Gross", "attributes": {} }
  ]
}
```

**Response**:

```json
{
  "employeeStartSuccess": true,
  "wageTypeResults": [
    { "wageTypeNumber": 100, "value": 3000, "tags": [], "attributes": {} },
    { "wageTypeNumber": 200, "value": -450, "tags": [], "attributes": {} }
  ],
  "collectorResults": [
    { "collectorName": "Gross", "value": 3000, "customResults": [] }
  ],
  "retroJobs": [],
  "employeeEndSuccess": true
}
```

Backend then creates **WageTypeResult** and **CollectorResult** rows from this response and continues with the next employee. No script, no binary in DB—only the **ServiceEndpoint** (or ARN) on the regulation row and this contract.
