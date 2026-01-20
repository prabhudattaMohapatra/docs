# Exact TypeScript Migration Problems - Payroll Engine

This document outlines the **specific, exact problems** you will face when migrating the Payroll Engine from .NET/C# to TypeScript, based on analysis of the actual codebase.

---

## Problem 1: Runtime C# Script Compilation (CRITICAL)

### Current Implementation
The system uses **Roslyn** (Microsoft.CodeAnalysis) to compile user-written C# code at runtime into .NET assemblies:

```csharp
// From CSharpCompiler.cs
var compilation = CSharpCompilation.Create(AssemblyName,
    syntaxTrees,
    allReferences,
    new(OutputKind.DynamicallyLinkedLibrary,
        optimizationLevel: OptimizationLevel.Release))
    .Emit(peStream);

// Load compiled assembly
var assembly = loadContext.LoadFromBinary(binary);
var scriptType = assembly.GetType(scriptType.FullName);
var script = Activator.CreateInstance(scriptType, runtime);
```

### Exact TypeScript Problems

#### 1.1 No Native TypeScript Runtime Compiler
- **Problem**: TypeScript is a **compile-time** language, not a runtime language
- **Impact**: Cannot compile TypeScript code at runtime like Roslyn does with C#
- **Workaround Options** (all problematic):
  - Use `ts-node` - **too slow** for production (compiles on every execution)
  - Use `tsc` in child process - **unacceptable latency** (100-500ms per compilation)
  - Pre-compile all scripts - **defeats purpose** of dynamic user code
  - Use JavaScript `eval()` - **security nightmare** and no type safety

#### 1.2 Assembly Loading & Reflection
- **Current**: Loads compiled .NET assemblies from byte arrays, uses reflection to instantiate classes
- **TypeScript Problem**: No equivalent to:
  - `Assembly.LoadFromBinary(byte[])` 
  - `assembly.GetType(className)`
  - `Activator.CreateInstance(type, args)`
- **Impact**: Cannot dynamically load and instantiate compiled code

#### 1.3 Metadata References
- **Current**: Adds references to 15+ .NET assemblies (System, System.Linq, System.Text.Json, etc.)
- **TypeScript Problem**: No equivalent metadata reference system
- **Impact**: Cannot provide type definitions for runtime APIs to user scripts

#### 1.4 Code Generation from Templates
- **Current**: Embeds user code into C# class templates:
```csharp
// Template structure
public partial class WageTypeValueFunction : WageTypeFunction {
    public object GetValue() {
        #region Function
        // USER CODE GOES HERE
        #endregion
        return null;
    }
}
```
- **TypeScript Problem**: 
  - No `#region` directives
  - Different class inheritance model
  - Cannot easily inject user code into class methods

---

## Problem 2: Dynamic Type System & Reflection

### Current Implementation
Uses C# reflection extensively:

```csharp
// From RuntimeBase.cs
var assembly = FunctionHost.GetObjectAssembly(typeof(T), item);
var assemblyScriptType = assembly.GetType(scriptType.FullName);
return Activator.CreateInstance(assemblyScriptType, this);

// Dynamic method invocation
script.Start(); // Calls compiled method
script.GetValue(); // Returns object
```

### Exact TypeScript Problems

#### 2.1 Type Erasure
- **Problem**: TypeScript types are **erased at runtime**
- **Impact**: 
  - Cannot use `typeof` to get runtime type information
  - Cannot dynamically discover method signatures
  - No reflection API equivalent to C#'s `MethodInfo`, `PropertyInfo`

#### 2.2 Dynamic Method Invocation
- **Current**: Calls methods on dynamically compiled classes
- **TypeScript Problem**: 
  - No `Activator.CreateInstance` equivalent
  - Cannot instantiate classes by name at runtime
  - Would need to use `eval()` or `Function()` constructor (security risk)

