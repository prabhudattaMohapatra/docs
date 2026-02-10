# Local development guide: Precompiled JAR POC (for new Java developers)

This guide walks you through building the **precompiled JAR** POC from scratch on your machine. It assumes you are new to Java and explains each step in detail, with exact commands and file contents you can copy.

**Related:** [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md) — POC scope, objectives, and evaluation.

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

### 1.5 Choose a workspace directory

Create a folder where both projects will live, e.g.:
```bash
mkdir -p ~/payroll-poc
cd ~/payroll-poc
```
All commands below assume you run them from the appropriate project folder (or `~/payroll-poc` when creating folders).

---

## Part 2: Concepts you need

- **Maven:** Build tool. It reads `pom.xml` (Project Object Model) in each project and module. Running `mvn compile` compiles Java files; `mvn package` builds a JAR; `mvn install` builds and **installs** the JAR into your local Maven repository (`~/.m2/repository` on macOS/Linux) so other projects can use it as a dependency.
- **Module:** A sub-project inside a parent project. The **parent** has a `pom.xml` with `<modules>`. Each module has its own `pom.xml` and `src/main/java/` (and optionally `src/test/java/`).
- **JAR:** A Java archive (a ZIP file containing compiled `.class` files and optionally a manifest). The engine will **load** a regulation JAR from a `plugins/` folder at runtime using `URLClassLoader`.
- **Two repos:** **payroll-engine-poc** (engine + contract) and **payroll-regulations-poc** (regulation implementation). You build the engine first and run `mvn install` so the contract JAR is in local Maven; then you build the regulation project, which depends on that contract.

---

## Part 3: Create payroll-engine-poc (engine repo)

### Step 3.1 Create the project folder and parent POM

From your workspace (e.g. `~/payroll-poc`):

```bash
mkdir payroll-engine-poc
cd payroll-engine-poc
git init
```

Create the **parent** `pom.xml` in the root of `payroll-engine-poc`:

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
    <description>Engine and regulation API for precompiled JAR POC</description>

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
- `modules` lists the two sub-projects: `regulation-api` and `engine`.

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
cd ~/payroll-poc/payroll-engine-poc
mvn clean install
```

You should see `BUILD SUCCESS`. The JAR `regulation-api-1.0.0-SNAPSHOT.jar` is now in `~/.m2/repository/com/payroll/regulation-api/1.0.0-SNAPSHOT/`. Other projects (like payroll-regulations-poc) can depend on it with `com.payroll:regulation-api:1.0.0-SNAPSHOT`.

---

### Step 3.3 Create the engine module (skeleton)

Create the engine module and the **plugins** directory. We will add the loader, registry, and payrun in the next steps.

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
    <description>Engine that loads regulation JARs from plugins/ and runs payrun</description>

    <dependencies>
        <dependency>
            <groupId>com.payroll</groupId>
            <artifactId>regulation-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</project>
```

Run from the **parent** directory again:
```bash
cd ~/payroll-poc/payroll-engine-poc
mvn clean install
```
Both modules should build. The `engine` JAR will be in `engine/target/engine-1.0.0-SNAPSHOT.jar`. The `plugins/` folder is where you will later copy the regulation JAR.

---

## Part 4: Create payroll-regulations-poc (regulation repo)

Open a **new** terminal (or `cd` out of the engine repo). Create the second project in the same workspace.

```bash
cd ~/payroll-poc
mkdir payroll-regulations-poc
cd payroll-regulations-poc
git init
```

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

### Step 4.2 Implement PocRegulationEvaluator

**File: `poc-regulation/src/main/java/com/payroll/regulation/poc/PocRegulationEvaluator.java`**
```java
package com.payroll.regulation.poc;

import com.payroll.regulation.api.EvaluationContext;
import com.payroll.regulation.api.RegulationEvaluator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PocRegulationEvaluator implements RegulationEvaluator {

    @Override
    public BigDecimal evaluateWageType(int wageTypeNumber, EvaluationContext context) {
        return switch (wageTypeNumber) {
            case 1001 -> new BigDecimal("100.00");
            case 1002 -> baseSalaryTimes(context, "0.20");
            case 1003 -> baseSalaryTimes(context, "0.10");
            case 1004 -> min(new BigDecimal("500"), getCase(context, "BaseSalary"));
            case 1005 -> getCase(context, "BaseSalary");
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal baseSalaryTimes(EvaluationContext context, String factor) {
        BigDecimal base = getCase(context, "BaseSalary");
        if (base == null) return BigDecimal.ZERO;
        return base.multiply(new BigDecimal(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal getCase(EvaluationContext context, String name) {
        return context.getCaseValue(name);
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (b == null) return a;
        return a.min(b);
    }
}
```

**Build the regulation JAR:**
```bash
cd ~/payroll-poc/payroll-regulations-poc
mvn clean package
```

If you see `BUILD SUCCESS`, the JAR is at `poc-regulation/target/poc-regulation-1.0.0.jar`.

**Copy it into the engine’s plugins folder:**
```bash
cp poc-regulation/target/poc-regulation-1.0.0.jar ~/payroll-poc/payroll-engine-poc/engine/plugins/
```

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
        String key = key(regulationId, version);
        jarPathByKey.put(key, pluginsBase.resolve(jarFileName).toString());
        classNameByKey.put(key, evaluatorClassName);
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

### Step 5.3 RegulationEvaluatorLoader

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

### Step 5.4 MinimalPayrun

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

### Step 5.5 Main class to run the payrun

