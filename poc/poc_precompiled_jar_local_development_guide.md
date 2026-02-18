# Local development guide: Precompiled JAR POC

This guide walks you through building the **precompiled JAR** POC from scratch: a minimal engine that loads regulation from a plugin directory and runs a payrun to wage type results. **External regulation service is out of scope** for this POC; regulation is **precompiled JAR only**. It assumes you are new to Java and explains each step with exact commands and copy-paste code.

**Related:** [poc_precompiled_jar_scope.md](poc_precompiled_jar_scope.md) — Scope (precompiled JAR only; full payrun to results) | [poc_precompiled_jar_objectives.md](poc_precompiled_jar_objectives.md) — Objectives | [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md) — POC details and evaluation.

**Repos:** This guide uses two repositories, both in the workspace: **payroll-engine-poc** (engine + regulation-api) and **payroll-regulations-poc** (France regulation JAR). Use their paths as the project roots; you do not need to create new folders or run `git init` if the repos are already cloned/opened.

---

## Status of tasks

Track progress by updating the status as you complete each part. Use: **Not started** | **In progress** | **Done**.

| Task | Repo | Status |
|------|------|--------|
| Part 1: Prerequisites (JDK, Maven, Git, IDE, workspace) | — | Done |
| Part 2: Concepts | — | Reference |
| Part 3.1: Parent POM (modules: regulation-api, engine) | payroll-engine-poc | Done |
| Part 3.2: regulation-api module (RegulationEvaluator, EvaluationContext, WageTypeResult) | payroll-engine-poc | Done |
| Part 3.3: engine module skeleton + plugins/ | payroll-engine-poc | Done |
| Part 4.1: payroll-regulations-poc parent POM + regulation module | payroll-regulations-poc | Done |
| Part 4.2: WageTypes.json (wage type numbers, order, mapping, collectors) | payroll-regulations-poc | Done |
| Part 4.3: France rules (FranceRules.java + FranceRulesContext) | payroll-regulations-poc | Done |
| Part 4.4: Running the France rules (required development) | both | Done |
| Part 5.1: StubEvaluationContext | payroll-engine-poc | Done |
| Part 5.2: RegulationRegistry (JAR path only) | payroll-engine-poc | Done |
| Part 5.3: RegulationEvaluatorLoader | payroll-engine-poc | Done |
| Part 5.4: MinimalPayrun (JAR path) | payroll-engine-poc | Done |
| Part 5.5: Main (run payrun via JAR) | payroll-engine-poc | Done |
| Part 6: Build and run (install, package, copy JAR, run engine) | both | Not started |
| Part 7: Troubleshooting | — | Reference |
| Part 8: Optional unit test | payroll-engine-poc | Optional |

---

## Part 1: Prerequisites

### 1.1 Install Java (JDK 17, 21, or 25)

