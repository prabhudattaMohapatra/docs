# Daily Meeting Questions: Payroll Engine Creator

Short list of questions to ask the creator of the .NET payroll engine during daily 1-hour sessions. Each question is scoped to one core component so you can spend 30–60 minutes on it and capture design rationale for building a new engine from scratch.

---

## 1. Domain model: Cases, wage types, collectors

*“How did you decide on the core domain: Cases (and case values), Wage Types, and Collectors? Why this separation, and how do you think about the relationship between them (e.g. case fields feeding wage types, wage types feeding collectors)?”*

**Goal**: Understand the core concepts, naming, and why the model is structured this way.

---

## 2. Regulation and payroll layering

*“How do Regulations, Payrolls, and derived/layered regulations work? When you ‘derive’ wage types and collectors from a payroll, what exactly is being merged or overridden, and what problems does that solve?”*

**Goal**: Regulation as a unit of reuse, layering, and override semantics.

---

## 3. Script lifecycle: when each script runs

*“Walk me through the script lifecycle: PayrunStart, EmployeeStart, WageTypeValue, CollectorStart, CollectorApply, CollectorEnd, WageTypeResult, EmployeeEnd, PayrunEnd. For each, what is it for, and what mistakes or misuses have you seen?”*

**Goal**: When and why each hook exists; pitfalls and best practices.

---

## 4. Script compilation and execution

*“How does script compilation work end-to-end: from DB script source + wage-type expressions + embedded templates to a runnable type? Why Roslyn and in-memory assemblies, and how do you handle caching, isolation, and versioning?”*

**Goal**: Compilation pipeline, caching, and why this approach vs. interpreters or external services.

---

## 5. Case values and evaluation

*“How do case values get resolved for an employee in a period (global, national, company, employee)? How do you handle overlapping periods, forecast vs. legal, and the interaction with the case field provider and calculator?”*

**Goal**: Case value model, scopes, and time/period semantics.

---

## 6. PayrunProcessor flow and invariants

*“What are the non-negotiable invariants in PayrunProcessor (e.g. order of collector start → wage types → collector apply → collector end)? What would break if we changed that order or merged/split steps?”*

**Goal**: Required execution order and dependencies; what a new engine must preserve.

---

## 7. Retro pay and payrun jobs

*“How does retro pay work: retro jobs, schedule dates, Reevaluation phase, and why RetroPayMode.None inside retro jobs? What’s the minimal mental model I need to reimplement this correctly?”*

**Goal**: Retro semantics, one-level recursion, and avoiding infinite retro.

---

## 8. Calendars, periods, and cycles

*“How do Calendars, PayrollCalculator, and period/cycle (e.g. period name, cycle start/end) interact? What does the engine assume about period boundaries and pay frequency?”*

**Goal**: Time model and calendar-driven period logic.

---

## 9. Persistence and API design

*“How did you split persistence (repositories, DB schema) from the domain? What would you keep the same vs. change if you redesigned the API and storage for a new engine?”*

**Goal**: Boundaries between API, persistence, and domain; lessons for a rewrite.

---

## 10. Multi-tenant and regulation sharing

*“How does multi-tenancy work (tenant, division, payroll), and how does regulation sharing across tenants work? What are the main constraints and failure modes?”*

**Goal**: Tenant/division model and shared-regulation rules.

---

## 11. Exchange format and import/export

*“What is the role of the Exchange format (JSON/schema) and import/export? What must be consistent between regulation repos (e.g. France, Swiss) and the engine for import to work?”*

**Goal**: Contract between regulation authoring and engine; versioning and compatibility.

---

## 12. Validation and error handling

*“How do you validate payroll and regulations before/during a payrun? How do you surface script errors, validation messages, and job abort reasons to the user?”*

**Goal**: Validation layers and error/abort semantics.

---

## 13. Reports and consolidation

*“How do reports and consolidated results (e.g. YTD, cross-period) fit into the model? Where do they read from (raw results vs. consolidated) and what would you simplify in a new engine?”*

**Goal**: Reporting and consolidation design; what’s essential vs. legacy.

---

## 14. What you’d do differently

*“If you were building the engine again from scratch, what would you keep, what would you drop, and what would you do differently (e.g. scripting model, persistence, or regulation structure)?”*

**Goal**: Honest retrospective to guide your new engine.

---

## Suggested order (roughly 2 weeks)

| Day | Topic |
|-----|--------|
| 1 | Domain model: Cases, wage types, collectors |
| 2 | Regulation and payroll layering |
| 3 | Script lifecycle: when each script runs |
| 4 | Script compilation and execution |
| 5 | Case values and evaluation |
| 6 | PayrunProcessor flow and invariants |
| 7 | Retro pay and payrun jobs |
| 8 | Calendars, periods, cycles |
| 9 | Persistence and API design |
| 10 | Multi-tenant and regulation sharing |
| 11 | Exchange format and import/export |
| 12 | Validation and error handling |
| 13 | Reports and consolidation |
| 14 | What you’d do differently |

You can trim or merge (e.g. 9 + 10, or 11 + 12) to fit your actual number of sessions. For each session, having the relevant doc open (e.g. **PAYRUN_PROCESSOR_DETAILS.md**, **COUNTRY_REGULATION_INGESTION_FLOW.md**) will help you map his answers to the codebase and to your new design.
