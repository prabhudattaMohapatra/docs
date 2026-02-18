# Rules.cs Import and Function Execution Flow

Complete explanation of how Rules.cs gets imported, where functions reside, and how wage type expressions trigger them.

---

## Overview

**Rules.cs** contains user-defined functions (like `gross_salary()`, `social_security_ceiling()`) that are called from wage type expressions. This document explains the complete flow from import to execution.

---

## 1. Rules.cs Generation & Import

### Step 1: DSL Conversion
**Source**: YAML DSL files  
**Output**: `Rules.cs` file

**Process**:
- DSL converter reads YAML rule definitions
- Generates C# code with functions in `WageTypeValueFunction` partial class
- Creates `Rules.cs` file

**Example Rules.cs Structure**:
```csharp
namespace PayrollEngine.Client.Scripting.Function;

public partial class WageTypeValueFunction
{
  public string Namespace => "FR";

  public Decimal gross_salary()
  {
    set_value("employee.contract_type", "permanent");
    set_value("employee.job_title", "executive");
    // ... calculation logic
    return get_field_value("employee_gross_salary");
  }

  public Decimal social_security_ceiling()
  {
    // ... calculation logic
    return get_field_value("monthly_ss_ceiling");
  }
}
```

---

### Step 2: Script Import
**Command**: `PayrollImport Rules.json` or `PayrollImportFromDsl`

**Import Process**:
1. **Script JSON File** (`Rules.json`):
```json
{
  "tenants": [{
    "regulations": [{
      "scripts": [{
        "name": "FR.Rules",
        "functionTypes": ["Payroll"],
        "valueFile": "Rules/Rules.cs"
      }]
    }]
  }]
}
```

2. **Script Object Creation**:
   - Creates `Script` object in database
   - `Name`: "FR.Rules"
   - `FunctionTypes`: `[FunctionType.Payroll]`
   - `Value`: Contents of `Rules.cs` file
   - Stored in `Script` table

3. **Database Storage**:
   - **Table**: `Script`
   - **Columns**: `Id`, `RegulationId`, `Name`, `FunctionTypeMask`, `Value`
   - **Relationship**: Script belongs to Regulation

**Key Point**: Rules.cs is stored as a **Script object** with `FunctionType.Payroll`, not as a separate file in the runtime.

---

## 2. Where Functions Reside in PayrollEngine

### Storage Location