You need a **JDK** (Java Development Kit), not just a JRE. **JDK 25 is the latest LTS** (per [Oracle's Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)); use 25 when available. JDK 21 and 17 are also LTS and valid if your environment or CI does not have 25 yet. Set `maven.compiler.source` and `maven.compiler.target` in the POMs to the version you install.

**macOS (Homebrew):**
```bash
# Prefer latest LTS: openjdk@25; or openjdk@21 / openjdk@17
brew install openjdk@25
# Add to PATH (for 25): sudo ln -sfn $(brew --prefix)/opt/openjdk@25/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-25.jdk
```

**Windows:** Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/) for Java 25 (latest LTS), or 21/17. During setup, check “Set JAVA_HOME” and “Add to PATH”.

**Verify:**
```bash
java -version
javac -version
```
You should see the version you installed (e.g. 17, 21, or 25). Both `java` and `javac` must work.

---

### 1.2 Install Maven

Maven is the build tool: it compiles code, runs tests, and packages JARs.

**macOS (Homebrew):**
```bash
brew install maven
```

**Windows:** Download from [maven.apache.org](https://maven.apache.org/download.cgi), unzip to e.g. `C:\Program Files\Apache\maven`, and add the `bin` folder to your PATH. Set `JAVA_HOME` to your JDK install directory.

**Verify:**
```bash
mvn -version
```
You should see Maven version 3.8 or higher and the Java version it is using.

---

### 1.3 Install Git

**macOS:** Often pre-installed; or `brew install git`.  
**Windows:** Download from [git-scm.com](https://git-scm.com/download/win).

**Verify:** `git --version`

---

### 1.4 (Optional) Install an IDE

- **IntelliJ IDEA** (Community is free): [jetbrains.com/idea](https://www.jetbrains.com/idea/)
- **Eclipse:** [eclipse.org](https://www.eclipse.org/downloads/) — choose “Eclipse IDE for Java Developers”

You can also use any text editor and run everything from the terminal; this guide uses terminal commands.

---

### 1.5 Workspace and repos

The two repos **payroll-engine-poc** and **payroll-regulations-poc** are already in your workspace. Use their paths as the project roots (e.g. `payroll-engine-poc/` and `payroll-regulations-poc/` alongside each other). All commands below assume you run them from the appropriate repo root or from the path indicated (e.g. `cd payroll-engine-poc`).

---

## Part 2: Concepts you need

- **Maven:** Build tool. It reads `pom.xml` (Project Object Model) in each project and module. Running `mvn compile` compiles Java files; `mvn package` builds a JAR; `mvn install` builds and **installs** the JAR into your local Maven repository (`~/.m2/repository` on macOS/Linux) so other projects can use it as a dependency.
- **Module:** A sub-project inside a parent project. The **parent** has a `pom.xml` with `<modules>`. Each module has its own `pom.xml` and `src/main/java/` (and optionally `src/test/java/`).
- **Regulation execution:** **Precompiled JAR only** (no external regulation service in this POC). Engine resolves regulation by (id, version) → loads a JAR from a `plugins/` directory → instantiates `RegulationEvaluator` → calls `evaluateWageType(wageTypeNumber, context)` in process.
- **JAR:** A Java archive (a ZIP file containing compiled `.class` files). The engine loads regulation JARs from `plugins/` at runtime using `URLClassLoader`.
- **Two repos:** **payroll-engine-poc** (regulation-api + engine) and **payroll-regulations-poc** (France regulation JAR). You build the engine repo first and install the regulation-api JAR; then you build the regulation JAR and copy it into the engine’s plugins directory.

---

## Part 3: payroll-engine-poc (engine repo)

Use the existing **payroll-engine-poc** repo in your workspace. Add or update the following structure and files.

### Step 3.1 Parent POM

Create or update the **parent** `pom.xml` in the root of **payroll-engine-poc**:

**File: `payroll-engine-poc/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.payroll</groupId>
    <artifactId>payroll-engine-poc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Payroll Engine POC</name>
    <description>Minimal engine and regulation API for precompiled JAR POC (no external service)</description>

    <modules>
        <module>regulation-api</module>
        <module>engine</module>
    </modules>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
```

- Use `25` (latest LTS) here, or `21`/`17` if your JDK is older.
- `packaging>pom</packaging>` means this is a parent that only aggregates modules (it does not produce a JAR itself).
- `modules`: `regulation-api` (JAR contract), `engine` (loader, registry, payrun).

---

### Step 3.2 Create the regulation-api module

Create the folder structure and the API (contract) classes.

```bash
mkdir -p regulation-api/src/main/java/com/payroll/regulation/api
```

**File: `regulation-api/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payroll</groupId>
        <artifactId>payroll-engine-poc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>regulation-api</artifactId>
    <packaging>jar</packaging>
    <name>Regulation API</name>
    <description>Contract (interfaces) for regulation evaluators</description>
</project>
```

**File: `regulation-api/src/main/java/com/payroll/regulation/api/RegulationEvaluator.java`**
```java
package com.payroll.regulation.api;

import java.math.BigDecimal;

public interface RegulationEvaluator {
    BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context);

    default void collectorStart(String collectorName, EvaluationContext context) {}
    default void collectorEnd(String collectorName, EvaluationContext context) {}
}
```

**File: `regulation-api/src/main/java/com/payroll/regulation/api/EvaluationContext.java`**
```java
package com.payroll.regulation.api;

import java.math.BigDecimal;

public interface EvaluationContext {
    String getTenantId();
    String getEmployeeId();
    java.time.LocalDate getPeriodStart();
    java.time.LocalDate getPeriodEnd();
    BigDecimal getCaseValue(String caseName);
    default String getLookup(String lookupName, String key) { return null; }
}
```

**File: `regulation-api/src/main/java/com/payroll/regulation/api/WageTypeResult.java`**
```java
package com.payroll.regulation.api;

import java.math.BigDecimal;

public record WageTypeResult(int wageTypeNumber, BigDecimal value) {}
```

**Build and install the API into your local Maven repo:**
```bash
cd payroll-engine-poc
mvn clean install
```

You should see `BUILD SUCCESS`. The JAR `regulation-api-1.0.0-SNAPSHOT.jar` is now in `~/.m2/repository/com/payroll/regulation-api/1.0.0-SNAPSHOT/`. Other projects (like payroll-regulations-poc) can depend on it with `com.payroll:regulation-api:1.0.0-SNAPSHOT`.

---

### Step 3.3 Create the engine module (skeleton)

Create the engine module and the **plugins** directory. The engine contains the JAR loader, registry, and payrun (precompiled JAR only). Skip the next block (regulation-service-api) — it is out of scope. Go to **File: engine/pom.xml** below.

**OUT OF SCOPE (do not implement): regulation-service-api module** Both the engine’s HTTP client and the stub service use these DTOs.

```bash
mkdir -p regulation-service-api/src/main/java/com/payroll/regulation/service/api
```

**File: `regulation-service-api/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payroll</groupId>
        <artifactId>payroll-engine-poc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>regulation-service-api</artifactId>
    <packaging>jar</packaging>
    <name>Regulation Service API</name>
    <description>Request/response DTOs for external regulation service (HTTP)</description>
</project>
```

**File: `regulation-service-api/src/main/java/com/payroll/regulation/service/api/EvaluateWageTypeRequest.java`**
```java
package com.payroll.regulation.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record EvaluateWageTypeRequest(
    String tenantId,
    String regulationId,
    String employeeId,
    LocalDate periodStart,
    LocalDate periodEnd,
    int wageTypeNumber,
    Map<String, BigDecimal> caseValues
) {}
```

**File: `regulation-service-api/src/main/java/com/payroll/regulation/service/api/EvaluateWageTypeResponse.java`**
```java
package com.payroll.regulation.service.api;

import java.math.BigDecimal;

public record EvaluateWageTypeResponse(BigDecimal value) {}
```

For errors, the stub can return HTTP 500 or 400; the engine client will throw or map to an error result. An optional `EvaluateWageTypeError` record can be added later if you need a structured error body.

Run from parent: `mvn clean install`. The engine and regulation-service-stub will depend on this module.

---

### Step 3.4 Create the engine module (skeleton)

Create the engine module and the **plugins** directory. The engine will contain the JAR loader, the regulation service HTTP client, and the payrun that supports both paths.

```bash
mkdir -p engine/src/main/java/com/payroll/engine
mkdir -p engine/plugins
```

**File: `engine/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payroll</groupId>
        <artifactId>payroll-engine-poc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>engine</artifactId>
    <packaging>jar</packaging>
    <name>Engine</name>
    <description>Engine: JAR loader and payrun (precompiled JAR only)</description>

    <dependencies>
        <dependency>
            <groupId>com.payroll</groupId>
            <artifactId>regulation-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.payroll</groupId>
            <artifactId>regulation-service-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.15.3</version>
        </dependency>
    </dependencies>
</project>
```

Jackson is used by the engine’s regulation service HTTP client to serialize requests and parse responses.

Run from the **parent** directory: `mvn clean install`. The `plugins/` folder is where you will copy the regulation JAR.

---

### Step 3.5 (Out of scope) regulation-service-stub module

**Skip this step** — external regulation service is out of scope for this POC. The following is retained for reference only.

The stub is a minimal HTTP service that implements the external-service contract. The engine will call it for the **service path**. It uses the JDK’s built-in `HttpServer` (no Spring) and Jackson for JSON.

```bash
mkdir -p regulation-service-stub/src/main/java/com/payroll/regulation/stub
```

**File: `regulation-service-stub/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payroll</groupId>
        <artifactId>payroll-engine-poc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>regulation-service-stub</artifactId>
    <packaging>jar</packaging>
    <name>Regulation Service Stub</name>
    <description>Minimal HTTP service for external regulation service path</description>

    <dependencies>
        <dependency>
            <groupId>com.payroll</groupId>
            <artifactId>regulation-service-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.15.3</version>
        </dependency>
    </dependencies>
</project>
```

**File: `regulation-service-stub/src/main/java/com/payroll/regulation/stub/StubRegulationServer.java`**
```java
package com.payroll.regulation.stub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payroll.regulation.service.api.EvaluateWageTypeRequest;
import com.payroll.regulation.service.api.EvaluateWageTypeResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class StubRegulationServer {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/evaluate/wage-type", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                EvaluateWageTypeRequest req = mapper.readValue(exchange.getRequestBody(), EvaluateWageTypeRequest.class);
                BigDecimal value = evaluate(req.wageTypeNumber(), req.caseValues());
                byte[] body = mapper.writeValueAsBytes(new EvaluateWageTypeResponse(value));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception e) {
                byte[] err = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            }
        });
        server.start();
        System.out.println("Stub regulation service listening on port " + port + ", path POST /evaluate/wage-type");
    }

    private static BigDecimal evaluate(int wageTypeNumber, Map<String, BigDecimal> caseValues) {
        BigDecimal base = caseValues != null ? caseValues.get("BaseSalary") : null;
        return switch (wageTypeNumber) {
            case 1001 -> new BigDecimal("100.00");
            case 1002 -> base != null ? base.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            case 1003 -> base != null ? base.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            case 1004 -> base != null ? new BigDecimal("500").min(base) : new BigDecimal("500");
            case 1005 -> base != null ? base : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }
}
```

Add a manifest main class so you can run the stub JAR. In `regulation-service-stub/pom.xml` add inside `<project>`:
```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.payroll.regulation.stub.StubRegulationServer</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

Run from parent: `mvn clean install`. You can start the stub later with:
`java -jar regulation-service-stub/target/regulation-service-stub-1.0.0-SNAPSHOT.jar [port]`
Default port is 8080; the engine will call `http://localhost:8080/evaluate/wage-type`.

---

## Part 4: payroll-regulations-poc (regulation repo)

Use the existing **payroll-regulations-poc** repo in your workspace. Add or update the following structure and files.

### Step 4.1 Parent POM and poc-regulation module

**File: `payroll-regulations-poc/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.payroll</groupId>
    <artifactId>payroll-regulations-poc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Payroll Regulations POC</name>
    <modules>
        <module>poc-regulation</module>
    </modules>
    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
```

- Use the same Java version as in the engine (e.g. `25`, or `21`/`17`) for `maven.compiler.source`/`target`.

```bash
mkdir -p poc-regulation/src/main/java/com/payroll/regulation/poc
```

**File: `poc-regulation/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payroll</groupId>
        <artifactId>payroll-regulations-poc</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>poc-regulation</artifactId>
    <packaging>jar</packaging>
    <version>1.0.0</version>
    <name>POC Regulation</name>

    <dependencies>
        <dependency>
            <groupId>com.payroll</groupId>
            <artifactId>regulation-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- **Important:** `<scope>provided</scope>` means “use this dependency to compile, but do not bundle it in the JAR”. The engine will supply the API at runtime when it loads this JAR.

The regulation JAR is produced by the **poc-regulation** Maven module and contains **FranceRegulationEvaluator** (see Part 4.3 and Part 4.4). No separate “POC” evaluator is used; the only regulation is **france-regulation**.

### Step 4.2 WageTypes.json (single source of truth)

All **wage type numbers**, their **execution order**, and **collector mapping** for the France regulation live in a single JSON file in the regulation JAR. The engine does not hardcode wage type numbers; it asks the regulation for the list via **RegulationEvaluator.getWageTypeNumbers()**.

**File: `poc-regulation/src/main/resources/WageTypes.json`**

- **Location:** In the regulation module's `src/main/resources/` so it is packaged in the JAR and loaded at runtime by **FranceRegulationEvaluator**.
- **Format:** A JSON object with a **wageTypes** array. Each entry has:
  - **wageTypeNumber** (integer) — unique ID for this wage type.
  - **methodName** (string) — name of the **FranceRules** static method to call (e.g. `grossSalary`, `socialSecurityCeiling`).
  - **collectors** (array of strings, optional) — collector names to add the result to (e.g. `total_gross_pay`, `total_employee_contributions`).
- **Order:** The array order is the **execution order** for the payrun. The evaluator returns this order from **getWageTypeNumbers()** so the engine runs wage types in the correct sequence.
- **Consistency:** Keep this file as the only place that defines wage type numbers and mapping for the France regulation; **FranceRegulationEvaluator** loads it once and uses it for dispatch and collector updates.

**regulation-api:** Add to **RegulationEvaluator** a default method **getWageTypeNumbers()** returning **List&lt;Integer&gt;** (default empty list). The France evaluator overrides it and returns the list read from **WageTypes.json** in array order.

**Build the regulation JAR:**
```bash
cd payroll-regulations-poc
mvn clean package
```

If you see `BUILD SUCCESS`, the JAR is at `poc-regulation/target/poc-regulation-1.0.0.jar`.

**Copy it into the engine’s plugins folder:**
```bash
cp poc-regulation/target/poc-regulation-1.0.0.jar ../payroll-engine-poc/engine/plugins/
```

### Step 4.3 France rules

The **payroll-regulations-poc** repo contains the France payroll regulation (converted from C# `payroll-engine-regulation-France/DSLOutput/FR/Rules/Rules.cs`):

- **`poc-regulation/src/main/java/com/payroll/regulation/poc/FranceRulesContext.java`** — Interface for case values, lookups (slabs), and collectors.
- **`poc-regulation/src/main/java/com/payroll/regulation/poc/FranceRules.java`** — Static methods, one per wage type (e.g. `grossSalary`, `socialSecurityCeiling`, `csgCrdsContributions`, withholding tax, net salary).
- **`FranceRegulationEvaluator`** implements **RegulationEvaluator**, loads **WageTypes.json** (Step 4.2), implements **getWageTypeNumbers()** from it, and delegates to **FranceRules** using the mapping in the JSON. The engine obtains the wage type list from the evaluator and runs them in that order (Part 4.4). A summary checklist is in the [Appendix](#appendix-requirements-to-run-a-rules-file-france).

---

## Part 4.4: Running the France rules — required development

To run **FranceRules.java** end-to-end, add the following. All steps are required unless noted.

### Step 4.4.1 Implement FranceRulesContext and input mapping

**Where:** payroll-engine-poc (engine) or a shared adapter.

- Implement **FranceRulesContext** with:
  - **State:** A `Map<String, Object>` (or separate maps for numeric/string/boolean) for values written by `setValue` / `setValueString` / `setValueBoolean`, so later wage types can read them (e.g. `employee_gross_salary`, `salary_within_ceiling`).
  - **Input resolution:** `getFieldValue(name)` / `getFieldValueString(name)` / `getFieldValueBoolean(name)` must resolve employee and company fields from your stub or payload. Map your case names to the names the rules expect, e.g. `"Basic Monthly Salary"` or `"BaseSalary"` → `employee.base_monthly_salary`, `"Gross salary"` → `employee.gross_salary`, `employee.transport_allowance`, `employee.meal_allowance`, `employee.other_allowance`, `employee.signing_bonus`, `employee.monthly_ss_ceiling`, `employee.contract_type`, `employee.job_title`, `employee.is_alsace_moselle`, `employee.address_province`, `employee.work_percentage`, `employee.individualized_tax_rate`, `company.headcount`.
- Implement **hasField(name)** so the rules can detect when an optional field (e.g. individualized tax rate) is present.

### Step 4.4.2 Add lookups and slabs

**Where:** same place as the context implementation (e.g. engine or a lookup module).

- **Constants2025:** Implement **getSlab("Constants2025", key)** by loading **`poc-regulation/src/main/resources/Constants2025.json`**. Keys and numeric values must match the France Rules lookup **`payroll-engine-regulation-France/DSLOutput/FR/Rules/Lookup.Constants2025.json`** (e.g. `monthly_ss_ceiling`, `annual_ss_ceiling`, `hourly_smic_2025`, `monthly_smic_2025`, `annual_smic_2025`, `short_term_allowance_2025`, `meal_benefit_rate_2025`, `minimum_deduction_2025`, `maximum_deduction_2025`, `enhanced_minimum_deduction`). The POC uses a flat JSON (key → value); the context resolves `employee.monthly_ss_ceiling` from the constant **`monthly_ss_ceiling`**.
- **Range slabs:**  
  - **getRangeSlab("WithholdingTaxMetropolitan2025JanApr", value)** — return the applicable rate for the given monthly base (bracket table).  
  - **applyRangeSlab("WithholdingTaxGuadeloupe", value)** and **applyRangeSlab("WithholdingTaxGuiana", value)** — return the withholding amount for the given base (bracket table).  
Use the official 2025 withholding tables for metropolitan France and overseas.

### Step 4.4.3 Implement collectors and wage type order

**Where:** payroll-engine-poc (engine payrun loop).

- Maintain **collector** sums: when each wage type returns a value, add it to the appropriate collector (e.g. employee contributions → `total_employee_contributions`, deductions → `total_deductions`, gross → `total_gross_pay`, employer contributions → `total_employer_contributions`, and similarly for pension and URSSAF totals). The context’s **getCollector(name)** must return the current sum for each name.
- Run France wage types in the **order** required by the rules (e.g. gross salary and SS ceiling first, then contributions, then CSG/CRDS, then withholding, then net salary). The exact order is defined by the dependencies between `FranceRules` methods.

### Step 4.4.4 Add FranceRegulationEvaluator and wage type mapping

**Where:** payroll-regulations-poc (poc-regulation module).

- **WageTypes.json** (Step 4.2) is the single source of truth: it defines wage type numbers, **methodName** (→ FranceRules method), and **collectors** in execution order. **FranceRegulationEvaluator** loads this file from the JAR classpath and uses it for dispatch and collector updates.
- Create **FranceRegulationEvaluator** implementing **RegulationEvaluator**: override **getWageTypeNumbers()** to return the wage type list from **WageTypes.json** (array order); hold or create a **FranceRulesContext**; in **evaluateWageType(int wageTypeId, EvaluationContext context)** look up the entry by wageTypeId, invoke the corresponding **FranceRules.***methodName*(ctx), and add the result to the entry’s **collectors**.
- Register the France regulation (id + version) and its JAR in the engine. The engine calls **evaluator.getWageTypeNumbers()** to get the list and runs the payrun in that order (see Step 4.4.3).

When all four steps are done, you can run a payrun for the France regulation with stub data that supplies the required employee and company fields; the engine will evaluate wage types in order, update collectors, and produce net salary and contribution totals.

---

## Part 5: Implement the engine (loader, registry, payrun)

Back in **payroll-engine-poc**, add the following classes in `engine/src/main/java/com/payroll/engine/`.

### Step 5.1 StubEvaluationContext

**File: `engine/src/main/java/com/payroll/engine/StubEvaluationContext.java`**
```java
package com.payroll.engine;

import com.payroll.regulation.api.EvaluationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public class StubEvaluationContext implements EvaluationContext {
    private final String tenantId;
    private final String employeeId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final Map<String, BigDecimal> caseValues;

    public StubEvaluationContext(String tenantId, String employeeId,
                                  LocalDate periodStart, LocalDate periodEnd,
                                  Map<String, BigDecimal> caseValues) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.caseValues = caseValues != null ? Map.copyOf(caseValues) : Map.of();
    }

    @Override public String getTenantId() { return tenantId; }
    @Override public String getEmployeeId() { return employeeId; }
    @Override public LocalDate getPeriodStart() { return periodStart; }
    @Override public LocalDate getPeriodEnd() { return periodEnd; }
    @Override public BigDecimal getCaseValue(String name) { return caseValues.get(name); }
}
```

### Step 5.2 RegulationRegistry

The registry maps (regulationId, version) to JAR path and evaluator class name (precompiled JAR only).

**File: `engine/src/main/java/com/payroll/engine/RegulationRegistry.java`**
```java
package com.payroll.engine;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RegulationRegistry {
    private final Map<String, String> jarPathByKey = new HashMap<>();
    private final Map<String, String> classNameByKey = new HashMap<>();
    private final Path pluginsBase;

    public RegulationRegistry(Path pluginsBase) {
        this.pluginsBase = pluginsBase;
    }

    public void register(String regulationId, String version, String jarFileName, String evaluatorClassName) {
        String k = key(regulationId, version);
        jarPathByKey.put(k, pluginsBase.resolve(jarFileName).toString());
        classNameByKey.put(k, evaluatorClassName);
    }

    public Optional<String> getJarPath(String regulationId, String version) {
        return Optional.ofNullable(jarPathByKey.get(key(regulationId, version)));
    }

    public Optional<String> getEvaluatorClassName(String regulationId, String version) {
        return Optional.ofNullable(classNameByKey.get(key(regulationId, version)));
    }

    private static String key(String id, String version) {
        return id + ":" + version;
    }
}
```

### Step 5.2a Regulation configuration (regulations.json)

Regulation **id**, **version**, **JAR file name**, and **evaluator class** are not hardcoded in Main. They are read from a config file so you can change or add regulations without recompiling.

**File: `engine/src/main/resources/regulations.json`**

- **Location:** In the engine module’s `src/main/resources/` so it is on the classpath and packaged in the engine JAR.
- **Format:** A JSON object with a **regulations** array. Each entry has:
  - **id** (string) — regulation identifier (e.g. `france-regulation`).
  - **version** (string) — version (e.g. `1.0.0`).
  - **jar** (string) — JAR file name under the plugins directory (e.g. `poc-regulation-1.0.0.jar`).
  - **evaluatorClass** (string) — fully qualified class name of the `RegulationEvaluator` implementation (e.g. `com.payroll.regulation.poc.FranceRegulationEvaluator`).

**Example: `engine/src/main/resources/regulations.json`**
```json
{
  "regulations": [
    {
      "id": "france-regulation",
      "version": "1.0.0",
      "jar": "poc-regulation-1.0.0.jar",
      "evaluatorClass": "com.payroll.regulation.poc.FranceRegulationEvaluator"
    }
  ]
}
```

Main loads this file at startup, registers each entry with the **RegulationRegistry**, then uses the **first** regulation in the list for the payrun (getEvaluator, wage type list, and run). To switch regulations, reorder or edit the file and ensure the corresponding JAR is in `plugins/`.

### Step 5.3 (Out of scope — skip) RegulationServiceClient

**Skip this step** — external regulation service is out of scope. The client would call the external regulation service over HTTP (POST /evaluate/wage-type). It uses the shared request/response DTOs and Jackson for JSON.

**File: `engine/src/main/java/com/payroll/engine/RegulationServiceClient.java`**
```java
package com.payroll.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payroll.regulation.api.WageTypeResult;
import com.payroll.regulation.service.api.EvaluateWageTypeRequest;
import com.payroll.regulation.service.api.EvaluateWageTypeResponse;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class RegulationServiceClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public RegulationServiceClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    public WageTypeResult evaluateWageType(String tenantId, String regulationId, String employeeId,
                                           java.time.LocalDate periodStart, java.time.LocalDate periodEnd,
                                           int wageTypeNumber, Map<String, BigDecimal> caseValues) throws Exception {
        EvaluateWageTypeRequest req = new EvaluateWageTypeRequest(
                tenantId, regulationId, employeeId, periodStart, periodEnd, wageTypeNumber,
                caseValues != null ? Map.copyOf(caseValues) : Map.of());
        byte[] body = mapper.writeValueAsBytes(req);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "evaluate/wage-type"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Regulation service returned " + response.statusCode() + ": " + response.body());
        }
        EvaluateWageTypeResponse resp = mapper.readValue(response.body(), EvaluateWageTypeResponse.class);
        return new WageTypeResult(wageTypeNumber, resp.value() != null ? resp.value() : BigDecimal.ZERO);
    }
}
```

### Step 5.4 RegulationEvaluatorLoader (JAR path)

**File: `engine/src/main/java/com/payroll/engine/RegulationEvaluatorLoader.java`**
```java
package com.payroll.engine;

import com.payroll.regulation.api.RegulationEvaluator;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RegulationEvaluatorLoader {
    private final RegulationRegistry registry;
    private final ConcurrentHashMap<String, RegulationEvaluator> cache = new ConcurrentHashMap<>();

    public RegulationEvaluatorLoader(RegulationRegistry registry) {
        this.registry = registry;
    }

    public RegulationEvaluator getEvaluator(String regulationId, String version) throws Exception {
        String key = regulationId + ":" + version;
        return cache.computeIfAbsent(key, k -> load(regulationId, version));
    }

    private RegulationEvaluator load(String regulationId, String version) {
        try {
            String path = registry.getJarPath(regulationId, version)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown regulation: " + regulationId + " " + version));
            String className = registry.getEvaluatorClassName(regulationId, version)
                    .orElseThrow(() -> new IllegalStateException("No evaluator class for " + regulationId + " " + version));

            File jar = new File(path);
            if (!jar.exists()) throw new IllegalStateException("JAR not found: " + path);

            URL[] urls = { jar.toURI().toURL() };
            try (URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader())) {
                Class<?> clazz = loader.loadClass(className);
                return (RegulationEvaluator) clazz.getConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load evaluator " + regulationId + " " + version, e);
        }
    }
}
```

### Step 5.5 MinimalPayrun (JAR path only)

The payrun uses the loader to get the evaluator and evaluates each wage type in process.

**File: `engine/src/main/java/com/payroll/engine/MinimalPayrun.java`**
```java
package com.payroll.engine;

import com.payroll.regulation.api.EvaluationContext;
import com.payroll.regulation.api.RegulationEvaluator;
import com.payroll.regulation.api.WageTypeResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MinimalPayrun {
    private final RegulationEvaluatorLoader loader;

    public MinimalPayrun(RegulationEvaluatorLoader loader) {
        this.loader = loader;
    }

    public List<WageTypeResult> run(String regulationId, String version,
                                    List<Integer> wageTypeNumbers,
                                    EvaluationContext context) throws Exception {
        RegulationEvaluator evaluator = loader.getEvaluator(regulationId, version);
        List<WageTypeResult> results = new ArrayList<>();
        for (int num : wageTypeNumbers) {
            BigDecimal value = evaluator.evaluateWageType(num, context);
            results.add(new WageTypeResult(num, value != null ? value : BigDecimal.ZERO));
        }
        return results;
    }
}
```

### Step 5.5a Stub data (JSON files)

Stub input data is kept in **separate JSON files**, one per employee. The engine reads the list of stub files from **`engine/src/main/resources/stub-data/index.txt`** (one path per line, e.g. `stub-data/PR15d3b0.json`). It runs the payrun for each listed file.

**Directory:** `engine/src/main/resources/stub-data/`

**File: `engine/src/main/resources/stub-data/index.txt`** — list the stub JSON paths, one per line. Example:
```
stub-data/PR15d3b0.json
stub-data/PR2b557c.json
```

Each stub JSON file has the same shape: `tenantId`, `employeeId`, `periodStart`, `periodEnd`, `caseValues` (map of case name → string value). Example **`stub-data/PR15d3b0.json`**:
```json
{
  "tenantId": "tenant-1",
  "employeeId": "PR15d3b0",
  "periodStart": "2025-01-01",
  "periodEnd": "2025-01-31",
  "caseValues": {
    "Basic Monthly Salary": "6641.25",
    "Gross salary": "6641.25",
    "BaseSalary": "6641.25"
  }
}
```

If `index.txt` is missing or empty, the engine runs no stubs (empty list). The engine runs the payrun for each listed employee and prints results per employee.

### Step 5.6 Main class to run the payrun (JAR path only)

Main **loads regulations from `regulations.json`** (Step 5.2a), registers each with the registry, then uses the **first** regulation (id + version) for the payrun. It **loads the stub file list from `stub-data/index.txt`**, runs the payrun for each listed stub file, and prints results per employee. No regulation id, version, or JAR name is hardcoded in code.

**File: `engine/src/main/java/com/payroll/engine/StubDataRecord.java`** (for JSON binding)
```java
package com.payroll.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class StubDataRecord {
    private final String tenantId;
    private final String employeeId;
    private final String periodStart;
    private final String periodEnd;
    private final Map<String, String> caseValues;

    @JsonCreator
    public StubDataRecord(@JsonProperty("tenantId") String tenantId,
                          @JsonProperty("employeeId") String employeeId,
                          @JsonProperty("periodStart") String periodStart,
                          @JsonProperty("periodEnd") String periodEnd,
                          @JsonProperty("caseValues") Map<String, String> caseValues) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.caseValues = caseValues != null ? Map.copyOf(caseValues) : Map.of();
    }

    public String getTenantId() { return tenantId; }
    public String getEmployeeId() { return employeeId; }
    public String getPeriodStart() { return periodStart; }
    public String getPeriodEnd() { return periodEnd; }
    public Map<String, String> getCaseValues() { return caseValues; }
}
```

**Records for loading regulations.json** (add to the engine module; Jackson uses getters, so Java records work with `JsonProperty` if needed for the array name):

**File: `engine/src/main/java/com/payroll/engine/RegulationEntry.java`**
```java
package com.payroll.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RegulationEntry(
    @JsonProperty("id") String id,
    @JsonProperty("version") String version,
    @JsonProperty("jar") String jar,
    @JsonProperty("evaluatorClass") String evaluatorClass
) {
    @JsonCreator
    public RegulationEntry {}
}
```

**File: `engine/src/main/java/com/payroll/engine/RegulationsConfig.java`**
```java
package com.payroll.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public record RegulationsConfig(
    @JsonProperty("regulations") List<RegulationEntry> regulations
) {
    @JsonCreator
    public RegulationsConfig {
        regulations = regulations != null ? List.copyOf(regulations) : Collections.emptyList();
    }
}
```

**File: `engine/src/main/java/com/payroll/engine/Main.java`**
```java
package com.payroll.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payroll.regulation.api.WageTypeResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        var pluginsDir = Paths.get("plugins").toAbsolutePath();
        if (!pluginsDir.toFile().exists()) {
            pluginsDir = Paths.get("engine/plugins").toAbsolutePath();
        }

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RegulationsConfig config = loadRegulationsConfig(mapper);
        if (config.regulations().isEmpty()) {
            throw new IllegalStateException("regulations.json has no regulations");
        }

        var registry = new RegulationRegistry(pluginsDir);
        for (RegulationEntry e : config.regulations()) {
            registry.register(e.id(), e.version(), e.jar(), e.evaluatorClass());
        }

        RegulationEntry first = config.regulations().get(0);
        var loader = new RegulationEvaluatorLoader(registry);
        var payrun = new MinimalPayrun(loader);
        var evaluator = loader.getEvaluator(first.id(), first.version());
        List<Integer> wageTypeNumbers = evaluator.getWageTypeNumbers();

        List<String> stubFiles = loadStubFileList();

        for (String stubPath : stubFiles) {
            StubDataRecord rec = loadStubData(mapper, stubPath);
            var context = new StubEvaluationContext(
                    rec.getTenantId(), rec.getEmployeeId(),
                    LocalDate.parse(rec.getPeriodStart()), LocalDate.parse(rec.getPeriodEnd()),
                    toBigDecimalMap(rec.getCaseValues()),
                    rec.getCaseValues()
            );
            List<WageTypeResult> results = payrun.run(first.id(), first.version(), wageTypeNumbers, context);
            System.out.println("Employee " + rec.getEmployeeId() + ":");
            for (WageTypeResult r : results) {
                if (r.value().compareTo(BigDecimal.ZERO) != 0)
                    System.out.println("  " + r.wageTypeNumber() + " -> " + r.value());
            }
            System.out.println();
        }
    }

    private static RegulationsConfig loadRegulationsConfig(ObjectMapper mapper) throws Exception {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("regulations.json")) {
            if (in == null) throw new IllegalStateException("regulations.json not found on classpath");
            return mapper.readValue(in, RegulationsConfig.class);
        }
    }

    private static List<String> loadStubFileList() {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("stub-data/index.txt")) {
            if (in == null) return List.of();
            List<String> list = new ArrayList<>();
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) list.add(line);
                }
            }
            return list.isEmpty() ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static StubDataRecord loadStubData(ObjectMapper mapper, String resourcePath) throws Exception {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("Stub data not found: " + resourcePath);
            return mapper.readValue(in, StubDataRecord.class);
        }
    }

    private static Map<String, BigDecimal> toBigDecimalMap(Map<String, String> stringMap) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (stringMap != null) {
            stringMap.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    try {
                        out.put(k, new BigDecimal(v.trim()));
                    } catch (NumberFormatException ignored) { }
                }
            });
        }
        return out;
    }
}
```

Add Jackson to the engine so stub JSON can be read. In `engine/pom.xml`, add inside `<dependencies>`:
```xml
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.15.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.15.3</version>
        </dependency>
