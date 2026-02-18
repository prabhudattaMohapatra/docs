# Payroll Regulations POC — Beginner's Guide

This document explains the **payroll-regulations-poc** repository for someone new to Java. It covers what the project does, how it is structured, and what each part does in simple terms. This repo works with **payroll-engine-poc** (see [payroll_engine_poc_beginner_guide.md](payroll_engine_poc_beginner_guide.md)) and aligns with [poc_precompiled_jar_local_development_guide.md](poc_precompiled_jar_local_development_guide.md).

---

## 1. What is this project?

**payroll-regulations-poc** builds a **single JAR** (`poc-regulation-1.0.0.jar`) that contains **one** implementation of the engine’s regulation contract:

- **FranceRegulationEvaluator** — A France payroll rules evaluator for wage types **2001–2060** (gross salary, social security ceiling, health/old-age/family contributions, withholding tax, net pay, DSN-style totals, etc.). It uses **FranceRules** (one static method per wage type), **FranceRulesContext** (state and lookups), and **FranceLookups** (2025 constants and withholding tables).

The **engine** (payroll-engine-poc) does **not** compile this repo; it only loads the built JAR from `engine/plugins/` at runtime. This repo implements the **regulation-api** interface and produces one JAR that the engine loads as **france-regulation** (registration id) with class `com.payroll.regulation.poc.FranceRegulationEvaluator`.

---

## 2. Repo structure (high level)

**payroll-regulations-poc** is a **Maven multi-module** project with a single module:

```
payroll-regulations-poc/
├── pom.xml              ← parent POM (Java 25, one module)
└── poc-regulation/      ← single module: the regulation JAR
    ├── pom.xml          ← artifactId poc-regulation, version 1.0.0, regulation-api provided
    └── src/main/java/com/payroll/regulation/poc/
        ├── FranceRegulationEvaluator.java ← wage types 2001–2060, uses FranceRules + context
        ├── FranceRules.java               ← static methods: one per France wage type
        ├── FranceRulesContext.java        ← interface for France (fields, slabs, collectors)
        ├── FranceRulesContextImpl.java    ← implements context; bridges engine context + lookups
        └── FranceLookups.java             ← 2025 constants and withholding tables
```

- **Parent POM**: Sets Java 25 and lists the module `poc-regulation`.
- **poc-regulation**: The only module. It depends on **regulation-api** with **scope `provided`**: the API is used at **compile time** (so we can implement `RegulationEvaluator` and use `EvaluationContext`), but the JAR does **not** bundle the API; the engine supplies it at runtime when it loads this JAR.

---

## 3. Why one module and one JAR?

- **POC focus**: One repo, one build, one JAR. The engine registers **france-regulation** and points it at this JAR and the class **FranceRegulationEvaluator**. One artifact keeps build and run simple.
- **Contract**: The JAR exposes a single **RegulationEvaluator** implementation. The engine loads the JAR and instantiates that class by name. You could add more evaluator classes (e.g. another country) in the same JAR later and register them separately; for the current POC, only France is included.

---

## 4. The regulation-api contract (reminder)

This repo **implements** types defined in **payroll-engine-poc/regulation-api** (you must build and install the engine repo first so that `regulation-api-1.0.0-SNAPSHOT.jar` is in your local Maven repo). The contract is:

- **RegulationEvaluator**: implement `BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context)`. Optionally override `collectorStart` / `collectorEnd` (default no-op).
- **EvaluationContext**: supplied by the engine; the regulation can call `getTenantId()`, `getEmployeeId()`, `getPeriodStart()`, `getPeriodEnd()`, `getCaseValue(String name)` (numeric inputs, e.g. BaseSalary), and `getCaseValueString(String name)` (optional string inputs).
- **WageTypeResult**: not implemented here; the engine builds `WageTypeResult(wageTypeNumber, value)` from the `BigDecimal` we return.

So: this repo only **returns** a `BigDecimal` per wage type; the engine turns that into `WageTypeResult` and prints or stores it.

---

## 5. The poc-regulation module in detail

### 5.1 `FranceRegulationEvaluator.java`

