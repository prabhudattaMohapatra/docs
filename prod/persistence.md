# Persistence design — Customer Management System (v1)

Design document for the DynamoDB table, key schema, access patterns, and repository layer for v1 of the Customer Management System.

**Status:** Draft (Phase B — 3.1)
**See also:** Build plan in docs repo: `docs/prod/BUILD-PLAN-API-PRIVATE-AND-EXTERNAL.md`

---

## 1. Store choice: DynamoDB

| Criterion | Decision |
|-----------|----------|
| **Store** | Amazon DynamoDB |
| **Billing** | PAY_PER_REQUEST (on-demand) |
| **Encryption** | SSE with AWS-managed key |
| **PITR** | Enabled |
| **Streams** | Disabled for v1 |
| **TTL** | Not used for v1 |

### Justification

- **DynamoDB** is the standard persistence choice for serverless TypeScript services in G-P (used by `benefits-api`, `engagement-service`, `customer-verification`).
- **PAY_PER_REQUEST** — appropriate for a new service with unpredictable traffic. All three reference repos use this billing mode. Can be switched to provisioned capacity later if traffic patterns stabilize.
- **SSE (AWS-managed)** — enables encryption at rest without managing a custom KMS key. `customer-verification` uses the same approach. `engagement-service` uses a custom KMS key, which can be added later if compliance requires it.
- **PITR** — both `engagement-service` and `customer-verification` enable point-in-time recovery. Provides continuous backups and is critical for a customer aggregate.
- **Streams** — not needed for v1 (no event-driven downstream consumers). Can be enabled later when EventBridge integration is added (e.g., for `CustomerCreated`, `CustomerUpdated` events).
- **TTL** — not applicable to customer records (customers are not ephemeral).

---

## 2. Table design

**Table name:** `${StackName}-Customers`

### 2.1 Key schema

| Attribute | Type | Key role | Example |
|-----------|------|----------|---------|
| `PK` | String (S) | Partition key (HASH) | `CUSTOMER#a1b2c3d4-...` |
| `SK` | String (S) | Sort key (RANGE) | `METADATA` |

### 2.2 GSI: StatusIndex

| Attribute | Type | Key role | Example |
|-----------|------|----------|---------|
| `status` | String (S) | Partition key | `active` |
| `PK` | String (S) | Sort key | `CUSTOMER#a1b2c3d4-...` |

Projection: **ALL** (project all attributes to avoid fetches back to the base table).

### 2.3 Key design justification

1. **Generic `PK`/`SK` attribute names** — matches the pattern used by `benefits-api` (PK/SK), `engagement-service` (pk/sk), and `customer-verification` (PK/SK). Generic names allow multiple entity types in the same table without schema migration.

2. **`CUSTOMER#<uuid>` prefix in PK** — future-proofs the table for additional entity types (configurations, overrides) that may share the same table. When sub-items are added, they could use the same PK with a different SK (e.g., `CONFIG#<id>`, `OVERRIDE#<id>`), enabling efficient single-PK queries for an entire customer aggregate.

3. **`SK = 'METADATA'` literal** — marks the customer record itself. This is a common DynamoDB single-table design pattern. For v1 there is only one item per PK, but the SK slot is reserved for future item collections.

4. **StatusIndex GSI** — the `listCustomers` endpoint supports `?status=active|inactive|pending`. A GSI with PK=`status` enables an efficient Query for filtered listing, avoiding a full Scan with a filter expression.

---

## 3. Access patterns

| # | Operation | API endpoint | DynamoDB | Key condition / filter |
|---|-----------|-------------|----------|----------------------|
| AP1 | Get customer by id | `GET /customers/{customerId}` | `GetItem` on base table | PK=`CUSTOMER#<id>`, SK=`METADATA` |
| AP2 | List customers (no filter) | `GET /customers` | `Scan` on base table | FilterExpression: `SK = 'METADATA'` |
| AP3 | List customers by status | `GET /customers?status=active` | `Query` on `StatusIndex` | PK=`<status>` |
| AP4 | Create customer | `POST /customers` | `PutItem` | PK=`CUSTOMER#<uuid>`, SK=`METADATA` |
| AP5 | Update customer | `PUT /customers/{customerId}` | `UpdateItem` | PK=`CUSTOMER#<id>`, SK=`METADATA` |
| AP6 | Delete customer | `DELETE /customers/{customerId}` | `DeleteItem` | PK=`CUSTOMER#<id>`, SK=`METADATA` |

### Notes on AP2 (unfiltered list)

For v1 with low volume, a Scan with filter is acceptable. If the table grows significantly, an additional GSI (e.g., `EntityTypeIndex` with PK=`SK`, SK=`PK`) can be added to convert the Scan into a Query — no schema change needed since `SK` is already an attribute on every item.

### Pagination

- All list operations use DynamoDB's `ExclusiveStartKey`/`LastEvaluatedKey` for cursor-based pagination.
- The API's `nextToken` is a base64-encoded, opaque representation of `LastEvaluatedKey`.
- The API's `limit` maps to DynamoDB's `Limit` parameter.