```

Add the main class to the engine POM so Maven knows how to run it:

**In `engine/pom.xml`, add inside `<project>` (e.g. after `</dependencies>`):**
```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.payroll.engine.Main</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

---

## Part 6: Build and run (full flow)

### 6.1 Build order

1. **Engine repo (install all modules including stub):**
   ```bash
   cd payroll-engine-poc
   mvn clean install
   ```

2. **Regulations repo (build regulation JAR for JAR path):**
   ```bash
   cd payroll-regulations-poc
   mvn clean package
   ```

3. **Copy regulation JAR into engine plugins (for JAR path):**
   ```bash
   cp payroll-regulations-poc/poc-regulation/target/poc-regulation-1.0.0.jar payroll-engine-poc/engine/plugins/
   ```
   (Run from the parent of both repos, or use absolute paths if your layout differs.)

### 6.2 Run the engine (JAR path only)

From the engine module directory, run the engine. The **JAR path** will work as long as the regulation JAR is in `engine/plugins/`. You do **not** need the stub for this.

Add to `engine/pom.xml` inside `<build><plugins>` (if not already present):
```xml
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>com.payroll.engine.Main</mainClass>
                </configuration>
            </plugin>
```

Run:
```bash
cd payroll-engine-poc/engine
mvn exec:java
```
Or run the JAR directly (working directory = `engine/`):
```bash
cd payroll-engine-poc/engine
java -jar target/engine-1.0.0-SNAPSHOT.jar
```

