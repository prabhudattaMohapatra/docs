# Payroll Engine POC — Beginner’s Guide

This document explains the **payroll-engine-poc** and **payroll-regulations-poc** repositories for someone new to Java. It covers what the projects do, how they are structured, and what each part does in simple terms. The setup aligns with [poc_precompiled_jar_local_development_guide.md](poc_precompiled_jar_local_development_guide.md).

---

## 1. What is this project?

**payroll-engine-poc** is a small **proof-of-concept** payroll engine. It:

1. **Loads regulation configuration** from `regulations.json` (which JARs and evaluator classes to use) and **regulation logic** from those JARs in `engine/plugins/` (e.g. built by **payroll-regulations-poc**).
2. **Runs a payrun employee by employee**: for each employee stub (from a list of JSON file paths in `stub-data/index.txt`), it builds a context from that stub, asks the regulation for each wage type in order (wage type list comes from the regulation via `getWageTypeNumbers()`), and collects the results.
3. **Prints** the wage type results per employee (only non-zero values).

So: **engine** = config-driven loader + **employee-by-employee** runner + stub data; **regulation JAR** = the rules (e.g. `FranceRegulationEvaluator`). No database, no web server — just Java and Maven. **payroll-regulations-poc** is a second repo that builds the regulation JAR; which regulations run is defined in **regulations.json**.

---

## 2. Repo structure (high level)

**payroll-engine-poc** is a **Maven multi-module** project: one parent and two modules. **payroll-regulations-poc** is a separate repo that builds the regulation JAR.

```
payroll-engine-poc/
├── pom.xml              ← parent POM (defines modules and Java 25)
├── scripts/
│   └── run_and_show_results.sh   ← build both repos, copy JAR, run engine
├── regulation-api/      ← module 1: the “contract” (interfaces)
│   ├── pom.xml
│   └── src/main/java/.../api/
│       ├── RegulationEvaluator.java
│       ├── EvaluationContext.java
│       └── WageTypeResult.java
└── engine/             ← module 2: the actual engine (loader + payrun)
    ├── pom.xml
    ├── plugins/        ← folder where regulation JARs are placed
    └── src/main/java/.../engine/
        ├── Main.java
        ├── RegulationRegistry.java
        ├── RegulationEvaluatorLoader.java
        ├── MinimalPayrun.java
        ├── StubEvaluationContext.java
        ├── StubDataRecord.java
        ├── RegulationsConfig.java   ← Jackson record for regulations.json
        └── RegulationEntry.java    ← id, version, jar, evaluatorClass
    └── src/main/resources/
        ├── regulations.json        ← which regulations to load (JAR + class per regulation)
        └── stub-data/              ← JSON employee stubs + index.txt (list of stub files to process)
```

