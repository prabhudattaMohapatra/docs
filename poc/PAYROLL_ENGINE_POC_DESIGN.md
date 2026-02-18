# Payroll Engine POC — Design Document

**Status**: Retroactive design. This document describes the design that the current POC code implements, written as if it had been produced before implementation. It serves as the single reference for the as-built behaviour of the engine and regulations.

---

## 1. Purpose and scope

### 1.1 Goals

- **Proof of concept**: Demonstrate a minimal payroll engine that can load regulation logic from plugin JARs and run payruns for a list of employees using stub (file-based) input.
- **Clear separation**: The engine owns orchestration, configuration, and I/O; regulations own only payroll rules and depend on a small, stable contract.
- **Config-driven**: Which regulations are available and how they are loaded is defined in configuration, not hardcoded in the engine.
- **Employee-by-employee processing**: Each employee is processed in isolation—one context per employee, one payrun invocation per employee—with no cross-employee state in the engine’s payrun loop.

### 1.2 Scope (in)

- Engine: loading regulations from JARs, building evaluation context from stub data, invoking the regulation evaluator per wage type, collecting and presenting results.
- Contract (regulation-api): interfaces and types that both the engine and regulation plugins implement or use.
- Regulations: plugin JARs that implement the contract (e.g. France regulation); each regulation declares its wage types and evaluates them given an evaluation context.
- Stub data: JSON employee records and an index file listing which stubs to process; no database or live APIs.

### 1.3 Scope (out)

- Production concerns: persistence, security, multi-tenancy enforcement, audit, retries, scaling.
- Multiple regulations per payrun: the POC runs one regulation (the first in config) per run; extending to multiple regulations is a natural evolution but not part of this design.
- Real employee data systems: the design assumes stub data only; integration with HR/payroll data stores is out of scope for the POC.

---

## 2. Architecture

### 2.1 High-level split

The system is split into:

1. **Engine** (payroll-engine-poc): orchestration, configuration, context building, invocation of regulations, and output. No payroll rules.
2. **Contract** (regulation-api): interfaces and data types shared by the engine and all regulation implementations. No engine or regulation logic.
3. **Regulation** (e.g. payroll-regulations-poc): plugin JAR(s) that implement the contract; contain payroll rules and wage type definitions.

Dependency direction: **Engine** and **Regulation** both depend on **Contract**. Neither Contract nor Regulation depend on the engine. This allows regulation JARs to be built and tested against only the API; the engine supplies the API at runtime.

### 2.2 Module layout

- **regulation-api**: Single module containing `RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`. Published as a small artifact; engine and regulation projects depend on it (regulation typically as `provided` scope).
- **engine**: Depends on regulation-api and JSON (Jackson). Contains Main, registry, loader, payrun, stub data types, and configuration types. Loads regulation JARs from a configured plugins directory.
- **Regulation projects**: Separate repo(s) that depend only on regulation-api, produce JAR(s), and place them in the engine’s plugins directory. Engine discovers them via `regulations.json`, not by scanning the filesystem.

### 2.3 Runtime model

- **Startup**: Engine reads `regulations.json` from the classpath, builds a registry of (id, version) → (JAR path, evaluator class name), and uses the first regulation in config for the run. It obtains the list of wage type numbers from that regulation’s evaluator via `getWageTypeNumbers()`. It reads the list of stub file paths from `stub-data/index.txt`.
- **Per employee**: For each path in the stub list, the engine loads the stub JSON, builds a single `EvaluationContext` for that employee, calls the payrun once (same regulation and wage type list, one context per employee), then writes that employee’s results. No batching of employees into one payrun.
- **Regulation loading**: On first use of a (regulationId, version), the engine loads the JAR via a URLClassLoader, instantiates the evaluator class (no-arg constructor), and caches the instance. Subsequent requests for the same id:version return the cached instance.

---

## 3. Contract (regulation-api)

The contract defines what the engine can call and what the regulation must implement, and what data is passed (context) and returned (results).

### 3.1 RegulationEvaluator

- **evaluateWageType(int wageTypeNumber, EvaluationContext context) → BigDecimal**  
  Given a wage type number and the current evaluation context, returns the amount for that wage type. The engine calls this once per wage type per employee. Null may be treated as zero by the engine.