**Database**:
- **Table**: `Script`
- **Column**: `Value` (contains the Rules.cs C# code)
- **Filter**: `FunctionTypeMask` includes `FunctionType.Payroll`

**At import** (when WageTypes.json etc. are imported):
- Scripts are loaded from database (Script table)
- Included in compilation unit and compiled into assembly binary
- Binary stored on script object (e.g. `WageType.Binary`). **No compilation at payrun.**

**At payrun** (during execution):
- Precompiled binary is loaded from DB or `AssemblyCache`; no compilation
- Functions are available as methods on `WageTypeValueFunction` class

---

## 3. How Wage Type Expression Triggers Functions

### Step 1: Wage Type Configuration

**Wage Type Definition**:
```json
{
  "wageTypeNumber": 1000,
  "name": "Gross Salary",
  "valueExpression": "gross_salary()"
}
```

**Key Field**: `ValueExpression` = `"gross_salary()"`

This expression is stored in the `WageType` table, `ValueExpression` column.

---

### Step 2: Script Compilation (At Import, When WageType is Saved)

**Trigger**: When a `WageType` is created or updated **during import** (e.g. when WageTypes.json is imported via PayrollImport), the system compiles its scripts and stores the binary on the WageType. **Compilation does not happen at payrun time**—at payrun only the precompiled binary is loaded and executed.

**Compilation Process** (`ScriptCompiler.Compile()`):

1. **Collect Function Scripts**:
   - Gets `ValueExpression` from WageType: `"gross_salary()"`
   - Maps to `FunctionType.WageTypeValue`

2. **Load Global Scripts**:
   ```csharp
   // From ScriptTrackChildDomainRepository.SetupBinaryAsync()
   scripts = await ScriptRepository.GetFunctionScriptsAsync(
       context, 
       regulationId, 
       [FunctionType.WageTypeValue]  // Actually gets all Payroll scripts
   );
   ```
   - Queries `Script` table
   - Filters by `FunctionType.Payroll` (or `All`)
   - Returns all Script objects including Rules.cs

3. **Compilation Code Structure**:
   ```csharp
   // 1. Embedded templates (system code)
   codes.Add("Cache\\Cache.cs");           // System cache utilities
   codes.Add("Function\\WageTypeFunction.cs");  // Base function class
   
   // 2. WageTypeValueFunction template with ValueExpression embedded
   var functionCode = GetFunctionCode(FunctionType.WageTypeValue, "gross_salary()");
   // Result: WageTypeValueFunction class with GetValue() containing "gross_salary()"
   codes.Add(functionCode);
   
   // 3. Global Scripts (includes Rules.cs)
   foreach (var script in Scripts)  // Scripts with FunctionType.Payroll
   {
       codes.Add(script.Value);  // This includes Rules.cs content
   }
   ```

4. **Final Compiled Code**:
   ```csharp
   // From embedded template
   namespace PayrollEngine.Client.Scripting.Function;
   public partial class WageTypeValueFunction : WageTypeFunction
   {
       public object GetValue()
       {
           #region Function
           return gross_salary();  // <-- ValueExpression embedded here
           #endregion
       }
   }
   
   // From Rules.cs (Script.Value)
   namespace PayrollEngine.Client.Scripting.Function;
   public partial class WageTypeValueFunction  // <-- Same partial class!
   {
       public Decimal gross_salary()
       {
           // ... function implementation
           return get_field_value("employee_gross_salary");
       }
   }
   ```

5. **Compilation**:
   - Uses Roslyn (`Microsoft.CodeAnalysis.CSharp`)
   - Compiles all code into a single assembly
   - Stores binary in `WageType.Binary` column
   - Stores hash in `WageType.ScriptHash`

**Key Point**: Rules.cs functions are included in the **same compilation unit** as the wage type expression, making them available as methods on the same class.

---

### Step 3: Script Execution (During Payrun)

**Execution Flow**:

1. **Load Compiled Assembly**:
   ```csharp
   // From RuntimeBase.CreateScript()
   var assembly = FunctionHost.GetObjectAssembly(typeof(WageType), wageType);
   // Loads from WageType.Binary (or database if not cached)
   ```

2. **Create Script Instance**:
   ```csharp
   // From WageTypeValueRuntime.EvaluateValue()
   using var script = CreateScript(typeof(WageTypeValueFunction), wageType);
   // Creates instance: new WageTypeValueFunction(runtime)
   ```

3. **Execute Expression**:
   ```csharp
   return script.GetValue();
   // Calls the compiled GetValue() method
   // Which contains: return gross_salary();
   ```

4. **Function Call**:
   - `gross_salary()` is called
   - Function exists because Rules.cs was included in compilation
   - Function executes and returns value

---

## 4. Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. DSL Conversion                                            │
│    YAML → Rules.cs (C# code with functions)                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Import (PayrollImport)                                    │
│    Rules.json → Script Object                                │
│    - Name: "FR.Rules"                                        │
│    - FunctionTypes: [Payroll]                                │
│    - Value: Rules.cs content                                 │
│    → Stored in Script table                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. WageType Configuration                                    │
│    WageType.ValueExpression = "gross_salary()"              │
│    → Stored in WageType table                                │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Compilation (When WageType Saved)                         │
│                                                              │
│    a. Load Scripts with FunctionType.Payroll                │
│       → Includes Rules.cs (Script.Value)                    │
│                                                              │
│    b. Build Compilation Unit:                                │
│       - Embedded templates (Cache, Function base classes)   │
│       - WageTypeValueFunction with ValueExpression:         │
│         public object GetValue() {                           │
│           return gross_salary();  // <-- Expression          │
│         }                                                     │
│       - Rules.cs content (partial class with functions)     │
│                                                              │
│    c. Compile with Roslyn → Assembly binary                  │
│                                                              │
│    d. Store binary in WageType.Binary                       │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Execution (During Payrun)                                 │
│                                                              │
│    a. FunctionHost.GetObjectAssembly()                       │
│       → Loads assembly from WageType.Binary                 │
│                                                              │
│    b. CreateScript(typeof(WageTypeValueFunction), wageType)  │
│       → new WageTypeValueFunction(runtime)                   │
│                                                              │
│    c. script.GetValue()                                      │
│       → Executes: return gross_salary();                     │
│                                                              │
│    d. gross_salary() function executes                       │
│       → Returns calculated value                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Key Components

### Script Storage
- **Location**: `Script` table in database
- **Key Fields**:
  - `RegulationId`: Links to regulation
  - `Name`: Script name (e.g., "FR.Rules")
  - `FunctionTypeMask`: Bitmask of function types (includes `Payroll`)
  - `Value`: C# source code (Rules.cs content)

### Script Compilation
- **Component**: `ScriptCompiler`
- **Process**:
  1. Collects function expressions (ValueExpression, ResultExpression)
  2. Loads global scripts (Script objects with matching FunctionTypes)
  3. Embeds expressions into function templates
  4. Compiles all code together
  5. Stores binary in object (WageType.Binary)

### Script Execution
- **Component**: `FunctionHost` + `AssemblyCache`
- **Process**:
  1. Loads compiled assembly from binary (or cache)
  2. Creates script instance using reflection
  3. Executes expression which calls function
  4. Function executes and returns value

---

## 6. Function Resolution

### How `gross_salary()` is Found

1. **Compilation Time**:
   - Rules.cs is included in compilation
   - `gross_salary()` method is part of `WageTypeValueFunction` partial class
   - Compiler resolves method call

2. **Execution Time**:
   - Compiled assembly contains both:
     - `GetValue()` method with `return gross_salary();`
     - `gross_salary()` method implementation
   - Runtime executes the compiled code directly
   - No dynamic resolution needed

**Key Point**: Functions are **statically compiled** into the same assembly, not dynamically resolved at runtime.

---

## 7. Multiple Scripts Support

### Multiple Rules Files

If you have multiple Script objects with `FunctionType.Payroll`:
- All are included in compilation
- All become part of the same `WageTypeValueFunction` partial class
- Functions from all scripts are available

**Example**:
```
Script 1: "FR.Rules" → Contains gross_salary()
Script 2: "FR.TaxRules" → Contains calculate_tax()
Script 3: "FR.BenefitsRules" → Contains calculate_benefits()

All compiled together → All functions available
```

---

## 8. Function Type Matching

### How Scripts are Selected

**When compiling WageType**:
```csharp
// From ScriptTrackChildDomainRepository
scripts = await ScriptRepository.GetFunctionScriptsAsync(
    context, 
    regulationId, 
    [FunctionType.WageTypeValue]  // Requested function types
);
```

**Script Selection Logic**:
- Scripts with `FunctionType.All` → Always included
- Scripts with `FunctionType.Payroll` → Included (covers WageTypeValue)
- Scripts with `FunctionType.WageTypeValue` → Included
- Scripts with only other types → Excluded

**Rules.cs typically has**: `FunctionType.Payroll` or `FunctionType.All`

---

## 9. Code Structure in Compiled Assembly

### Final Compiled Structure

```csharp
// Compiled into single assembly

namespace PayrollEngine.Client.Scripting.Function;

// Base class (from embedded template)
public abstract partial class WageTypeFunction : PayrunFunction
{
    // Base functionality
}

// Value function (from template + expression)
public partial class WageTypeValueFunction : WageTypeFunction
{
    private IWageTypeValueRuntime Runtime { get; }
    
    // Expression embedded here
    public object GetValue()
    {
        return gross_salary();  // <-- Your ValueExpression
    }
}

// Rules functions (from Rules.cs Script.Value)
public partial class WageTypeValueFunction  // Same partial class!
{
    public Decimal gross_salary()
    {
        // Your function implementation
        set_value("employee.contract_type", "permanent");
        return get_field_value("employee_gross_salary");
    }
    
    public Decimal social_security_ceiling()
    {
        // Another function
        return get_field_value("monthly_ss_ceiling");
    }
}
```

**Key**: All code is in the **same namespace and partial class**, so functions are directly callable.

---

## 10. Database Schema

### Script Table
```sql
CREATE TABLE Script (
    Id INT PRIMARY KEY,
    RegulationId INT,
    Name NVARCHAR(128),
    FunctionTypeMask BIGINT,  -- Bitmask of FunctionTypes
    Value NVARCHAR(MAX),       -- C# source code (Rules.cs content)
    -- ... other fields
)
```

### WageType Table
```sql
CREATE TABLE WageType (
    Id INT PRIMARY KEY,
    RegulationId INT,
    WageTypeNumber DECIMAL(18,4),
    Name NVARCHAR(128),
    ValueExpression NVARCHAR(MAX),  -- e.g., "gross_salary()"
    ResultExpression NVARCHAR(MAX),
    Binary VARBINARY(MAX),          -- Compiled assembly binary
    ScriptHash INT,                  -- Hash of compiled code
    -- ... other fields
)
```

---

## 11. Example: Complete Execution

### Wage Type Definition
```json
{
  "wageTypeNumber": 1000,
  "name": "Gross Salary",
  "valueExpression": "gross_salary()"
}
```

### Rules.cs Content (in Script table)
```csharp
public partial class WageTypeValueFunction
{
    public Decimal gross_salary()
    {
        set_value("employee.contract_type", "permanent");
        if (get_field_value("employee.base_monthly_salary") > 0)
        {
            set_value("employee_gross_salary", 
                get_field_value("employee.base_monthly_salary") + 
                get_field_value("employee.transport_allowance"));
        }
        return get_field_value("employee_gross_salary");
    }
}
```

### Compiled Code (in WageType.Binary)
```csharp
public partial class WageTypeValueFunction
{
    public object GetValue()
    {
        return gross_salary();  // Calls function from Rules.cs
    }
    
    public Decimal gross_salary()
    {
        // Implementation from Rules.cs
        // ...
    }
}
```

### Execution
```csharp
// 1. Load assembly
var assembly = FunctionHost.GetObjectAssembly(typeof(WageType), wageType);

// 2. Create instance
var script = new WageTypeValueFunction(runtime);

// 3. Execute
var value = script.GetValue();
// → Calls gross_salary()
// → Returns calculated value
```

---

## 12. Key Takeaways

### Where Functions Reside

1. **Source**: Rules.cs file (generated from DSL)
2. **Storage**: Script table, `Value` column (as C# source code)
3. **Compilation**: Included in WageType assembly binary
4. **Execution**: Available as methods on `WageTypeValueFunction` class

### How Expressions Trigger Functions

1. **WageType.ValueExpression** contains function call: `"gross_salary()"`
2. **Compilation** embeds expression into `GetValue()` method
3. **Rules.cs** is included in same compilation (as partial class)
4. **Execution** calls `GetValue()` which calls `gross_salary()`
5. **Function** executes and returns value

### Critical Points

- ✅ Rules.cs is stored as **Script object** with `FunctionType.Payroll`
- ✅ Functions are **compiled together** with wage type expressions
- ✅ All code is in **same partial class** (`WageTypeValueFunction`)
- ✅ Functions are **statically compiled**, not dynamically resolved
- ✅ Compiled binary is **cached** for performance

---

## 13. Troubleshooting

### Function Not Found

**Problem**: `gross_salary()` not found during execution

**Causes**:
1. Script not imported (check Script table)
2. Script has wrong FunctionType (should be `Payroll` or `All`)
3. WageType not recompiled after Script import
4. Script.Value is empty or invalid

**Solution**:
- Rebuild regulation: `RegulationRebuild`
- Or update WageType to trigger recompilation

### Compilation Errors

**Problem**: Compilation fails when saving WageType

**Causes**:
1. Syntax error in Rules.cs
2. Missing dependencies in Rules.cs
3. Function signature mismatch

**Solution**:
- Check Script.Value for syntax errors
- Validate Rules.cs compiles standalone
- Check compilation error messages

---

*Document Generated: 2025-01-05*  
*Based on analysis of: ScriptCompiler, FunctionHost, WageTypeValueRuntime, and Script import process*

