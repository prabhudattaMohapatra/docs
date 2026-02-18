# What You Need to Know to Build a Full Payroll Engine (Like This One) in Java from Scratch

This document lists the knowledge areas, concepts, and implementation concerns you would need to build a full payroll engine equivalent to the one in this workspace—but implemented in **Java** from scratch. It is organized by domain knowledge, technical skills, and Java-specific choices.

---

## 1. Payroll Domain Concepts You Must Understand

### 1.1 Multi-Tenant, Multi-Regulation Model

- **Tenant**: Top-level isolation (e.g. one company or one product instance). All data is scoped by tenant.
- **Regulation**: Country/jurisdiction rules (e.g. France, India, Switzerland). A tenant can have multiple regulations; a **payroll** is built from one or more regulations (base + shared).
- **Payroll**: Defines *what* is calculated (which wage types, collectors, cases, lookups) for a division. It references regulations and can layer/override.
- **Division**: Organizational unit (e.g. legal entity, cost center). Has culture, calendar; payroll is per division.
- **Payrun**: A *run* of payroll for a **period** (e.g. monthly). Has status (draft, ready, processing, completed); contains **payrun jobs** (one per employee or batch).

You need to understand how tenant → regulation → payroll → division → payrun → payrun job relate, and how regulation sharing and overrides work (e.g. base regulation + country overlay).

### 1.2 Case and Case Values

- **Case**: A named container of **case fields** (e.g. “Employee”, “EmployeeStatistics”, “EmployeeAddress”). Used for employee/company/global inputs and configuration.
- **Case field**: A slot in a case (e.g. `ContractualMonthlyWage`, `Canton`, `BirthDate`). Has type (string, numeric, boolean, date, etc.), possibly slots (multi-value).
- **Case value**: An *actual value* for a case field, with **start/end date** (effective dating). Stored at global, national, company, or employee level.
- **Case value resolution**: For a given employee and date, the engine resolves the effective value by scope (e.g. employee overrides company overrides national overrides global) and by date (valid period).

You need to model cases, case fields, and case values with **effective dating** and **scope** (global/national/company/employee), and implement resolution that the rule scripts (or your Java rules) can query.

### 1.3 Wage Types and Collectors

- **Wage type**: A numbered *output* of payroll (e.g. “1000 = Monthly Salary”, “5010 = AHV contribution”). Each wage type can have a **value** (decimal) and can **feed** one or more collectors.
- **Collector**: An **aggregation** of wage type values (e.g. “GrossSalary”, “EmployeeContributions”). Wage types declare which collector(s) they feed; collectors sum those values (some negative for deductions).
- **Collector groups**: Some engines group collectors (e.g. “AhvAlvAlvzBase”); wage types feed into groups, and deduction wage types read from collector *totals* (e.g. “AhvBase”) to compute contributions.

Calculation flow:

1. **Case values** (inputs) → **wage type value** (e.g. monthly salary from `ContractualMonthlyWage`).
2. Wage type values → **collectors** (e.g. GrossSalary += 5500).
3. **Deduction wage types** use collector totals + lookups/cases (e.g. AhvBase × rate → EmployeeContributions).
4. **Net** = GrossSalary − EmployeeContributions + Expenses (and similar formulas).
5. **Payment** = Net − AdvancePayment − PeriodPaidWageRuns (or equivalent).

You need to understand **evaluation order**: which wage types run first (e.g. gross before deductions), which collectors are available when a wage type runs, and how **retro pay** and **YTD** (year-to-date) collectors are handled.

### 1.4 Lookups

- **Lookup**: Table of key/value or key/range rows (e.g. tax rate by canton and code, insurance rate by code and gender). Regulation-defined; resolved by **lookup name + key (and optional date)**.
- **Lookup value**: One row (value, range start/end, attributes). Used by scripts/rules to get rates, thresholds, etc.

You need a lookup model and an API that rules can call (e.g. “get rate for this canton and tax code”).

### 1.5 Payroll Periods and Calendars

- **Period**: Start and end date (e.g. month, week, bi-weekly). Defined by **calendar** and **cycle** (monthly, weekly, etc.).
- **Calendar**: Determines period boundaries (e.g. calendar month, 4-4-5). Used to compute “current period” and “period index” for a given date.
- **Payrun job**: Represents “run payroll for employee E in period P”. Has status, result set (wage type results, collector results).

You need **period calculation** (given a payrun date or period, compute period start/end and any related periods for YTD/retro).

### 1.6 Retro Pay and Job Dependencies