- **regulation-api**: only **interfaces** and one record. Other projects (like payroll-regulations-poc) implement these interfaces.
- **engine**: loads **regulations.json** to know which JARs and evaluator classes to register; loads regulation JARs from `engine/plugins/`; reads employee stub list from `stub-data/index.txt`; **processes one employee at a time** (load stub JSON → build context → run payrun → print results). Depends on regulation-api and Jackson. Contains **RegulationsConfig** / **RegulationEntry**, **StubDataRecord**, and **stub-data/** (JSON stubs + index.txt).
- **payroll-regulations-poc**: builds the regulation JAR (e.g. `FranceRegulationEvaluator`). Which regulation(s) to use is configured in the engine’s **regulations.json**.
- **scripts/run_and_show_results.sh**: builds both repos, copies JAR to `engine/plugins/`, runs the engine.

---

## 2.1 Why two separate modules?

Splitting into **regulation-api** and **engine** is a deliberate design choice. Here’s why it’s done this way.

**1. Regulation JARs need only the API, not the engine.**

- **payroll-regulations-poc** (and any future regulation like France, India) must implement `RegulationEvaluator` and use `EvaluationContext` / `WageTypeResult`.
- Those regulation projects should **depend only on the contract** — the interfaces and data types — not on the engine’s loader, registry, or Main.
- If the API lived inside the engine module, every regulation would have to depend on the whole engine JAR just to get three small types. That would pull in engine code into regulation builds and blur the boundary between “contract” and “implementation.”

**2. Clean dependency direction.**

- **regulation-api** has **no dependency** on the engine. It’s just interfaces and one record.
- **engine** depends on **regulation-api**.
- **payroll-regulations-poc** depends only on **regulation-api** (as `provided` scope: use at compile time, engine supplies it at runtime).

So the dependency flow is one-way: **engine** and **regulations** both depend on **regulation-api**; neither the API nor the regulations depend on the engine. That keeps the contract stable and reusable.

**3. Independent versioning and publishing.**

- **regulation-api** can be versioned and published (e.g. to Maven Central or an internal repo) as a small, stable artifact: `regulation-api-1.0.0.jar`.
- Regulation projects depend on that version (e.g. `1.0.0`) and don’t care which engine version is running.
- The engine can depend on the same API version and load any regulation JAR built against it. If the API were buried inside the engine module, you’d have to publish the whole engine just to get the contract, and regulation builds would be tied to engine versions.

**4. Single place for the contract.**

- All “what the engine and regulations agree on” lives in one module: **regulation-api**. Changing the contract (e.g. adding a method to `EvaluationContext`) is done in one place, and both engine and regulation projects see the same change.
- If the API were mixed into the engine, contract changes would be harder to track and would force unnecessary engine churn for what is really an interface change.

**5. Matches how the JAR is used at runtime.**

- At runtime, the engine puts **regulation-api** on the classpath and loads regulation JARs that were compiled against that API. The regulation JAR does not (and should not) bundle the API; the engine provides it.
- Having **regulation-api** as its own module and JAR mirrors this: the API is one artifact, the engine is another, and regulation JARs are built against the API artifact only.

**In short:** two modules keep the **contract** (regulation-api) separate from the **engine** (loader + payrun). That gives clean dependencies, safe reuse by regulation projects, and a clear, single place to evolve the interface between the engine and all regulations.

---

## 3. Maven and POMs (in simple terms)

- **Maven** is a build tool: it compiles Java, runs tests, and packages code into JARs.
- A **POM** (`pom.xml`) describes a project: name, Java version, dependencies (other JARs it needs), and modules (subprojects).
- **Parent POM** (root `pom.xml`): sets Java 25 and lists the two modules: `regulation-api` and `engine`.
- **Module POMs**: each module has its own `pom.xml`; it declares a **parent** (the root) and its own **artifactId** and **dependencies**.

So when you run `mvn clean install` at the root, Maven builds `regulation-api` first (because `engine` depends on it), then `engine`.

---

## 4. The regulation-api module (the “contract”)

This module does **not** contain payroll logic. It only defines **what** a regulation must provide and **what** the engine will pass to it. Think of it as a contract between the engine and any regulation JAR.

### 4.1 `RegulationEvaluator.java` (interface)

- **What it is**: An **interface** — a list of methods that any “regulation” must implement. In Java, a class can `implements RegulationEvaluator` and then the engine can call it without knowing the concrete type.
- **Main method**: `BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context)`  
  “Given a wage type number and a context (employee, period, case values), return the amount.”
- **Wage type list**: `List<Integer> getWageTypeNumbers()` — returns the ordered list of wage type numbers this regulation evaluates (e.g. 2001–2060 for France). The engine uses this to know which wage types to call; default is empty list.
- **Other methods**: `collectorStart` and `collectorEnd` have default empty implementations, so implementors can leave them out if they don’t need them.

### 4.2 `EvaluationContext.java` (interface)

- **What it is**: Another interface. It describes the **input** the regulation gets for each evaluation: who, when, and what input values (e.g. BaseSalary).
- **Methods**:  
  `getTenantId()`, `getEmployeeId()`, `getPeriodStart()`, `getPeriodEnd()`, `getCaseValue(String name)` (numeric), `getCaseValueString(String name)` (optional, for text like contract type; default returns null), and optionally `getLookup(...)`.  
  So the regulation can ask “what is the BaseSalary?” via `getCaseValue("BaseSalary")` or string inputs via `getCaseValueString(...)`.

### 4.3 `WageTypeResult.java` (record)

- **What it is**: A **record** — a simple, immutable data holder. In Java, `record WageTypeResult(int wageTypeNumber, BigDecimal value)` automatically gives you a constructor, getters (`wageTypeNumber()`, `value()`), and equals/hashCode.
- **Use**: The engine collects one `WageTypeResult` per wage type (e.g. 1001 → 100.00) and returns a list of them.

**Summary**: regulation-api = “contract”: implement `RegulationEvaluator`, use `EvaluationContext` to read inputs, and the engine works with `WageTypeResult` as output.

---

## 5. The engine module (loader + payrun)

The engine is the code that **uses** the contract: it loads a regulation JAR, gets an evaluator, runs a payrun, and prints results.

### 5.1 `RegulationRegistry.java`

- **Role**: A simple **registry** (map) that says: “for regulation id X and version Y, which JAR file and which class name?”
- **How it works**:  
  - You give it a **plugins directory** (e.g. `engine/plugins`).  
  - You **register** each regulation from **regulations.json** (id, version, JAR file name, evaluator class name).  
  - It stores the **full path** to the JAR and the class name, keyed by `"id:version"`.
- **Used by**: The loader asks the registry for the JAR path and class name when it needs to load an evaluator. Registrations come from **RegulationsConfig** (loaded from `regulations.json`), not from hardcoded Main code.

### 5.2 `RegulationEvaluatorLoader.java`

- **Role**: **Loads** a regulation JAR from disk and creates an instance of the evaluator class.
- **How it works**:  
  - It uses `RegulationRegistry` to get the JAR path and the evaluator class name.  
  - It creates a **URLClassLoader** with that JAR (so classes inside the JAR can be loaded).  
  - It loads the class by name, gets its no-arg constructor, and calls `newInstance()` to get a `RegulationEvaluator`.  
  - It **caches** evaluators by `"regulationId:version"` so the same JAR is not loaded again and again.
- **Important**: The regulation JAR is compiled **against** the same `regulation-api` interfaces (from this repo). At runtime, the engine provides the API classes; the JAR only contains the implementation (e.g. `PocRegulationEvaluator`).

### 5.3 `StubEvaluationContext.java`

- **Role**: A **concrete implementation** of `EvaluationContext` used for this POC.
- **How it works**: The constructor takes tenant id, employee id, period start/end, a **map of numeric case values** (e.g. `"BaseSalary" -> 3000.00`), and optionally a **map of string case values** (e.g. for contract type, job title). It answers `getCaseValue(name)` and `getCaseValueString(name)` from those maps.
- **Use**: In a real system, context might come from a database or API. Here the engine builds it from **StubDataRecord** (loaded from JSON).

### 5.4 `StubDataRecord.java`

- **Role**: A **data class** (Jackson-friendly) for one employee stub: tenantId, employeeId, periodStart, periodEnd, and a map of case values as strings (e.g. `"BaseSalary" -> "3000.00"`).
- **How it works**: The engine loads JSON from classpath resources (e.g. `stub-data/emp-1.json`). Each file has the shape `{ "tenantId", "employeeId", "periodStart", "periodEnd", "caseValues": { "BaseSalary": "3000.00", ... } }`. Main converts numeric case values to `BigDecimal` and builds `StubEvaluationContext` from the record.
- **Where the data lives**: `engine/src/main/resources/stub-data/` (e.g. `PR2b557c.json`, `emp-1.json`). **`stub-data/index.txt`** lists which stub files to process (one path per line, e.g. `stub-data/PR2b557c.json`). If the file is missing or empty, no employees are processed. This list is what drives **employee-by-employee** processing: each line = one employee.

### 5.5 `MinimalPayrun.java`

- **Role**: Runs the actual “payrun” for one regulation and one context.
- **How it works**:  
  - It takes a `RegulationEvaluatorLoader`, a regulation id and version, a **list of wage type numbers** (e.g. 1001, 1002, …, 1005), and an `EvaluationContext`.  
  - It gets the `RegulationEvaluator` from the loader (cached after first load).  
  - For each wage type number, it calls `evaluator.evaluateWageType(num, context)` and collects the result into a `WageTypeResult`.  
  - It returns a `List<WageTypeResult>`.
- So: **MinimalPayrun** = “for these wage types, call the regulation and collect results.”

### 5.6 `RegulationsConfig.java` and `RegulationEntry.java`

- **Role**: Jackson-friendly records for **regulations.json**. **RegulationsConfig** holds a list of **RegulationEntry**; each entry has `id`, `version`, `jar` (file name), and `evaluatorClass` (fully qualified class name). The engine loads this file from the classpath and registers every entry with the **RegulationRegistry**.
- **Example regulations.json**: `{"regulations":[{"id":"france-regulation","version":"1.0.0","jar":"poc-regulation-1.0.0.jar","evaluatorClass":"com.payroll.regulation.poc.FranceRegulationEvaluator"}]}`

### 5.7 `Main.java`

- **Role**: The **entry point** of the application (what runs when you execute the engine JAR or run the main class).
- **What it does step by step**:  
  1. **Plugins folder**: Tries `plugins` then `engine/plugins` (so it works from repo root or from `engine`).  
  2. **Regulations config**: Loads **regulations.json** into **RegulationsConfig**; fails if empty.  
  3. **Registry**: Creates a `RegulationRegistry` and **registers** every regulation from config (id, version, jar, evaluatorClass).  
  4. **Loader, payrun, and first regulation**: Creates `RegulationEvaluatorLoader(registry)` and `MinimalPayrun(loader)`; gets the **first** regulation from config and loads its evaluator; gets the **wage type list** from that evaluator via `getWageTypeNumbers()`.  
  5. **Stub list**: Loads the list of employee stub file paths from `stub-data/index.txt` (one path per line; if missing or empty, the list is empty and no employees run).  
  6. **Employee-by-employee loop**: For **each** path in the stub list: load JSON into **StubDataRecord**, convert case values to `BigDecimal`, build **StubEvaluationContext**, run **MinimalPayrun** once (regulation id/version from first config entry, wage type numbers from evaluator, context for this employee), then print `"Employee <employeeId>:"` and only the **non-zero** wage type results.

So when you run the engine, it is **config-driven** (regulations from JSON), loads the regulation JAR(s) from `engine/plugins/`, and **processes one employee at a time** using the stub list in **index.txt**.

---

## 5.8 Employee-by-employee processing

The POC does **not** batch employees: each employee is processed in isolation, one after the other.

1. **Stub list**: Main reads `stub-data/index.txt` from the classpath. Each line is a path to one stub JSON file (e.g. `stub-data/PR2b557c.json`). One line = one employee. If the file is missing or empty, the list is empty and no payruns run.
2. **Loop**: For each path in that list:
   - **Load**: Read the JSON file from the classpath into a **StubDataRecord** (tenantId, employeeId, periodStart, periodEnd, caseValues).
   - **Context**: Build a single **StubEvaluationContext** from that record (numeric and string case values).
   - **Payrun**: Call **MinimalPayrun.run(regulationId, version, wageTypeNumbers, context)** exactly **once** for this employee. The wage type list comes from the regulation evaluator’s **getWageTypeNumbers()**; the context is only this employee’s data.
   - **Output**: Print `Employee <employeeId>:` and then each wage type result with a non-zero value; then a blank line.
3. **No sharing**: There is no combined context or batch; each payrun sees one employee’s context. The same regulation and wage type list are used for every employee, but the **context** (and thus the results) change per stub file.

So: **one stub file → one StubDataRecord → one StubEvaluationContext → one payrun.run(...) → one set of printed results**. That is the employee-by-employee processing model.

---

## 6. Flow from start to finish

1. You run the engine (e.g. `./scripts/run_and_show_results.sh` or `java -jar engine/target/engine-1.0.0-SNAPSHOT.jar` from the repo root, with `engine/plugins/` containing the JAR(s) named in **regulations.json**).
2. **Main** loads **regulations.json** into **RegulationsConfig**, sets up the plugins path, creates **RegulationRegistry** and registers every regulation from config, then creates **RegulationEvaluatorLoader** and **MinimalPayrun**.
3. **Main** takes the **first** regulation from config, loads its evaluator, and gets the wage type list from **evaluator.getWageTypeNumbers()**.
4. **Main** loads the list of stub file paths from **stub-data/index.txt** (one path per line; if missing or empty, no employees are processed).
5. **For each path (employee-by-employee)**: Main loads that JSON into **StubDataRecord**, builds **StubEvaluationContext**, runs **MinimalPayrun.run(regulationId, version, wageTypeNumbers, context)** once, then prints `Employee <id>:` and non-zero wage type results.
6. **MinimalPayrun** gets the evaluator from the loader (cached), then for each wage type number calls `evaluator.evaluateWageType(num, context)` and collects **WageTypeResult**s. The regulation uses `context.getCaseValue(...)` and optionally `getCaseValueString(...)` to compute amounts.

So: **regulations.json** → **Registry**; **index.txt** → stub paths; for each path: **Stub JSON** → **StubDataRecord** → **StubEvaluationContext** → **Payrun** (one per employee) → **Results** (printed by Main).

---

## 7. Key Java concepts used (for beginners)

- **Interface**: A type that only declares method signatures. The engine depends on `RegulationEvaluator` and `EvaluationContext`, not on a specific regulation implementation.
- **Package**: `com.payroll.engine`, `com.payroll.regulation.api` — organizes classes and avoids name clashes.
- **Record**: A compact way to define an immutable data type with constructor and accessors (`WageTypeResult`).
- **`var`**: Local variable type inference (e.g. `var registry = new RegulationRegistry(...)`); the type is still fixed at compile time.
- **`List.of(...)`**: Creates an immutable list (e.g. wage type numbers).
- **`Map.of("BaseSalary", new BigDecimal("3000.00"))`**: Immutable map for stub context.
- **Optional**: Used in the registry for “maybe there is a path / class name”. Call `.orElseThrow(...)` when the value must be present.
- **URLClassLoader**: Loads classes from a JAR (or directory) so you can instantiate a class that was not on the classpath at engine startup.
- **Jackson** (ObjectMapper): Used in the engine to deserialize stub JSON into `StubDataRecord`; `@JsonCreator` / `@JsonProperty` map JSON fields to the record.

---

## 8. What you need to run it

- **One-command run**: From **payroll-engine-poc** root, run `./scripts/run_and_show_results.sh`. It builds the engine, builds **payroll-regulations-poc** (if present as a sibling repo), copies `poc-regulation-1.0.0.jar` into `engine/plugins/`, and runs the engine. You will see results per employee for both poc-regulation and france-regulation.
- **Manual build and run**:  
  - Build engine: `mvn clean install` in payroll-engine-poc (produces `engine/target/engine-1.0.0-SNAPSHOT.jar` and `engine/target/lib/` with regulation-api and Jackson).  
  - Build regulation: in payroll-regulations-poc run `mvn clean package`, then copy `poc-regulation/target/poc-regulation-1.0.0.jar` to `payroll-engine-poc/engine/plugins/`.  
  - Run: from payroll-engine-poc root, `java -jar engine/target/engine-1.0.0-SNAPSHOT.jar` (or run `com.payroll.engine.Main` from your IDE with working directory = repo root).  
- **Stub data**: Add or edit JSON files in `engine/src/main/resources/stub-data/`. Create **stub-data/index.txt** with one path per line (e.g. `stub-data/PR2b557c.json`) to define which employees to process; if index.txt is missing or empty, no employees are run.

---

## 9. Summary table

| Piece | Role |
|-------|------|
| **regulation-api** | Contract: `RegulationEvaluator` (incl. `getWageTypeNumbers()`), `EvaluationContext` (incl. `getCaseValueString`), `WageTypeResult`. |
| **RegulationsConfig / RegulationEntry** | Loaded from **regulations.json**; lists regulations (id, version, jar, evaluatorClass). Drives what is registered in the registry. |
| **RegulationRegistry** | Maps (regulation id, version) → JAR path + evaluator class name. Populated from RegulationsConfig, not hardcoded in Main. |
| **RegulationEvaluatorLoader** | Loads JAR via URLClassLoader, instantiates evaluator, caches by id:version. |
| **StubEvaluationContext** | Implementation of `EvaluationContext` with numeric and optional string case value maps. One per employee. |
| **StubDataRecord** | Jackson POJO for stub JSON: tenantId, employeeId, periodStart, periodEnd, caseValues (string map). |
| **MinimalPayrun** | For a regulation (id/version), list of wage types, and one context, calls evaluator for each wage type and returns `List<WageTypeResult>`. |
| **Main** | Loads regulations.json → registry; gets first regulation and wage type list from evaluator; loads stub paths from index.txt; **for each path**, loads StubDataRecord, builds StubEvaluationContext, runs one payrun, prints employee results (non-zero only). |
| **scripts/run_and_show_results.sh** | Builds engine and (if present) payroll-regulations-poc, copies JAR to plugins, runs engine. |

Together, they form a minimal engine that is **config-driven** (regulations from **regulations.json**), loads regulation logic from JARs in `engine/plugins/`, runs **employee-by-employee** (one stub file → one context → one payrun per employee), and prints wage type results, aligned with the [local development guide](poc_precompiled_jar_local_development_guide.md).