#### 2.3 Runtime Type Checking
- **Current**: C# provides runtime type checking and casting
- **TypeScript Problem**: 
  - Type checking happens at compile time only
  - Runtime type validation requires manual `instanceof` checks
  - No equivalent to C#'s `is` operator or `as` casting

---

## Problem 3: Assembly Caching & Memory Management

### Current Implementation
```csharp
// From AssemblyCache.cs
private static readonly ConcurrentDictionary<AssemblyKey, AssemblyRuntime> Assemblies = new();

// Collectible AssemblyLoadContext for memory management
public class CollectibleAssemblyLoadContext : AssemblyLoadContext {
    public CollectibleAssemblyLoadContext() : base(true) { }
    // Allows unloading assemblies to prevent memory leaks
}
```

### Exact TypeScript Problems

#### 3.1 No Assembly Unloading
- **Problem**: JavaScript/TypeScript has **no concept of unloading modules**
- **Impact**: 
  - Once a module is loaded via `require()` or `import()`, it stays in memory
  - Cannot unload old script versions when rules change
  - Memory leaks will accumulate over time
  - **Critical for payroll system** that may have thousands of rule versions

#### 3.2 Module Caching
- **Current**: Caches compiled assemblies by hash, unloads unused ones
- **TypeScript Problem**: 
  - Node.js caches modules by file path, not by content hash
  - Cannot have multiple versions of same "module" loaded
  - Would need to use dynamic `require()` with unique paths (hacky)

#### 3.3 Memory Isolation
- **Current**: Each assembly in separate `AssemblyLoadContext` for isolation
- **TypeScript Problem**: 
  - No equivalent isolation mechanism
  - All modules share same global scope
  - Risk of variable/function name collisions

---

## Problem 4: Performance - Script Execution

### Current Implementation
- Compiled C# assemblies execute at **near-native speed**
- JIT compilation optimizes hot paths
- Assembly caching prevents recompilation

### Exact TypeScript Problems

#### 4.1 JavaScript Execution Speed
- **Problem**: V8 is fast, but **not as fast as JIT-compiled C#**
- **Impact**: 
  - Payroll calculations are CPU-intensive
  - May see 2-5x slower execution for complex rules
  - Could impact batch processing performance

#### 4.2 No JIT Optimization for User Code
- **Current**: .NET JIT optimizes compiled assemblies
- **TypeScript Problem**: 
  - V8 optimizes, but less predictable
  - User code runs as JavaScript, not optimized bytecode
  - No control over optimization level

#### 4.3 Compilation Overhead
- **Current**: Compile once, cache assembly, execute many times
- **TypeScript Problem**: 
  - If using `ts-node` or `tsc`, compilation happens every time
  - Even with caching, TypeScript compilation is slower than C# compilation
  - First execution will be slower

---

## Problem 5: Type System Integration

### Current Implementation
User scripts have access to strongly-typed APIs:

```csharp
// User can write:
Employee["Wage"]  // Returns PayrollValue
WageType[2300]    // Returns decimal
Collector["MyCollector"]  // Returns decimal
GetWageTypeResults(2300, query)  // Returns IEnumerable<WageTypeResult>
```

### Exact TypeScript Problems

#### 5.1 Type Definitions for Runtime APIs
- **Problem**: Need to provide TypeScript type definitions for 100+ runtime methods
- **Impact**: 
  - Must manually create `.d.ts` files for all runtime interfaces
  - User scripts need these types for IntelliSense
  - Maintenance burden when APIs change

#### 5.2 Generic Type Support
- **Current**: C# generics work seamlessly
- **TypeScript Problem**: 
  - TypeScript generics are compile-time only
  - Cannot use `IEnumerable<T>` equivalent at runtime
  - Must use `Array<T>` or custom collection types

#### 5.3 Nullable Value Types
- **Current**: C# has `decimal?`, `int?` for nullable value types
- **TypeScript Problem**: 
  - TypeScript uses `number | null` or `number | undefined`
  - Different semantics (undefined vs null)
  - More verbose type annotations

