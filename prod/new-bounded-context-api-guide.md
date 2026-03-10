# Developing a New Bounded Context with Fresh APIs (OpenAPI-Extensible)

Step-by-step guide derived from Backstage catalog, TechDocs, IDP templates, and GitHub (`gp-nova` org).

---

## Overview

```
Step 1  Define BC identity (value stream, domain, bounded context)
Step 2  Scaffold repo via IDP (Roadie developer portal)
Step 3  Write the OpenAPI spec (source of truth)
Step 4  Infrastructure: SAM nested stacks (API GW, Lambda, DB, Events)
Step 5  Implement API handlers
Step 6  OpenAPI linting & type generation
Step 7  Set up Atlan schema repo for events
Step 8  Backstage catalog registration (catalog-info.yaml)
Step 9  CodeArtifact package management
Step 10 Telemetry & observability (OpenTelemetry)
Step 11 (Optional) Expose externally via canonical model + WES + public API
```

---

## Step 1: Define your Bounded Context identity

Every service in Nova lives inside a three-level hierarchy. You need to decide this upfront because it flows into repo naming, AWS accounts, CodeArtifact repos, telemetry namespaces, and Atlan schema repos.

| Level | Example |
|-------|---------|
| **Value stream** | `employer-of-record` |
| **Domain** | `payroll` |
| **Bounded context** | `payroll-management` |

This naming determines:
- Repo name: `{bc-name}` or `{domain}-{bc-name}` (convention varies)
- AWS account names: `{bc-name}-dev`, `{bc-name}-test`, `{bc-name}-demo`, `{bc-name}-prod`
- Telemetry root: `gp.{value-stream}.{domain}.{bc}` (e.g. `gp.eor.hr.engagements`)
- Event source: `{value-stream}.{domain}.{bc}` (e.g. `employer-of-record.hr.job`)

---

## Step 2: Scaffold via the IDP (Roadie developer portal)

The org's Internal Developer Platform (IDP) at [Roadie](https://globalization-partners.roadie.so/create) provides scaffolder templates that generate a fully wired repo.

### For a serverless TypeScript API

Use the **Serverless Typescript** template (currently v1.6.0):