- **getWageTypeNumbers() → List\<Integer\>**  
  Returns the ordered list of wage type numbers this regulation evaluates (e.g. 2001–2060). The engine calls this once at startup to know which wage types to request. Default implementation returns an empty list.
- **collectorStart / collectorEnd** (optional): Hooks for the engine to signal the start/end of a collector phase; default no-op. The POC engine does not call them; they are reserved for future use.

### 3.2 EvaluationContext

The regulation receives only this interface; it has no access to files, configuration, or the stub list. The engine is responsible for building a context per employee that implements:

- **getTenantId(), getEmployeeId()** → String  
- **getPeriodStart(), getPeriodEnd()** → LocalDate  
- **getCaseValue(String caseName)** → BigDecimal (numeric input, e.g. BaseSalary)  
- **getCaseValueString(String caseName)** → String (optional text input; default null)  
- **getLookup(String lookupName, String key)** → String (optional; default null)

All regulation input for a given evaluation comes from the context. The regulation must not perform I/O or depend on global state beyond what it derives from the context.

### 3.3 WageTypeResult

- **record WageTypeResult(int wageTypeNumber, BigDecimal value)**  
  Immutable result for one wage type. The engine collects one per wage type per payrun and returns a list to the caller.

---

## 4. Engine design

### 4.1 Configuration

- **regulations.json** (classpath): Root object has a list of regulation entries. Each entry has:
  - **id**: logical regulation id (e.g. `france-regulation`).
  - **version**: version string (e.g. `1.0.0`).
  - **jar**: JAR file name (e.g. `poc-regulation-1.0.0.jar`), resolved relative to the plugins directory.
  - **evaluatorClass**: fully qualified class name of the class implementing `RegulationEvaluator`.

The engine fails fast if `regulations.json` is missing or if the regulations list is empty. It does not validate that the JAR exists until the first request for that regulation.

### 4.2 Regulation registry and loader

- **RegulationRegistry**: Holds a mapping (regulationId, version) → (absolute JAR path, evaluator class name). Built at startup from `regulations.json`; the engine registers every entry. The registry does not load classes or open JARs.
- **RegulationEvaluatorLoader**: Given (regulationId, version), returns a `RegulationEvaluator` instance. Uses the registry to resolve JAR path and class name; creates a URLClassLoader with that JAR (parent: engine class loader), loads the class, instantiates via no-arg constructor, and caches the instance by `id:version`. Thread-safe cache so that concurrent requests for the same regulation reuse the same instance.

### 4.3 Payrun

- **MinimalPayrun**: Accepts (regulationId, version, list of wage type numbers, EvaluationContext). Obtains the `RegulationEvaluator` from the loader, then for each wage type number calls `evaluator.evaluateWageType(number, context)` and collects a `WageTypeResult`. Returns the list of results. Null from the evaluator is coerced to BigDecimal.ZERO. No collector lifecycle (collectorStart/collectorEnd) is invoked in the POC.

### 4.4 Stub data and context

- **Stub list**: The engine reads `stub-data/index.txt` from the classpath (one path per line, trimmed, non-empty). Each line is a path to a stub JSON file (e.g. `stub-data/PR2b557c.json`). If the file is missing or empty, the list of employees is empty and no payruns are run.
- **Stub JSON**: Each file is a single employee stub. Shape: tenantId, employeeId, periodStart, periodEnd, caseValues (map of string to string). The engine deserializes this into a **StubDataRecord**, converts numeric case values to BigDecimal (non-numeric or blank entries skipped), and builds a **StubEvaluationContext** with tenantId, employeeId, parsed period dates, numeric case map, and string case map. This context is the only input the regulation sees for that employee.
- **One context per employee**: For each stub path, exactly one StubDataRecord and one StubEvaluationContext are created; exactly one `MinimalPayrun.run(..., context)` is invoked. There is no shared or batched context.

### 4.5 Main entry point

The main class:

1. Resolves the plugins directory (e.g. `plugins` or `engine/plugins`).
2. Loads `regulations.json` into a **RegulationsConfig** (list of **RegulationEntry**); throws if empty.
3. Creates **RegulationRegistry** and registers each entry from config.
4. Takes the first regulation entry; creates **RegulationEvaluatorLoader** and **MinimalPayrun**; loads the evaluator and gets the wage type list via `getWageTypeNumbers()`.
5. Loads the stub file list from `stub-data/index.txt`.
6. For each stub path: load JSON → StubDataRecord → StubEvaluationContext → payrun.run(first.id, first.version, wageTypeNumbers, context) → print employee id and non-zero wage type results, then a blank line.

