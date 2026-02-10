# POC Evaluation Criteria and Decision Making (Runtime Rules Options)

**Status:** Draft  
**Related RFC:** [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md)  
**POC docs:** [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md), [poc_runtime_rules_external_service_java.md](poc_runtime_rules_external_service_java.md)

This document defines how the two POCs (Precompiled JAR and External Regulation Service) will be evaluated and how the final decision on the runtime rules execution model will be made after the POCs complete.

---

## 1. Context

- **Options under evaluation:** Option 2 (Precompiled JAR) and Option 3 (External Regulation Service), as defined in the RFC.
- **POCs:** One Java POC per option; each produces a report with approach, metrics, failure handling, and recommendations.
- **Goal:** Compare both options on a common set of criteria and decide: **(A)** adopt Precompiled JAR, **(B)** adopt External Service, **(C)** adopt Hybrid (use both: e.g. expression + JAR for most, service for specific cases), or **(D)** run a follow-up (e.g. deeper POC or spike) before deciding.

---

## 2. Evaluation criteria

The following criteria will be used to compare the two options. Each criterion should be assessed from **POC evidence** (measurements, code, report) and, where relevant, **extrapolation to production**.

### 2.1 Feasibility and implementation complexity

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **Technical feasibility** | Can we implement the full flow (load/invoke or HTTP call) without fundamental blockers? | POC completed end-to-end; no showstopper bugs or design flaws. |
| **Implementation complexity** | Relative complexity of engine code: loader + cache + interface vs HTTP client + retries + error handling. | Compare lines of code, number of components, and POC report “complexity” section. |
| **Contract stability** | How stable and clear is the contract (Java interface vs HTTP API)? Versioning and backward compatibility. | Ease of adding a new regulation or changing contract; POC report. |

**Weight (suggested):** Medium–high. Both must be feasible; complexity affects long-term maintenance.

---

### 2.2 Performance

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **First-use / cold start** | Time to “ready” (JAR load + evaluator creation vs first HTTP call). | POC metrics: load time (JAR) vs first request latency (service). |
| **Per–wage-type latency** | Time for one wage type evaluation (in-process call vs HTTP round-trip). | POC: median/p99 per call; document batch size if applicable. |
| **Throughput** | Evaluations per second for a single “payrun” (e.g. 1000 wage types). | POC: total time for N evaluations; extrapolate to “payrun duration” for typical N. |
| **Predictability** | Variance in latency (network jitter vs in-process). | POC report and optional percentile metrics. |

**Weight (suggested):** High. Payrun duration and SLA may be driven by regulation execution.

---

### 2.3 Reliability and failure handling

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **Failure modes** | Missing JAR / wrong version vs service down / timeout / 5xx. | POC: list of failure modes tested; recommended behaviour (fail payrun, fail wage type, retry). |
| **Recoverability** | Can we retry or rollback? (JAR: switch version; Service: retry, circuit breaker.) | POC report and code. |
| **Observability** | How easy to debug? (Stack trace in engine vs correlation id across engine + service.) | POC: logging, request-id/trace-id; report. |

**Weight (suggested):** High. Payrun must behave predictably under failure.

---

### 2.4 Operations and deployment

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **Deploy model** | One service (engine + JAR on classpath or plugin dir) vs two services (engine + regulation service). | RFC and POC report; operational impact. |
| **Configuration** | Regulation id + version → JAR path vs regulation id → service endpoint (+ auth). | Complexity of config and secrets (e.g. API key). |
| **Version rollback** | Rollback regulation logic: point to previous JAR version vs previous service version/deploy. | POC report and design. |

**Weight (suggested):** Medium–high. Ops burden and incident response depend on this.

---

### 2.5 Security and auditability

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **Security** | No code in DB (both options). JAR: load from trusted source only. Service: auth and network boundary. | RFC; POC notes on production requirements (signed JAR, TLS, auth). |
| **Auditability** | “What logic ran for payrun X?” — JAR version in config/DB vs service version and API contract. | Traceability of regulation version to artifact or deployment. |

