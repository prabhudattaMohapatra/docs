# API resources and operations (v1)

Design document for the internal API surface of the Global Payroll Customer Management System. This defines resource names, path layout, and operations for **v1** of the BC API.

**Status:** Draft (Phase A — 2.1)  
**See also:** Build plan in docs repo: `docs/prod/BUILD-PLAN-API-PRIVATE-AND-EXTERNAL.md`

---

## 1. Resource summary

| # | Resource | Path prefix | Description | Operations | Phase |
|---|----------|-------------|-------------|------------|-------|
| 1 | **Customers** | `/api/v1/customers` | Top-level customer (company) | List, Get, Create, Update, Delete | **v1 (done)** |
| 2 | **Pay Groups** | `.../customers/{customerId}/pay-groups` | Group of employees sharing pay config | List, Get, Create, Update, Delete | v2 |
| 3 | **Pay Schedules** | `.../pay-groups/{payGroupId}/pay-schedules` | Payment frequency per pay group | List, Get, Create, Update, Delete | v2 |
| 4 | **Bank Accounts** | `.../customers/{customerId}/bank-accounts` | Payroll disbursement accounts | List, Get, Create, Update, Delete | v2 |
| 5 | **Benefits** | `.../customers/{customerId}/benefits` | Health, pension, insurance offerings | List, Get, Create, Update, Delete | v3 |
| 6 | **Leave Policies** | `.../customers/{customerId}/leave-policies` | One row per leave type (annual, sick, etc.) | List, Get, Create, Update, Delete | v3 |
| 7 | **Holiday Calendars** | `.../customers/{customerId}/holiday-calendars` | Named calendar per year | List, Get, Create, Update, Delete | v3 |
| 8 | **Holiday Calendar Entries** | `.../holiday-calendars/{calendarId}/entries` | Individual holiday dates | List, Get, Create, Update, Delete | v3 |
| 9 | **General Policies** | `.../customers/{customerId}/general-policies` | Expense, travel, remote work, probation, notice period | List, Get, Create, Update, Delete | v4 |
| 10 | **Overtime Policies** | `.../customers/{customerId}/overtime-policies` | Policy header (standard hours, etc.) | List, Get, Create, Update, Delete | v4 |
| 11 | **Overtime Policy Rules** | `.../overtime-policies/{policyId}/rules` | Per (context + tier) multiplier rules | List, Get, Create, Update, Delete | v4 |

Path versioning: **`/api/v1/...`**.

---

## 2. Path layout

### 2.1 Customers

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `/api/v1/customers` | `listCustomers` | Query: `tenantId?`, `status?`, `limit?`, `nextToken?` | `{ items: CustomerSummary[], nextToken? }` |
| GET | `/api/v1/customers/{customerId}` | `getCustomerById` | — | `Customer` |
| POST | `/api/v1/customers` | `createCustomer` | `CreateCustomerRequest` | `Customer` (201) |
| PUT | `/api/v1/customers/{customerId}` | `updateCustomer` | `UpdateCustomerRequest` | `Customer` |
| DELETE | `/api/v1/customers/{customerId}` | `deleteCustomer` | — | 204 (no body) |

- **Path parameter:** `customerId` — UUID.

#### Customer (full response)

| Field | Type | Req | RO | Notes |
|-------|------|-----|----|-------|
| `customerId` | uuid | yes | yes | Generated on create |
| `companyName` | string | yes | | Display name |
| `legalEntityName` | string | yes | | Registered legal entity name |
| `companyCode` | string | | | Short alphanumeric code |
| `taxIdNumber` | string | | | Format varies by country |
| `registrationNumber` | string | | | Business registration number |
| `incorporationDate` | date | | | YYYY-MM-DD |
| `registeredAddress` | Address | | | Embedded object (see below) |
| `kybStatus` | enum | | yes | `not_started` / `in_progress` / `approved` / `rejected` |
| `status` | enum | yes | | `active` / `inactive` / `pending` |
| `payrollStartDate` | date | | | YYYY-MM-DD |
| `createdAt` | date-time | yes | yes | |
| `updatedAt` | date-time | yes | yes | |

#### CustomerSummary (list item)