---

## 4. Item shape (DynamoDB record)

The stored item maps to the `Customer` schema from the OpenAPI spec, plus DynamoDB key attributes.

| DynamoDB attribute | Source (OpenAPI field) | Type | Notes |
|-------------------|----------------------|------|-------|
| `PK` | derived from `customerId` | S | `CUSTOMER#<uuid>` |
| `SK` | — | S | `METADATA` (literal) |
| `customerId` | `customerId` | S | UUID, stored separately for GSI sort keys and API responses |
| `companyName` | `companyName` | S | |
| `legalEntityName` | `legalEntityName` | S | |
| `companyCode` | `companyCode` | S | Optional |
| `taxIdNumber` | `taxIdNumber` | S | Optional |
| `registrationNumber` | `registrationNumber` | S | Optional |
| `incorporationDate` | `incorporationDate` | S | ISO 8601 date (YYYY-MM-DD) |
| `registeredAddress` | `registeredAddress` | M (Map) | Nested object: `{ addressLine1, addressLine2, city, stateProvince, postalCode, countryCode }` |
| `kybStatus` | `kybStatus` | S | Enum: `not_started`, `in_progress`, `approved`, `rejected` |
| `status` | `status` | S | Enum: `active`, `inactive`, `pending` — also the PK of `StatusIndex` |
| `payrollStartDate` | `payrollStartDate` | S | ISO 8601 date (YYYY-MM-DD), optional |
| `createdAt` | `createdAt` | S | ISO 8601 date-time |
| `updatedAt` | `updatedAt` | S | ISO 8601 date-time |

### Nested `registeredAddress`

Stored as a DynamoDB Map attribute (not flattened). This matches the API schema and avoids attribute-name collisions with top-level fields. DynamoDB natively supports nested objects in document mode.

---

## 5. Repository layer

| File | Purpose | Pattern reference |
|------|---------|-------------------|
| `src/module/database/dynamodb-client.ts` | DynamoDB client singleton + table name accessor | `benefits-api` (`database/benefits-dynamo-db.ts`) |
| `src/module/model/customer.ts` | Domain types (`CustomerRecord`, `CreateCustomerInput`, `UpdateCustomerInput`) | — |
| `src/module/repository/customer-repository.ts` | CRUD operations for the Customer aggregate | `benefits-api` (`repo/benefits.ts`) |

### Design decisions

- **AWS SDK v3** (`@aws-sdk/lib-dynamodb` with `DynamoDBDocumentClient`) — the standard in G-P TS repos. Already externalized in `esbuild.js` config (`external: ['@aws-sdk']`), so it's not bundled (Lambda runtime provides it).
- **Repository returns domain types, not API types** — per the build plan: "Keep no HTTP or OpenAPI types in the repository layer." Handlers are responsible for mapping between API types and domain types.
- **Table name from `process.env.CUSTOMERS_TABLE`** — passed via SAM template `Globals.Function.Environment`.
- **Path aliases**: `#database/*`, `#repository/*` added to `package.json` imports map.

---

## 6. Assumptions

| # | Assumption | Impact if wrong |
|---|-----------|----------------|
| A1 | **No multi-tenancy in v1.** The `tenantId` query param was removed from the OpenAPI spec. All customers are in a single partition namespace. | If multi-tenancy is needed, add a `TenantIndex` GSI (PK=`tenantId`, SK=`PK`). No base table schema change required. |
| A2 | **No DynamoDB streams in v1.** No event-driven consumers downstream. | Enable streams (`NEW_AND_OLD_IMAGES`) and add an EventBridge pipe/Lambda when events are needed. |
| A3 | **AWS-managed SSE, no custom KMS key.** | If compliance requires customer-managed keys, add a `AWS::KMS::Key` resource and reference it in `SSESpecification.KMSMasterKeyId`. |
| A4 | **Hard delete** for `DELETE` operation. | If audit trail is needed, switch to soft-delete (set `status = 'deleted'`, add a `deletedAt` timestamp). |
| A5 | **DynamoDB VPC gateway endpoint exists** in the account (via `devx-common-infra` or equivalent). Lambdas reach DynamoDB through the prefix list egress rule. | If the endpoint doesn't exist, DynamoDB calls will fail from VPC-attached Lambdas. |
| A6 | **Low data volume in v1.** Scan for unfiltered list is acceptable. | Add an `EntityTypeIndex` GSI if volume exceeds ~10K items to convert Scan to Query. |

---

## 7. Out of scope for v1

- DynamoDB streams and EventBridge integration.
- Custom KMS encryption key.
- Soft-delete / audit trail.
- Multi-tenancy partitioning.
- Bulk operations (batch write).
- Global tables / multi-region replication.

---

## 8. Next steps (per build plan)

1. **3.2** — Add DynamoDB table + GSI, IAM policy, VPC egress, and `CUSTOMERS_TABLE` env var to `template.yaml`.
2. **3.3** — Implement `database/dynamodb-client.ts`, `model/customer.ts`, `repository/customer-repository.ts`.
3. **Phase C** — Wire handlers to the repository layer.