- **Role**: Implements **RegulationEvaluator** for France wage types 2001–2060. It delegates each wage type to a static method in **FranceRules** and uses **FranceRulesContextImpl** to hold state and collectors for the duration of one employee’s run.
- **State per run**: It keeps a map `contextByRun` keyed by `tenantId|employeeId`. For each `(tenant, employee)` it creates one **FranceRulesContextImpl** (wrapping the engine’s `EvaluationContext`) and reuses it for all wage type calls for that employee. So 2001 can write “employee_gross_salary” into the context, and 2002–2060 can read it.
- **Flow**: For each `evaluateWageType(wageTypeNumber, context)` call, it gets or creates the **FranceRulesContextImpl** for this tenant/employee, calls **dispatch** to map 2001→FranceRules.grossSalary(ctx), 2002→FranceRules.socialSecurityCeiling(ctx), … 2060→FranceRules.emitTotalEmployerUrssafContributions(ctx). If the result is non-null, it **addToCollectors** so later wage types (e.g. 2055–2060) can read totals via `ctx.getCollector("total_employee_contributions")` etc.
- **Collectors**: Methods like 2055–2060 (“emit total…”) typically return a value that was accumulated in the context (e.g. total employee contributions). The **addToCollectors** switch statement decides which wage type values are added to which collector names (e.g. total_gross_pay, total_employee_contributions, total_employer_contributions, total_deductions).

---

### 5.2 `FranceRulesContext.java` (interface)

- **Role**: Contract used **only inside this JAR** by **FranceRules**. The engine never sees it; the engine only passes **EvaluationContext** (regulation-api) into **FranceRegulationEvaluator**.
- **Methods**:
  - **Read/write state**: `getFieldValue(name)`, `getFieldValueString(name)`, `getFieldValueBoolean(name)`; `setValue(name, value)`, `setValueString`, `setValueBoolean`. FranceRules use these to pass data between wage types (e.g. gross salary, ceiling, contributions).
  - **Lookups**: `getSlab(lookupName, key)` for constants (e.g. SMIC, ceiling); `getRangeSlab(lookupName, value)` for rate by base (e.g. withholding rate); `applyRangeSlab(lookupName, value)` for amount by base (e.g. withholding amount Guadeloupe/Guiana).
  - **Collectors**: `getCollector(collectorName)` for the sum of amounts added so far to that collector; the implementation accumulates via `addToCollector`.
  - **Presence**: `hasField(name)` to check if a value has been set or is available from the engine context.

---

### 5.3 `FranceRulesContextImpl.java`

- **Role**: Implements **FranceRulesContext** and **bridges** the engine’s **EvaluationContext** (regulation-api) to the France rules. It holds mutable state (maps for numeric, string, boolean fields and for collectors) and resolves field names to stub/Excel-style case names when reading from the engine context.
- **State**: Three maps for values set by FranceRules: `state` (BigDecimal), `stateString`, `stateBoolean`; one map **collectors** for sums per collector name.
- **Reading from the engine**: For `getFieldValue(name)` it first checks its own `state`; if missing, it maps `name` to a stub key (e.g. `employee.base_monthly_salary` → `"Basic Monthly Salary"`) and calls `apiContext.getCaseValue(stubKey)` or, for string fields, `apiContext.getCaseValueString(stubKey)`. So the stub JSON (e.g. emp-1.json) with keys like "Basic Monthly Salary", "Gross salary", "BaseSalary" feeds into France rules. Special case: `employee.monthly_ss_ceiling` can fall back to a constant from FranceLookups if not in the stub.
- **Lookups**: `getSlab("Constants2025", key)` delegates to **FranceLookups.getConstant(key)**. `getRangeSlab("WithholdingTaxMetropolitan2025JanApr", value)` and `applyRangeSlab("WithholdingTaxGuadeloupe"|"WithholdingTaxGuiana", value)` delegate to **FranceLookups** for rate/amount by base.
- **Collectors**: `getCollector(name)` returns the sum for that name; **addToCollector(name, amount)** is called by FranceRegulationEvaluator after each wage type so that FranceRules can use `getCollector("total_employee_contributions")` etc. in later wage types.

---

### 5.4 `FranceLookups.java`

- **Role**: Static helper with **2025 French payroll constants** and **withholding tax tables**. Used only by **FranceRulesContextImpl** (and thus by FranceRules indirectly). Not part of regulation-api.
- **Constants2025**: e.g. monthly_smic_2025, annual_smic_2025, monthly_ss_ceiling_2025, minimum_deduction_2025, maximum_deduction_2025, short_term_allowance_2025, meal_benefit_rate_2025. Exposed via `getConstant(key)`.
- **WithholdingTaxMetropolitan2025JanApr**: NavigableMap from monthly taxable base to rate (0–0.50). Used for metropolitan France withholding rate. Exposed via `getWithholdingRateMetropolitan(monthlyBase)`.
- **WithholdingTaxGuadeloupe / WithholdingTaxGuiana**: NavigableMaps from base to amount; interpolation between brackets. Exposed via `getWithholdingAmountGuadeloupe(monthlyBase)` and `getWithholdingAmountGuiana(monthlyBase)`.