- **Retro pay**: Re-running past periods (e.g. correction). May create **retro payrun jobs** linked to the original job; results can be merged or replaced.
- **Retro date**: The date as-of-which rules are evaluated (can be different from period end for retro).

You need a clear model for “original job” vs “retro job” and how results are consolidated (replace vs add).

---

## 2. Payrun Lifecycle (Execution Model)

The engine runs a **lifecycle** for each payrun and each employee. Your Java engine must implement the same logical phases:

| Phase | Purpose |
|-------|--------|
| **PayrunStart** | One-time per payrun; validate or init global state. |
| **EmployeeStart** | Per employee; load case values, decide if employee is in scope. |
| **WageTypeValue** | For each wage type (in order): compute value; add to collectors. |
| **CollectorStart** | Before a collector is closed; e.g. create retro jobs or custom results. |
| **CollectorEnd** | After collector is closed; use accumulated value (e.g. for deductions). |
| **WageTypeAvailable** | Decide if a wage type is active for this employee/period. |
| **CaseAvailable** | Decide if a case is active. |
| **EmployeeEnd** | Per employee; cleanup or aggregate. |
| **PayrunEnd** | One-time per payrun; finalize. |

**Order matters**: wage types are evaluated in a defined order (e.g. by number or dependency); collectors are “closed” at the right time so deduction wage types see the correct totals.

You need to design an **orchestrator** (like `PayrunProcessor`) that:

1. Loads regulation (wage types, collectors, cases, lookups, scripts/rules).
2. For each employee: builds case value context, then runs wage types and collectors in order, invoking your **rule implementation** (script or Java) at each hook.

---

## 3. Regulation and Rules: Two Implementation Styles

In the current engine, **rules** are C# **scripts** stored in the DB and compiled at runtime (Roslyn). In Java you have several options.

### 3.1 Option A: Dynamic Scripting in Java

- **GraalVM JavaScript / GraalJS**: Run JS rules from DB; fast, sandboxed. You expose a Java API (case values, collectors, wage types, lookups) to the script.
- **Groovy**: Scripts in DB; compile and run with `GroovyShell` or `GroovyClassLoader`. Natural Java interop.
- **JSR 223 (Scripting for Java)**: Use Nashorn, GraalJS, or Groovy via `ScriptEngine`; same idea—load script from DB, bind context, evaluate.

**What you need**: Script engine choice; a **script API** (object passed into scripts: `employee`, `caseValue("EmployeeStatistics.ContractualMonthlyWage")`, `collector("GrossSalary")`, `wageType(1000)`, `lookup("TaxRates", key)`); lifecycle method names (e.g. `wageTypeValue(wageTypeNumber)`); and **caching** of compiled scripts per regulation/version.

### 3.2 Option B: Pre-Compiled Java Regulation Modules

- Regulations are **JARs** or **classes** (e.g. one module per country) implementing an interface, e.g. `WageTypeValueFunction`, `CollectorStartFunction`.
- No script in DB; you **load the regulation class** (e.g. by regulation id → class name or module path) and invoke methods.

**What you need**: A **regulation interface** (e.g. one method per lifecycle hook); a **registry** (regulation id → class or `ServiceLoader`); classloader isolation if you want to reload regulations without restart.

### 3.3 Option C: External Regulation Service

- Backend does **not** run rules; it calls an **external service** (REST/gRPC) with context (tenant, regulation, employee, period, case values) and gets back wage type values and collector results.  
- The “engine” in Java is then an **orchestrator** + **client**; the actual rules can be in another language/service.

**What you need**: Contract design (request/response per lifecycle phase or batched), idempotency, and error/retry handling.

For “build from scratch in Java”, **Option A (GraalJS or Groovy)** is the closest to the current engine (DB-driven scripts). **Option B** is simpler operationally (no runtime compilation) but requires shipping regulation as code.

---

## 4. Data Model and Persistence

You need to design and implement:

- **Tenants**, **Regulations**, **Payrolls**, **Divisions**, **Employees**, **Users**.
- **Cases**, **CaseFields**, **CaseValues** (with tenant/regulation/division/employee scope and start/end).
- **WageTypes**, **Collectors** (with regulation; wage type → collector mapping).
- **Lookups**, **LookupValues** (and possibly **LookupSets** for versioning).
- **Scripts** (if Option A): e.g. `regulation_id`, `name`, `value` (source), `function_type_mask`.
- **Payruns**, **PayrunJobs** (with status, period, employee, retro linkage).
- **Results**: **WageTypeResult**, **CollectorResult** (per payrun job: wage type number/value, collector name/value/custom results).
- **Consolidated results** (optional): YTD or multi-period aggregates.

