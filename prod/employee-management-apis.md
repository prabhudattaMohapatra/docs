# Employee Management System – API list (payroll-focused)

Derived from Event Storming (Global Payroll – Employee Management): Onboarding, Steady State, Termination.

---

## 1. Onboarding

| Area | APIs |
|------|------|
| **Employee core** | `POST /employees`, `GET /employees/{id}`, `PUT /employees/{id}/personal-details`, `PUT /employees/{id}/address`, `PUT /employees/{id}/bank-accounts`, `POST /employees/{id}/employment-details` |
| **Contract** | `POST /employees/{id}/contracts`, `PUT /employees/{id}/contracts/{contractId}/status` |
| **Compensation** | `POST /employees/{id}/compensation-details`, `GET /employees/{id}/compensation-details` |
| **Benefits** | `POST /employees/{id}/benefits-enrollment`, `GET /employees/{id}/benefits-enrollment` |
| **Tax & compliance** | `POST /employees/{id}/tax-details`, `POST /employees/{id}/onboarding-compliance-status` |
| **System setup** | `POST /employees/{id}/time-tracking-setup`, `POST /employees/{id}/expense-management-setup`, `POST /employees/{id}/leave-management-setup`, `POST /payroll/initiate-employee-data/{employeeId}` |

---

## 2. Steady state

| Area | APIs |
|------|------|
| **Employee** | `PUT /employees/{id}`, `GET /employees/{id}/status` |
| **Company policy** | `GET /company/policies`, `PUT /company/policies/{policyId}` |
| **Time & absence** | `POST /employees/{id}/leaves`, `PUT /leaves/{id}/approve`, `GET /employees/{id}/leave-balance`, `POST /employees/{id}/timesheets`, `PUT /timesheets/{id}/approve`, `GET /employees/{id}/timesheets` |
| **Expense** | `POST /employees/{id}/expenses`, `PUT /expenses/{id}/approve`, `GET /employees/{id}/expenses` |
| **Compensation & deductions** | `PUT /employees/{id}/compensation`, `POST /employees/{id}/bonuses`, `POST /employees/{id}/commissions`, `POST /employees/{id}/deductions`, `POST /employees/{id}/garnishments`, `GET /employees/{id}/deductions`, `GET /employees/{id}/garnishments` |
| **Benefits** | `PUT /employees/{id}/benefits-enrollment/{benefitId}` |
| **Tax** | `PUT /employees/{id}/tax-details` |
| **Payroll** | `POST /payroll/calculate-run`, `POST /payroll/process-run`, `GET /employees/{id}/paystubs/{periodId}`, `POST /payroll/disburse-payments/{payrollRunId}`, `GET /payroll/status/{payrollRunId}` |

---

## 3. Termination

| Area | APIs |
|------|------|
| **Employee** | `POST /employees/{id}/terminate`, `PUT /employees/{id}/status` |
| **Final pay** | `GET /employees/{id}/final-pay-calculation`, `POST /employees/{id}/severance-payout` |
| **Benefits** | `PUT /employees/{id}/benefits-enrollment/{benefitId}/deactivate`, `POST /employees/{id}/benefits/deactivate-all` |
| **Assets** | `PUT /employees/{id}/assets/{assetId}/return-status`, `GET /employees/{id}/assets/outstanding` |
| **Access** | `POST /employees/{id}/revoke-system-access` |
| **Payroll** | `POST /payroll/process-final/{employeeId}`, `GET /employees/{id}/final-paystub`, `POST /payroll/disburse-final-payment/{employeeId}` |

---

## 4. Integration / eventing (cross-cutting)

- Webhooks or event subscriptions for: employee created/updated, compensation changed, leave approved, timesheet approved, expense approved, payroll run completed, employee terminated.
- If using EventBridge: event schemas (e.g. `EmployeeCreated`, `CompensationChanged`, `PayrollRunCompleted`, `EmployeeTerminated`) in addition to or instead of REST-only integration.