- URL: `https://globalization-partners.roadie.so/create/templates/default/serverless-typescript`
- Source exemplar: [gp-nova/serverless-api-ts-exemplar](https://github.com/gp-nova/serverless-api-ts-exemplar) (a.k.a. `devx-exemplar-ts-serverless-api`)
- Docs: [Template Documentation (Confluence)](https://globalization-partners.atlassian.net/wiki/spaces/NOVA/pages/2899869796/Template+Serverless+Project)

The template asks for:
1. **Repository location** (org: `gp-nova`)
2. **Owner** (your team in engineering)
3. **Description**
4. **Service type** (private API or external API)
5. **API name**
6. **Value stream → Domain → Bounded context** (selected from live BC list)

What you get out of the box:

```
your-service/
├── catalog-info.yaml           # Backstage registration
├── mkdocs.yml                  # TechDocs config
├── template.yaml               # SAM root (private or external)
├── samconfig.toml               # SAM deploy config
├── package.json                 # Node.js project
├── eslint.config.ts             # Linting
├── .prettierrc.json             # Formatting
├── .nvmrc                       # Node version
├── .npmrc                       # CodeArtifact registry
├── .github/                     # CI/CD workflows
├── .gp/                         # GP metadata
├── docs/                        # TechDocs content
├── e2e/                         # E2E tests
├── features/                    # BDD features
├── src/
│   ├── function/                # Lambda handlers
│   └── module/
│       ├── middleware/           # Shared middleware
│       └── util/                # Utilities
└── scripts/                     # Build/deploy helpers
```

### Other available IDP templates

| Template | Use case |
|----------|----------|
| `serverless-typescript` | TypeScript Lambda + API GW service |
| `serverless-java` | Java Lambda + API GW service |
| `serverless-python` | Python Lambda + API GW service |
| `sam-starter` | Minimal SAM infra skeleton |
| `npm-library` | TypeScript NPM library |
| `java-library` | Java library (Gradle) |
| `poetry-library` | Python library |
| `mfe-remote` | Micro-frontend remote |
| `mfe` | Micro-frontend shell |
| `atlan` | Atlan schema repo (see Step 7) |
| `mcp-python` | Python MCP server |
| `titangent-python` | Titan AI agent |
| `terraform-project` | Terraform infra project |

All templates live in [gp-nova/backstagetemplates](https://github.com/gp-nova/backstagetemplates).

---

## Step 3: Write the OpenAPI spec

The OpenAPI spec is the **contract and source of truth** for your API consumers. Define it early -- it drives type generation, linting, and documentation.

### Where to put it

By convention, the spec lives in the repo root or under `docs/`:

- `openapi.yaml` (sync APIs)
- `docs/async-api.yaml` (async event definitions, if applicable)

### What to include

```yaml
openapi: 3.0.2
info:
  title: Your Service API
  version: 1.0.0
  description: >
    Brief description of your bounded context API.
  contact:
    name: Your Team Name
servers:
  - url: 'https://{service}--{bc}--{domain}.{region}.private-api.{env}.g-p.dev'
    description: Internal Service URL
  - url: 'https://{env}.g-p.{domain}/api/{domain}/{service}'
    description: External Service URL (if applicable)
paths:
  /api/v1/your-resource:
    get:
      operationId: listResources
      summary: List resources
      # ...
components:
  schemas:
    YourResource:
      type: object
      properties:
        # ...
```

### Key conventions from the org

- Path prefix: `/api/v1/...` (versioned)
- Use `operationId` on every operation
- Define `components/schemas` for all request/response bodies
- Server URLs follow the pattern `{service}--{bc}--{domain}.{region}.private-api.{env}.g-p.dev`

---

## Step 4: Infrastructure — SAM nested stacks

The org uses **AWS SAM** with **nested stacks** for infrastructure. The engagement-service is the exemplar for this pattern.

### Root template.yaml

The root `template.yaml` defines parameters and references nested stacks:

```yaml
# Root template.yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Parameters:
  BoundedContext:
    Type: String
  # ...

Resources:
  ApiStack:
    Type: AWS::Serverless::Application
    Properties:
      Location: ./sam/api.yaml
      Parameters:
        BoundedContext: !Ref BoundedContext

  DbStack:
    Type: AWS::Serverless::Application
    Properties:
      Location: ./sam/db.yaml

  EventStack:
    Type: AWS::Serverless::Application
    Properties:
      Location: ./sam/events.yaml
```

### Nested stacks

| Stack | File | Purpose | Key resources |
|-------|------|---------|---------------|
| **API** | `sam/api.yaml` | API Gateway + Lambda handlers | `AWS::Serverless::Api`, `AWS::Serverless::Function`, `AWS::IAM::Role`, `AWS::Logs::LogGroup` |
| **DB** | `sam/db.yaml` | DynamoDB table + access policies | `AWS::DynamoDB::Table`, IAM outputs |
| **Events** | `sam/events.yaml` | EventBridge rules, SQS queues, DLQs | `AWS::Events::Rule`, `AWS::SQS::Queue` |

The API stack outputs the API domain and API ID. The DB stack outputs table access for Lambda functions.

---

## Step 5: Implement API handlers

### Lambda function structure

```
src/function/
├── get-resource/
│   ├── handler.ts          # Lambda entry point
│   └── handler.test.ts     # Unit tests
├── create-resource/
│   ├── handler.ts
│   └── handler.test.ts
└── ...
```

### Middleware stack

The exemplar includes a middleware pattern:

```
src/module/
├── middleware/              # Request validation, auth, error handling
└── util/                   # Shared utilities
```

### Telemetry layer

SAM template includes the OpenTelemetry Lambda layer:

```yaml
Layers:
  - arn:aws:lambda:us-east-1:637423388126:layer:gp-otel-node-lambda-instrumentation-layer-arm64-1_8_6:1
Environment:
  Variables:
    GP_TELEMETRY_SERVICE_NAME: !Ref BoundedContext
    GP_TELEMETRY_SERVICE_NAMESPACE: !Ref BoundedContext
    AWS_LAMBDA_EXEC_WRAPPER: /opt/otel-initialisation-wrapper
```

---

## Step 6: OpenAPI linting & type generation

### Linting options (pick one)

| Tool | Used by | How |
|------|---------|-----|
| **vacuum** | engagement-service | `npm run api:spec:lint` |
| **Redocly CLI + Spectral** | benefits-api, benefits-bff | Config: `redocly-config.yaml`, `redocly-entities-config.yaml` |
| **openapi-linter** GitHub Action | org-wide | [gp-nova/openapi-linter](https://github.com/gp-nova/openapi-linter) |

All enforce linting in CI -- lint errors block merges.

### Type generation from OpenAPI

The engagement-service pattern (recommended):

1. Install `openapi-typescript` for generating TypeScript types from the spec.
2. Use `husky` + `lint-staged` to auto-regenerate types when the spec changes.
3. Developers don't manually write API types -- they are derived from the spec.

```bash
npm run api:spec:lint       # Validate the spec
npx openapi-typescript openapi.yaml -o src/types/api.d.ts  # Generate types
```

### Event model definitions

For async events, use the `async-api.yaml` pattern:

```bash
npm run create-event -- --eventName "PayrollRunCompleted"
```

This updates `docs/async-api.yaml` with a new event definition in PascalCase.

---

## Step 7: Set up Atlan schema repo for events

If your BC emits events, create a dedicated schema repo for Atlan governance.

### Scaffold via IDP

Use the **Atlan Schema Project** template (currently v1.0.26):

- URL: `https://globalization-partners.roadie.so/create/templates/default/atlan`
- Source exemplar: [gp-nova/devx-exemplar-schema-template](https://github.com/gp-nova/devx-exemplar-schema-template)

The template asks for:
1. Repository location
2. Owner team
3. Value stream → Domain → Bounded context

### Naming convention

```
{domain}-schema-{bounded-context}
```

Examples from the org:
- `payroll-schema-payroll-management` (the-payoneers)
- `engagement-schema` (engagements)
- `invoicing-schema-contractor-billing` (billders)
- `benefits-schema-benefits-service` (benefits)

### Schema tooling chain

1. Define schemas in the schema repo
2. `core-schema-manager` (Java) or `py-schema-manager` (Python) publishes to Atlan
3. `devx-action-model-generator-ts` generates TypeScript types from Atlan schemas
4. `devx-action-model-generator-java` generates Java POJOs from Atlan schemas

---

## Step 8: Backstage catalog registration

Your repo needs a `catalog-info.yaml` at the root to register with Backstage.

### Minimum for a service component

```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: your-service-name
  description: "Brief description of your service"
  annotations:
    github.com/project-slug: gp-nova/your-service-name
    backstage.io/techdocs-ref: dir:.
  tags:
    - serverless-typescript
    - typescript
    - nodejs
    - lambda
    - apigateway
    - employer-of-record       # value stream tag
    - payroll                  # domain tag
    - payroll-management       # BC tag
spec:
  type: service
  lifecycle: production        # or experimental
  owner: "gp-nova/your-team"
```

### Adding the OpenAPI spec to the catalog

Add an API entity (either in the same file or a separate one):

```yaml
---
apiVersion: backstage.io/v1alpha1
kind: API
metadata:
  name: your-service-api
  description: "Your Service API"
spec:
  type: openapi
  lifecycle: production
  owner: "gp-nova/your-team"
  definition:
    $text: ./openapi.yaml      # path to your OpenAPI spec
```

Then link from the Component:

```yaml
spec:
  providesApis:
    - your-service-api
```

### TechDocs

The repo also needs `mkdocs.yml` at the root for TechDocs:

```yaml
site_name: Your Service Name
nav:
  - Home: index.md
  - Playbook: playbook/index.md
  - Runbook: runbook/index.md
plugins:
  - techdocs-core
```

Documentation goes in `docs/` following the Diátaxis framework:
- **Playbook** (explanation + design decisions)
- **Runbook** (how-to guides + reference)

---

## Step 9: CodeArtifact package management

### Repository structure

Nova uses AWS CodeArtifact with an air-gapped architecture:

```
npm-staging (untrusted)  ← public npm (external upstream)
    ↓ (Socket scan)
npm (trusted)            ← scanned & approved packages
    ↓
Releases & Shared        ← internal releases and org-wide deps
    ↓
all-dependencies         ← aggregated registry (single endpoint for Socket)
```

### Setup for your BC

1. Your BC gets its own CodeArtifact repository in its AWS account.
2. `.npmrc` in the repo points to the CodeArtifact endpoint.
3. Auth token:
   ```bash
   export ARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
     --profile {bc}-dev --domain gp-dev --domain-owner 890779668410 \
     --region us-east-1 --query authorizationToken --output text)
   ```
4. If publishing a library, configure `package.json` to publish to CodeArtifact.

See the [aws-artifact-management TechDocs](https://globalization-partners.roadie.so/catalog/default/component/aws-artifact-management/docs) for the full migration/setup runbook.

---

## Step 10: Telemetry & observability (OpenTelemetry)

### KPI naming convention

```
{root}.{thing}.{action}
```

Where root = `gp.{value-stream}.{domain}.{bc}`

Example: `gp.eor.payroll.payroll-management.payroll_run.calculation.request.count`

### SAM integration

Add the OTel Lambda layer and env vars to your SAM template (see Step 5).

### Libraries

- `@globalization-partners/gp-telemetry-instrumentation` — auto-instrumentation
- `@globalization-partners/gp-telemetry-metric-client` — metric definitions
- `@globalization-partners/gp-telemetry-context-propagation-client` — context propagation

---

## Step 11 (Optional): Expose externally via canonical model + WES

If your API needs to be exposed publicly to partners/customers, you go through the existing orchestration chain:

```
Your Service  →(mTLS)→  WES  →(proxy)→  integrations-external-api  →(Kong)→  Public
```

### Sub-steps

1. **Update the canonical model** — add TypeScript + Zod types in [work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model) mapping your internal types to external representation.
   - Follow the [Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model).

2. **Allow mTLS from WES** — configure your service to accept calls from the `work-engagement-sync` service via mTLS.

3. **Build WES proxy lambda** — in [work-engagement-sync](https://github.com/gp-nova/work-engagement-sync), create a lambda that:
   - Calls your core service via mTLS
   - Maps the response through the canonical model types

4. **Build public API proxy** — in [integrations-external-api](https://github.com/gp-nova/integrations-external-api), create a proxy function that forwards to WES and is exposed through Kong Gateway.

5. **Register the public OpenAPI spec** — add an API entity in the integrations-external-api catalog for the new endpoints.

### If you only need a private API

Skip this step entirely. Your private API (from Steps 1-10) is accessible internally via mTLS. No canonical model mapping, WES proxy, or public API proxy needed. You only enter the orchestration chain when external exposure is required.

---

## Quick reference: Key repos

| Repo | Purpose |
|------|---------|
| [gp-nova/backstagetemplates](https://github.com/gp-nova/backstagetemplates) | All IDP scaffolder templates |
| [gp-nova/serverless-api-ts-exemplar](https://github.com/gp-nova/serverless-api-ts-exemplar) | TS serverless API exemplar (template source) |
| [gp-nova/devx-exemplar-schema-template](https://github.com/gp-nova/devx-exemplar-schema-template) | Atlan schema repo exemplar |
| [gp-nova/openapi-linter](https://github.com/gp-nova/openapi-linter) | OpenAPI lint GitHub Action |
| [gp-nova/work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model) | Canonical model (TS + Zod) |
| [gp-nova/work-engagement-sync](https://github.com/gp-nova/work-engagement-sync) | WES orchestration service |
| [gp-nova/integrations-external-api](https://github.com/gp-nova/integrations-external-api) | Public API (Kong Gateway) |
| [gp-nova/aws-artifact-management](https://github.com/gp-nova/aws-artifact-management) | CodeArtifact management & docs |
| [gp-nova/core-schema-manager](https://github.com/gp-nova/core-schema-manager) | Atlan schema manager (Java) |

## Quick reference: Key Confluence pages

| Page | Topic |
|------|-------|
| [Template Serverless Project](https://globalization-partners.atlassian.net/wiki/spaces/NOVA/pages/2899869796/Template+Serverless+Project) | IDP template documentation |
| [Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model) | How to update the canonical model |

## Quick reference: Key TechDocs

| Component | TechDocs page | Topic |
|-----------|---------------|-------|
| engagement-service | playbook/explanation | Broader system context, API design principles, nomenclature |
| engagement-service | runbook/infrastructure | SAM nested stacks pattern (API, DB, events) |
| engagement-service | runbook/how-to-guides | OpenAPI linting, type generation |
| aws-artifact-management | migration | CodeArtifact migration for new BCs |
| benefits-api | playbook | API and schema validation patterns |