**Expected output:** (per employee, France wage types with non-zero values only; exact numbers depend on stub data and `stub-data/index.txt`)
```
Employee PR15d3b0:
  2001 -> 6641.25
  2002 -> 3031.00
  ...
  2049 -> 2313.93
  2054 -> 6641.25
  ...

Employee PR2b557c:
  2001 -> ...
  ...
```

---

## Part 7: Troubleshooting

| Problem | What to check |
|--------|----------------|
| `regulation-api` not found | Run `mvn install` in **payroll-engine-poc** first so regulation-api is in `~/.m2/repository`. |
| `JAR not found: ... plugins/poc-regulation-1.0.0.jar` | Copy the regulation JAR into `payroll-engine-poc/engine/plugins/`. Run the engine from `engine/` so `plugins/` resolves correctly. |
| `ClassNotFoundException: com.payroll.regulation.poc.FranceRegulationEvaluator` | JAR path: ensure the JAR is in `engine/plugins/` and the registry uses `register("france-regulation", "1.0.0", "poc-regulation-1.0.0.jar", "com.payroll.regulation.poc.FranceRegulationEvaluator")`. |
| `NoSuchMethodException: <init>` | The regulation class must have a **no-argument** public constructor. |
| Java version mismatch | Use the same version (17, 21, 25) in all projects and for `java`/`javac`/`mvn`. |