**Skills**: Relational modeling, migrations (e.g. Flyway/Liquibase), JPA/Hibernate or JDBC repositories, **effective dating** queries (e.g. “case value valid at date D for employee E”), and indexing for tenant + regulation + date.

---

## 5. Exchange Format and Import/Export

The current engine uses an **Exchange** model (JSON): tenants, regulations, payrolls, employees, case values, wage types, collectors, lookups, scripts, etc., in one or more JSON files (or ZIP).

You need:

- **Exchange schema**: Define DTOs or POJOs for each entity and the root “Exchange” container.
- **Serialization**: JSON (e.g. Jackson) with optional **schema validation** (JSON Schema).
- **Import**: Read Exchange JSON → validate → map to domain → **upsert** via repositories (tenant, regulation, scripts, wage types, collectors, cases, lookups, etc.). Handle **references** (e.g. regulation id by name or identifier).
- **Export**: Query by tenant (and optional payroll/regulation) → build Exchange object → write JSON.

**Skills**: JSON (de)serialization, idempotent upserts, reference resolution (name → id), and handling **binary/large text** (e.g. script source, report templates).

---

## 6. REST API and Backend Services

You need an API that mirrors the domain:

- **Tenants**: CRUD.
- **Regulations**: CRUD (under tenant); **scripts** CRUD (under regulation).
- **Payrolls**, **Divisions**, **Employees**: CRUD.
- **Cases / Case values**: Query and upsert (with effective date and scope).
- **Wage types, Collectors, Lookups**: Typically managed via regulation import; optionally expose read/update.
- **Payruns**: Create, start, get status; **Payrun jobs**: list, get results.
- **Results**: Get wage type and collector results for a payrun job (or consolidated).

Plus:

- **Import/Export**: e.g. `POST /tenants/{id}/import` (Exchange JSON), `GET /tenants/{id}/export`.
- **Payrun execution**: Either **synchronous** (start payrun and wait) or **asynchronous** (enqueue job; worker runs **PayrunProcessor**-equivalent and writes results).

**Skills**: REST design, Spring Boot (or Quarkus, Micronaut), authentication/authorization (e.g. API key or OAuth), multi-tenant request context (tenant id from URL or token), and **background job processing** (e.g. Spring async, SQS, or in-memory queue with a worker thread).

---

## 7. Payrun Processor (Orchestrator) in Java

The core of the engine is the **PayrunProcessor** equivalent:

1. **Load** payrun, payroll, regulation(s), wage types, collectors, cases, lookups.
2. **Resolve** case value caches (global, national, company, employee) for the period.
3. **For each employee** in scope:
   - Build **evaluation context** (employee, period, case values, calendar, culture).
   - **PayrunStart** (once) / **EmployeeStart**.
   - **Evaluate wage types** in order: for each wage type, call your rule implementation (script or Java); get value; **add to collectors**; persist **WageTypeResult**.
   - **CollectorStart** / **CollectorEnd** where defined; persist **CollectorResult**.
   - **EmployeeEnd**.
4. **PayrunEnd** (once).
5. **Mark** payrun job(s) and payrun status complete; handle errors (e.g. fail job, retry).

You need:

- **Dependency and order**: Wage type evaluation order (e.g. by number or by dependency graph); collector close order.
- **Result provider**: Abstraction that writes **WageTypeResult** and **CollectorResult** to persistence (and possibly in-memory for same-run collector reads).
- **Script/Rule invoker**: For each lifecycle method, resolve the script or Java handler and invoke with **context** (employee, period, case values, current collector totals, lookups).

---

## 8. Reporting and Documents

The current engine supports **reports** (templates + data). You may need:

- **Report definition**: Name, parameters, template (e.g. FastReport, Jasper, or HTML/PDF).
- **Report execution**: Run with context (payrun job, employee, tenant); fill data (e.g. wage type results, collector results); produce PDF/Excel.
- **Document generation**: Similar (e.g. payslip, summary).  
- **Skills**: Template engines, PDF generation (e.g. iText, OpenPDF), Excel (Apache POI), and possibly a **report runner** that has access to payroll results and case values.

---

## 9. CLI / Console Equivalent

To support import/export and ad-hoc payruns without a UI:

- **CLI**: e.g. Picocli, Spring Shell, or plain `main` with args.
- **Commands**: Import (Exchange path), Export (tenant, output path), RunPayrun (tenant, payrun id), GetResults (payrun job id), etc.
- **HTTP client**: Call your backend REST API (e.g. RestTemplate, WebClient, or OkHttp).

