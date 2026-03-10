# Build Plan: Customer Management System API (Private + External OpenAPI)

Step-by-step plan to build APIs for the Global Payroll Customer Management System — from API design and persistence through private API delivery and (optionally) external exposure. Tailored to this repo.

**Repo:** `global-payroll-customer-management-system`  
**Team:** Payblaze · **Domain:** global-payroll · **BC:** payroll-engine  
**Current state:** Full Customer CRUD API (5 endpoints), DynamoDB persistence, repository layer, SAM-wired Lambda functions, private API with VPC endpoint restriction, Backstage catalog, TechDocs. Phases A–D complete.

### Status

| Phase | Step | Description | Status |
|-------|------|-------------|--------|
| **A** | 2.1 | Define domain resources and operations | ✅ Done |
| A | 2.2 | Author OpenAPI spec (BC spec) | ✅ Done |
| A | 2.3 | Lint and generate types | ✅ Done |
| **B** | 3.1 | Choose store and access pattern | ✅ Done |
| B | 3.2 | Add DynamoDB to SAM template | ✅ Done |
| B | 3.3 | Repository layer | ✅ Done |
| **C** | 4.1 | Align handlers with BC spec | ✅ Done |
| C | 4.2 | Wire new functions in SAM | ✅ Done |
| C | 4.3 | Validation and errors | ✅ Done |
| **D** | 5.1 | Checklist: private only | ✅ Done |
| D | 5.2 | mTLS and callers | ✅ Done |
| **E** | 6.1–6.5 | External API (optional) | ⬜ Not started |

*Update this table as you complete each step.*

### G-P standards alignment

This plan is aligned with G-P's documented API standards:

