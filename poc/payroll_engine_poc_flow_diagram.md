# Payroll Engine POC — Flow Diagram

This document describes **everything that happens** when you run the payroll-engine-poc Java application (e.g. `java -jar engine/target/engine-1.0.0-SNAPSHOT.jar` or `com.payroll.engine.Main`). It complements the [beginner guide](payroll_engine_poc_beginner_guide.md).

---

## 1. High-level flow

1. **Run Main.main**
2. Resolve plugins dir (try `plugins` then `engine/plugins`)
3. Create ObjectMapper
4. Load `regulations.json` from classpath
5. **If** config.regulations is empty → **throw**
6. Create RegulationRegistry (with plugins dir)
7. Register each entry from config (id, version, jar, evaluatorClass)
8. Take first regulation from config
9. Create RegulationEvaluatorLoader and MinimalPayrun
10. loader.getEvaluator(first.id, first.version)
11. evaluator.getWageTypeNumbers() → wage type list
12. Load stub file list from `stub-data/index.txt`
13. **If** stub list is empty → **end** (no output)
14. **If** stub list has paths → **for each** stub path:
    - Run per-employee flow (see section 3)
    - Then next stub or end

---

## 2. Engine vs regulation: who does what

The application splits work between the **engine** (this repo, orchestration and I/O) and the **regulation** (plugin JAR, payroll rules). The **regulation-api** module is the contract both sides use. Below is the responsibility split in diagram form.

### 2.1 Responsibility split

| **Engine** | **Contract (regulation-api)** | **Regulation (plugin JAR)** |
|------------|-------------------------------|----------------------------|
| Load config and stub list | RegulationEvaluator | getWageTypeNumbers |
| Build Registry and Loader | EvaluationContext | evaluateWageType |
| Load regulation JAR | WageTypeResult | Use context only, no I/O |
| Build context per employee | | |
| Get wage types from evaluator | | |
| Call evaluateWageType per wage type | → | ← |
| Collect results and print | | |

Engine and Regulation both depend on the **Contract**. The engine calls the regulation (e.g. E6 → R2).

### 2.2 What the engine does

- **Read config and data**
  - `regulations.json`
  - `stub-data/index.txt`
  - `stub-data/*.json` (employee stub files)
- **Orchestration**
  - Create registry, loader, payrun
  - Get evaluator and wage type list from regulation
  - Loop: one employee → one context → one payrun.run
- **Context building**
  - Parse JSON → StubDataRecord
  - Convert case values to BigDecimal
  - Build StubEvaluationContext per employee
- **Invocation**
  - Call getWageTypeNumbers once
  - Call evaluateWageType for each wage type
- **Output**
  - Collect WageTypeResult list
  - Print employee id and non-zero results

### 2.3 What the regulation does

- **Implement RegulationEvaluator**
  - getWageTypeNumbers → return list of wage type numbers (e.g. 2001–2060)
  - evaluateWageType(wageTypeNumber, context) → BigDecimal
- **Use only the context**
  - getTenantId, getEmployeeId
  - getPeriodStart, getPeriodEnd
  - getCaseValue(name) → numeric input (e.g. BaseSalary)
  - getCaseValueString(name) → optional text input
- **Pure logic**
  - No file I/O, no network
  - No knowledge of stub data format — only the context interface
  - Return one amount per evaluateWageType call

### 2.4 Call boundary (engine calls regulation)

```
Main                    Loader                  Evaluator (regulation)
  |                        |                            |
  |--- getEvaluator ------>|                            |
  |<-------- evaluator ----|                            |
  |                        |                            |
  |--- getWageTypeNumbers ---------------------------->|
  |<-------- wage type list --------------------------|
  |                        |                            |
  (for each employee)      |                            |
  |  load stub, build context                          |
  |--- run(id, version, wageTypes, context) ----------> MinimalPayrun
  |                        |                            |
  |                        |  (for each wage type)      |
  |                        |--- evaluateWageType(number, context) -->|
  |                        |<-- BigDecimal -------------|
  |<-------- results ------|                            |
  |  print                |                            |
```