---

## Problem 6: Security & Sandboxing

### Current Implementation
- Compiled assemblies run in same process but with controlled access
- Uses .NET security attributes and permissions

### Exact TypeScript Problems

#### 6.1 No Built-in Sandboxing
- **Problem**: Node.js has **no secure sandbox** for user code
- **Impact**: 
  - User scripts can access `require()`, `process`, `fs`, `http`
  - Can read/write files, make network calls, access environment variables
  - **Critical security risk** for payroll system

#### 6.2 Sandboxing Solutions Are Limited
- **Options**:
  - `vm2` - **deprecated**, has known vulnerabilities
  - `isolated-vm` - Requires native module, complex setup
  - `worker_threads` - Limited isolation, still can access some globals
  - Custom V8 isolate - **Very complex**, requires C++ knowledge

#### 6.3 Code Injection Risks
- **Problem**: If using `eval()` or `Function()` constructor:
  - Vulnerable to code injection attacks
  - No way to restrict what code can execute
  - Cannot prevent access to Node.js APIs

---

## Problem 7: Database Integration - Dapper Equivalent

### Current Implementation
Uses Dapper for high-performance, raw SQL queries:

```csharp
// From RepositoryBase.cs
var compiler = new SqlServerCompiler();
var query = new Query(tableName).Where(conditions);
var sql = compiler.Compile(query).Sql;
var results = connection.Query<T>(sql, parameters);
```

### Exact TypeScript Problems

#### 7.1 No Dapper Equivalent
- **Problem**: No TypeScript library matches Dapper's performance
- **Options** (all have issues):
  - `mssql` with raw queries - **slower**, more verbose
  - `typeorm` - **much slower**, ORM overhead
  - `prisma` - **different paradigm**, not raw SQL focused
  - `knex` - Query builder, but **not as fast** as Dapper

#### 7.2 Type Mapping
- **Current**: Dapper automatically maps SQL results to C# objects
- **TypeScript Problem**: 
  - Manual mapping required or use slower ORM
  - No automatic property mapping
  - Must write mapping code for each entity

#### 7.3 Custom Type Handlers
- **Current**: Custom Dapper handlers for JSON, lists, dictionaries
- **TypeScript Problem**: 
  - Must reimplement all type handlers
  - JSON handling different (JSON.parse vs System.Text.Json)
  - Different serialization semantics

---

## Problem 8: Decimal Precision

### Current Implementation
Uses C# `decimal` type (128-bit, exact decimal arithmetic):

```csharp
public decimal WageTypeValue { get; }
public decimal GetValue() { return 1234.56m; }
```

### Exact TypeScript Problems

#### 8.1 JavaScript Number Type
- **Problem**: JavaScript only has `number` (IEEE 754 double, 64-bit)
- **Impact**: 
  - **Precision loss** for financial calculations
  - Cannot represent exact decimal values
  - Example: `0.1 + 0.2 !== 0.3` in JavaScript
  - **Critical for payroll** where exact currency amounts matter

#### 8.2 Decimal Libraries
- **Solutions**:
  - `decimal.js` - **Slower**, requires explicit usage everywhere
  - `big.js` - Similar issues
  - Must replace all `number` with `Decimal` type
  - **Massive refactoring** required

#### 8.3 Performance Impact
- **Problem**: Decimal libraries are **much slower** than native `decimal`
- **Impact**: 
  - 10-100x slower for arithmetic operations
  - Significant performance degradation for payroll calculations

---

## Problem 9: LINQ & Collection Operations

### Current Implementation
Extensive use of LINQ:

```csharp
var results = GetWageTypeResults(2300, query)
    .Where(x => x.Period.Start >= startDate)
    .Select(x => x.Value)
    .DefaultIfEmpty()
    .Average();
```

### Exact TypeScript Problems

