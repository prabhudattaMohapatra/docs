# Implementing Rules.cs Import and Execution in Java

Complete guide on how to implement the Rules.cs import and function execution mechanism in Java, including runtime compilation, class loading, and function invocation.

---

## Overview

**Challenge**: Java doesn't have:
- ❌ Partial classes (C# feature)
- ❌ Roslyn (C# compiler)
- ❌ `CollectibleAssemblyLoadContext` (easy assembly unloading)

**Solution**: Use Java alternatives:
- ✅ **Janino** or **Java Compiler API** for runtime compilation
- ✅ **Class composition** or **code generation** instead of partial classes
- ✅ **Custom ClassLoader** with unloading support

---

## Architecture Comparison

### C# Approach (Current)
```
Rules.cs → Script Table → Compilation (Roslyn) → Assembly Binary → Load & Execute
```

### Java Approach (Proposed)
```
Rules.java → Script Table → Compilation (Janino/Compiler API) → Class Bytecode → Load & Execute
```

---

## 1. Runtime Code Compilation in Java

### Option 1: Janino (Recommended)

**Janino** is a lightweight Java compiler that can compile Java source code at runtime.

#### Maven Dependency
```xml
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>commons-compiler</artifactId>
    <version>3.1.12</version>
</dependency>
<dependency>
    <groupId>org.codehaus.janino</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.12</version>
</dependency>
```

#### Compilation Example
```java
import org.codehaus.janino.Compiler;
import org.codehaus.janino.SimpleCompiler;

public class JavaScriptCompiler {
    
    public byte[] compileScript(String className, String sourceCode) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(sourceCode);
            
            // Get compiled class bytes
            Class<?> compiledClass = compiler.getClassLoader()
                .loadClass(className);
            
            // Convert to bytecode (for storage)
            // Note: Janino doesn't directly provide bytecode,
            // you may need to use Java Compiler API for this
            
            return getClassBytes(compiledClass);
        } catch (Exception e) {
            throw new ScriptCompileException("Compilation failed", e);
        }
    }
}
```

**Pros**:
- ✅ Lightweight and fast
- ✅ Good error messages
- ✅ Easy to use

**Cons**:
- ⚠️ Doesn't directly provide bytecode (for database storage)
- ⚠️ Limited Java version support

---

### Option 2: Java Compiler API (JDK Built-in)

**Java Compiler API** is part of the JDK and can compile Java source at runtime.

#### Compilation Example
```java
import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class JavaScriptCompiler {
    
    public byte[] compileScript(String className, String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java compiler not available");
        }
        
        // Create in-memory file manager
        StandardJavaFileManager fileManager = 
            compiler.getStandardFileManager(null, null, null);
        
        // Create source file
        JavaFileObject sourceFile = new JavaSourceFromString(className, sourceCode);
        Iterable<? extends JavaFileObject> compilationUnits = 
            Collections.singletonList(sourceFile);
        
        // Compile
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, null, null, null, compilationUnits);
        
        boolean success = task.call();
        if (!success) {
            throw new ScriptCompileException("Compilation failed");
        }
        
        // Load compiled class and get bytecode
        // Note: This requires custom classloader to capture bytecode
        return getClassBytes(className);
    }
    
    // Custom JavaFileObject for in-memory source
    static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;
        
        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + 
                  Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
```

**Pros**:
- ✅ Built into JDK (no external dependencies)
- ✅ Full Java language support
- ✅ Can generate bytecode

**Cons**:
- ⚠️ Requires JDK (not just JRE)
- ⚠️ More complex setup
- ⚠️ Slower than Janino

---

### Option 3: Groovy (Alternative Approach)

**Groovy** is a dynamic language for the JVM that can compile to bytecode.

#### Maven Dependency
```xml
<dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.15</version>
</dependency>
```

#### Compilation Example
```java
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;

public class GroovyScriptCompiler {
    
    public byte[] compileScript(String className, String sourceCode) {
        GroovyClassLoader classLoader = new GroovyClassLoader();
        
        GroovyCodeSource codeSource = new GroovyCodeSource(
            sourceCode, className, "/groovy/script");
        
        Class<?> compiledClass = classLoader.parseClass(codeSource);
        
        return getClassBytes(compiledClass);
    }
}
```

**Pros**:
- ✅ Very flexible (dynamic language)
- ✅ Easy to use
- ✅ Good for scripting

**Cons**:
- ⚠️ Different syntax from Java
- ⚠️ Performance overhead
- ⚠️ May not match existing Java codebase

---

## 2. Handling "Partial Classes" in Java

### Problem: Java Doesn't Have Partial Classes

**C# Approach**:
```csharp
// Rules.cs
public partial class WageTypeValueFunction {
    public Decimal gross_salary() { ... }
}

// Compiled with expression
public partial class WageTypeValueFunction {
    public object GetValue() {
        return gross_salary();  // Can call gross_salary()
    }
}
```

**Java Solution**: Use **Class Composition** or **Code Generation**

---

### Solution 1: Code Generation (Recommended)

Generate a complete class that includes both the expression and functions.

#### Generated Java Code Structure
```java
// Generated from WageType + Rules
package com.payroll.scripting.function;

import com.payroll.scripting.runtime.*;

public class WageTypeValueFunction_1000 extends WageTypeFunctionBase {
    
    private IWageTypeValueRuntime runtime;
    
    public WageTypeValueFunction_1000(IWageTypeValueRuntime runtime) {
        this.runtime = runtime;
    }
    
    // Expression embedded here
    public Object getValue() {
        return gross_salary();  // <-- Your ValueExpression
    }
    
    // Functions from Rules.java
    public Decimal grossSalary() {
        runtime.setValue("employee.contract_type", "permanent");
        if (runtime.getFieldValue("employee.base_monthly_salary") > 0) {
            runtime.setValue("employee_gross_salary", 
                runtime.getFieldValue("employee.base_monthly_salary") + 
                runtime.getFieldValue("employee.transport_allowance"));
        }
        return runtime.getFieldValue("employee_gross_salary");
    }
    
    public Decimal socialSecurityCeiling() {
        // Implementation from Rules.java
        return runtime.getFieldValue("monthly_ss_ceiling");
    }
}
```

#### Code Generation Process
```java
public class ScriptCodeGenerator {
    
    public String generateWageTypeClass(
            String className,
            String valueExpression,
            List<Script> rulesScripts) {
        
        StringBuilder code = new StringBuilder();
        
        // Package and imports
        code.append("package com.payroll.scripting.function;\n");
        code.append("import com.payroll.scripting.runtime.*;\n\n");
        
        // Class declaration
        code.append("public class ").append(className)
            .append(" extends WageTypeFunctionBase {\n");
        code.append("    private IWageTypeValueRuntime runtime;\n\n");
        
        // Constructor
        code.append("    public ").append(className)
            .append("(IWageTypeValueRuntime runtime) {\n");
        code.append("        this.runtime = runtime;\n");
        code.append("    }\n\n");
        
        // GetValue method with expression
        code.append("    public Object getValue() {\n");
        code.append("        return ").append(valueExpression).append(";\n");
        code.append("    }\n\n");
        
        // Functions from Rules.java
        for (Script script : rulesScripts) {
            code.append(extractFunctions(script.getValue()));
        }
        
        code.append("}\n");
        
        return code.toString();
    }
    
    private String extractFunctions(String rulesCode) {
        // Parse Rules.java and extract function definitions
        // This would need a Java parser (e.g., JavaParser library)
        return rulesCode;  // Simplified
    }
}
```

---

### Solution 2: Interface-Based Composition

Use interfaces and composition instead of partial classes.

#### Base Interface
```java
public interface WageTypeValueFunction {
    Object getValue();
    Decimal grossSalary();
    Decimal socialSecurityCeiling();
    // ... other functions
}
```

#### Generated Implementation
```java
public class WageTypeValueFunctionImpl implements WageTypeValueFunction {
    
    private IWageTypeValueRuntime runtime;
    private RulesFunctions rulesFunctions;
    
    public WageTypeValueFunctionImpl(IWageTypeValueRuntime runtime) {
        this.runtime = runtime;
        this.rulesFunctions = new RulesFunctions(runtime);
    }
    
    @Override
    public Object getValue() {
        return rulesFunctions.grossSalary();  // Call function
    }
    
    @Override
    public Decimal grossSalary() {
        return rulesFunctions.grossSalary();
    }
    
    // Delegate to RulesFunctions
    @Override
    public Decimal socialSecurityCeiling() {
        return rulesFunctions.socialSecurityCeiling();
    }
}
```

#### Rules Functions Class
```java
// Compiled from Rules.java
public class RulesFunctions {
    private IWageTypeValueRuntime runtime;
    
    public RulesFunctions(IWageTypeValueRuntime runtime) {
        this.runtime = runtime;
    }
    
    public Decimal grossSalary() {
        runtime.setValue("employee.contract_type", "permanent");
        // ... implementation
        return runtime.getFieldValue("employee_gross_salary");
    }
    
    public Decimal socialSecurityCeiling() {
        // ... implementation
        return runtime.getFieldValue("monthly_ss_ceiling");
    }
}
```

**Pros**:
- ✅ Cleaner separation
- ✅ Easier to test
- ✅ Functions can be reused

**Cons**:
- ⚠️ More complex structure
- ⚠️ Extra method calls (performance)

---

## 3. Complete Java Implementation

### Step 1: Script Storage (Same as C#)

**Database Schema**:
```sql
CREATE TABLE Script (
    id BIGINT PRIMARY KEY,
    regulation_id BIGINT,
    name VARCHAR(128),
    function_type_mask BIGINT,
    value CLOB,  -- Java source code (Rules.java content)
    binary BLOB, -- Compiled bytecode (optional)
    script_hash INT
);
```

**Script Model**:
```java
public class Script {
    private Long id;
    private Long regulationId;
    private String name;
    private Long functionTypeMask;  // Bitmask of FunctionTypes
    private String value;           // Java source code
    private byte[] binary;          // Compiled bytecode
    private Integer scriptHash;
    
    // Getters and setters
}
```

---

### Step 2: Rules.java Generation

**DSL Converter Output** (similar to C#):
```java
// Rules.java (generated from DSL)
package com.payroll.scripting.function;

import com.payroll.scripting.runtime.*;

public class RulesFunctions {
    private IWageTypeValueRuntime runtime;
    
    public RulesFunctions(IWageTypeValueRuntime runtime) {
        this.runtime = runtime;
    }
    
    public Decimal grossSalary() {
        runtime.setValue("employee.contract_type", "permanent");
        runtime.setValue("employee.job_title", "executive");
        if (runtime.getFieldValue("employee.base_monthly_salary") > 0) {
            runtime.setValue("employee_gross_salary", 
                runtime.getFieldValue("employee.base_monthly_salary") + 
                runtime.getFieldValue("employee.transport_allowance"));
        }
        return runtime.getFieldValue("employee_gross_salary");
    }
    
    public Decimal socialSecurityCeiling() {
        if (runtime.getFieldValue("employee_gross_salary") > 0) {
            runtime.setValue("monthly_ss_ceiling", 
                runtime.getFieldValue("employee.monthly_ss_ceiling"));
        }
        return runtime.getFieldValue("monthly_ss_ceiling");
    }
}
```

**Import Process**:
```java
@Service
public class ScriptImportService {
    
    @Autowired
    private ScriptRepository scriptRepository;
    
    public void importScript(Long regulationId, Script script) {
        // Store Rules.java source code
        script.setRegulationId(regulationId);
        script.setFunctionTypeMask(FunctionType.PAYROLL.getMask());
        script.setValue(readRulesJavaFile());  // Rules.java content
        
        scriptRepository.save(script);
    }
}
```

---

### Step 3: Script Compilation

**Script Compiler**:
```java
@Service
public class JavaScriptCompiler {
    
    @Autowired
    private ScriptRepository scriptRepository;
    
    public ScriptCompileResult compileWageType(
            WageType wageType,
            Long regulationId) {
        
        // 1. Get function scripts (Rules.java)
        List<Script> rulesScripts = scriptRepository
            .findByRegulationIdAndFunctionType(
                regulationId, 
                FunctionType.PAYROLL);
        
        // 2. Generate complete class code
        String className = "WageTypeValueFunction_" + wageType.getId();
        String sourceCode = generateWageTypeClass(
            className,
            wageType.getValueExpression(),  // e.g., "grossSalary()"
            rulesScripts);
        
        // 3. Compile to bytecode
        byte[] bytecode = compileToBytecode(className, sourceCode);
        
        // 4. Store in WageType
        wageType.setBinary(bytecode);
        wageType.setScriptHash(calculateHash(bytecode));
        
        return new ScriptCompileResult(bytecode, sourceCode);
    }
    
    private String generateWageTypeClass(
            String className,
            String valueExpression,
            List<Script> rulesScripts) {
        
        StringBuilder code = new StringBuilder();
        
        // Package
        code.append("package com.payroll.scripting.function;\n\n");
        
        // Imports
        code.append("import com.payroll.scripting.runtime.*;\n");
        code.append("import java.math.BigDecimal;\n\n");
        
        // Class declaration
        code.append("public class ").append(className)
            .append(" implements WageTypeValueFunction {\n");
        code.append("    private IWageTypeValueRuntime runtime;\n");
        code.append("    private RulesFunctions rules;\n\n");
        
        // Constructor
        code.append("    public ").append(className)
            .append("(IWageTypeValueRuntime runtime) {\n");
        code.append("        this.runtime = runtime;\n");
        code.append("        this.rules = new RulesFunctions(runtime);\n");
        code.append("    }\n\n");
        
        // GetValue method
        code.append("    @Override\n");
        code.append("    public Object getValue() {\n");
        code.append("        return rules.").append(valueExpression).append(";\n");
        code.append("    }\n\n");
        
        // Include Rules.java functions
        for (Script script : rulesScripts) {
            code.append(script.getValue());  // Rules.java content
        }
        
        code.append("}\n");
        
        return code.toString();
    }
    
    private byte[] compileToBytecode(String className, String sourceCode) {
        // Using Janino
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(sourceCode);
            
            Class<?> compiledClass = compiler.getClassLoader()
                .loadClass("com.payroll.scripting.function." + className);
            
            // Get bytecode (requires custom classloader or Java Compiler API)
            return getClassBytes(compiledClass);
            
        } catch (Exception e) {
            throw new ScriptCompileException("Compilation failed", e);
        }
    }
}
```

---

### Step 4: Class Loading and Execution

**Custom ClassLoader** (for unloading support):
```java
public class CollectibleClassLoader extends ClassLoader {
    private final Map<String, Class<?>> classes = new HashMap<>();
    
    public CollectibleClassLoader(ClassLoader parent) {
        super(parent);
    }
    
    public Class<?> loadClassFromBytecode(String className, byte[] bytecode) {
        Class<?> clazz = defineClass(className, bytecode, 0, bytecode.length);
        classes.put(className, clazz);
        return clazz;
    }
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) 
            throws ClassNotFoundException {
        // Check if we loaded it
        Class<?> clazz = classes.get(name);
        if (clazz != null) {
            return clazz;
        }
        return super.loadClass(name, resolve);
    }
    
    public void unload() {
        classes.clear();
        // Note: Java doesn't support true unloading,
        // but we can clear references for GC
    }
}
```

**Assembly Cache** (similar to C#):
```java
@Service
public class AssemblyCache {
    
    private final Map<CacheKey, Class<?>> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    
    public AssemblyCache() {
        this.cleanupExecutor = Executors.newScheduledThreadPool(1);
        // Schedule cleanup every 30 minutes
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanup, 30, 30, TimeUnit.MINUTES);
    }
    
    public Class<?> getWageTypeClass(WageType wageType) {
        CacheKey key = new CacheKey(
            WageType.class, 
            wageType.getScriptHash());
        
        return cache.computeIfAbsent(key, k -> {
            // Load from binary
            byte[] bytecode = wageType.getBinary();
            if (bytecode == null) {
                bytecode = loadFromDatabase(wageType);
            }
            
            // Create classloader and load class
            CollectibleClassLoader loader = new CollectibleClassLoader(
                getClass().getClassLoader());
            return loader.loadClassFromBytecode(
                "com.payroll.scripting.function.WageTypeValueFunction_" + 
                wageType.getId(),
                bytecode);
        });
    }
    
    private void cleanup() {
        // Remove old entries (simplified - would need timestamp tracking)
        // Note: True unloading not possible, but we can clear cache
    }
}
```

**Function Host**:
```java
@Service
public class FunctionHost {
    
    @Autowired
    private AssemblyCache assemblyCache;
    
    public Object executeWageTypeValue(
            WageType wageType,
            IWageTypeValueRuntime runtime) {
        
        try {
            // 1. Load compiled class
            Class<?> functionClass = assemblyCache.getWageTypeClass(wageType);
            
            // 2. Create instance
            Constructor<?> constructor = functionClass.getConstructor(
                IWageTypeValueRuntime.class);
            WageTypeValueFunction function = 
                (WageTypeValueFunction) constructor.newInstance(runtime);
            
            // 3. Execute
            return function.getValue();
            
        } catch (Exception e) {
            throw new ScriptExecutionException("Execution failed", e);
        }
    }
}
```

---

## 4. Runtime Interface

### Base Runtime Interface
```java
public interface IWageTypeValueRuntime {
    // Case value access
    Decimal getFieldValue(String fieldName);
    void setValue(String fieldName, Object value);
    
    // Wage type access
    Decimal getWageTypeValue(BigDecimal wageTypeNumber);
    
    // Collector access
    Decimal getCollectorValue(String collectorName);
    
    // Lookup access
    Decimal getSlab(String lookupName, String key);
    
    // Logging
    void addLog(int level, String message);
    
    // ... other runtime methods
}
```

### Runtime Implementation
```java
@Service
public class WageTypeValueRuntime implements IWageTypeValueRuntime {
    
    private final CaseValueProvider caseValueProvider;
    private final PayrollContext context;
    
    public WageTypeValueRuntime(
            CaseValueProvider caseValueProvider,
            PayrollContext context) {
        this.caseValueProvider = caseValueProvider;
        this.context = context;
    }
    
    @Override
    public Decimal getFieldValue(String fieldName) {
        return caseValueProvider.getCaseValue(fieldName);
    }
    
    @Override
    public void setValue(String fieldName, Object value) {
        caseValueProvider.setCaseValue(fieldName, value);
    }
    
    @Override
    public Decimal getWageTypeValue(BigDecimal wageTypeNumber) {
        return context.getWageTypeResult(wageTypeNumber);
    }
    
    // ... other implementations
}
```

---

## 5. Complete Flow in Java

```
┌─────────────────────────────────────────────────────────────┐
│ 1. DSL Conversion                                            │
│    YAML → Rules.java (Java code with functions)             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Import (ScriptImportService)                              │
│    Rules.json → Script Object                                │
│    - Name: "FR.Rules"                                        │
│    - FunctionTypes: [PAYROLL]                               │
│    - Value: Rules.java content                              │
│    → Stored in Script table                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. WageType Configuration                                    │
│    WageType.valueExpression = "grossSalary()"               │
│    → Stored in WageType table                                │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Compilation (When WageType Saved)                         │
│                                                              │
│    a. Load Scripts with FunctionType.PAYROLL               │
│       → Includes Rules.java (Script.value)                 │
│                                                              │
│    b. Generate Complete Class:                              │
│       - Package and imports                                  │
│       - Class with constructor                               │
│       - getValue() method with expression:                  │
│         return rules.grossSalary();                         │
│       - Rules.java functions (as methods or composition)    │
│                                                              │
│    c. Compile with Janino/Compiler API → Bytecode           │
│                                                              │
│    d. Store bytecode in WageType.binary                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Execution (During Payrun)                                 │
│                                                              │
│    a. AssemblyCache.getWageTypeClass()                      │
│       → Loads class from WageType.binary                    │
│                                                              │
│    b. Create instance:                                       │
│       new WageTypeValueFunction_1000(runtime)               │
│                                                              │
│    c. function.getValue()                                    │
│       → Executes: return rules.grossSalary();               │
│                                                              │
│    d. grossSalary() function executes                       │
│       → Returns calculated value                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Key Differences: C# vs Java

| Aspect | C# | Java |
|--------|----|----|
| **Partial Classes** | ✅ Native support | ❌ Use code generation or composition |
| **Runtime Compilation** | Roslyn | Janino or Java Compiler API |
| **Assembly Unloading** | `CollectibleAssemblyLoadContext` | Custom ClassLoader (limited) |
| **Reflection** | `Activator.CreateInstance` | `Constructor.newInstance()` |
| **Binary Storage** | Assembly binary | Class bytecode |
| **Class Loading** | `Assembly.LoadFromBinary` | Custom ClassLoader |

---

## 7. Implementation Recommendations

### Recommended Approach

**Use Janino + Code Generation**:

1. **Rules.java**: Store as Script object (same as C#)
2. **Compilation**: Generate complete class with expression + functions
3. **Storage**: Store bytecode in WageType.binary
4. **Execution**: Load class, create instance, call method

### Code Generation Strategy

**Option A: Single Class** (Simpler)
```java
// Generated class includes everything
public class WageTypeValueFunction_1000 {
    public Object getValue() {
        return grossSalary();  // Expression
    }
    
    public Decimal grossSalary() {
        // From Rules.java
    }
}
```

**Option B: Composition** (Cleaner)
```java
// Generated class delegates to RulesFunctions
public class WageTypeValueFunction_1000 {
    private RulesFunctions rules;
    
    public Object getValue() {
        return rules.grossSalary();  // Expression
    }
}
```

**Recommendation**: Use **Option A** for simplicity and performance (no extra method calls).

---

## 8. Maven Dependencies

### Complete pom.xml Dependencies
```xml
<dependencies>
    <!-- Runtime Compilation -->
    <dependency>
        <groupId>org.codehaus.janino</groupId>
        <artifactId>commons-compiler</artifactId>
        <version>3.1.12</version>
    </dependency>
    <dependency>
        <groupId>org.codehaus.janino</groupId>
        <artifactId>janino</artifactId>
        <version>3.1.12</version>
    </dependency>
    
    <!-- Java Parser (for code generation) -->
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>3.25.7</version>
    </dependency>
    
    <!-- Spring Boot (if using) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Database (JPA/Hibernate) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

---

## 9. Example: Complete Java Implementation

### Script Compiler Service
```java
@Service
public class JavaScriptCompilerService {
    
    @Autowired
    private ScriptRepository scriptRepository;
    
    public ScriptCompileResult compileWageType(
            WageType wageType, 
            Long regulationId) {
        
        // 1. Load Rules.java scripts
        List<Script> rulesScripts = scriptRepository
            .findByRegulationIdAndFunctionTypeMask(
                regulationId, 
                FunctionType.PAYROLL.getMask());
        
        // 2. Generate class code
        String className = "WageTypeValueFunction_" + wageType.getId();
        String sourceCode = generateClassCode(
            className,
            wageType.getValueExpression(),
            rulesScripts);
        
        // 3. Compile
        byte[] bytecode = compileWithJanino(className, sourceCode);
        
        return new ScriptCompileResult(bytecode, sourceCode);
    }
    
    private String generateClassCode(
            String className,
            String valueExpression,
            List<Script> rulesScripts) {
        
        StringBuilder code = new StringBuilder();
        
        code.append("package com.payroll.scripting.function;\n\n");
        code.append("import com.payroll.scripting.runtime.*;\n");
        code.append("import java.math.BigDecimal;\n\n");
        
        code.append("public class ").append(className)
            .append(" implements WageTypeValueFunction {\n");
        code.append("    private IWageTypeValueRuntime runtime;\n\n");
        
        code.append("    public ").append(className)
            .append("(IWageTypeValueRuntime runtime) {\n");
        code.append("        this.runtime = runtime;\n");
        code.append("    }\n\n");
        
        code.append("    @Override\n");
        code.append("    public Object getValue() {\n");
        code.append("        return ").append(valueExpression).append(";\n");
        code.append("    }\n\n");
        
        // Add functions from Rules.java
        for (Script script : rulesScripts) {
            code.append(convertRulesToMethods(script.getValue()));
        }
        
        code.append("}\n");
        
        return code.toString();
    }
    
    private String convertRulesToMethods(String rulesCode) {
        // Parse Rules.java and convert to methods
        // This would use JavaParser to extract methods
        // For now, simplified:
        return rulesCode.replace("public class RulesFunctions", 
                                 "// Rules functions");
    }
    
    private byte[] compileWithJanino(String className, String sourceCode) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(sourceCode);
            
            // Get bytecode using reflection or custom classloader
            Class<?> clazz = compiler.getClassLoader()
                .loadClass("com.payroll.scripting.function." + className);
            
            // Note: Getting bytecode from Janino requires custom approach
            // Alternative: Use Java Compiler API which provides bytecode directly
            return getClassBytes(clazz);
            
        } catch (Exception e) {
            throw new ScriptCompileException("Compilation failed", e);
        }
    }
}
```

### Function Host Service
```java
@Service
public class JavaFunctionHost {
    
    @Autowired
    private AssemblyCache assemblyCache;
    
    public Object executeWageTypeValue(
            WageType wageType,
            IWageTypeValueRuntime runtime) {
        
        try {
            // Load class
            Class<?> functionClass = assemblyCache
                .getWageTypeClass(wageType);
            
            // Create instance
            Constructor<?> constructor = functionClass.getConstructor(
                IWageTypeValueRuntime.class);
            WageTypeValueFunction function = 
                (WageTypeValueFunction) constructor.newInstance(runtime);
            
            // Execute
            return function.getValue();
            
        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Failed to execute wage type " + wageType.getId(), e);
        }
    }
}
```

---

## 10. Challenges and Solutions

### Challenge 1: No Partial Classes

**Solution**: Generate complete class with all code combined

### Challenge 2: Class Unloading

**Problem**: Java doesn't support true class unloading

**Solution**: 
- Use custom ClassLoader per compilation
- Clear references for GC
- Accept that classes may remain in memory

### Challenge 3: Getting Bytecode from Janino

**Problem**: Janino doesn't directly provide bytecode

**Solution**:
- Use Java Compiler API (provides bytecode)
- Or use custom ClassLoader to capture bytecode
- Or use ASM library to generate bytecode directly

### Challenge 4: Performance

**Problem**: Runtime compilation is slower than C#

**Solution**:
- Cache compiled classes aggressively
- Pre-compile common scripts
- Use bytecode storage (avoid recompilation)

---

## 11. Alternative: Pre-compilation Approach

Instead of runtime compilation, pre-compile Rules.java during import:

```java
@Service
public class ScriptPreCompiler {
    
    public void importAndCompile(Script script) {
        // 1. Store source
        scriptRepository.save(script);
        
        // 2. Compile Rules.java to bytecode
        byte[] bytecode = compileRulesJava(script.getValue());
        
        // 3. Store bytecode
        script.setBinary(bytecode);
        scriptRepository.save(script);
    }
    
    private byte[] compileRulesJava(String sourceCode) {
        // Compile Rules.java standalone
        // Store bytecode for later inclusion
    }
}
```

**Then during WageType compilation**:
- Load Rules.java bytecode
- Combine with WageType expression code
- Use ASM to merge bytecode (advanced)

---

## 12. Summary

### Implementation Steps

1. **Use Janino** for runtime compilation (or Java Compiler API)
2. **Generate complete class** (no partial classes)
3. **Store bytecode** in database (WageType.binary)
4. **Use custom ClassLoader** for loading (with caching)
5. **Create instances** using reflection
6. **Execute** via interface method calls

### Key Components

- **JavaScriptCompiler**: Compiles Java source to bytecode
- **AssemblyCache**: Caches loaded classes
- **FunctionHost**: Loads and executes functions
- **CodeGenerator**: Generates complete class code

### Time Estimate

- **Script Compiler**: 2-3 weeks
- **Class Loading System**: 1-2 weeks
- **Code Generation**: 1-2 weeks
- **Integration**: 1 week

**Total**: **5-8 weeks** (vs 6-8 weeks for C# Roslyn version)

---

*Document Generated: 2025-01-05*  
*Java implementation guide for Rules.cs import and execution*

