# OpenAPI & Canonical Models in GP-Nova

Findings from Backstage/Roadie catalog, TechDocs, and GitHub (`gp-nova` org).

---

## 1. The Canonical Model

### work-engagement-canonical-model

The **primary canonical model** defines the unified representation of an employee across G-P and partner/customer integrations. It acts as a data mapper: internal domain types (e.g. "EOR Worker") become external public API types (e.g. "Employee").

| Field | Value |
|-------|-------|
| **Repo** | [gp-nova/work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model) |
| **NPM package** | `@gp-work-engagement-sync/canonical-model` |
| **Owner** | ctrl-alt-defeat |
| **Domain** | customer-integrations |
| **Bounded context** | work-engagement-sync |
| **Lifecycle** | production |
| **Tech** | TypeScript + Zod (runtime schema validation) |
| **Published to** | AWS CodeArtifact (`gp-prod` domain, account `237156726900`) |
| **Runbook** | [Confluence — Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model) |

### Types exported

```
src/types/
├── index.ts              # re-exports everything
├── address.ts            # Address value type
├── phone-number.ts       # PhoneNumber value type
├── employee.ts           # Employee (employeeId, name, email, DOB, phones, address)
├── employment.ts         # Employment (status enum, tenantId, countryCode, dates)
├── customer.ts           # Customer
├── benefits/
│   └── benefit.ts        # Benefit
├── time-off/
│   └── time-off-allowance.ts
├── job/
│   └── job.ts
├── payroll/
│   └── payroll.ts        # PayrollDetails (salary, currency, frequency)
├── invoice/
│   └── invoice.ts
├── contract/             # Full subdomain: allowance, amendment, details, terms,
│   ├── index.ts          #   covenants, job, period, termination-terms, work-schedule
│   └── ...
└── contractor/
    ├── index.ts
    ├── contractor.ts
    └── contract.ts
```

### Key type definitions

**Employee**

```typescript
EmployeeSchema = z.object({
  employeeId: z.string().describe('workerId'),
  firstName: z.string(),
  lastName: z.string(),
  personalEmail: z.string(),
  dateOfBirth: z.string().optional(),
  phoneNumbers: z.array(PhoneNumberSchema).optional(),
  homeAddress: AddressSchema.optional(),
});
```

**Employment**

```typescript
enum EmploymentStatus {
  New = 1, Onboarding = 2, Active = 3, Terminating = 4,
  Inactive = 5, Withdrawn = 6, OnLeave = 7,
}

EmploymentSchema = z.object({
  employmentId: z.string().describe('engagement externalId'),
  employeeId: z.string().describe('workerId'),
  tenantId: z.string(),
  status: z.enum(EmploymentStatus),
  contractId: z.string().optional(),
  citizenshipCountryCode: z.string().optional(),
  workEmail: z.string().optional(),
  workPhone: z.string().optional(),
  jobTitle: z.string().optional(),
  countryCode: z.string().optional(),
  forecastStartDate: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
});
```

**PayrollDetails**

```typescript
enum SalaryFrequency {
  Monthly = 1, BiMonthly = 2, Weekly = 3, MonthlyWithAdvance = 4,
}

PayrollDetailsSchema = z.object({
  id: z.string(),
  payrollStartDate: z.string().optional(),
  payrollEndDate: z.string().optional(),
  annualBaseSalary: z.number().optional(),
  salaryCurrency: z.string().optional(),
  salaryFrequency: SalaryFrequencySchema.optional(),
});
```

---

## 2. API orchestration chain

To expose a core service endpoint to the outside world, four layers are involved:

```
┌─────────────────┐  mTLS  ┌────────────────────┐  proxy  ┌──────────────────────────┐  Kong  ┌──────────┐
│  Core Service    │ ─────> │  work-engagement-   │ ──────> │ integrations-external-api │ ─────> │  Public  │
│  (e.g. payroll)  │        │  sync (WES)         │         │                          │        │  API     │
└─────────────────┘        └────────────────────┘         └──────────────────────────┘        └──────────┘
                                     │                                │
                                     │ uses                           │ provides
                                     ▼                                ▼
                            canonical-model               OpenAPI spec (public contract)
                            (TS + Zod types)
```

### Step-by-step for a new endpoint

1. **Canonical model** — add/update TypeScript + Zod types mapping internal to external representation.
2. **Core service** — implement the private API (mTLS-secured, not user-authenticated).
3. **WES** — build a proxy lambda that calls the core service and applies canonical-model mapping.
4. **integrations-external-api** — another proxy function exposing the WES endpoint through Kong Gateway.

### Key services

