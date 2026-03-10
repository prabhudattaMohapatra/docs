# API Development Guide: Private and Public (Open) APIs

How to start, design, implement, and expose APIs in GP-Nova — including private APIs (internal only) and public/open APIs (external via Kong).

---

## 1. Private vs public APIs

| | **Private API** | **Public (open) API** |
|---|------------------|------------------------|
| **Consumers** | Internal services only (e.g. other BCs, WES) | External partners, customers, integrations |
| **Auth** | mTLS (service-to-service); not tied to a specific user | Kong Gateway; typically API keys / OAuth for end users |
| **Exposure** | Private API Gateway in your BC’s AWS account | integrations-external-api → Kong → internet |
| **Data shape** | Your internal domain model | Canonical model (internal → external mapping) |
| **When to use** | New BC, internal-only capabilities | Features that must be consumed outside the platform |

**Rule of thumb:** Start with a **private API**. Add the public path only when you need external exposure.

---

## 2. Two OpenAPI specs — don’t confuse them

There are **two different** OpenAPI specs in play. The one in your BC repo (from the scaffolder) is **not** the same as the one external users see.

| | **Your BC’s spec** (scaffolder / your repo) | **External (G-P public) spec** |
|---|---------------------------------------------|--------------------------------|
| **Purpose** | Describes **your bounded context’s** API only (paths and schemas your service implements). | Describes the **aggregated public** API surface that partners/customers call via Kong. |
| **Where it lives** | Your BC repo (e.g. `docs/api/openapi-spec.yaml` from the template, or `openapi.yaml`). | [integrations-external-api](https://github.com/gp-nova/integrations-external-api) repo: `docs/api/openapi-spec.yaml`. |
| **Server URL** | Private URL for your service, or (if you chose “external” in the template) the URL for **that one service** on app.integration.g-p.dev. | The public Integrations External API base URL that Kong exposes. |
| **Paths / schemas** | Your service’s paths (e.g. `/api/v1/...`) and **internal** domain types. | Public paths (e.g. `/v1/employees`) and **canonical model** types (e.g. `Employee`, `Employment`). |
| **Who uses it** | Your team, Backstage catalog, and (for private) internal callers like WES. | External partners, customers, and the Developer Portal. |

**Implications:**

- **Private API:** You only need your BC’s spec. It describes your contract; internal callers use it.
- **Public API:** You still have your BC’s spec (internal contract). The **external** contract is the **integrations-external-api** spec. When you add a new public capability, you (or the owning team) update that **separate** public spec and the WES/integrations-external-api proxies; your BC’s spec does not become “the” external spec.

See [openapi-scaffolder-vs-external.md](./openapi-scaffolder-vs-external.md) for more detail.

---

## 3. How to start: design-first with OpenAPI

APIs in Nova are **contract-first**. The OpenAPI spec is the source of truth; implementation and types follow.

### 3.0 Order of thinking: private API first, then canonical only if you go public

**Think in this order:**

1. **Private API first** — Define the API your bounded context will implement. This is the contract internal callers (e.g. WES, other BCs) will use. You always need this. List the operations and resources that make sense for your domain; capture them in your **BC’s OpenAPI spec** (Section 2).

2. **Design the private API with OpenAPI extensibility in mind** — Even if you don’t expose externally today, write a proper OpenAPI spec from day one: clear paths, explicit request/response schemas, and consistent naming. That gives you type generation, linting, and a stable contract. It also makes later external exposure easier: when you add a public path, the mapping from your internal shapes to the canonical model is simpler if your private API is already well-structured (e.g. resource-oriented, stable field names).

3. **Canonical model only when you need external exposure** — Don’t design the canonical model first. Use it when you have a concrete requirement for partners or customers to call your capability. At that point you (or the owning team) define or extend the **external** shapes in the canonical model and in the integrations-external-api spec, and implement the mapping in WES. Your private API stays the internal source of truth; the canonical model is the public view.

**Summary:** Private API list and OpenAPI spec first, with extensibility in mind. Canonical model later, only for the parts you expose publicly.

### 3.1 Decide scope

1. **Private only** — Your service is called by other internal services (e.g. WES, other BCs). No external consumers.
2. **Public (open)** — Partners or customers will call your API. You will need the canonical model and the WES → integrations-external-api chain.

### 3.2 Create the OpenAPI spec first

Before writing Lambda code:

1. Create `openapi.yaml` (or `openapi.json`) in your repo (root or `docs/`).
2. Define **paths**, **operations**, and **components/schemas**.
3. Run linting and fix errors.
4. Generate types from the spec; then implement handlers.

This keeps the contract stable and avoids drift between spec and code. For a private API, this is your **BC’s spec** (Section 2). For a public API, you will also have a **separate** external spec in integrations-external-api.

---

## 4. Steps for a private API only

End-to-end flow for an API that stays internal (no Kong, no canonical model).

### Step 1: Scaffold the service

Use the **Serverless Typescript** template in Roadie:

- [Create → Serverless Typescript](https://globalization-partners.roadie.so/create/templates/default/serverless-typescript)
- Choose **private API** as service type.
- Fill repo URL, owner team, description, API name, value stream / domain / bounded context.

You get a repo with SAM (private API template), Lambda layout, and CI.

### Step 2: Write the OpenAPI spec (your BC’s spec)

Add or edit `openapi.yaml` (or `docs/api/openapi-spec.yaml`) in **your repo**. This describes your service’s contract only — not the external API (see Section 2).

```yaml
openapi: 3.0.2
info:
  title: Your Service API
  version: 1.0.0
  description: Private API for {your-bounded-context}.
  contact:
    name: Your Team
servers:
  - url: 'https://{service}--{bc}--{domain}.{region}.private-api.{env}.g-p.dev'
    description: Private API (internal only)
paths:
  /api/v1/resources:
    get:
      operationId: listResources
      summary: List resources
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Resource'
    post:
      operationId: createResource
      # ...
components:
  schemas:
    Resource:
      type: object
      required: [id, name]
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
```

Conventions:

- Base path: `/api/v1/...`
- Every operation has an `operationId`.
- Reuse `components/schemas` for request/response bodies.
- Private server URL pattern: `{service}--{bc}--{domain}.{region}.private-api.{env}.g-p.dev`

### Step 3: Lint and generate types

**Lint** (pick one and run in CI):

- **vacuum:** `npm run api:spec:lint` (e.g. engagement-service).
- **Redocly CLI + Spectral:** `redocly lint openapi.yaml` (e.g. benefits-api).
- **openapi-linter action:** use [gp-nova/openapi-linter](https://github.com/gp-nova/openapi-linter) in GitHub Actions.

**Generate TypeScript types:**

```bash
npx openapi-typescript openapi.yaml -o src/types/api.d.ts
```

Optional: use `husky` + `lint-staged` to regenerate types when the spec changes (see engagement-service).

### Step 4: Implement Lambda handlers

Implement one Lambda per logical operation (or group of operations), using the generated types:

- Handlers live under `src/function/` (e.g. `get-resource/handler.ts`, `create-resource/handler.ts`).
- Use shared code in `src/module/` (middleware, validation, DB).
- Wire routes in SAM so that API Gateway paths invoke the correct Lambda (e.g. in `sam/api.yaml`).

Keep request/response types aligned with the OpenAPI spec (prefer generated types).

### Step 5: Configure SAM for private API

Ensure your root `template.yaml` and `sam/api.yaml`:

- Use the **private** API template (exemplar has `template.yaml` and `template.yaml.external`; private is the default for internal-only).
- Define `AWS::Serverless::Api` with the correct path and method integrations.
- Attach the OTel Lambda layer and required env vars for telemetry.

Deploy with `sam build` and `sam deploy` (or via your pipeline).

### Step 6: Allow callers via mTLS

Your API is private and secured by mTLS. Any caller (e.g. WES, another BC) must:

- Use a client certificate that your API Gateway (or load balancer) trusts.
- Call the private URL for the right environment (dev/test/demo/prod).

Document the private base URL and any required headers in your TechDocs/runbook.

### Step 7: Register in Backstage catalog

In `catalog-info.yaml`:

1. **Component** — your service (already created by the template).
2. **API entity** — link the OpenAPI spec:

```yaml
---
apiVersion: backstage.io/v1alpha1
kind: API
metadata:
  name: your-service-api
  description: "Private API for Your Bounded Context"
spec:
  type: openapi
  lifecycle: production
  owner: "gp-nova/your-team"
  definition:
    $text: ./openapi.yaml
```

In the Component’s `spec`, add:

```yaml
providesApis:
  - your-service-api
```

After registration, the spec is discoverable in Roadie and can be used for discovery and documentation.

---

## 5. Steps for a public (open) API

When external partners or customers need to call your capability, expose it through the standard chain: **your service → WES → integrations-external-api → Kong**.

### High-level flow

```
Your private API  →(mTLS)→  work-engagement-sync (WES)  →(proxy)→  integrations-external-api  →(Kong)→  Public
                                    ↑
                            canonical model
                            (internal → external mapping)
```

### Step 1–5: Same as private API

Design and implement your **private** API first (Steps 1–5 in Section 3):

1. Scaffold with **private** service type.
2. Write the OpenAPI spec for your **internal** contract.
3. Lint and generate types.
4. Implement Lambda handlers.
5. Deploy and secure with mTLS.

Your core service stays private; only WES (and any other allowed internal callers) talks to it.

### Step 6: Define the external contract (public OpenAPI)

The **external** contract is **not** your BC’s spec (Section 2). It is the **integrations-external-api** OpenAPI spec, which lives in that repo and describes the aggregated public surface (canonical paths and types).

- Update or add the new paths and schemas in [integrations-external-api](https://github.com/gp-nova/integrations-external-api) `docs/api/openapi-spec.yaml` (or the location that repo uses for the public spec). That is the spec external users and the Developer Portal use.
- Do **not** assume the scaffolder-generated spec in your BC repo becomes the external spec; they are separate. Your BC spec = internal contract; integrations-external-api spec = external contract.

Public spec conventions:

- Base path often under something like `/api/{domain}/{capability}/v1/...` or as defined by integrations-external-api.
- All request/response bodies use **canonical model** types (e.g. `Employee`, `Employment`, not internal “EOR Worker” or DB-specific fields).
- Document auth (e.g. API key, OAuth) in the spec.

### Step 7: Add or extend the canonical model

The canonical model defines how internal data is mapped to external shapes (e.g. internal “EOR Worker” → public “Employee”).

- Repo: [gp-nova/work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model)
- Runbook: [Confluence — Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model)

Steps:

1. Add or update TypeScript types and Zod schemas in the canonical model repo for the **external** request/response shapes.
2. Publish the updated `@gp-work-engagement-sync/canonical-model` package (e.g. to CodeArtifact).
3. Ensure WES and integrations-external-api use this version when mapping to/from your service.

### Step 8: Implement WES proxy (work-engagement-sync)

In [gp-nova/work-engagement-sync](https://github.com/gp-nova/work-engagement-sync):

1. Add a new Lambda (or extend an existing one) that:
   - Receives the **external** request (canonical shape).
   - Optionally maps it to your **internal** shape (if different).
   - Calls your **private** API via mTLS.
   - Maps the internal response back to the **canonical** shape and returns it.
2. Wire this Lambda to the appropriate path in WES’s API Gateway.
3. Ensure your private API’s mTLS configuration allows calls from WES.

### Step 9: Expose via integrations-external-api

In [gp-nova/integrations-external-api](https://github.com/gp-nova/integrations-external-api):

1. Add a **proxy** function that:
   - Receives the public request (e.g. from Kong).
   - Forwards it to the corresponding WES endpoint.
   - Returns the response (and status codes) to the client.
2. Register the route in the public API Gateway so Kong can route to it.
3. Update the **public** OpenAPI spec in the integrations-external-api repo with the new path and canonical schemas — that is the spec external users see (Section 2).

### Step 10: Kong and public documentation

- Kong is deployed and configured via **integrations-external-api-gateway** (and extensions). Routing to your new path is configured there.
- Document the public base URL, auth, and usage in the Developer Portal (integrations-external-developer-bff / developer-mfe) and/or in your public OpenAPI spec.

### Step 11: Register public API in Backstage

Register the **public** API in the catalog (either in integrations-external-api’s catalog or a shared docs repo):

- Kind: `API`
- `spec.type: openapi`
- `spec.definition` pointing to the **public** OpenAPI spec.
- Owner: your team or the team that owns the public surface.

This makes the public API discoverable and documented in Roadie.

---

## 6. OpenAPI authoring: conventions and quality

### 6.1 File location and naming

| Type | Location | Notes |
|------|----------|--------|
| Sync REST API | `openapi.yaml` or `docs/openapi.yaml` | Prefer root for the main spec |
| Async events | `docs/async-api.yaml` | For event-driven flows |

### 6.2 Required elements

- **info:** `title`, `version`, `description`, `contact`.
- **servers:** At least one URL per environment (private and/or public).
- **paths:** Every path has at least one operation with `operationId`, `summary`, and `responses`.
- **components.schemas:** Shared request/response bodies; use `$ref` in paths.
- **security** (for public API): Document how the API is secured (e.g. API key, OAuth2).

### 6.3 URL patterns

| API type | Server URL pattern (example) |
|----------|-----------------------------|
| Private | `https://{service}--{bc}--{domain}.{region}.private-api.{env}.g-p.dev` |
| Public (via Kong) | Per integrations-external-api and Kong config (e.g. `https://{env}.g-p.{domain}/api/...`) |

### 6.4 Versioning

- Path versioning: `/api/v1/...`, `/api/v2/...`.
- When breaking the contract, introduce a new `v2` path (or new operations) and deprecate the old one in the spec.

### 6.5 Linting and CI

- Run lint on every PR; fail the build on errors.
- Use **vacuum**, **Redocly CLI**, **Spectral**, or the **openapi-linter** action — align with your team’s choice (see Section 4, Step 3).

### 6.6 Type generation

- Use `openapi-typescript` (or your team’s chosen generator) so that TypeScript types stay in sync with the spec.
- Prefer generated types in handlers and shared modules; avoid hand-written duplicates of the same structures.

---

## 7. Quick reference: private vs public

| Step | Private API only | Public (open) API |
|------|-------------------|--------------------|
| 1 | Scaffold (private) | Same |
| 2 | Write OpenAPI (internal) | Same |
| 3 | Lint + generate types | Same |
| 4 | Implement handlers | Same |
| 5 | SAM private API + deploy | Same |
| 6 | mTLS for internal callers | Define **public** OpenAPI contract |
| 7 | Register API in Backstage | Add/update **canonical model** |
| 8 | — | Implement **WES proxy** |
| 9 | — | Add **integrations-external-api** proxy |
| 10 | — | Kong + public docs |
| 11 | — | Register **public** API in Backstage |

---

## 8. Related docs

| Document | Content |
|----------|---------|
| [new-bounded-context-api-guide.md](./new-bounded-context-api-guide.md) | Full BC setup: scaffolding, infra, Atlan, CodeArtifact, telemetry |
| [openapi-and-canonical-models.md](./openapi-and-canonical-models.md) | Canonical model details, WES/integrations-external-api, schema governance |
| [openapi-scaffolder-vs-external.md](./openapi-scaffolder-vs-external.md) | Why the BC spec and the external spec are not the same |
| [Confluence — Template Serverless Project](https://globalization-partners.atlassian.net/wiki/spaces/NOVA/pages/2899869796/Template+Serverless+Project) | IDP template usage |
| [Confluence — Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model) | Canonical model changes |

---

## 9. Key repos

| Repo | Role |
|------|------|
| [gp-nova/serverless-api-ts-exemplar](https://github.com/gp-nova/serverless-api-ts-exemplar) | Exemplar for new serverless TS APIs (private/external) |
| [gp-nova/openapi-linter](https://github.com/gp-nova/openapi-linter) | OpenAPI lint GitHub Action |
| [gp-nova/work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model) | Canonical types (internal → external) |
| [gp-nova/work-engagement-sync](https://github.com/gp-nova/work-engagement-sync) | WES — proxies to private APIs, applies canonical model |
| [gp-nova/integrations-external-api](https://github.com/gp-nova/integrations-external-api) | Public API behind Kong |