**File: `engine/src/main/java/com/payroll/engine/Main.java`**
```java
package com.payroll.engine;

import com.payroll.regulation.api.WageTypeResult;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        // plugins/ is relative to engine module directory when run from engine/target/ or from IDE
        var pluginsDir = Paths.get("plugins").toAbsolutePath();
        if (!pluginsDir.toFile().exists()) {
            pluginsDir = Paths.get("engine/plugins").toAbsolutePath();
        }

        var registry = new RegulationRegistry(pluginsDir);
        registry.register("poc-regulation", "1.0.0",
                "poc-regulation-1.0.0.jar",
                "com.payroll.regulation.poc.PocRegulationEvaluator");

        var loader = new RegulationEvaluatorLoader(registry);
        var payrun = new MinimalPayrun(loader);

        var context = new StubEvaluationContext(
                "tenant-1", "emp-1",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                Map.of("BaseSalary", new java.math.BigDecimal("3000.00"))
        );

        List<WageTypeResult> results = payrun.run("poc-regulation", "1.0.0",
                List.of(1001, 1002, 1003, 1004, 1005), context);

        System.out.println("Wage type results:");
        for (WageTypeResult r : results) {
            System.out.println("  " + r.wageTypeNumber() + " -> " + r.value());
        }
    }
}
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

1. **Engine repo (install API and engine):**
   ```bash
   cd ~/payroll-poc/payroll-engine-poc
   mvn clean install
   ```

2. **Regulations repo (build JAR):**
   ```bash
   cd ~/payroll-poc/payroll-regulations-poc
   mvn clean package
   ```

3. **Copy regulation JAR into engine plugins:**
   ```bash
   cp ~/payroll-poc/payroll-regulations-poc/poc-regulation/target/poc-regulation-1.0.0.jar \
      ~/payroll-poc/payroll-engine-poc/engine/plugins/
   ```

### 6.2 Run the engine

You must run the engine with **working directory** set to a place where the relative path `plugins/` (or `engine/plugins/`) points to the folder that contains `poc-regulation-1.0.0.jar`.

**Option A — From engine module directory:**
```bash
cd ~/payroll-poc/payroll-engine-poc/engine
mvn exec:java -Dexec.mainClass="com.payroll.engine.Main"
```
To use `exec:java`, add this plugin to `engine/pom.xml` inside `<build><plugins>`:
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
Then run:
```bash
cd ~/payroll-poc/payroll-engine-poc/engine
mvn exec:java
```
By default, Maven runs with working directory = engine module, so `plugins/` is `engine/plugins/` — make sure the JAR is in `engine/plugins/`.

**Option B — Run the JAR directly:**
```bash
cd ~/payroll-poc/payroll-engine-poc/engine
java -jar target/engine-1.0.0-SNAPSHOT.jar
```
Here the working directory is `engine/`, so `plugins/` must be the folder `engine/plugins/` (which we created and where you copied the JAR). So this should work.

**Expected output (something like):**
```
Wage type results:
  1001 -> 100.00
  1002 -> 600.00
  1003 -> 300.00
  1004 -> 500
  1005 -> 3000.00
```

---

## Part 7: Troubleshooting

| Problem | What to check |
|--------|----------------|
| `regulation-api` not found when building payroll-regulations-poc | Run `mvn install` in **payroll-engine-poc** first so regulation-api is in `~/.m2/repository`. |
| `JAR not found: ... plugins/poc-regulation-1.0.0.jar` | Copy the JAR into `payroll-engine-poc/engine/plugins/`. When you run, the current directory must be such that `plugins` or `engine/plugins` exists and contains the JAR (e.g. run from `engine/` and use path `plugins/`). |
| `ClassNotFoundException: com.payroll.regulation.poc.PocRegulationEvaluator` | The JAR might not be in the right place, or the path in the registry is wrong. Ensure registry uses the same path as where you copied the JAR (e.g. `pluginsBase.resolve("poc-regulation-1.0.0.jar")`). |
| `NoSuchMethodException: <init>` | The regulation class must have a **no-argument** public constructor. |
| Java version mismatch | Use the same version (17, 21, 25, or whatever you chose) in both projects and for `java`/`javac`/`mvn`. |

---

## Part 8: Optional — add a unit test in the engine

To run the payrun from a test (and ensure `plugins/` is found), add JUnit to the engine and a test that builds registry (with path to `engine/plugins/`), loader, and MinimalPayrun, then asserts the results. This keeps the “run from IDE” path consistent by using the project layout (e.g. `Paths.get("src/test/resources/plugins")` or a path relative to the project root).

Add to `engine/pom.xml` in `<dependencies>`:
```xml
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
```

Create `engine/src/test/java/com/payroll/engine/MinimalPayrunTest.java` that uses a path to `engine/plugins` (e.g. from `System.getProperty("user.dir")` or a known relative path) and calls `payrun.run(...)` then asserts `results.size() == 5` and one or two values. Run with `mvn test`.

---

## Quick reference: directory layout

**payroll-engine-poc**
- `pom.xml` (parent)
- `regulation-api/pom.xml` + `regulation-api/src/main/java/com/payroll/regulation/api/*.java`
- `engine/pom.xml` + `engine/plugins/` (put regulation JAR here) + `engine/src/main/java/com/payroll/engine/*.java`

**payroll-regulations-poc**
- `pom.xml` (parent)
- `poc-regulation/pom.xml` + `poc-regulation/src/main/java/com/payroll/regulation/poc/PocRegulationEvaluator.java`

**Build & run**
1. `cd payroll-engine-poc` → `mvn clean install`
2. `cd payroll-regulations-poc` → `mvn clean package`
3. `cp payroll-regulations-poc/poc-regulation/target/poc-regulation-1.0.0.jar payroll-engine-poc/engine/plugins/`
4. `cd payroll-engine-poc/engine` → `java -jar target/engine-1.0.0-SNAPSHOT.jar`

You now have the full local development flow for the precompiled JAR POC.