| Field | Type | Req |
|-------|------|-----|
| `customerId` | uuid | yes |
| `companyName` | string | yes |
| `legalEntityName` | string | yes |
| `companyCode` | string | |
| `status` | enum | yes |

#### CreateCustomerRequest

| Field | Type | Req | Notes |
|-------|------|-----|-------|
| `companyName` | string | yes | |
| `legalEntityName` | string | yes | |
| `companyCode` | string | | |
| `taxIdNumber` | string | | |
| `registrationNumber` | string | | |
| `incorporationDate` | date | | |
| `registeredAddress` | Address | | |
| `payrollStartDate` | date | | |
| `status` | enum | | Default: `active` |

#### UpdateCustomerRequest

All fields optional; only supplied fields are updated. Same fields as `CreateCustomerRequest` (no default for `status`).

#### Address (embedded in `registeredAddress`)

| Field | Type | Notes |
|-------|------|-------|
| `addressLine1` | string | |
| `addressLine2` | string | |
| `city` | string | |
| `stateProvince` | string | |
| `postalCode` | string | |
| `countryCode` | string(2) | ISO 3166-1 alpha-2 |

#### ErrorResponse (all 4xx / 5xx)

`{ message: string, code?: string, details?: object }`

**Source of truth:** `docs/api/openapi-spec.yaml`

---

### 2.2 Pay Groups

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/pay-groups` | `listPayGroups` | Query: `status?`, `limit?`, `nextToken?` | `{ items: PayGroupSummary[], nextToken? }` |
| GET | `.../pay-groups/{payGroupId}` | `getPayGroupById` | — | `PayGroup` |
| POST | `.../customers/{customerId}/pay-groups` | `createPayGroup` | `CreatePayGroupRequest` | `PayGroup` (201) |
| PUT | `.../pay-groups/{payGroupId}` | `updatePayGroup` | `UpdatePayGroupRequest` | `PayGroup` |
| DELETE | `.../pay-groups/{payGroupId}` | `deletePayGroup` | — | 204 |

Key fields: `payGroupId`, `companyId`, `payGroupName`, `currencyCode` (ISO 4217), `payrollStatus` (active/inactive/pending_setup), `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

---

### 2.3 Pay Schedules

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../pay-groups/{payGroupId}/pay-schedules` | `listPaySchedules` | Query: `status?`, `limit?`, `nextToken?` | `{ items: PayScheduleSummary[], nextToken? }` |
| GET | `.../pay-schedules/{payScheduleId}` | `getPayScheduleById` | — | `PaySchedule` |
| POST | `.../pay-groups/{payGroupId}/pay-schedules` | `createPaySchedule` | `CreatePayScheduleRequest` | `PaySchedule` (201) |
| PUT | `.../pay-schedules/{payScheduleId}` | `updatePaySchedule` | `UpdatePayScheduleRequest` | `PaySchedule` |
| DELETE | `.../pay-schedules/{payScheduleId}` | `deletePaySchedule` | — | 204 |

Key fields: `payScheduleId`, `payGroupId`, `payrollScheduleName`, `payrollCalendar` (yearly/half-yearly/quarterly/monthly/bi-weekly/weekly), `payPeriod`, `firstMonthOfYear?`, `weekMode` (week/workweek), work day booleans (mon–sun), `payrollScheduleStatus`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

---

### 2.4 Bank Accounts

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/bank-accounts` | `listBankAccounts` | Query: `limit?`, `nextToken?` | `{ items: BankAccountSummary[], nextToken? }` |
| GET | `.../bank-accounts/{bankAccountId}` | `getBankAccountById` | — | `BankAccount` |
| POST | `.../customers/{customerId}/bank-accounts` | `createBankAccount` | `CreateBankAccountRequest` | `BankAccount` (201) |
| PUT | `.../bank-accounts/{bankAccountId}` | `updateBankAccount` | `UpdateBankAccountRequest` | `BankAccount` |
| DELETE | `.../bank-accounts/{bankAccountId}` | `deleteBankAccount` | — | 204 |

Key fields: `bankAccountId`, `companyId`, `accountName`, `bankName`, `accountNumber`, `routingNumber?`, `swiftCode?`, `iban?`, `currency` (ISO 4217), `accountType` (checking/savings), `isPrimary`, `bankAccountStatus`, `createdAt`, `updatedAt`.