Optional: **DSL conversion** (YAML → Exchange JSON + scripts) as a separate tool; you’d need a parser and code generator or script emitter for your chosen script language.

---

## 10. Non-Functional and Operational

- **Multi-tenancy**: Tenant id in every request and in every query; no cross-tenant data leak.
- **Audit**: Who changed what (case value, regulation, script) and when—audit tables or event log.
- **Logging**: Structured logging (e.g. MDC with tenant, payrun job id) and log levels for rule execution.
- **Configuration**: DB connection, API keys, feature flags; per-tenant overrides if needed.
- **Security**: Validate and sanitize script content if using dynamic scripts; limit script API (no arbitrary file/network access) and timeouts.
- **Performance**: Caching of regulation metadata and compiled scripts; batch DB reads for case values; connection pooling; async payrun processing so API stays responsive.

---

## 11. Suggested Learning and Implementation Order (Java)

1. **Domain model (in memory)**  
   Tenant, Regulation, Payroll, Employee, Case/CaseValue, WageType, Collector, Lookup. No DB yet; use POJOs and maybe a simple in-memory “repository”.

2. **Case value resolution**  
   Implement effective-dated, scope-aware resolution (global → national → company → employee) for a given date. Unit test with sample case values.

3. **Wage type and collector order**  
   Define a small regulation (e.g. 2 wage types, 1 collector). Implement a **minimal processor** that runs WageTypeValue in order and accumulates collector; no scripts, just hardcoded logic or one Java class per wage type.

4. **Lookups**  
   Model lookups and lookup values; implement resolution by key (and optional date). Use in the minimal processor (e.g. “get rate for canton”).

5. **Persistence**  
   Add DB (e.g. PostgreSQL); JPA entities and repositories for tenants, regulations, employees, case values, wage types, collectors, lookups, payruns, payrun jobs, results. Migrations.

6. **REST API**  
   Expose CRUD for tenants, regulations, employees, case values; endpoint to create/start payrun and get results. Wire the minimal processor to a “run payrun” service.

7. **Scripting (if chosen)**  
   Integrate GraalJS or Groovy; define script API (case, collector, wage type, lookup); implement **WageTypeValue** script invocation from the processor; store script in DB and compile/cache per regulation.

8. **Full lifecycle**  
   Add PayrunStart, EmployeeStart, CollectorStart/End, EmployeeEnd, PayrunEnd to the processor and to the script API; implement ordering and collector close semantics.

9. **Exchange import/export**  
   Define Exchange JSON format; implement import (upsert) and export; optional validation with JSON Schema.

10. **CLI**  
    Implement Import, Export, RunPayrun (or trigger payrun) via HTTP client.

11. **Reporting (optional)**  
    Report definitions and runner; template + payroll result data → PDF/Excel.

12. **Retro pay, consolidation, audit**  
    Retro job linkage, result consolidation, and audit logging as needed.

---

## 12. Summary Checklist

| Area | What you need to know / implement |
|------|-----------------------------------|
| **Payroll domain** | Multi-tenant regulation/payroll/division/payrun; cases and case values (effective dating, scope); wage types and collectors (order, aggregation); lookups; periods and calendars; retro pay. |
| **Execution model** | Payrun lifecycle (PayrunStart → EmployeeStart → WageTypeValue / CollectorStart/End → EmployeeEnd → PayrunEnd); evaluation order; result persistence. |
| **Rules in Java** | Either: dynamic scripts (GraalJS/Groovy/JSR 223) with script API and lifecycle hooks, or pre-compiled Java modules, or external regulation service. |
| **Persistence** | Relational model for all entities; effective-dated queries; repositories for payrun and results. |
| **API** | REST for tenants, regulations, payrolls, employees, case values, payruns, results; import/export; async payrun worker. |
| **Exchange** | JSON schema and DTOs; import (upsert) and export; script storage if using DB scripts. |
| **Processor** | Orchestrator that loads regulation, resolves case values, runs wage types and collectors in order, invokes rules, writes results. |
| **CLI** | Commands for import, export, run payrun; HTTP client to backend. |
| **Reporting** | Optional; templates and data binding to payroll results. |
| **Non-functional** | Multi-tenancy, audit, logging, security (script sandbox), performance (caching, async). |

Building a **full** payroll engine in Java from scratch requires all of the above: strong domain modeling, a clear execution model, a chosen rule strategy (script vs Java vs external), solid persistence and API, and operational rigor. The suggested order in §11 gets you from a minimal in-memory prototype to a deployable backend and CLI step by step.