**Weight (suggested):** Medium. Both options are acceptable from RFC; differentiate only if one has clear advantage.

---

### 2.6 Fit for organisation and roadmap

| Criterion | Description | How to assess |
|-----------|-------------|---------------|
| **Ownership** | Is regulation logic owned by same team as engine (JAR in same repo/org) or by another team/product (service)? | Organisation context; RFC “when to use”. |
| **Multi-regulation / multi-country** | Adding a new country: new JAR + deploy vs new service or new endpoint. | POC report; scaling to 3–5 regulations. |
| **Future flexibility** | Hybrid possibility: could we add expression DSL later and keep JAR or service for complex cases? | RFC Option 5; compatibility with future hybrid. |

**Weight (suggested):** Medium. May override technical criteria if organisation strongly prefers one model.

---

## 3. Evaluation process

### 3.1 Inputs

- **Precompiled JAR POC report** (from [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md) deliverables).
- **External Service POC report** (from [poc_runtime_rules_external_service_java.md](poc_runtime_rules_external_service_java.md) deliverables).
- **RFC** and **comparison table** (runtime rules options).
- **Organisation context:** Who owns regulation logic? Preferred deploy model? Any hard constraints (e.g. “no external service for payrun”).

### 3.2 Evaluation table (template)

Fill this table after both POCs are done. Use a simple scale (e.g. 1–5 or Low/Medium/High) or narrative; add a short justification per cell.

| Criterion | Precompiled JAR (POC result) | External Service (POC result) | Notes |
|-----------|------------------------------|--------------------------------|------|
| Feasibility / complexity | | | |
| Performance (latency, throughput) | | | |
| Reliability / failure handling | | | |
| Operations / deployment | | | |
| Security / auditability | | | |
| Fit for org / roadmap | | | |
| **Overall (summary)** | | | |

Optional: add a **weighted score** (e.g. 1–5 per criterion × weight) and sum to get a numeric comparison. Document weights in this doc so the decision is reproducible.

### 3.3 Decision options

- **A — Precompiled JAR:** Adopt Option 2 as the primary (or only) execution model for regulation logic. Proceed to full design and implementation.
- **B — External Service:** Adopt Option 3 as the primary model. Proceed to full design (engine as client; regulation service as separate product/service).
- **C — Hybrid:** Adopt a hybrid (e.g. Option 5): expression DSL for simple wage types, and either JAR or external service for complex regulations. POC results inform which “complex” path to implement first.
- **D — Defer / follow-up:** Need more evidence (e.g. load test, security review, or spike on a specific risk) before committing. Document follow-up actions and decision date.

### 3.4 Who decides

- **Decision owner:** [To be filled: e.g. Tech Lead, Architecture Board, or Product/Engineering lead.]
- **Stakeholders to consult:** [To be filled: e.g. Engine team, Regulation/Country team, Ops, Security.]
- **Decision record:** The outcome (A/B/C/D) and rationale will be recorded in this document (or a linked ADR) and communicated to the team.

---

## 4. Timeline

| Milestone | Description |
|-----------|-------------|
| T0 | Both POCs completed; POC reports submitted. |
| T0 + 1 week | Evaluation table filled; review meeting with stakeholders. |
| T0 + 2 weeks | Decision (A/B/C/D) documented and communicated. |
| After decision | Backlog updated: full design and implementation for chosen option(s); or follow-up tasks if D. |

Adjust dates to match project calendar.

---

## 5. Document history

| Date | Change | Author |
|------|--------|--------|
| (Draft) | Initial version: criteria, process, decision options. | |

---

## 6. References

- RFC: [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md)
- POC Precompiled JAR: [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md)
- POC External Service: [poc_runtime_rules_external_service_java.md](poc_runtime_rules_external_service_java.md)