---

### 2.5 Benefits

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/benefits` | `listBenefits` | Query: `benefitType?`, `status?`, `limit?`, `nextToken?` | `{ items: BenefitSummary[], nextToken? }` |
| GET | `.../benefits/{benefitId}` | `getBenefitById` | — | `Benefit` |
| POST | `.../customers/{customerId}/benefits` | `createBenefit` | `CreateBenefitRequest` | `Benefit` (201) |
| PUT | `.../benefits/{benefitId}` | `updateBenefit` | `UpdateBenefitRequest` | `Benefit` |
| DELETE | `.../benefits/{benefitId}` | `deleteBenefit` | — | 204 |

Key fields: `benefitId`, `companyId`, `benefitName`, `benefitType` (health/dental/vision/pension/life_insurance/bonus/rsu/other), `providerName?`, `employerContributionType` (fixed/percentage), `employerContribution?`, `benefitFrequency` (one_time/monthly/annual/per_pay_period/weekly), `benefitStatus`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

---

### 2.6 Leave Policies

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/leave-policies` | `listLeavePolicies` | Query: `leaveType?`, `status?`, `limit?`, `nextToken?` | `{ items: LeavePolicySummary[], nextToken? }` |
| GET | `.../leave-policies/{leavePolicyId}` | `getLeavePolicyById` | — | `LeavePolicy` |
| POST | `.../customers/{customerId}/leave-policies` | `createLeavePolicy` | `CreateLeavePolicyRequest` | `LeavePolicy` (201) |
| PUT | `.../leave-policies/{leavePolicyId}` | `updateLeavePolicy` | `UpdateLeavePolicyRequest` | `LeavePolicy` |
| DELETE | `.../leave-policies/{leavePolicyId}` | `deleteLeavePolicy` | — | 204 |

Key fields: `leavePolicyId`, `companyId`, `policyName`, `accrualFrequency` (monthly/annual/per_pay_period), `carryOverAllowed`, `maxCarryOverDays?`, `leaveType` (annual/sick/maternity/paternity/unpaid/comp), `annualEntitlementDays`, `probationEntitlementDays?`, `minServiceMonths?`, `status`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

---

### 2.7 Holiday Calendars

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/holiday-calendars` | `listHolidayCalendars` | Query: `year?`, `status?`, `limit?`, `nextToken?` | `{ items: HolidayCalendarSummary[], nextToken? }` |
| GET | `.../holiday-calendars/{calendarId}` | `getHolidayCalendarById` | — | `HolidayCalendar` |
| POST | `.../customers/{customerId}/holiday-calendars` | `createHolidayCalendar` | `CreateHolidayCalendarRequest` | `HolidayCalendar` (201) |
| PUT | `.../holiday-calendars/{calendarId}` | `updateHolidayCalendar` | `UpdateHolidayCalendarRequest` | `HolidayCalendar` |
| DELETE | `.../holiday-calendars/{calendarId}` | `deleteHolidayCalendar` | — | 204 |

Key fields: `holidayCalendarId`, `companyId`, `holidayCalendarName`, `year`, `status`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

### 2.7.1 Holiday Calendar Entries

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../holiday-calendars/{calendarId}/entries` | `listHolidayEntries` | Query: `limit?`, `nextToken?` | `{ items: HolidayEntry[], nextToken? }` |
| GET | `.../holiday-entries/{entryId}` | `getHolidayEntryById` | — | `HolidayEntry` |
| POST | `.../holiday-calendars/{calendarId}/entries` | `createHolidayEntry` | `CreateHolidayEntryRequest` | `HolidayEntry` (201) |
| PUT | `.../holiday-entries/{entryId}` | `updateHolidayEntry` | `UpdateHolidayEntryRequest` | `HolidayEntry` |
| DELETE | `.../holiday-entries/{entryId}` | `deleteHolidayEntry` | — | 204 |

Key fields: `holidayEntryId`, `holidayCalendarId`, `holidayDate`, `holidayName`, `isHalfDay`, `isOptional`, `createdAt`, `updatedAt`.

---