#### 9.1 No LINQ Equivalent
- **Problem**: TypeScript/JavaScript has array methods, but **different API**
- **Impact**: 
  - Must rewrite all LINQ queries
  - Different method names (`filter` vs `Where`, `map` vs `Select`)
  - Different lazy evaluation semantics
  - No `DefaultIfEmpty()` equivalent

#### 9.2 Performance Differences
- **Current**: LINQ is optimized, can use `IEnumerable` for lazy evaluation
- **TypeScript Problem**: 
  - Array methods create intermediate arrays
  - More memory allocations
  - Less efficient for large datasets

---

## Problem 10: Exception Handling & Error Reporting

### Current Implementation
```csharp
// From CSharpCompiler.cs
if (!compilation.Success) {
    throw new ScriptCompileException(GetCompilerFailures(compilation));
}

// Detailed error messages with line numbers
failure += $" [{diagnostic.Id}: Line {spanStart.Line + 1}, Column {spanStart.Character + 1}";
```

### Exact TypeScript Problems

#### 10.1 Compiler Error Format
- **Problem**: TypeScript compiler errors have **different format**
- **Impact**: 
  - Must adapt error parsing
  - Different error codes and messages
  - May lose some diagnostic information

#### 10.2 Runtime Error Stack Traces
- **Problem**: JavaScript stack traces are **less informative** than C#
- **Impact**: 
  - Harder to debug user script errors
  - Less context about where error occurred
  - Different exception types

---

## Problem 11: Async/Await Patterns

### Current Implementation
Uses synchronous execution for script methods:

```csharp
public object GetValue() {  // Synchronous
    return WageType[2300];
}
```

### Exact TypeScript Problems

#### 11.1 Node.js Async Nature
- **Problem**: Many Node.js APIs are **async by default**
- **Impact**: 
  - Database queries, file I/O are async
  - Must use `await` everywhere
  - User scripts would need to be async
  - **Breaking change** for existing scripts

#### 11.2 Synchronous Alternatives
- **Options**:
  - Use sync versions of APIs (deprecated, slower)
  - Make all scripts async (major refactoring)
  - Use blocking calls (defeats purpose of async)

---

## Problem 12: Embedded Resources & Code Templates

### Current Implementation
Embeds C# code files as resources:

```csharp
// From PayrollEngine.Client.Scripting.csproj
<EmbeddedResource Include="Function\WageTypeValueFunction.cs">
    <LogicalName>Function\WageTypeValueFunction.cs</LogicalName>
</EmbeddedResource>

// Loaded at runtime
var functionCode = CodeFactory.GetEmbeddedCodeFile($"Function\\{functionType}Function");
```

### Exact TypeScript Problems

#### 12.1 No Embedded Resources
- **Problem**: TypeScript/JavaScript has **no embedded resource concept**
- **Impact**: 
  - Must use `fs.readFileSync()` or `require()` for templates
  - Templates must be separate files
  - Cannot bundle templates into single executable

#### 12.2 Template Loading
- **Current**: Templates loaded from assembly resources
- **TypeScript Problem**: 
  - Must distribute template files separately
  - Deployment complexity increases
  - Cannot easily version templates with code

---

## Problem 13: Reflection-Based Function Discovery

### Current Implementation
Uses reflection to discover and invoke action methods:

```csharp
// From PayrollFunction.cs
var method = GetActionMethod<TAction>(action);
method.Invoke(actionsInstance, parameterValues.ToArray());
```

### Exact TypeScript Problems

#### 13.1 No Reflection API
- **Problem**: JavaScript has **no reflection API**
- **Impact**: 
  - Cannot discover methods by name
  - Cannot get method signatures
  - Must use string-based method calls or `eval()`

#### 13.2 Dynamic Method Resolution
- **Current**: Resolves methods at runtime using reflection
- **TypeScript Problem**: 
  - Must maintain method registry manually
  - Cannot automatically discover available methods
  - More error-prone