---

## Part 8: Optional — add a unit test in the engine

To run the payrun from a test (and ensure `plugins/` is found), add JUnit to the engine and a test that builds registry (with path to `engine/plugins/`), loader, and MinimalPayrun(loader), then asserts the results. This keeps the “run from IDE” path consistent by using the project layout (e.g. `Paths.get("src/test/resources/plugins")` or a path relative to the project root).

Add to `engine/pom.xml` in `<dependencies>`:
```xml
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
```

Create `engine/src/test/java/com/payroll/engine/MinimalPayrunTest.java` that builds a registry (with `register("france-regulation", "1.0.0", "poc-regulation-1.0.0.jar", "com.payroll.regulation.poc.FranceRegulationEvaluator")`), loader, and `MinimalPayrun(loader)`; use a path to `engine/plugins`, then call `payrun.run("france-regulation", "1.0.0", List.of(2001, 2002, 2003, ...), context)` and assert result size and one or two values. Run with `mvn test`.

---

## Quick reference: directory layout

**payroll-engine-poc**
- `pom.xml` (parent; modules: regulation-api, engine)
- `regulation-api/` — JAR contract (RegulationEvaluator, EvaluationContext, WageTypeResult)
- `engine/` — Registry (JAR), loader, MinimalPayrun, Main; `engine/plugins/` for regulation JAR

