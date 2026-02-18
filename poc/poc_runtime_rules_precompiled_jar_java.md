# POC: Precompiled JAR Option for Regulation Execution (Java)

**Status:** Draft  
**Related:** [poc_precompiled_jar_scope.md](poc_precompiled_jar_scope.md) — Scope (precompiled JAR only; full payrun to results) | [poc_precompiled_jar_objectives.md](poc_precompiled_jar_objectives.md) — Objectives  
**RFC:** [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) — Option 2: Precompiled (JAR / JS Bundle)  
**Language / stack:** Java

---

## Pre-requisites

Before starting the POC, ensure the following are in place.

### Environment and tools

| Requirement | Details |
|-------------|---------|
| **JDK** | Java 17 or 21 (LTS). Verify with `java -version` and `javac -version`. |
| **Build tool** | Maven 3.8+ or Gradle 7+ (choose one for both engine and regulation projects). |
| **IDE** | Optional but recommended: IntelliJ IDEA or Eclipse with Lombok plugin if you use Lombok. |
| **Git** | For versioning the POC code and (optional) separate regulation repo. |

### Repo and project layout: two repos

This POC uses **two repositories**:

| Repo | Contents | Publishes / produces |
|------|----------|----------------------|
| **payroll-engine-poc** | (1) **regulation-api** module: Java interfaces and DTOs only (`RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`). (2) **engine** module: loader, registry, minimal payrun, stub context. | Running `mvn install` in the engine repo installs **regulation-api** to the local Maven repo so that payroll-regulations-poc can depend on it. The engine app runs from the engine module; at runtime it loads regulation JAR(s) from a **plugins/** directory. |
| **payroll-regulations-poc** | Regulation implementation(s). For POC: one module (e.g. `poc-regulation`) with a single class implementing `RegulationEvaluator`. Depends on `com.payroll:regulation-api` from local Maven. | JAR file(s) (e.g. `poc-regulation-1.0.0.jar`). JAR is **not** on engine classpath at build time; you copy it into the engine repo’s **plugins/** directory and the engine loads it at runtime. |

**Build order:** (1) In **payroll-engine-poc**: `mvn install` (builds regulation-api and engine; installs regulation-api to `~/.m2/repository`). (2) In **payroll-regulations-poc**: `mvn package` (builds regulation JAR). Copy the JAR to `payroll-engine-poc/plugins/`. (3) Run the engine from payroll-engine-poc. The engine discovers the regulation JAR by path in its registry.

### Contract dependency strategy

- The **contract** (regulation-api) lives inside **payroll-engine-poc** as a Maven module. The engine repo publishes it to the local Maven repository so that **payroll-regulations-poc** can depend on it (e.g. `com.payroll:regulation-api:1.0.0-SNAPSHOT`).
- **payroll-regulations-poc** has a Maven dependency on `regulation-api` only. Use `<scope>provided</scope>` for a **thin JAR**: the engine’s classloader (parent when loading the regulation JAR) supplies the interface at runtime.
- **payroll-engine-poc** does **not** depend on payroll-regulations-poc. At runtime it loads the regulation JAR from the file system (plugin directory) via `RegulationRegistry` and `RegulationEvaluatorLoader` (URLClassLoader).

### What you need before Phase 1

- [ ] JDK 17+ installed and on PATH.
- [ ] Maven or Gradle installed (same tool for both repos).
- [ ] Two Git repos: **payroll-engine-poc**, **payroll-regulations-poc**.
- [ ] A **plugins/** directory in the engine repo (e.g. `payroll-engine-poc/engine/plugins/`). After building the regulation JAR, copy it into this directory.

---

## Development guide

This section gives a single place for **frameworks**, **repo setup**, and **ordered development steps** from zero to a running POC.

### Frameworks and dependencies

| Area | Choice | Notes |
|------|--------|--------|
| **Language** | Java 17 or 21 | LTS; no preview features required. |
| **Build** | Maven 3.8+ | Same for both repos. No Gradle in this POC. |
| **Application framework** | **None** | No Spring Boot, Quarkus, or Micronaut. Plain Java: `main`, classes, `URLClassLoader`. Keeps the POC minimal and focused on the loader contract. |
| **Testing** | JUnit 5 (Jupiter) | Use for unit tests in both repos. Add `junit-jupiter` (and optionally `maven-surefire-plugin` default) to the POMs that have tests. |
| **Other libraries** | **None required** | JDK only for regulation-api and engine core. Optional: SLF4J + Logback in the engine for logging. |

**Summary:** No application server, no dependency injection, no web layer. The engine is a simple Java app with a `main` (or a test) that builds the registry, loader, and minimal payrun and runs them.

### Repo setup

**1. Create the two repos (local or remote).**

```bash
# Example: create locally and optionally push to GitHub/GitLab
mkdir payroll-engine-poc && cd payroll-engine-poc && git init
mkdir payroll-regulations-poc && cd payroll-regulations-poc && git init
```

**2. payroll-engine-poc — directory layout.**

Create the following structure (Maven parent + two modules):

```
payroll-engine-poc/
├── pom.xml                    # Parent: <modules><module>regulation-api</module><module>engine</module></modules>
├── regulation-api/
│   ├── pom.xml                # artifactId regulation-api, packaging jar
│   └── src/main/java/com/payroll/regulation/api/
│       ├── RegulationEvaluator.java
│       ├── EvaluationContext.java
│       └── WageTypeResult.java
└── engine/
    ├── pom.xml                # artifactId engine, depends on regulation-api
    ├── plugins/               # Empty dir; copy regulation JAR here later
    └── src/main/java/com/payroll/engine/
        # (loader, registry, stub context, minimal payrun — add as you implement)
```

Parent `pom.xml`: use a parent POM with `<packaging>pom</packaging>`, `<modules>regulation-api</modules>`, `<modules>engine</modules>`. Set Java version via `maven.compiler.source` and `maven.compiler.target` (17 or 21).

**3. payroll-regulations-poc — directory layout.**

```
payroll-regulations-poc/
├── pom.xml                    # Parent with module poc-regulation (or single module at root)
└── poc-regulation/
    ├── pom.xml                # artifactId poc-regulation, depends on com.payroll:regulation-api with scope provided
    └── src/main/java/com/payroll/regulation/poc/
        └── PocRegulationEvaluator.java
```

**4. First-time build and install.**

- In **payroll-engine-poc**: run `mvn clean install`. This installs **regulation-api** into `~/.m2/repository/com/payroll/regulation-api/1.0.0-SNAPSHOT/`.
- In **payroll-regulations-poc**: run `mvn clean package`. It will resolve regulation-api from local Maven. Output: `poc-regulation/target/poc-regulation-1.0.0.jar` (or the version you set).
- Copy the regulation JAR into the engine’s plugins directory:  
  `cp payroll-regulations-poc/poc-regulation/target/poc-regulation-1.0.0.jar payroll-engine-poc/engine/plugins/`

### Development steps (ordered)

Follow in order. Each step assumes the previous is done.

| Step | Repo | Action |
|------|------|--------|
| **1** | payroll-engine-poc | Create parent POM and **regulation-api** module; add `RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`. Run `mvn install`. |
| **2** | payroll-engine-poc | Create **engine** module (POM depends on regulation-api); add package `com.payroll.engine`. Create **engine/plugins/** (empty). Run `mvn install` from root. |
| **3** | payroll-regulations-poc | Create **poc-regulation** module; POM depends on `com.payroll:regulation-api` with `<scope>provided</scope>`. Implement `PocRegulationEvaluator` with 3–5 wage type numbers. Run `mvn package`. Add unit test: instantiate evaluator, call `evaluateWageType`, assert result. |
| **4** | payroll-engine-poc | In engine module: implement **StubEvaluationContext** (implements `EvaluationContext`, holds tenant, employee, period, case values map). |
| **5** | payroll-engine-poc | In engine module: implement **RegulationRegistry** — in-memory map `(regulationId, version)` → JAR path (and optionally evaluator class name). E.g. `("poc-regulation", "1.0.0")` → `"plugins/poc-regulation-1.0.0.jar"`. Method `getJarPath(regulationId, version)`. |
| **6** | payroll-engine-poc | In engine module: implement **RegulationEvaluatorLoader** — given (regulationId, version), get JAR path from registry, create `URLClassLoader` with parent = engine classloader, load evaluator class, instantiate, cache by (regulationId, version). Return `RegulationEvaluator`. |
| **7** | payroll-engine-poc | In engine module: implement **MinimalPayrun** — takes regulationId, version, list of wage type numbers, context (or context factory). Gets evaluator from loader; for each wage type calls `evaluator.evaluateWageType(number, context)`; collects `WageTypeResult` list. |
| **8** | payroll-engine-poc | Copy regulation JAR to **engine/plugins/** if not already. Wire in a test or `main`: build Registry (with entry for poc-regulation 1.0.0), Loader, MinimalPayrun; run with wage types 1001, 1002, 1003; assert three results. |
| **9** | payroll-engine-poc | Add tests: unknown regulation/version → loader fails; optional: evaluator throws → document behaviour. Run with N = 100 or 1000 wage types; record cold vs cached time. |
| **10** | — | Write POC report (approach, metrics, failure behaviour, recommendation). |

**Run order for a full pass:** From payroll-engine-poc root: `mvn install`. From payroll-regulations-poc root: `mvn package`. Copy JAR to `payroll-engine-poc/engine/plugins/`. Run engine (test or main). No framework startup: just Java.

---

## 1. Purpose and objectives

**Primary:** Build a minimal payroll engine in Java, inspired by the .NET engine, that runs a **full payrun** from start through to **payrun results**. Regulation behaviour is provided **only** by a **precompiled JAR** (loaded from a plugin directory). See [poc_precompiled_jar_objectives.md](poc_precompiled_jar_objectives.md).

**Objectives:**

- **Prove precompiled JAR execution:** Demonstrate that regulation logic in a versioned JAR can be loaded by (regulation id, version) and invoked by the engine (`evaluateWageType`, collector lifecycle) with acceptable complexity and performance.
- **Validate full payrun flow:** PayrunStart → (per employee) EmployeeStart → wage types + collectors → EmployeeEnd → PayrunEnd → **payrun results**.
- **Versioning and resolution:** (regulation id + version) → JAR path; load by version; optionally rollback.
- **Implementation choices:** Classpath vs plugin directory, caching, classloader behaviour; document build/deploy flow and failure modes.
- **Inform future choice:** Produce evidence to support the decision between precompiled JAR and other options (e.g. external service in a separate POC).

---

## 2. POC scope

**Regulation execution:** Precompiled JAR only (no external regulation service in this POC).  
**Payrun:** Full payrun from start through to **payrun results** (PayrunStart → EmployeeStart → wage types + collectors → EmployeeEnd → PayrunEnd → payrun results). See [poc_precompiled_jar_scope.md](poc_precompiled_jar_scope.md) and [poc_precompiled_jar_objectives.md](poc_precompiled_jar_objectives.md).

### In scope

- **Engine (Java):** Minimal payroll engine that runs a **full payrun** to **payrun results**. Regulation behaviour from **precompiled JAR only**: resolve (regulation id, version) → load JAR from plugin directory → obtain `RegulationEvaluator` → call `evaluateWageType`, collector start/end as needed through the payrun flow.
- **Full payrun flow:** PayrunStart → (per employee) EmployeeStart → wage type evaluation + collector lifecycle (start/apply/end) → EmployeeEnd → PayrunEnd → **payrun results** (wage type results, collector results; minimal but complete outcome).
- **Contract:** Java interfaces `RegulationEvaluator`, `EvaluationContext`, `WageTypeResult`, and collector lifecycle as needed. Enough to drive wage types and collectors through the payrun.
- **Sample regulation JAR:** One JAR implementing the contract with wage type and collector logic (trivial or simplified for POC).
- **Versioning / resolution:** (regulation id, version) → JAR path. Load by version; optionally rollback.
- **Caching:** Load evaluator once per (regulationId, version); reuse across payrun. Document approach.
- **Persistence for payrun:** Storage (in-memory or minimal) sufficient to run the payrun and produce payrun results.
- **Failure handling:** Missing JAR, wrong version, evaluator exception; document behaviour.
- **Tests:** Unit tests for contract, loader, payrun steps; integration test for full payrun to results. Optional benchmark.
- **Documentation:** POC report: design, payrun flow, metrics, failure behaviour, recommendations.

### Out of scope

- External regulation service (HTTP/gRPC); separate POC or later phase.
- Real France/India/Swiss regulation logic; only simplified/stub logic in sample JAR.
- Production DB or full persistence schema; minimal storage for POC.
- Production-grade security (e.g. signed JARs, sandboxing); note for production.
- Multi-tenant isolation beyond what is needed for one payrun.
- Report generation beyond payrun results (e.g. no PDF/Excel reports).
- Detailed .NET comparison or migration tooling.

---

## 3. POC steps

### Phase 1: Contract and sample JAR (Days 1–2)

1. **Define Java contract (payroll-engine-poc repo)**
   - In **payroll-engine-poc**, add a **regulation-api** module with `EvaluationContext`, `RegulationEvaluator`, `WageTypeResult` (see §6.2). Run `mvn install` from the engine repo so regulation-api is published to local Maven.
   - Document the contract in the engine repo README or Javadoc.

2. **Create sample regulation (payroll-regulations-poc repo)**
   - In repo **payroll-regulations-poc**: Maven dependency on `com.payroll:regulation-api` (use `<scope>provided</scope>` for thin JAR).
   - Single class implementing `RegulationEvaluator`; 3–5 wage type numbers with trivial logic.
   - Build JAR: `mvn package` → e.g. `poc-regulation-1.0.0.jar`. Copy JAR to **payroll-engine-poc/plugins/** for runtime loading.

3. **Verify**  
   - In payroll-regulations-poc: unit test that instantiates the evaluator and calls `evaluateWageType` with a stub context; assert result.

### Phase 2: Engine loader and resolution (Days 3–4)

4. **JAR loading (payroll-engine-poc repo)**
   - Use **plugin directory** loading: registry maps (regulationId, version) to JAR path (e.g. `plugins/poc-regulation-1.0.0.jar`).
   - Implement **RegulationEvaluatorLoader**: resolve path → URLClassLoader(urls, engine classloader) → load evaluator class from JAR → instantiate and cache by (regulationId, version).

5. **Version resolution**
   - In engine: stub “config” or in-memory map: (regulation id, version) → JAR path (and optionally evaluator class name).
   - Implement resolution and handle “missing JAR” / “unknown version” with clear errors.

6. **Tests**
   - Load evaluator for (e.g. "poc-regulation", "1.0.0"); call evaluateWageType for each wage type number; assert no exception and sane result.
   - Optional: load second version (e.g. 1.0.1) and confirm different behaviour or separate instance.

### Phase 3: Minimal payrun and evaluation (Days 5–7)

7. **Minimal payrun**
   - “Payrun” that: (1) resolves regulation (e.g. single regulation for POC), (2) loads evaluator once, (3) for each of N wage types (from stub list), builds EvaluationContext (stub case values, period, employee), (4) calls evaluator.evaluateWageType, (5) collects results into a list (WageTypeResult).
   - Run with N = 10, 100, 1000 (or 10k) and record elapsed time; document “first load” vs “cached” if noticeable.

8. **Failure modes**
   - Test: missing JAR, wrong version, evaluator throws exception. Document how engine should behave (fail payrun, fail single wage type, retry, etc.).

9. **Documentation and report**
   - POC report: approach (classpath vs URLClassLoader, caching), metrics (load time, eval time per wage type), lessons learned, and recommendation (proceed with precompiled JAR / refine / abandon).
   - List open questions for full implementation (e.g. artifact registry, rollback, multiple regulations per payrun).

---

## 4. Evaluation (during and after POC)

- **Feasibility:** Can we load and call a JAR by (regulationId, version) with acceptable complexity? Any showstoppers (e.g. classloader conflicts, reflection issues)?
- **Performance:** Time to load JAR and create evaluator (one-time); time per evaluateWageType call; behaviour under 1k–10k evaluations per “payrun”.
- **Operability:** How would version rollback work? What happens when JAR is missing or incompatible? Is the contract stable enough to support multiple regulations?
- **Developer experience:** How easy is it to add a new regulation JAR and wire it in? Build and deploy steps.
- **Risks:** Classloader leaks, version skew between engine and JAR contract, multi-regulation memory usage.

Findings will be fed into the **Evaluation criteria and decision making** document to inform the choice between precompiled JAR and other options (e.g. external service in a later phase).

---

## 5. Deliverables

| Deliverable | Description |
|-------------|-------------|
| Contract (API) | Java interfaces and DTOs: `RegulationEvaluator`, `EvaluationContext`, and any shared types. |
| Sample regulation JAR | One JAR implementing the contract with 3–5 wage types and optional collector. |
| Engine loader | Code that resolves (regulationId, version) to JAR, loads it, caches evaluator, and exposes `RegulationEvaluator`. |
| Minimal payrun | Code path: resolve → load → loop wage types → evaluate → collect results. |
| Tests | Unit tests for contract, loader, and payrun; optional benchmark. |
| POC report | Document with approach, metrics, failure-mode behaviour, recommendations, and open questions. |

---

## 6. What to implement (concrete)

### 6.1 Project structure (two repos)

**Repo 1: payroll-engine-poc**

```
payroll-engine-poc/
├── pom.xml                          # parent POM: modules regulation-api, engine
├── regulation-api/
│   ├── pom.xml                      # groupId com.payroll, artifactId regulation-api, version 1.0.0-SNAPSHOT
│   └── src/main/java/com/payroll/regulation/api/
│       ├── RegulationEvaluator.java
│       ├── EvaluationContext.java
│       └── WageTypeResult.java
├── engine/
│   ├── pom.xml                      # depends on com.payroll:regulation-api (same repo)
│   ├── plugins/                     # copy regulation JAR(s) here at runtime
│   │   └── poc-regulation-1.0.0.jar
│   └── src/main/java/com/payroll/engine/
│       ├── RegulationEvaluatorLoader.java
│       ├── RegulationRegistry.java
│       ├── MinimalPayrun.java
│       └── StubEvaluationContext.java
```

**Repo 2: payroll-regulations-poc**

```
payroll-regulations-poc/
├── pom.xml                          # parent POM with module poc-regulation (or single module at root)
├── poc-regulation/
│   ├── pom.xml                      # depends on com.payroll:regulation-api (from engine repo mvn install)
│   └── src/main/java/com/payroll/regulation/poc/
│       └── PocRegulationEvaluator.java
```

The engine does **not** depend on payroll-regulations-poc at build time. It loads the regulation JAR from `engine/plugins/` at runtime via `URLClassLoader`. Run `mvn install` in payroll-engine-poc first so regulation-api is in local Maven; then build payroll-regulations-poc.

### 6.2 Contract (regulation-api module in payroll-engine-poc)

| Type | Kind | Purpose |
|------|------|---------|
| **RegulationEvaluator** | Interface | Implemented by each regulation JAR. Engine calls it with context. |
| **EvaluationContext** | Interface (or class) | Supplies case values, period, employee id, and (optional) lookups to the evaluator. |
| **WageTypeResult** | Record / class | Holds wage type number and computed value (BigDecimal); returned from payrun. |

**RegulationEvaluator** (minimal for POC):

```java
package com.payroll.regulation.api;

import java.math.BigDecimal;

public interface RegulationEvaluator {
    /** Returns the value for the given wage type number in the given context. */
    BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context);

    /** Optional for POC: called when a collector starts. Default no-op. */
    default void collectorStart(String collectorName, EvaluationContext context) {}

    /** Optional for POC: called when a collector ends. Default no-op. */
    default void collectorEnd(String collectorName, EvaluationContext context) {}
}
```

**EvaluationContext** (minimal for POC):

```java
package com.payroll.regulation.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface EvaluationContext {
    String getTenantId();
    String getEmployeeId();
    LocalDate getPeriodStart();
    LocalDate getPeriodEnd();
    /** Case value by name (e.g. "BaseSalary"). Null if absent. */
    BigDecimal getCaseValue(String caseName);
    /** Optional: lookup by table and key. */
    default String getLookup(String lookupName, String key) { return null; }
}
```

**WageTypeResult** (can live in api or engine):

```java
package com.payroll.regulation.api;