### 2.8 General Policies

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/general-policies` | `listGeneralPolicies` | Query: `policyType?`, `status?`, `limit?`, `nextToken?` | `{ items: GeneralPolicySummary[], nextToken? }` |
| GET | `.../general-policies/{policyId}` | `getGeneralPolicyById` | — | `GeneralPolicy` |
| POST | `.../customers/{customerId}/general-policies` | `createGeneralPolicy` | `CreateGeneralPolicyRequest` | `GeneralPolicy` (201) |
| PUT | `.../general-policies/{policyId}` | `updateGeneralPolicy` | `UpdateGeneralPolicyRequest` | `GeneralPolicy` |
| DELETE | `.../general-policies/{policyId}` | `deleteGeneralPolicy` | — | 204 |

Key fields: `generalPolicyId`, `companyId`, `policyName`, `policyType` (expense/travel/remote_work/probation/notice_period), `description?`, `status`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

---

### 2.9 Overtime Policies

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../customers/{customerId}/overtime-policies` | `listOvertimePolicies` | Query: `status?`, `limit?`, `nextToken?` | `{ items: OvertimePolicySummary[], nextToken? }` |
| GET | `.../overtime-policies/{policyId}` | `getOvertimePolicyById` | — | `OvertimePolicy` |
| POST | `.../customers/{customerId}/overtime-policies` | `createOvertimePolicy` | `CreateOvertimePolicyRequest` | `OvertimePolicy` (201) |
| PUT | `.../overtime-policies/{policyId}` | `updateOvertimePolicy` | `UpdateOvertimePolicyRequest` | `OvertimePolicy` |
| DELETE | `.../overtime-policies/{policyId}` | `deleteOvertimePolicy` | — | 204 |

Key fields: `overtimePolicyId`, `companyId`, `policyName`, `policyCode`, `countryCode?`, `standardHoursPerWeek`, `status`, `effectiveFrom`, `effectiveTo?`, `createdAt`, `updatedAt`.

### 2.9.1 Overtime Policy Rules

| Method | Path | `operationId` | Request body / query | Response body |
|--------|------|---------------|----------------------|---------------|
| GET | `.../overtime-policies/{policyId}/rules` | `listOvertimeRules` | Query: `limit?`, `nextToken?` | `{ items: OvertimeRule[], nextToken? }` |
| GET | `.../overtime-rules/{ruleId}` | `getOvertimeRuleById` | — | `OvertimeRule` |
| POST | `.../overtime-policies/{policyId}/rules` | `createOvertimeRule` | `CreateOvertimeRuleRequest` | `OvertimeRule` (201) |
| PUT | `.../overtime-rules/{ruleId}` | `updateOvertimeRule` | `UpdateOvertimeRuleRequest` | `OvertimeRule` |
| DELETE | `.../overtime-rules/{ruleId}` | `deleteOvertimeRule` | — | 204 |

Key fields: `overtimeRuleId`, `overtimePolicyId`, `applyTo` (weekday/weekend/national_holiday/rest_day), `tierOrder`, `maxHoursInTier?`, `multiplier`, `createdAt`, `updatedAt`.

---

## 3. Implementation phases

| Phase | APIs | Rationale |
|-------|------|-----------|
| **v1 (done)** | Customers | Foundation |
| **v2** | Pay Groups, Pay Schedules, Bank Accounts | Required before payroll runs |
| **v3** | Benefits, Leave Policies, Holiday Calendars + Entries | Employee entitlements and calendar-driven calculations |
| **v4** | General Policies, Overtime Policies + Rules | Policy framework completion |

---

## 4. Out of scope

- Public (external) API shape — defined in integrations-external-api; may differ (canonical model, subset of operations).
- Bulk operations (e.g. bulk create/update) — later version.
- Audit/history endpoints — deferred.
- Optional org hierarchy entities (Legal Entity, Business Unit, Department, Cost Center, Work Location, Job Position, Tax Jurisdiction, Tax Registration) — deferred; see ER model.

---

## 5. Next steps (per build plan)

1. **2.2** — Author `docs/api/openapi-spec.yaml`: add paths, `operationId`s, and request/response schemas per phase.
2. **2.3** — Add lint and type generation (openapi-typescript, optional Zod) from the spec.