**Summary**: The **engine** owns configuration, data loading, context construction, the employee loop, and printing. The **regulation** owns only “given this wage type and this context, what is the amount?” and “what wage type numbers do you support?”. The regulation never sees files or the stub list; it only sees the **EvaluationContext** the engine builds for each employee.
**Summary**: The **engine** owns configuration, data loading, context construction, the employee loop, and printing. The **regulation** owns only “given this wage type and this context, what is the amount?” and “what wage type numbers do you support?”. The regulation never sees files or the stub list; it only sees the **EvaluationContext** the engine builds for each employee.

---

## 3. Per-employee flow (one iteration of the main loop)

For **each** path in the stub list (e.g. `stub-data/PR2b557c.json`), the following happens:

1. **For each stub path**
2. Load JSON from classpath at that path
3. Parse → StubDataRecord
4. Convert case values (strings) → BigDecimal map
5. Build StubEvaluationContext (tenantId, employeeId, period, numeric map, string map)
6. payrun.run(first.id, first.version, wageTypeNumbers, context)
7. Print `Employee <id>:`
8. For each WageTypeResult: if value ≠ 0, print `  wageType -> value`
9. Print blank line
10. Next stub or end

---

## 4. MinimalPayrun.run

When **MinimalPayrun.run(regulationId, version, wageTypeNumbers, context)** is called:

1. **payrun.run** invoked
2. loader.getEvaluator(id, version)
3. **If** evaluator not cached:
   - Registry → JAR path and class name
   - URLClassLoader load JAR
   - Instantiate evaluator (no-arg constructor)
   - Cache evaluator by id:version
4. **For each** wage type number in wageTypeNumbers:
   - evaluator.evaluateWageType(wageTypeNumber, context) → regulation uses context (getCaseValue, etc.)
   - Regulation returns BigDecimal
   - Collect WageTypeResult(wageTypeNumber, value)
5. Return List of WageTypeResult

---

## 5. Data and file flow (what is read from where)

**Classpath (read by Main):**

- `regulations.json` → Main
- `stub-data/index.txt` → Main
- `stub-data/*.json` → Main (per employee)

**Engine components:**

- Main → uses Registry, Loader, Payrun
- RegulationRegistry → used by Loader (JAR path, class name)
- RegulationEvaluatorLoader → loads from **plugins dir** (regulation JAR)
- MinimalPayrun → uses Loader to get evaluator

**Plugins dir (e.g. `engine/plugins/`):**

- Regulation JAR (e.g. `poc-regulation-1.0.0.jar`) → opened by Loader when getEvaluator is first called

---

- **regulations.json**: Read once at startup → RegulationsConfig → registry entries.
- **stub-data/index.txt**: Read once → list of paths; each path used to load one stub JSON.
- **stub-data/&lt;path&gt;.json**: Read once per employee in the loop → StubDataRecord → StubEvaluationContext.
- **Plugin JAR**: Opened by RegulationEvaluatorLoader when getEvaluator is first called for that id:version; evaluator is cached.

---

## 6. Sequence summary (ordered list)

1. **Start**: Resolve plugins directory; create ObjectMapper.
2. **Config**: Load `regulations.json` → RegulationsConfig; throw if no regulations.
3. **Registry**: Create RegulationRegistry; register every entry from config.
4. **Engine setup**: Create loader and MinimalPayrun; get first regulation’s evaluator and wage type list from `getWageTypeNumbers()`.
5. **Stub list**: Load `stub-data/index.txt`; trim non-empty lines → list of stub paths (empty if file missing or empty).
6. **Per employee** (for each path in stub list):
   - Load stub JSON → StubDataRecord.
   - Convert case values to BigDecimal; build StubEvaluationContext.
   - Call MinimalPayrun.run(first.id, first.version, wageTypeNumbers, context).
   - MinimalPayrun gets evaluator (from loader cache or load JAR), then for each wage type calls evaluator.evaluateWageType(num, context) and collects WageTypeResult.
   - Print "Employee &lt;id&gt;:" then each non-zero wage type result; print blank line.
7. **End**: After last employee, main exits.