import java.math.BigDecimal;

public record WageTypeResult(int wageTypeNumber, BigDecimal value) {}
```

### 6.3 Sample regulation (payroll-regulations-poc) — what to implement

| Item | Implementation |
|------|----------------|
| **Class name** | `PocRegulationEvaluator` (or any name; engine will load by interface). |
| **Constructor** | No-arg (engine will instantiate via reflection or SPI). |
| **Wage types** | Map 3–5 wage type numbers to logic, e.g. 1001 → 100.00, 1002 → context.getCaseValue("BaseSalary") * 0.2, 1003 → Min(500, context.getCaseValue("BaseSalary")). |
| **JAR artifact** | Build as `poc-regulation-1.0.0.jar` in payroll-regulations-poc. ensure it includes regulation-api classes (or mark as provided and document that engine supplies API at load time). For URLClassLoader, JAR must be self-contained (include API) or engine’s classloader must be parent. |

### 6.4 Engine (payroll-engine-poc, engine module) — what to implement

| Component | Responsibility |
|-----------|----------------|
| **RegulationRegistry** | In-memory map: (regulationId, version) → JAR path or class name. Method: `String getJarPath(String regulationId, String version)` or `String getEvaluatorClassName(String regulationId, String version)`. Throw or return optional if not found. |
| **RegulationEvaluatorLoader** | Given (regulationId, version): (1) resolve JAR path or class name from registry, (2) if not cached, load JAR (URLClassLoader) or load class (current classloader), (3) instantiate class as RegulationEvaluator, (4) cache by (regulationId, version), (5) return instance. |
| **Stub EvaluationContext** | A simple implementation of EvaluationContext for tests and minimal payrun (e.g. in-memory map for case values, fixed period, tenant, employee). |
| **MinimalPayrun** | Accept regulationId, version, list of wage type numbers. Resolve registry → load evaluator → for each wage type build context → evaluator.evaluateWageType(...) → collect WageTypeResult list. Return list and (optional) elapsed time. |

### 6.5 Loading strategy (plugin directory)

The engine does not have the regulation JAR on its classpath at build time. Use **plugin directory** loading:

| What | How |
|------|-----|
| **Registry** | Maps (regulationId, version) to **JAR path** (e.g. `plugins/poc-regulation-1.0.0.jar` or `%CONFIG%/plugins/poc-regulation-1.0.0.jar`). |
| **Loader** | Resolves path → builds `URL[]` from file → `new URLClassLoader(urls, getClass().getClassLoader())` (parent = engine classloader so `RegulationEvaluator` is visible). Load the evaluator class from the JAR (class name in registry or in JAR manifest). Instantiate with no-arg constructor; cast to `RegulationEvaluator`; cache by (regulationId, version). |
| **Regulation JAR** | Must contain the implementation class. Prefer **thin JAR**: mark `regulation-api` as `provided` in payroll-regulations-poc’s POM so it is not bundled; the engine’s classpath has the API, so the loaded class will see it via parent classloader. |

---

## 7. How to implement (step-by-step, two repos)

### Step 1: Create payroll-engine-poc repo with regulation-api and engine modules

1. Create Git repo **payroll-engine-poc**. Add a **parent** `pom.xml` with modules `regulation-api` and `engine`.
2. Add module **regulation-api**: `pom.xml` (groupId `com.payroll`, artifactId `regulation-api`, version `1.0.0-SNAPSHOT`) and package `com.payroll.regulation.api` with `RegulationEvaluator.java`, `EvaluationContext.java`, `WageTypeResult.java` (see §6.2).
3. Add module **engine**: `pom.xml` with dependency on `com.payroll:regulation-api` (same repo). Create package `com.payroll.engine` for loader, registry, payrun, stub context. Add directory **engine/plugins/** (for regulation JARs at runtime).
4. Run **`mvn install`** from the engine repo root. This builds both modules and publishes **regulation-api** to local Maven (`~/.m2/repository/com/payroll/regulation-api/1.0.0-SNAPSHOT/`) so payroll-regulations-poc can depend on it.

### Step 2: Create payroll-regulations-poc repo and build JAR

1. Create Git repo **payroll-regulations-poc**. Add a module (e.g. **poc-regulation**) with `pom.xml` depending on `com.payroll:regulation-api:1.0.0-SNAPSHOT`. Use `<scope>provided</scope>` for a thin JAR.
2. Create `PocRegulationEvaluator` in package `com.payroll.regulation.poc`, implementing `RegulationEvaluator`, with 3–5 wage type numbers and trivial logic (see §6.3).
3. Run **`mvn package`**. Output: e.g. `poc-regulation/target/poc-regulation-1.0.0.jar`.
4. **Copy the JAR** to the engine’s plugin directory: e.g. `cp poc-regulation/target/poc-regulation-1.0.0.jar /path/to/payroll-engine-poc/engine/plugins/`.

### Step 3: Implement stub EvaluationContext (in payroll-engine-poc, engine module)

In the **engine module** of payroll-engine-poc, create a simple implementation, e.g.:

```java
public class StubEvaluationContext implements EvaluationContext {
    private final String tenantId;
    private final String employeeId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final Map<String, BigDecimal> caseValues;