---

## Problem 14: Multi-threading & Concurrency

### Current Implementation
Uses .NET tasks and thread pool:

```csharp
var task = Task.Factory.StartNew(() => {
    using var script = CreateScript(typeof(ReportStartFunction), report);
    script.Start();
});
task.Wait(Timeout);
```

### Exact TypeScript Problems

#### 14.1 Single-threaded Event Loop
- **Problem**: Node.js is **single-threaded** (event loop)
- **Impact**: 
  - Cannot use true parallelism for CPU-intensive calculations
  - Must use `worker_threads` for parallel execution
  - More complex concurrency model

#### 14.2 Worker Threads Complexity
- **Problem**: `worker_threads` require:
  - Separate script files
  - Message passing (no shared memory)
  - More complex error handling
  - **Significant refactoring** required

---

## Problem 15: DateTime & Time Zone Handling

### Current Implementation
Uses C# `DateTime` with time zone support:

```csharp
public DateTime EvaluationDate { get; }
public Tuple<DateTime, DateTime> GetEvaluationPeriod();
```

### Exact TypeScript Problems

#### 15.1 JavaScript Date Limitations
- **Problem**: JavaScript `Date` is **notoriously problematic**
- **Impact**: 
  - Time zone handling is error-prone
  - No built-in time zone support
  - Must use libraries like `date-fns` or `moment.js`
  - Different API than C# `DateTime`

#### 15.2 Date Arithmetic
- **Current**: C# `DateTime` has rich arithmetic operations
- **TypeScript Problem**: 
  - Must use library functions for date math
  - More verbose
  - Potential for errors

---

## Summary: Critical Blockers

### Must Solve (System Won't Work Without These)
1. ✅ **Runtime Script Compilation** - No equivalent to Roslyn
2. ✅ **Assembly Loading** - Cannot dynamically load compiled code
3. ✅ **Memory Management** - Cannot unload old script versions
4. ✅ **Decimal Precision** - JavaScript numbers insufficient for payroll

### Major Challenges (Significant Refactoring Required)
5. ⚠️ **Security/Sandboxing** - No built-in sandbox for user code
6. ⚠️ **Performance** - Slower execution, compilation overhead
7. ⚠️ **Type System** - Type erasure, no reflection
8. ⚠️ **Database Access** - No Dapper equivalent

### Moderate Issues (Solvable but Time-Consuming)
9. ⚠️ **LINQ Replacement** - Different collection APIs
10. ⚠️ **Async Patterns** - Node.js async nature
11. ⚠️ **Error Handling** - Different exception model
12. ⚠️ **Template System** - No embedded resources

---

## Recommended Solutions (If Proceeding)

### For Script Compilation:
1. **Use V8 Isolates** (via `isolated-vm` or custom C++ module)
   - Provides true isolation
   - Can compile JavaScript at runtime
   - Complex but feasible

2. **Use GraalVM with JavaScript**
   - Polyglot runtime
   - Better performance
   - Can compile JavaScript to native code

3. **Use QuickJS**
   - Lightweight JavaScript engine
   - Can embed in Node.js
   - Good performance

### For Decimal Precision:
- Use `decimal.js` or `big.js` throughout
- Create wrapper types
- Extensive refactoring required

### For Security:
- Use `isolated-vm` for sandboxing
- Or implement custom V8 isolate
- Restrict available APIs

---

## Conclusion

**TypeScript migration is technically possible but requires:**
- Custom script compilation system (V8 isolates or similar)
- Complete decimal arithmetic replacement
- Security sandboxing implementation
- Significant performance optimization
- **Estimated effort: 18-24 months**

**Recommendation**: Consider Java instead, which has better solutions for these problems (Janino compiler, BigDecimal, JVM security).

---

*Document Generated: 2025-01-05*
*Based on analysis of: payroll-engine-backend, PayrollEngine.Client.Scripting*