| Service | Description | Owner | Tech | Repo |
|---------|-------------|-------|------|------|
| **work-engagement-sync** (WES) | Orchestrates data sync between GP Platform and HCMs | ctrl-alt-defeat | serverless TS, Lambda, API Gateway | [gp-nova/work-engagement-sync](https://github.com/gp-nova/work-engagement-sync) |
| **integrations-external-api** | Public API exposed through Kong | ctrl-alt-defeat | serverless TS, Lambda, API Gateway | [gp-nova/integrations-external-api](https://github.com/gp-nova/integrations-external-api) |
| **integrations-external-api-gateway** | Kong data plane (SAM + Fargate) | fellowship | SAM, Fargate | [gp-nova/integrations-external-api-gateway](https://github.com/gp-nova/integrations-external-api-gateway) |
| **integrations-external-api-gateway-extensions** | Kong data plane extensions | ctrl-alt-defeat | serverless TS | [gp-nova/integrations-external-api-gateway-extensions](https://github.com/gp-nova/integrations-external-api-gateway-extensions) |

### Supporting components

| Component | Purpose |
|-----------|---------|
| integrations-external-mock | Mocks for external API development |
| integrations-external-api-tests | Dedicated test suite |
| integrations-external-developer-bff | Developer Portal BFF |
| integrations-external-developer-mfe | Developer Portal frontend |
| integrations-external-dev-portal-assets | Dev Portal static assets |
| integrations-external-webhook | Webhook component |
| customer-integrations-schema-work-engagement-sync | Atlan-governed event schema for WES |
| customer-integrations-schema-integrations-external-api | Atlan-governed event schema for public API |

### AWS accounts

WES and integrations-external-api each have dedicated accounts for dev, test, demo, and prod across all three account tiers (standard, test, preview).

---

## 3. OpenAPI usage across the org

### OpenAPI as the API contract

Teams treat OpenAPI specs as the **source of truth** for API consumers. Key patterns:

- **Type generation**: `openapi-typescript` generates TS types from the spec (engagement-service uses `husky` + `lint-staged` for automatic generation on spec changes).
- **Linting**: multiple tools in use:
  - `vacuum` (engagement-service)
  - Redocly CLI + Spectral (benefits-api, benefits-bff)
  - `openapi-linter` GitHub Action ([gp-nova/openapi-linter](https://github.com/gp-nova/openapi-linter), Go/Kotlin/JS, owned by Juggernauts)
- **CI enforcement**: lint errors block merges.

### OpenAPI specs registered in the Backstage catalog

All registered as `API` kind entities with `type: openapi`:

| API | Description | Owner |
|-----|-------------|-------|
| **work-engagement-sync-api** | Data sync between GP Platform and HCMs | ctrl-alt-defeat |
| **integrations-external-api** | Public integrations API | ctrl-alt-defeat |
| **integrations-external-api-gateway-extensions-api** | Kong data plane extensions | ctrl-alt-defeat |
| **payroll-bria-bff-api** | BFF for BRIA payroll management | Orion |
| **core-compliance-service-api** | Nova Compliance engine (has `/api/v1/schema-validator` for validating against canonical schema) | Juggernauts |
| **devx-repo-env-bc-map-api** | Repo/env to bounded-context mapping (canonical environments) | dev-enablement |

Plus many more across engagement, billing, benefits, contractor, payments, etc.

### API and schema validation patterns (from TechDocs)

**engagement-service** (exemplar):

- OpenAPI spec = contract and source of truth
- `npm run api:spec:lint` using vacuum
- `openapi-typescript` for type generation
- Types auto-generated and committed via `husky` + `lint-staged`

**benefits-api / benefits-bff**:

- Redocly CLI + Spectral for OpenAPI/entity spec linting
- Config: `redocly-config.yaml`, `redocly-entities-config.yaml`
- Types and validators published to CodeArtifact on each deploy
- Async schema validation: event definitions validated and transformed into async schema

---

## 4. Schema governance (Atlan)

The org uses **Atlan** as the data governance platform. Bounded context schemas follow the naming convention `{domain}-schema-{bounded-context}`.

### Schema repos

| Schema repo | BC / Domain | Owner |
|-------------|-------------|-------|
| **payroll-schema-payroll-management** | payroll / EOR | the-payoneers |
| engagement-schema | engagements / HR | engagements |
| accounting-schema-accounting-service | accounting / billing | rockumatica |
| invoicing-schema-contractor-billing | contractor-billing / billing | billders |
| invoicing-schema-customer-level-billing | customer-billing / billing | revamps |
| invoicing-schema-accounting-invoicing | accounting-invoicing / billing | rockumatica |
| benefits-schema-benefits-answers | benefits-answers / EOR | benefits |
| benefits-schema-benefits-service | benefits / EOR | benefits |
| application-security-schema-authentication | authentication / core-platform | core-app-sec-hackoverflow |
| application-security-schema-user-record | user-record / core-platform | iac |
| customer-integrations-schema-work-engagement-sync | WES / partner-integrations | ctrl-alt-defeat |
| hr-schema-contract | contract / HR | — |
| payments-transfers-schema-payment | payments / billing | fintegrators |
| contractor-contracts-schema | contracts / contractor | — |
| customer-schema-record | customer-record / core-platform | quasar-team |
| core-customer-schema-customer-account | customer-account / core-platform | quasar-team |
| professional-onboarding-schema-prof-onboarding-identity | prof-onboarding / EOR | the-last-jedi |

### Schema tooling

| Tool | Description | Tech |
|------|-------------|------|
| **core-schema-manager** | Core schema manager for Atlan project update | Java/Gradle library (dev-enablement) |
| **py-schema-manager** | Python schema manager | Python/Shell |
| **devx-action-model-generator-ts** | GitHub Action: generates TS types from Atlan schema files | dev-enablement |
| **devx-action-model-generator-java** | GitHub Action: generates Java POJOs from Atlan schema files | dev-enablement |
| **gp-data-meta-schema** | Metadata schema repo for publishing to Atlan | crucial-data-crew |

---

## 5. Other model libraries

| Library | Description | Owner | Tech |
|---------|-------------|-------|------|
| **professional-lifecycle-models** | Common models for the professional-lifecycle BC (frontend + BFF) | new-sensations | TypeScript, npm |
| **gp-event-model** | Event types (Data Sink, Cloud Event) | the-observers | TypeScript, npm |
| **business-event-publisher** | Streamlines event generation for Data Sink / Audit Trail | core-app-sec-hackoverflow | TypeScript, npm |