| Standard | Source | This plan |
|----------|--------|-----------|
| OpenAPI spec in `<repo>/docs/api` | [Spec Setup](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/5258444869/Spec+Setup) (GPARCH) | BC spec in `docs/api/openapi-spec.yaml` |
| OpenAPI at least 3.x.x | Spec Setup | Use OpenAPI 3.0.x in the BC spec |
| Types generated from spec (single source of truth) | [Bill Management BC API Design Standards](https://globalization-partners.atlassian.net/wiki/spaces/GPB/pages/4820467774), [OpenAPI Specs Validation](https://globalization-partners.atlassian.net/wiki/spaces/GPB/pages/2867496028) (GPB) | Phase A: openapi-typescript (or team generator) from spec |
| Spec validation in CI / pre-commit | [How do I publish my API Specs to Event Catalog](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/5207359620) (GPARCH) | Phase A: lint script in CI; optionally pre-commit |
| Private API: mTLS, private API GW | [Private Services](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/3257729316/Private+Services) (GPARCH) | Phase D: private endpoint, VPC/resource policy, mTLS for callers |
| Backstage API entity with spec definition | Event Catalog / Roadie docs | Phase D: `catalog-info.yaml` with `definition` at BC spec |
| Sync API spec = OpenAPI | [API & Event Specifications](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/5030838479/API+Event+Specifications) (GPARCH) | BC spec is OpenAPI 3.x |

**Reference repos (gp-nova):** `benefits-api` (Redocly + Spectral, openapi-typescript, Zod from spec, playbook/runbook), `engagement-service` (playbook/runbook, SAM, private API), `work-engagement-sync` (WES proxy pattern), `integrations-external-api` (public API surface).

---

## 1. Two specs to keep in mind

| Spec | Purpose | Where |
|------|---------|--------|
| **BC spec (this repo)** | Your service's contract — internal domain model, paths you implement. | `docs/api/openapi-spec.yaml` |
| **External spec (G-P public)** | What partners/customers see via Kong — canonical types only. | [integrations-external-api](https://github.com/gp-nova/integrations-external-api) repo |

You will maintain the BC spec here. If you expose endpoints externally, the **external** contract is updated in integrations-external-api; it is not the same file. See `docs/prod/openapi-scaffolder-vs-external.md` in the docs repo for detail.

---

## 2. Phase A: API design (BC spec)

### 2.1 Define domain resources and operations

Decide the **internal** API surface for customer management. For v1:

- **Customers** — Full CRUD: list, get by id, create, update, delete.

Document in a short ADR or in `docs/` (e.g. `docs/design/api-resources.md`): resource names, path layout, and which operations are needed for v1. See `api-resources.md` in the repo for the current definition (Customers only in v1).

### 2.2 Author the OpenAPI spec (BC spec)

**When to decide the Customer schema:** You decide the exact Customer (and CustomerSummary) schema — fields, types, required vs optional — in this step, when you define `components/schemas` in the spec. You can sketch fields in `docs/design/api-resources.md` or a separate design doc first, but the source of truth is the OpenAPI spec.

**API schema first, not DB schema first:** Define the Customer shape from the **API (contract) perspective** in Phase A. In Phase B you then design the store (e.g. DynamoDB tables and keys) to support that API — mapping API fields to table attributes and access patterns. That keeps the API stable for consumers while letting you change storage details later.

Edit **`docs/api/openapi-spec.yaml`** in this repo:

1. **Version and info** — Use **OpenAPI 3.x** (e.g. `openapi: 3.0.2`). Set a real `version` (e.g. `1.0.0`), keep `title` and `description` aligned with the product.
2. **Servers** — Keep the existing private URL; add more environments (test, prod) if needed.
3. **Paths** — Replace or extend the placeholder with your real paths (Customers only for v1), e.g.:
   - `GET /api/v1/customers` — list (with optional query params).
   - `GET /api/v1/customers/{customerId}` — get one.
   - `POST /api/v1/customers` — create.
   - `PUT /api/v1/customers/{customerId}` — update.
   - `DELETE /api/v1/customers/{customerId}` — delete.
4. **Components** — Define request/response schemas (e.g. `Customer`, `CustomerSummary`) under `components/schemas`. Use `$ref` in paths.
5. **Operation IDs** — Set a unique `operationId` per operation (e.g. `listCustomers`, `getCustomerById`, `createCustomer`, `updateCustomer`, `deleteCustomer`). These will map to handlers.
6. **Responses** — Include at least 200 (and 201 for create), plus 400, 404, 500 where relevant.

Use path versioning: `/api/v1/...`. Do not add external-only or canonical model types here; this spec is the **internal** contract.

### 2.3 Lint and generate types

- **Lint:** Add a script (e.g. `npm run api:spec:lint`) and run it in CI (and optionally pre-commit). Use one of:
  - **Redocly CLI + Spectral** (e.g. `benefits-api`: `lint:openapi:redocly`, `lint:openapi:spectral`) — add `redocly-config.yaml` and optionally `.spectral.yaml` at repo root.
  - **vacuum** (used by some BCs).
  - **openapi-linter** (gp-nova) GitHub Action / Zally if your team uses it.
  Fix all reported issues.
- **Types:** Add **openapi-typescript** (or your team's generator) and generate TypeScript types from `docs/api/openapi-spec.yaml` into e.g. `src/module/model/api.generated.ts` or `src/module/types/api.ts`. Run generation in CI and optionally on pre-commit.
- **Optional:** Generate Zod (or other) validators from the spec (e.g. `benefits-api`'s `generate:zod-api`) for runtime validation of request/response bodies.

After this phase, the BC spec is the single source of truth for your API surface and for generated types.

---

## 3. Phase B: Persistence

> Full design document: [`docs/design/persistence.md`](https://github.com/gp-nova/global-payroll-customer-management-system/blob/main/docs/design/persistence.md) in the service repo.

### 3.1 Choose store and access pattern

**Decision: DynamoDB** (PAY_PER_REQUEST, SSE with AWS-managed key, PITR enabled, no streams/TTL for v1).

**Table: `${StackName}-Customers`**

| Attribute | Type | Key role | Example |
|-----------|------|----------|---------|
| `PK` | S | Partition key | `CUSTOMER#<uuid>` |
| `SK` | S | Sort key | `METADATA` |

**GSI `StatusIndex`:** PK = `status` (S), SK = `PK` (S), Projection = ALL.

**Access patterns (from OpenAPI spec):**

| # | Operation | DynamoDB | Key / filter |
|---|-----------|----------|-------------|
| AP1 | Get by id | GetItem | PK=`CUSTOMER#<id>`, SK=`METADATA` |
| AP2 | List (no filter) | Scan | Filter: `SK = 'METADATA'` |
| AP3 | List by status | Query on `StatusIndex` | PK=`<status>` |
| AP4 | Create | PutItem | PK=`CUSTOMER#<uuid>`, SK=`METADATA` |
| AP5 | Update | UpdateItem | PK=`CUSTOMER#<id>`, SK=`METADATA` |
| AP6 | Delete | DeleteItem | PK=`CUSTOMER#<id>`, SK=`METADATA` |

**Architectural decisions:**

1. **Generic `PK`/`SK` names** — matches `benefits-api`, `engagement-service`, `customer-verification`. Allows future entity types without schema migration.
2. **`CUSTOMER#<uuid>` prefix** — future-proofs for sub-items (`CONFIG#`, `OVERRIDE#`) under the same PK.
3. **`SK = 'METADATA'`** — literal marker for the customer record; sort key slot reserved for future item collections.
4. **StatusIndex GSI** — supports `?status=` filter on `listCustomers` via efficient Query (avoids Scan with filter).
5. **Scan for unfiltered list** — acceptable for v1 low volume. Add `EntityTypeIndex` (PK=`SK`, SK=`PK`) later if volume exceeds ~10K items.
6. **`registeredAddress` stored as nested Map** — matches the API schema structure; DynamoDB supports nested objects natively.

**Assumptions:**

| # | Assumption | Mitigation if wrong |
|---|-----------|---------------------|
| A1 | No multi-tenancy in v1 | Add `TenantIndex` GSI |
| A2 | No DynamoDB streams in v1 | Enable `NEW_AND_OLD_IMAGES` when events needed |
| A3 | AWS-managed SSE (no custom KMS) | Add `AWS::KMS::Key` if compliance requires |
| A4 | Hard delete for DELETE | Switch to soft-delete + `deletedAt` if audit needed |
| A5 | DynamoDB VPC gateway endpoint exists in account | Required for VPC-attached Lambda → DynamoDB access |
| A6 | Low data volume (Scan OK) | Add GSI to convert Scan → Query |

Document: `docs/design/persistence.md` in the service repo.

### 3.2 Add DynamoDB to SAM template

In **`template.yaml`**:

1. **Parameter:** `DynamoDbPrefixListId` (default `pl-02cd2c6b`, matches `benefits-api` and `engagement-service`).
2. **Resource:** `CustomersTable` — `AWS::DynamoDB::Table` with PK/SK + `StatusIndex` GSI. PAY_PER_REQUEST, PITR, SSE.
3. **Security group:** Uncomment DynamoDB egress rule using the prefix list ID.
4. **Global env var:** `CUSTOMERS_TABLE: !Ref CustomersTable` in `Globals.Function.Environment.Variables`.
5. **IAM:** `LambdaDynamoDBPolicy` — `dynamodb:GetItem`, `PutItem`, `UpdateItem`, `DeleteItem`, `Query`, `Scan` scoped to the table and its indexes.

### 3.3 Repository layer

| File | Purpose | Pattern reference |
|------|---------|-------------------|
| `src/module/database/dynamodb-client.ts` | DynamoDB client singleton + table name | `benefits-api` `database/benefits-dynamo-db.ts` |
| `src/module/model/customer.ts` | Domain types (`CustomerRecord`, `CreateCustomerInput`, etc.) | — |
| `src/module/repository/customer-repository.ts` | CRUD: `getById`, `list`, `create`, `update`, `delete` | `benefits-api` `repo/benefits.ts` |

- Uses **AWS SDK v3** (`@aws-sdk/lib-dynamodb`, `DynamoDBDocumentClient`). Already externalized in `esbuild.js`.
- Repository returns **domain types** (`src/module/model/`), not generated API types. Handlers map between API ↔ domain.
- Table name from `process.env.CUSTOMERS_TABLE`.
- Path aliases added: `#database/*`, `#repository/*`.

---

## 4. Phase C: Development (handlers and middleware)

### 4.1 Align handlers with the BC spec

For each operation in `docs/api/openapi-spec.yaml`:

1. **Create a handler directory** under `src/function/`, e.g.:
   - `list-customers/`
   - `get-customer-by-id/`
   - `create-customer/`
   - `update-customer/`
   - `delete-customer/`
2. **Implement the handler** in `app.ts`:
   - Parse path/query/body from the API Gateway event.
   - Validate input (use generated types or Zod schemas derived from the OpenAPI schemas).
   - Call the repository (and any application service in `src/module/service/`).
   - Return status and body that match the BC spec (use generated response types where possible).
3. **Wrap with middleware** — Use the existing `apiGatewayMiddleware` from `#middleware/middleware` for tracing, error handling, and response formatting.
4. **Unit tests** — Add tests under `test/function/<name>/` that mock the repository and assert status codes and body shape.

### 4.2 Wire new functions in SAM

In **`template.yaml`**:

1. **Resources:** Add an `AWS::Serverless::Function` per new handler (e.g. `ListCustomersFunction`, `GetCustomerByIdFunction`). Reuse the same Globals (runtime, env, VpcConfig, Layers). Set `CodeUri` to the built output for that function (e.g. `dist/list-customers`).
2. **API definition:** The API uses `DefinitionBody` from `docs/api/openapi-spec.yaml`. Ensure each path has an `x-amazon-apigateway-integration` that points to the correct Lambda (e.g. `ListCustomersFunction.Alias`). Match the `operationId` or path to the integration.
3. **ApiExecutionRole:** Extend the policy so API Gateway can `lambda:InvokeFunction` on every new function's alias.
4. **Build:** Ensure `esbuild.js` (or your build script) produces one output directory per function under `dist/`.

### 4.3 Validation and errors

- Validate path/query/body early; return **400** with a clear body when validation fails.
- Use **404** when a resource is not found (e.g. customer id not in DynamoDB).
- Use **500** only for unexpected errors; log and do not leak internals. Use the existing middleware and logging so that responses match your BC spec's response schemas.

### Architectural decisions (Phase C)

**Reference repos checked via `gh` CLI:** `benefits-api`, `customer-verification`, `engagement-service` (all gp-nova).

#### 1. Handler layout: one function per operation

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Directory structure** | `src/function/<operation-name>/app.ts` | All three repos use `src/function/<name>/app.ts` |
| **Handler export** | `export const main = apiGatewayMiddleware(handler, '<name>')` | `customer-verification` uses `app.main`; `benefits-api` uses `app.handler` |
| **SAM `Handler` global** | `app.main` | Matches `customer-verification` scaffold pattern |
| **Middleware** | `apiGatewayMiddleware` wraps every handler (tracing, metrics, error handling, JSON parsing) | Consistent with scaffold and `customer-verification` |

**Justification:**
- One Lambda per API operation follows the standard G-P pattern. All reference repos have a 1:1 mapping between API paths and Lambda functions.
- The `app.main` export name is inherited from the scaffold's `apiGatewayMiddleware` and matches `customer-verification`. `benefits-api` uses `app.handler` — both are valid; the SAM `Handler` global must match the export.
- Keeping the middleware wrapper on every handler ensures consistent observability (traces, metrics, structured logging) without per-handler boilerplate.

#### 2. SAM function resources

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **One `AWS::Serverless::Function` per handler** | `ListCustomersFunction`, `GetCustomerByIdFunction`, `CreateCustomerFunction`, `UpdateCustomerFunction`, `DeleteCustomerFunction` | All repos: one resource per handler |
| **`CodeUri`** | `dist/<operation-name>` (e.g. `dist/list-customers`) | All repos: `dist/<name>` |
| **`SkipBuild: true`** | Yes (esbuild handles bundling) | All repos: `SkipBuild: true` |
| **`FunctionName`** | `${StackName}-<FunctionName>` (hyphen separator) | `customer-verification`: `${StackName}-<Name>` (hyphen); `benefits-api`: `${StackName}<Name>` (no separator) |
| **`DeploymentPreference`** | `AllAtOnce` with `CodeDeployServiceRole` | Matches `customer-verification` |
| **`PermissionsBoundary`** | `gp-boundary` | All repos |
| **`Layers`** | OTEL lambda layer via `GpTelemetryLambdaLayerArn` | All repos use telemetry layers |
| **`AutoPublishAlias`** | `!Ref LiveAlias` (from Globals) | All repos use aliased deployments |

**Justification:**
- Reuse `Globals.Function` settings (runtime, VPC, env, tracing, telemetry) so each resource only declares what's unique: `Description`, `FunctionName`, `CodeUri`, `Policies`.
- `SkipBuild: true` because `esbuild.js` already builds each function into `dist/<name>/app.js`. SAM should not attempt a second build. This is the universal pattern in G-P TS repos.
- Hyphen separator in `FunctionName` matches `customer-verification` (the customer BC reference repo). `benefits-api` omits the separator — both are valid but we follow the customer domain convention.

#### 3. IAM policy: per-function SAM policy templates

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Read-only functions** (list, get) | `DynamoDBReadPolicy: { TableName: !Ref CustomersTable }` | `benefits-api` uses `DynamoDBReadPolicy` for read-only functions |
| **Write functions** (create, update, delete) | `DynamoDBCrudPolicy: { TableName: !Ref CustomersTable }` | `benefits-api` uses `DynamoDBCrudPolicy` for write functions |
| **Standalone managed policy** | Removed (`LambdaDynamoDBPolicy` deleted) | — |

**Justification:**
- SAM policy templates (`DynamoDBReadPolicy`, `DynamoDBCrudPolicy`) are the cleanest approach: SAM automatically creates an IAM role per function with the correct policy, scoped to the specific table. No manual ARN construction needed.
- `benefits-api` uses this exact pattern: `DynamoDBReadPolicy` for read-only functions, `DynamoDBCrudPolicy` for functions that write. We follow the same principle of least privilege — read-only handlers do not receive write permissions.
- `customer-verification` uses inline IAM statements instead — also valid, but more verbose and error-prone. SAM policy templates are preferred when they fit the use case.
- The standalone `LambdaDynamoDBPolicy` (a shared managed policy from Phase B) was removed because per-function policies are the standard. A shared policy would require manual attachment and grants the same permissions to all functions regardless of need.

#### 4. LogGroup and observability

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **LogGroup per function** | Yes: `ListCustomersFunctionLogGroup`, etc. | `customer-verification`: per-function log groups |
| **LogGroup DeletionPolicy** | `!If [IsProduction, Retain, Delete]` | `benefits-api`: same conditional pattern |
| **SubscriptionFilter** | Per-function, conditional on `IsNotDev`, → Coralogix Firehose | `benefits-api`: single filter on shared group; `customer-verification`: per-function |
| **Coralogix ingestion** | `arn:aws:firehose:…/coralogix-log-ingestion` with `cloudwatch-to-firehose-role` | Exact same ARN/role across all repos |

**Justification:**
- Per-function log groups allow independent retention and access control. `customer-verification` uses this approach. `benefits-api` uses a shared `LambdaCloudWatchLogGroup` with `LoggingConfig.LogGroup` on each function — a newer, more concise pattern that reduces resource count. Either is valid; per-function is the safer default for a new service (isolates log streams).
- Conditional `DeletionPolicy` (`Retain` in prod, `Delete` otherwise) prevents accidental log loss in production while keeping lower environments clean. Matches `benefits-api`.
- Subscription filters forward logs to Coralogix via Firehose. The `IsNotDev` condition avoids noise in dev. Same pattern as the scaffold and reference repos.

#### 5. ApiExecutionRole

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Explicit alias list** | One `!Ref <Function>.Alias` per function in `lambda:InvokeFunction` | `benefits-api`: identical approach (explicit list, no wildcards) |

**Justification:**
- Explicit alias references are the `benefits-api` pattern. API Gateway can only invoke the specific function aliases listed — no wildcard grants. This is least-privilege.
- Each time a new function is added, its alias must be added to the role. This is intentional friction that prevents accidental exposure.

#### 6. Build pipeline (esbuild)

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Entry points** | Dynamic: `fs.readdirSync('src/function').map(…)` | `benefits-api` (`esbuild.mjs`): same dynamic pattern |
| **Output** | `dist/<name>/app.js` | Same |
| **Externals** | `['@aws-sdk']` | Same (Lambda runtime provides the SDK) |
| **Bundle + minify** | Yes | Same |
| **Platform** | `node` | Same |

**Justification:**
- Dynamic entry-point discovery means adding or removing a handler directory automatically includes or excludes it from the build — no build config changes needed.
- `@aws-sdk` is externalized because Lambda's Node.js runtime includes it. Bundling would increase cold start and artifact size. All G-P TS repos follow this convention.

#### 7. Scaffold removal

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Removed `hello-gp-world`** | Deleted `src/function/hello-gp-world/`, `test/function/hello-gp-world/`, `e2e/hello.steps.ts`, `features/hello.feature` | `engagement-service` still retains `hello-gp-world`; `customer-verification` and `benefits-api` do not have it |

**Justification:**
- The scaffold `hello-gp-world` handler has no business purpose once real handlers exist. Keeping it would add a deployable Lambda with no route (the OpenAPI spec no longer references it), wasting resources and confusing the template.
- `benefits-api` and `customer-verification` do not have a scaffold endpoint in their current mainline. `engagement-service` still has one — likely an oversight or used for health checks (they have a separate health function too).
- The SAM function resource, its log group, and its subscription filter were all removed to keep the template clean.

#### 8. Validation and error handling

| Decision | Our implementation | Reference |
|----------|-------------------|-----------|
| **Inline validation** | Each handler validates its own input and returns `{ message }` error bodies | `customer-verification`: central `failResponse` helper |
| **Error body shape** | `{ message: string }` matching OpenAPI `ErrorResponse` schema | `customer-verification`: same shape via helper |
| **Status codes** | 400 (validation), 404 (not found), 201 (create), 204 (delete), 200 (others) | `customer-verification`: same codes via helper |
| **List response mapping** | `toSummary()` maps `CustomerRecord` → `CustomerSummary` (5 fields) | `benefits-api`: no explicit summary mapping found |

**Justification:**
- Inline validation is the simplest approach for v1 with 5 handlers. `customer-verification` uses a central `failResponse(error)` utility that maps error types to status codes — a cleaner pattern that becomes worthwhile at scale. A central error utility can be extracted in a later iteration without changing the API contract.
- All error responses use the `{ message }` shape defined in `components/schemas/ErrorResponse` in the OpenAPI spec. The `code` and `details` fields are optional per the spec and omitted for v1.
- The `toSummary()` mapper in the list handler ensures the response matches `CustomerListResponse.items` which references `CustomerSummary` (only `customerId`, `companyName`, `legalEntityName`, `companyCode`, `status`). This is stricter than necessary (JSON clients tolerate extra fields) but keeps the API response honest to its spec — important for API-first design.

#### Standards alignment summary (Phase C)

| Aspect | G-P standard (from reference repos) | Our implementation | Aligned? |
|--------|--------------------------------------|-------------------|----------|
| Handler layout | `src/function/<name>/app.ts` | Same | ✅ |
| Handler export | `app.main` or `app.handler` | `app.main` (matches `customer-verification`) | ✅ |
| One Lambda per operation | Yes (all repos) | Yes (5 functions) | ✅ |
| `CodeUri: dist/<name>` | Yes (all repos) | Yes | ✅ |
| `SkipBuild: true` | Yes (all repos) | Yes | ✅ |
| Per-function SAM DynamoDB policies | `benefits-api` pattern | `DynamoDBReadPolicy` / `DynamoDBCrudPolicy` | ✅ |
| ApiExecutionRole explicit alias list | `benefits-api` pattern | Same | ✅ |
| LogGroup conditional DeletionPolicy | `benefits-api`: `IsProduction` conditional | Same | ✅ |
| SubscriptionFilter → Coralogix | All repos | Same (per-function, `IsNotDev`) | ✅ |
| OTEL telemetry layer | All repos | Same | ✅ |
| `DeploymentPreference: AllAtOnce` | `customer-verification` | Same | ✅ |
| `PermissionsBoundary: gp-boundary` | All repos | Same | ✅ |
| esbuild dynamic entry points + `@aws-sdk` external | `benefits-api` | Same | ✅ |
| Scaffold endpoint removed | `benefits-api`, `customer-verification` (no scaffold in mainline) | Removed | ✅ |
| Error response shape | `{ message }` | Same | ✅ |

**No gaps identified.** All Phase C decisions align with established G-P patterns. The two minor variations (per-function log groups vs shared, inline validation vs central helper) are both valid approaches seen across reference repos and can be converged in later iterations.

---

## 5. Phase D: Private API — finish and operate

### 5.1 Checklist for "private only"

**Audit performed against `customer-verification`, `benefits-api`, and the `devx-shared-workflows` CI pipeline via `gh` CLI.**

- [x] **BC spec** in `docs/api/openapi-spec.yaml` is complete, linted, and types are generated.
  - 5 CRUD operations with schemas, parameters, error responses.
  - `npm run api:lint` (Redocly) passes.
  - `npm run api:types` generates `src/module/types/api.ts` via `openapi-typescript`.
- [x] **Persistence** (DynamoDB) is in the SAM template and repository layer is implemented.
  - `CustomersTable` resource with PK/SK, StatusIndex GSI, PAY_PER_REQUEST, PITR, SSE, conditional deletion protection.
  - Repository layer: `dynamodb-client.ts`, `customer.ts`, `customer-repository.ts`.
  - **Gap: unit tests for repository not yet written.** Repository functions are exercised through handler integration but dedicated unit tests (mocking DynamoDB client) are a follow-up.
- [x] **All handlers** are implemented, wired in SAM.
  - 5 Lambda functions: `ListCustomersFunction`, `GetCustomerByIdFunction`, `CreateCustomerFunction`, `UpdateCustomerFunction`, `DeleteCustomerFunction`.
  - Per-function SAM DynamoDB policies (`DynamoDBReadPolicy` / `DynamoDBCrudPolicy`).
  - API Gateway integration URIs in OpenAPI spec match function names.
  - **Gap: handler unit tests not yet written.** Scaffold hello-gp-world test was removed; new handler tests are a follow-up.
- [x] **CI runs:** build, lint, tests, `sam validate --lint`.
  - **PR workflow** (`pull-request.yaml`): `serverless-test-npm.yaml@v6` → lint code (eslint + prettier), lint infra (cfn-lint + cfn-nag), unit test. Then `serverless-pr-npm.yaml@v6` → build.
  - **Main build** (`main-build.yaml`): same lint/test, then `serverless-build-npm.yaml@v6` → package artifact, deploy to dev.
  - **`pre-commit` script** also runs `api:lint` (Redocly) and `cfn-lint`.
  - **Note:** `api:lint` (Redocly) runs in `pre-commit` but is not in the shared CI workflow. This is consistent with `benefits-api` where Redocly lint is a repo-level concern, not a shared workflow step.
- [x] **Deploy** to dev/test is configured.
  - `deploy.yaml` uses `deploy-v1.yaml@v6` shared workflow.
  - Main build auto-deploys to dev.
  - Artifact includes `dist/`, `docs/api/*.yaml`, `scripts/`, `samconfig.toml`, `catalog-info.yaml`.
- [x] **Private URL and VPC endpoint** are configured.
  - `EndpointConfiguration: PRIVATE` on the API.
  - Resource policy restricts access via `aws:SourceVpce`:
    - dev: hardcoded `vpce-0f69b2da2e44d29a6`
    - non-dev: SSM parameter `/network/hub/vpc/endpoint/execute-api/id`
  - Matches `customer-verification` and `benefits-api` resource policy structure exactly.
- [x] **Backstage catalog** — Component and API entities in `catalog-info.yaml`.
  - Component: `global-payroll-customer-management-system`, type `service`, lifecycle `production`.
  - API: `global-payroll-customer-management-system-api`, type `openapi`, lifecycle `production`.
  - `spec.definition.$text` points to `docs/api/openapi-spec.yaml` on main branch.
  - Matches `customer-verification` and `benefits-api` catalog structure exactly.

**Outstanding items (follow-up, not blocking):**

| Item | Priority | Effort |
|------|----------|--------|
| Unit tests for handlers (`test/function/<name>/`) | High | ~1 day |
| Unit tests for repository (`test/module/repository/`) | Medium | ~0.5 day |
| Add `api:lint` to CI as a custom job (or wait for shared workflow update) | Low | ~1 hr |

### 5.2 mTLS and callers

**Access documentation created:** `docs/private-api-access.md` (also linked from `docs/index.md` and registered in `mkdocs.yml`).

**Summary of access model:**

| Layer | Mechanism | Status |
|-------|-----------|--------|
| **Network** | Private API Gateway — not internet-accessible | ✅ Configured |
| **Resource policy** | Denies requests not from allowed VPC endpoint (`aws:SourceVpce`) | ✅ Configured |
| **Lambda network** | Private subnets, restricted security group egress (DynamoDB prefix list + OTEL only) | ✅ Configured |
| **mTLS** | Not configured for v1 | ⬜ Future (if needed) |

**Decision: no mTLS for v1.** Neither `customer-verification` nor `benefits-api` configure mTLS on their private API Gateway. Access control relies on:

1. VPC endpoint restriction (resource policy)
2. Network isolation (private subnets, no internet gateway)
3. Security group egress rules

mTLS would be added if cross-account or cross-region callers need access, or if compliance requires mutual authentication. The Confluence [Private Services](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/3257729316/Private+Services) guide documents the mTLS setup process.

**For WES integration (future):**

- Ensure WES's VPC endpoint ID is in the `aws:SourceVpce` condition of the resource policy.
- WES calls the private URL, maps canonical ↔ internal types, returns canonical response.
- No changes to the API itself are needed — only the resource policy's VPC endpoint list.

**Documentation delivered:**

| File | Content |
|------|---------|
| `docs/index.md` | Updated: project description, API surface, architecture overview, playbook, runbook |
| `docs/private-api-access.md` | New: private base URLs, resource policy, caller instructions, mTLS status, security group, VPC endpoints |
| `mkdocs.yml` | Updated: added Private API Access page to nav |
| `catalog-info.yaml` | Updated: descriptions, API lifecycle → `production` |

---

## 6. Phase E: External (open) API — optional

Only do this when you need to expose customer management (or a subset) to external partners/customers.

### 6.1 Design the external contract

- The **external** contract is **not** the same as `docs/api/openapi-spec.yaml`. It lives in the **integrations-external-api** repo and describes the **public** surface (canonical types and paths).
- Decide which operations and fields to expose (often a subset). Use **canonical model** names (e.g. `Customer` as defined in work-engagement-canonical-model or the agreed external schema), not internal DB field names.
- Document the desired public paths (e.g. `GET /v1/customers`, `GET /v1/customers/{customerId}`, `DELETE /v1/customers/{customerId}`) and request/response shapes in an RFC or ticket before changing code.

### 6.2 Canonical model

- If the canonical model does not yet have types for "customer" in the sense you need, add or extend them in [work-engagement-canonical-model](https://github.com/gp-nova/work-engagement-canonical-model) (TypeScript + Zod).
- Follow the [Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model).
- Publish the updated package so WES and integrations-external-api can consume it.

### 6.3 WES proxy (work-engagement-sync)

- In [work-engagement-sync](https://github.com/gp-nova/work-engagement-sync), add (or extend) a Lambda that:
  - Receives the **external** request (canonical shape).
  - Maps to your **internal** shape if needed, then calls your **private** Customer Management System API via mTLS.
  - Maps the internal response back to the **canonical** shape and returns it.
- Wire this Lambda to the appropriate path in WES's API Gateway. Ensure your private API's resource policy allows invocations from WES.

### 6.4 integrations-external-api and Kong

- In [integrations-external-api](https://github.com/gp-nova/integrations-external-api):
  - Add a **proxy** function that forwards the public request to the WES endpoint you added and returns the response.
  - Register the route so Kong can route to it.
  - **Update the public OpenAPI spec** in that repo (`docs/api/openapi-spec.yaml` or equivalent) with the new paths and **canonical** schemas. That spec is what external users see.
- Configure Kong (via integrations-external-api-gateway or extensions) so the new path is exposed under the public base URL. Document the public base URL and auth (e.g. API key) in the Developer Portal.

### 6.5 Backstage for the public API

- Register the **public** API in the catalog (in integrations-external-api's catalog or where your team maintains it): Kind `API`, `spec.type: openapi`, `spec.definition` pointing at the **public** spec. That makes the external API discoverable in Roadie.

---

## 7. Order of work (summary)

| Order | Phase | What to do |
|-------|--------|------------|
| 1 | **A – API design** | Define resources; write and lint BC spec; generate types. |
| 2 | **B – Persistence** | Design tables/keys; add DynamoDB and repo layer. |
| 3 | **C – Development** | Implement handlers and wire them in SAM; add tests. |
| 4 | **D – Private API** | CI, deploy, document private URL and mTLS; Backstage BC spec. |
| 5 | **E – External (optional)** | External contract in integrations-external-api; canonical model; WES proxy; public proxy + Kong; register public API. |

---

## 8. File and folder checklist (this repo)

- [x] `docs/api/openapi-spec.yaml` — Full BC spec (paths, schemas, operationIds).
- [x] `docs/design/api-resources.md` (or similar) — Resource and path decisions.
- [x] `docs/design/persistence.md` (or similar) — DynamoDB key design and access patterns.
- [x] `docs/index.md` — TechDocs home: project description, API surface, architecture, playbook, runbook.
- [x] `docs/private-api-access.md` — Private API access guide: base URLs, resource policy, caller instructions, mTLS status.
- [x] `src/module/types/api.ts` — Generated API types (from openapi-typescript).
- [x] `src/module/database/dynamodb-client.ts` — DynamoDB client singleton and table name accessor.
- [x] `src/module/model/customer.ts` — Domain types for the Customer aggregate.
- [x] `src/module/repository/customer-repository.ts` — DynamoDB CRUD for Customers.
- [ ] `src/module/service/` — Optional application logic (or keep in handlers).
- [x] `src/function/<operation>/app.ts` — One handler per operation (list, get, create, update, delete).
- [ ] `test/function/<operation>/` — Unit tests per handler.
- [x] `template.yaml` — DynamoDB table(s), new Lambdas, API integrations, IAM, env vars.
- [x] `esbuild.js` — Builds all new functions into `dist/<function-name>`.
- [x] `package.json` — Scripts for API spec lint and type generation; deps (e.g. openapi-typescript, DynamoDB client). Optionally: Redocly (`@redocly/cli`), Spectral (`@stoplight/spectral-cli`), Zod generator.
- [x] `catalog-info.yaml` — Component + API entity with correct `definition` for the BC spec.
- [x] `mkdocs.yml` — TechDocs nav with home and private API access pages.

---

## 9. References

| Doc | Purpose |
|-----|--------|
| [api-development-guide-private-and-public.md](api-development-guide-private-and-public.md) | Org-wide private vs public API steps and two-spec model. |
| [openapi-scaffolder-vs-external.md](openapi-scaffolder-vs-external.md) | Why the BC spec ≠ external spec. |
| [new-bounded-context-api-guide.md](new-bounded-context-api-guide.md) | Full BC setup (infra, Atlan, CodeArtifact, telemetry). |
| [Confluence — Spec Setup](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/5258444869/Spec+Setup) | OpenAPI spec location and version (docs/api, 3.x). |
| [Confluence — Private Services](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/3257729316/Private+Services) | Private API Gateway, mTLS requirements. |
| [Confluence — Runbook: Updating the Canonical Model](https://globalization-partners.atlassian.net/wiki/spaces/GPARCH/pages/4725670129/Runbook+Updating+the+Canonical+Model) | Canonical model changes. |