Output is to stdout only; format is informal (e.g. `Employee <id>:` followed by lines `  wageType -> value` for non-zero values).

---

## 5. Regulation design

### 5.1 Responsibilities

A regulation plugin:

- Implements **RegulationEvaluator**: provides `getWageTypeNumbers()` and `evaluateWageType(int, EvaluationContext)`.
- Uses **only** the supplied EvaluationContext for inputs (tenant, employee, period, case values, optional lookups). No file I/O, no network, no engine-specific APIs.
- Returns one BigDecimal per `evaluateWageType` call; may return null (engine may treat as zero).
- May maintain internal state per “run” (e.g. per tenant+employee) for the duration of one payrun, as long as it is derived from the context (e.g. building an internal context object keyed by tenant+employee from the API context).

### 5.2 Wage type definition

The regulation defines which wage type numbers it supports and in what order. This is typically read from a configuration resource (e.g. WageTypes.json) inside the regulation JAR and returned by `getWageTypeNumbers()`. The engine does not hardcode wage type numbers; it always asks the evaluator.

### 5.3 Evaluation

For each `evaluateWageType(wageTypeNumber, context)` call, the regulation typically:

- Maps wage type number to a rule or method (e.g. via a config or switch).
- Reads inputs from `context.getCaseValue(...)`, `context.getCaseValueString(...)`, and period/employee identifiers.
- Computes the amount and returns it. It may also update internal collectors or accumulators for use by later wage types in the same payrun; that is an implementation detail of the regulation.

---

## 6. Data flow summary

| Data | Source | Consumer | When |
|------|--------|----------|------|
| regulations.json | Classpath | Main → RegulationsConfig → Registry | Startup |
| stub-data/index.txt | Classpath | Main | Startup (list of stub paths) |
| stub-data/\*.json | Classpath | Main → StubDataRecord → StubEvaluationContext | Per employee |
| Regulation JAR | Plugins dir | RegulationEvaluatorLoader | First getEvaluator(id, version) |
| Wage type list | Evaluator.getWageTypeNumbers() | Main → MinimalPayrun | Once at startup |
| EvaluationContext | Engine (StubEvaluationContext) | Regulation (evaluateWageType) | Per wage type, per employee |
| WageTypeResult list | MinimalPayrun | Main | After each payrun.run |

---

## 7. Design decisions and rationale

| Decision | Rationale |
|----------|-----------|
| **Contract in a separate module (regulation-api)** | Regulations must not depend on the engine. A shared API module keeps the contract in one place and allows regulation JARs to be built and versioned independently. |
| **Config-driven registry (regulations.json)** | Adding or changing a regulation does not require engine code changes; only config and the JAR in the plugins dir. |
| **Wage types from the evaluator** | The engine does not hardcode wage type numbers; each regulation declares its own. This keeps the engine regulation-agnostic. |
| **One context per employee, one payrun per employee** | Simplifies reasoning and avoids cross-employee state in the engine; regulations can still keep per-employee state within a single payrun.run call. |
| **Stub list in index.txt** | Which employees to process is explicit and easy to change without editing engine code; empty or missing file means “no employees” (safe default). |
| **Loader caches evaluator by id:version** | Avoids repeated class loading and instantiation; one evaluator instance per regulation version per process. |
| **First regulation only for POC** | Keeps the main loop simple; multi-regulation per run can be added later by iterating over config entries and wage type lists. |
| **Optional collectorStart/collectorEnd** | Reserved for future engine-driven phases (e.g. accumulation passes); POC does not use them, so default no-op keeps existing regulations compatible. |

---

## 8. Out of scope / possible extensions

- Running multiple regulations in one run (loop over config, run payrun per regulation per employee).
- Persistence of results, audit logs, or reconciliation.
- Authentication, authorization, or tenant isolation in the engine.
- Real data adapters (DB, APIs) instead of stub JSON.
- Engine-driven collector phases (calling collectorStart/collectorEnd).
- Dynamic reload of regulation JARs without restart.

This design document describes the POC as implemented. Changes to behaviour or structure should be reflected here and then implemented in code.