---

### 5.5 `FranceRules.java`

- **Role**: **Static methods**, one per France wage type (2001–2060). Each method takes **FranceRulesContext** and returns the **BigDecimal** value for that wage type. They read inputs from the context (fields set by earlier wage types or by the engine via stub keys), use **getSlab** / **getRangeSlab** / **applyRangeSlab** for constants and withholding, and write intermediate results back with **setValue** / **setValueString** so later wage types can use them.
- **Order**: The engine calls wage types in a fixed order (2001, 2002, … 2060). 2001 (gross salary) runs first and sets employee_gross_salary, contract type, etc.; then ceiling, then contributions, then withholding, then net and “emit” totals. So dependency order is encoded in the wage type list in the engine and in the logic inside each FranceRules method.
- **Examples**: `grossSalary(ctx)` reads base salary and allowances from context, sets employee_gross_salary; `socialSecurityCeiling` / `socialSecurityCeilingCalculation` set monthly_ss_ceiling and salary_within_ceiling; health/old-age/family/unemployment/CSG/withholding/net methods use those and the lookups; 2054–2060 return collector totals.

---

## 6. How the engine uses this JAR

1. The engine loads **poc-regulation-1.0.0.jar** from `engine/plugins/` (and puts regulation-api on the classpath).
2. It registers **france-regulation** (id) → JAR `poc-regulation-1.0.0.jar`, class `com.payroll.regulation.poc.FranceRegulationEvaluator`.
3. For each stub employee (from JSON), it builds an **EvaluationContext** (StubEvaluationContext with case values from the stub).
4. It calls **MinimalPayrun** once for regulation id **france-regulation** with wage types 2001–2060. The loader returns the **FranceRegulationEvaluator** instance (cached); the payrun calls `evaluateWageType(num, context)` for each wage type and collects **WageTypeResult**s.
5. The engine prints (or otherwise uses) the results per employee. All input (tenant, employee, period, case values) comes from the stub JSON via **EvaluationContext**.

---

## 7. Build and run

- **Prerequisite**: **regulation-api** must be installed in your local Maven repo. From **payroll-engine-poc** run:  
  `mvn clean install -pl regulation-api`  
  (or install the whole engine repo).
- **Build this repo**: From **payroll-regulations-poc** root:  
  `mvn clean package`  
  The JAR is produced at **poc-regulation/target/poc-regulation-1.0.0.jar**.
- **Use in the engine**: Copy that JAR into **payroll-engine-poc/engine/plugins/**. Then run the engine (e.g. `./scripts/run_and_show_results.sh` from payroll-engine-poc). The engine will load **FranceRegulationEvaluator** and run the France payrun (wage types 2001–2060) for each stub employee.

---

## 8. Key Java concepts used (for beginners)

- **Implementing an interface**: **FranceRegulationEvaluator** implements **RegulationEvaluator**; the engine only knows the interface type.
- **Provided scope**: The dependency on regulation-api is **provided**, so it is used to compile but not packaged in the JAR; the engine provides it at runtime.
- **Switch expressions**: `return switch (wageTypeNumber) { case 2001 -> ...; case 2002 -> ...; default -> null; };` for mapping wage type to FranceRules methods.
- **Static methods**: **FranceRules** and **FranceLookups** are used as static utility classes (no instance state).
- **NavigableMap / TreeMap**: In **FranceLookups**, used for bracket lookups (floor entry for a given base) and interpolation.
- **computeIfAbsent**: In **FranceRegulationEvaluator**, to get or create the **FranceRulesContextImpl** per tenant|employee.

---

## 9. Summary table

| Piece | Role |
|-------|------|
| **poc-regulation (module)** | Builds one JAR; depends on regulation-api (provided). |
| **FranceRegulationEvaluator** | Implements RegulationEvaluator for 2001–2060; one FranceRulesContextImpl per tenant|employee; dispatches to FranceRules and updates collectors. |
| **FranceRulesContext** | Interface for France: get/set fields, getSlab, getRangeSlab, applyRangeSlab, getCollector. |
| **FranceRulesContextImpl** | Implements FranceRulesContext; bridges EvaluationContext (stub keys) and FranceLookups; holds state and collectors. |
| **FranceLookups** | 2025 constants and withholding tables (Metropolitan, Guadeloupe, Guiana). |
| **FranceRules** | Static methods, one per France wage type; read/write via FranceRulesContext. |

Together, this repo provides a single JAR that the engine can use to run the France payrun (wage types 2001–2060) for each stub employee, with input (employee/period/case values) coming from the engine’s **EvaluationContext**.