**payroll-regulations-poc**
- `pom.xml` (parent) + `poc-regulation/` — France regulation JAR (FranceRegulationEvaluator)

**Build & run**
1. `cd payroll-engine-poc` → `mvn clean install`
2. `cd payroll-regulations-poc` → `mvn clean package`
3. `cp payroll-regulations-poc/poc-regulation/target/poc-regulation-1.0.0.jar payroll-engine-poc/engine/plugins/`
4. `cd payroll-engine-poc/engine` → `java -jar target/engine-1.0.0-SNAPSHOT.jar`

You now have the full local development flow for the **precompiled JAR** POC, as defined in [poc_precompiled_jar_scope.md](poc_precompiled_jar_scope.md). External regulation service is out of scope.

---

## Appendix: Requirements to run a rules file (France)

The following list describes what is in place for **FranceRules.java** to run in this POC (france-regulation only). All items are implemented.

1. **Context implementation**  
   The engine (or adapter) must provide an implementation of **FranceRulesContext** that:
   - Persists values written via `setValue` / `setValueString` / `setValueBoolean` so later wage types can read them (e.g. `employee_gross_salary`, `salary_within_ceiling`).
   - Reads input case names from employee/company data (see below).

2. **Wage type → rule method mapping**  
   A mapping from wage type number to the corresponding static method in **FranceRules** (e.g. gross salary, SS ceiling, health, old-age, CSG/CRDS, withholding, net salary). Optionally, a **FranceRegulationEvaluator** implementing **RegulationEvaluator** that holds a **FranceRulesContext** and dispatches `evaluateWageType(id, context)` to the correct `FranceRules.*(context)` method.