    // constructor and getters setting fields
    @Override public BigDecimal getCaseValue(String name) { return caseValues.getOrDefault(name, null); }
    // ... other getters
}
```

Use this in tests and in MinimalPayrun.

### Step 4: Implement RegulationRegistry (in payroll-engine-poc, engine module)

- Create a class that holds a map: key = `regulationId + ":" + version`, value = **JAR file path** (absolute or relative to engine working directory).
- E.g. `("poc-regulation", "1.0.0")` → `"plugins/poc-regulation-1.0.0.jar"` (path relative to engine working directory, or absolute path where you copied the JAR).
- Populate in code or from a properties file. Provide method `String getJarPath(String regulationId, String version)`; throw `IllegalArgumentException` or return `Optional.empty()` if not found.
- Optionally also map to **evaluator class name** (e.g. `"com.payroll.regulation.poc.PocRegulationEvaluator"`) so the loader knows which class to load from the JAR. Either store (path, className) per key or derive class name by convention (e.g. main class in JAR manifest).

### Step 5: Implement RegulationEvaluatorLoader (in payroll-engine-poc, engine module)

- **Plugin directory:** Call `RegulationRegistry.getJarPath(regulationId, version)`. Build `URL[]` from the file path (e.g. `new File(path).toURI().toURL()`). Create `URLClassLoader urlCl = new URLClassLoader(urls, getClass().getClassLoader())` (parent = engine classloader so `RegulationEvaluator` is visible). Load the evaluator class: either from registry (class name) or from JAR manifest (Main-Class or custom attribute). Then `urlCl.loadClass(className).asSubclass(RegulationEvaluator.class).getConstructor().newInstance()`.
- **Cache:** Cache the `RegulationEvaluator` instance (and optionally the ClassLoader) by key `regulationId + ":" + version` in a `ConcurrentHashMap`. Synchronize or use `computeIfAbsent` so the same JAR is not loaded twice.
- **Class name:** If not in registry, you can require a fixed convention (e.g. `com.payroll.regulation.poc.PocRegulationEvaluator`) or read from JAR manifest (e.g. attribute `Regulation-Evaluator-Class`).

### Step 6: Implement MinimalPayrun (in payroll-engine-poc, engine module)

- Input: `regulationId`, `version`, `List<Integer> wageTypeNumbers`, and a way to build EvaluationContext (e.g. one context per “employee” for POC).
- Steps: (1) `loader.getEvaluator(regulationId, version)`, (2) for each wage type number, build `StubEvaluationContext` (same or varying case values), (3) `evaluator.evaluateWageType(number, context)`, (4) add `new WageTypeResult(number, value)` to result list, (5) return list. Optionally record start/end time and log or return duration.

### Step 7: Wire and run

- In **payroll-engine-poc** (engine module): create Registry with entry `("poc-regulation", "1.0.0")` → path to `plugins/poc-regulation-1.0.0.jar` (and evaluator class name if stored). Create Loader and MinimalPayrun. Run `minimalPayrun.run("poc-regulation", "1.0.0", List.of(1001, 1002, 1003), stubContext)`. Assert list size 3 and values as expected. Ensure the JAR was copied to **engine/plugins/** before running.
- Run with N = 10, 100, 1000: build a list of wage type numbers (repeating 1001–1005), run once (cold: first load from JAR), run again (cached). Record total time and (optional) time per evaluation.

### Step 8: Failure-mode tests

- **Missing JAR / unknown version:** Registry returns null or throws for ("unknown", "1.0.0"). Assert loader throws or returns empty; payrun fails fast with clear message.
- **Evaluator throws:** In **payroll-regulations-poc**, for wage type 9999 throw RuntimeException. Rebuild the JAR, copy to engine/plugins/, and assert payrun or single wage type fails; document whether you fail entire payrun or record error result per wage type.

### Step 9: Document and report

- Write POC report: loading strategy chosen, cache design, metrics (load time, eval time, total for 1k/10k), failure behaviour, recommendation. Add open questions for production (signed JAR, rollback, multi-regulation).

---

## 8. Timeline and effort (indicative)

| Phase | Duration | Focus |
|-------|----------|--------|
| Phase 1 | 2 days | Contract + sample JAR + unit test. |
| Phase 2 | 2 days | Loader, version resolution, caching, tests. |
| Phase 3 | 2–3 days | Minimal payrun, benchmarks, failure modes, report. |
| **Total** | **~7 working days** | Single developer; adjust if part-time or extra scenarios. |

---

## 9. Success criteria (go/no-go for “proceed to full design”)

- Engine can load a regulation JAR by id/version and obtain a working `RegulationEvaluator`.
- At least one full “minimal payrun” path runs (resolve → load → evaluate N wage types → results) without fundamental blockers.
- Load time and per-call latency are documented; no unacceptable performance red flags.
- Failure modes (missing JAR, exception in evaluator) are identified and documented with suggested behaviour.
- POC report is written and shared for use in post-POC evaluation and decision.

---

## 10. AWS deployment (eventual)

Yes, the engine and regulations can be deployed on AWS. Below are practical options and how the two-repo / plugin-JAR model fits.

### Can it be done?

Yes. The engine is a standard Java application; regulation JARs are files the engine loads from a directory (or from S3). Both can run on AWS compute (ECS, Lambda, EC2, App Runner) with the right packaging and configuration.

### How: deployment options

| Option | Description | Engine | Regulation JARs | Best for |
|--------|-------------|--------|------------------|----------|
| **ECS (Fargate) or ECS on EC2** | Run engine as a container. One task definition; image includes engine JAR + (optionally) plugin JARs in a folder. | Single service (REST or message-driven). Scale by task count. | Bake into image at build time, or mount from EFS/S3 at startup (download to container). | Production: long-running engine, multiple regulations, scaling. |
| **AWS App Runner** | Managed container service; simpler than ECS. Build from source or from ECR image. | One App Runner service. | Include in image or pull from S3 on start. | Simpler ops; single region. |
| **EC2** | VM(s) running the engine (e.g. `java -jar engine.jar`). | Traditional deploy (systemd, or run in background). Plugins in a local dir (e.g. `/opt/engine/plugins/`). | Copy JARs to plugin dir (Ansible, SSM, or CI/CD). | Full control; lift-and-shift. |
| **Lambda** | Engine runs per payrun invocation (cold start each time unless provisioned concurrency). | Package engine + dependencies as Lambda deployment package. Plugin JARs: in layer or in package, or download from S3 on first invoke and cache in `/tmp`. | S3 bucket; Lambda downloads to `/tmp/plugins/` on cold start; loader uses that path. Layer possible but size limits. | Event-driven payruns; pay per run; cold start acceptable. |

### Recommended pattern for production (two repos)

1. **CI/CD (e.g. GitHub Actions, AWS CodePipeline)**  
   - **payroll-engine-poc:** Build engine JAR (or image). No regulation JAR in this build.  
   - **payroll-regulations-poc:** Build regulation JAR(s) per repo (e.g. `poc-regulation-1.0.0.jar`). Publish to artifact store (e.g. S3, CodeArtifact) or embed in engine image at a later stage.

2. **Regulation JARs at runtime**  
   - **Option A — Baked in image:** A separate “assembly” or Docker build (downstream of both repos) copies engine JAR + chosen regulation JAR(s) into an image with a fixed layout (e.g. `/app/engine.jar`, `/app/plugins/*.jar`). Engine config points to `/app/plugins`. Deploy that image to ECS/App Runner/EC2.  
   - **Option B — S3 + startup:** Engine container starts, reads config (e.g. env or Parameter Store) for an S3 bucket/prefix, downloads JARs to a local `plugins/` dir, then starts the engine. Registry maps (regulationId, version) to those local paths.  
   - **Option C — EFS:** Mount an EFS volume at `engine/plugins/`. A separate process or pipeline copies regulation JARs to EFS; engine reads from the mount. Good for many JARs or frequent updates without rebuilding the image.

3. **Engine configuration on AWS**  
   - Plugin directory path: env var (e.g. `PLUGINS_DIR=/app/plugins`) or config file (e.g. from S3 or Parameter Store).  
   - Registry (regulation id → JAR path / class name): config file, or derived from directory listing of `plugins/` (e.g. `poc-regulation-1.0.0.jar` → regulationId `poc-regulation`, version `1.0.0`).

4. **Supporting services**  
   - When you add a DB: RDS (PostgreSQL/SQL Server) or Aurora. Engine connects via JDBC; no change to the plugin-loading model.  
   - Secrets: AWS Secrets Manager or Parameter Store for DB credentials, API keys.  
   - Observability: CloudWatch Logs (engine logs to stdout/stderr); optional X-Ray or metrics for payrun duration and loader cache hits.

### Summary

- **Yes**, the POC and a full engine can run on AWS.  
- **How:** Run the engine as a container (ECS, App Runner) or on EC2; optionally use Lambda for event-driven payruns.  
- **Regulation JARs:** Either bake them into the image, download from S3 (or similar) at startup, or serve from EFS.  
- **Two repos:** Engine image is built from payroll-engine-poc; regulation JARs from payroll-regulations-poc are added at build time (assembly image) or at runtime (S3/EFS). No change to the contract or loader logic.

---

## 11. References

- **Local development (step-by-step for new Java developers):** [poc_precompiled_jar_local_development_guide.md](poc_precompiled_jar_local_development_guide.md).
- RFC: [rfc_runtime_rules_scripting_options.md](rfc_runtime_rules_scripting_options.md) — Option 2 (Precompiled JAR/JS Bundle).
- Diagram: [diagram/03_option2_build_deploy_run.md](diagram/03_option2_build_deploy_run.md).
