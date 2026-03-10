# OpenAPI: Scaffolder Exemplar vs External (G-P Public) API

**Short answer: No.** The OpenAPI in the scaffolder/exemplar repo is **not** the same as the OpenAPI exposed to external users in G-P. They describe different surfaces and live in different places.

---

## 1. What the scaffolder exemplar contains

**Repo:** [gp-nova/serverless-api-ts-exemplar](https://github.com/gp-nova/serverless-api-ts-exemplar)  
**Files:** `docs/api/openapi-spec.yaml` and `docs/api/openapi-spec.yaml.tmpl`

- The exemplar has **one** OpenAPI spec that is **templated** so the same paths and schemas can be used for either a private or an external **deployment** of **that one service**.
- **Paths and schemas are identical** for both choices; only the **`servers`** block changes:

| Choice   | Server URL (from `.tmpl`) |
|----------|----------------------------|
| **private**  | `https://{apiName}--{bc}--{domain}.us-east-1.private-api.integration-direct.g-p.dev` |
| **external** | `https://app.integration.g-p.dev/api/{domain}/{apiName}--{bc}` |

- So the scaffolder generates a spec that describes **your bounded context’s own API** (e.g. a single service’s contract). If you choose “external”, that spec’s server URL is the external one, but it still describes **that service**, not the aggregated G-P public API.

---

## 2. What external users actually see (G-P public API)

**Repo:** [gp-nova/integrations-external-api](https://github.com/gp-nova/integrations-external-api)  
**File:** `docs/api/openapi-spec.yaml`

- The **external** API that partners/customers use is the **Integrations External API** behind Kong. Its contract is defined in **this** repo.
- That spec is **separate** from any scaffolder-generated spec. It includes:
  - **Paths** like `/v1/employees`, `/v1/employees/{employeeId}`, etc. (not the exemplar’s `/api/v0/hello`).
  - **Tags** such as Employee, Employment, Contract, Jobs, Benefits, Payroll, Time Off, Customer, Contractor, ContractorContract.
  - **Schemas** that align with the **canonical model** (e.g. `Employee`, not internal “EOR Worker”).
  - **Security** (e.g. `BearerAuth`) and **responses** (401, 429, 502, etc.) appropriate for a public API.
- So the OpenAPI that external users see is the **integrations-external-api** spec, not the exemplar’s.

---

## 3. How they relate

| Spec | Purpose | Where it lives | Who uses it |
|------|--------|----------------|--------------|
| **Exemplar / scaffolder-generated** | Describes **one BC service’s** API (paths + schemas for that service). Server URL is either private or external for *that* service. | Your BC repo (e.g. `docs/api/openapi-spec.yaml` from the template). | Your team, Backstage catalog, and (if “external”) anyone calling that single service at the external URL. |
| **integrations-external-api** | Describes the **aggregated public** API surface (canonical resources and paths) exposed via Kong. | [integrations-external-api](https://github.com/gp-nova/integrations-external-api) repo. | External partners/customers and the Developer Portal. |

For the **WES → integrations-external-api** flow:

- Each **core service** has its own **private** API and its own spec (which can be scaffolder-style: same contract, private server URL).
- **WES** calls those private APIs (mTLS) and maps internal ↔ canonical using the canonical model.
- **integrations-external-api** exposes the **public** routes and publishes the **public** OpenAPI spec. That public spec is the one external users see; it is **not** a copy of the scaffolder’s `openapi-spec.yaml`.

---

## 4. Summary

- **Scaffolder/exemplar OpenAPI** = one service’s contract; only the server URL (private vs external) changes with the template. Same paths/schemas in both cases.
- **External users’ OpenAPI** = the **integrations-external-api** spec: different repo, aggregated public surface, canonical model types.
- So the openapi in the scaffolder repo is **not** the same as the OpenAPI exposed to external users as per G-P; the former describes a single BC service, the latter describes the full public API behind Kong.