3. **Lookups / slabs**  
   The context must resolve:
   - **Constants2025:** e.g. `monthly_smic_2025`, `annual_smic_2025`, `minimum_deduction_2025`, `maximum_deduction_2025`, `short_term_allowance_2025`, `meal_benefit_rate_2025`.
   - **WithholdingTaxMetropolitan2025JanApr** (range slab returning a rate).
   - **WithholdingTaxGuadeloupe**, **WithholdingTaxGuiana** (range slabs returning an amount via `applyRangeSlab`).

4. **Collectors**  
   The engine must run wage types in a defined order and aggregate results into collectors such as: `total_employee_contributions`, `total_deductions`, `total_gross_pay`, `total_employer_contributions`, `total_employee_pension_contributions`, `total_employer_pension_contributions`, `total_employee_urssaf_contributions`, `total_employer_urssaf_contributions`. The context’s `getCollector(name)` must return the current sum for each.

5. **Input case names**  
   Employee and company fields expected by the rules (stub/Excel may use different names; a mapping or convention is required), e.g.:  
   `employee.base_monthly_salary`, `employee.gross_salary`, `employee.transport_allowance`, `employee.meal_allowance`, `employee.other_allowance`, `employee.signing_bonus`, `employee.monthly_ss_ceiling`, `employee.contract_type`, `employee.job_title`, `employee.is_alsace_moselle`, `employee.address_province`, `employee.work_percentage`, `employee.individualized_tax_rate`, `company.headcount`.
