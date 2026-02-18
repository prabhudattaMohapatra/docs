# Runtime Rules and Scripting in the .NET Payroll Engine — Detailed Guide with France Examples

This document describes **in detail** how runtime rules and scripting work in the existing .NET payroll engine, using concrete examples from **payroll-engine-regulation-France** and the **Rules** (DSL) output. It is a companion to `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md`, which also covers Java/TypeScript options and alternatives.

**Repos referenced**: `payroll-engine-backend`, `payroll-engine-client-scripting`, `payroll-engine-regulation-France` (Regulation/, Scripts/, DSLOutput/FR/Rules/).

---

## 1. What “Runtime Rules / Scripting” Means

Regulation logic (wage type values, collector start/apply/end, payrun/employee lifecycle) is implemented as **C# code** that is:

1. **Stored** as source in the database when Rules.json (or similar) is imported—script source goes into the **Script** table (Value column).
2. **Compiled** during **import** when JSONs that reference those scripts (e.g. WageTypes.json with valueExpression) are imported—the backend compiles with Roslyn and stores **binaries** on the script objects (WageType.Binary, Collector.Binary, etc.). **No compilation at payrun time.**
3. **Loaded** at payrun time from the database or AssemblyCache (binary only).
4. **Invoked** at payrun time via reflection (e.g. `GetValue()`, `CollectorStart()`).

So: **runtime rules/scripting** = “the engine compiles user C# at **import** time, loads binary and runs it at **payrun** time.”

---

## 2. Two Ways Rules Appear in France: “Rules” (DSL) vs Regulation Scripts

The France regulation illustrates **two patterns** that both end up as compiled C#:

### 2.1 Pattern A: Rules (DSL output) — one script + many wage type expressions

Used when regulation is generated from a DSL (e.g. **DSLOutput/FR/Rules/**):

- **One regulation script**: `FR.Rules` with **valueFile** `Rules.cs`.
- **Rules.cs** is a single C# file: a **partial class** extending `WageTypeValueFunction` and containing **one method per wage type** (e.g. `gross_salary()`, `social_security_ceiling()`, `health_insurance_low_salary()`).
- **Each wage type** has a **valueExpression** that is a one-liner calling that method: e.g. `"return social_security_ceiling();"`.

**Example — Rules.json** (`payroll-engine-regulation-France/DSLOutput/FR/Rules/Rules.json`):

```json
{
  "tenants": [{
    "identifier": "FR.Tenant",
    "regulations": [{
      "name": "FR.Regulation",
      "scripts": [{
        "name": "FR.Rules",
        "functionTypes": ["Payroll"],
        "valueFile": "Rules.cs"
      }]
    }]
  }]
}
```

**Example — WageTypes.json** (same folder): wage types with **valueExpression** pointing into Rules.cs:

```json
{
  "wageTypes": [
    {
      "wageTypeNumber": 1000,
      "name": "gross_salary",
      "valueType": "Money",
      "valueExpression": "return gross_salary();",
      "collectors": ["FR.total_gross_pay"]
    },
    {
      "wageTypeNumber": 1500,
      "name": "social_security_ceiling",
      "valueType": "Money",
      "valueExpression": "return social_security_ceiling();"
    },
    {
      "wageTypeNumber": 3000,
      "name": "health_insurance_low_salary",
      "valueType": "Money",
      "valueExpression": "return health_insurance_low_salary();",
      "collectors": ["FR.total_employer_contributions", "FR.total_employer_urssaf_contributions"]
    }
  ]
}
```

**Example — Rules.cs** (excerpt): methods that implement each wage type; they use the **runtime API** (`get_field_value`, `set_value`, `get_slab`, etc.):

```csharp
/* WageTypeValueFunction Rules (auto-generated) */
using System;
using System.Linq;
using System.Collections.Generic;
using PayrollEngine.Client.Scripting;

namespace PayrollEngine.Client.Scripting.Function;

public partial class WageTypeValueFunction
{
  public string Namespace => "FR";

  public Decimal gross_salary()
  {
    set_value("employee.contract_type", "permanent");
    set_value("employee.job_title", "executive");
    set_value("company.headcount", 100);
    if (get_field_value("employee.base_monthly_salary") > 0)
    {
      set_value("taxable_transport_allowance", max(0, get_field_value("employee.transport_allowance")));
      // ...
      set_value("employee_gross_salary", get_field_value("employee.gross_salary") + get_field_value("monthly_signing_bonus"));
    }
    return get_field_value("employee_gross_salary");
  }

  public Decimal social_security_ceiling()
  {
    if (get_field_value("employee_gross_salary") > 0)
    {
      set_value("monthly_ss_ceiling", get_field_value("employee.monthly_ss_ceiling"));
    }
    return get_field_value("monthly_ss_ceiling");
  }

  public Decimal health_insurance_low_salary()
  {
    if (get_field_value("employee_gross_salary") > 0 &&
        get_field_value("employee.contract_type") != "apprentice" &&
        get_field_value("employee_gross_salary") <= get_slab("Constants2025", "monthly_smic_2025") * 2.25)
    {
      set_value("health_contribution_employer", get_field_value("employee_gross_salary") * 0.07);
      set_value("health_contribution_employee", 0);
    }
    return get_field_value("health_contribution_employer");
  }

  // ... more methods (complementary_health_insurance_contribution, old_age_insurance_contributions, etc.)
}
```

So for **Pattern A**:

- **Wage type 1500** has `valueExpression = "return social_security_ceiling();"`.
- **FR.Rules** script stores the full **Rules.cs** (all methods).
- At **compile time**, the engine builds **one assembly** that contains:
  - The **WageTypeValueFunction** template with `#region Function` filled by the **valueExpression** for the current wage type (e.g. `return social_security_ceiling();`).
  - The **Rules.cs** content (so `social_security_ceiling()` and all other methods exist in the same assembly).
- At **runtime**, for wage type 1500, the engine invokes `GetValue()` on the compiled type; that executes `return social_security_ceiling();`, which calls the method defined in Rules.cs in the same assembly.

### 2.2 Pattern B: Regulation scripts (many scripts, composite objects)

Used in the “manual” France regulation (**Regulation/Scripts.json** + **Scripts/*.cs**):

- **Many scripts** per regulation: e.g. `FR.Employee`, `FR.Core`, `FR.Collectors`, `FR.WageTypes`, each with **valueFile** pointing to a .cs file (e.g. `Scripts/Employee.cs`, `Scripts/WageTypes.cs`).
- Each file is a **partial class** in namespace `FR` extending a base (e.g. `FunctionObjectBase`, or wage-type/collector-specific base). They define **composite objects** (Employee, Company, etc.) and shared helpers.
- Wage types or collectors can have **valueExpression** (inline) or **script** reference; the compiler merges the relevant script sources into the same assembly as the wage type/collector.

**Example — Regulation/Scripts.json** (excerpt):

```json
{
  "regulations": [{
    "name": "FR.Regulation",
    "scripts": [
      { "name": "FR.Employee", "functionTypes": ["Payroll"], "valueFile": "Scripts/Employee.cs" },
      { "name": "FR.Core", "functionTypes": ["Payroll"], "valueFile": "Scripts/Core.cs" },
      { "name": "FR.WageTypes", "functionTypes": ["Payroll"], "valueFile": "Scripts/WageTypes.cs" },
      { "name": "FR.Collectors", "functionTypes": ["Payroll"], "valueFile": "Scripts/Collectors.cs" }
    ]
  }]
}
```

**Example — Scripts/Employee.cs**: composite “employee” type used by the engine when evaluating payroll scripts:

```csharp
namespace FR;
public partial class Employee : FunctionObjectBase
{
    public Employee(PayrollFunction function) : base(function) { address = new(function); }
    public EmployeeAddress address { get; }
}
```

For **Pattern B**, payrun scripts (e.g. wage type value, collector start) are compiled together with the **regulation scripts** that the wage type/collector references (e.g. FR.WageTypes, FR.Employee), so the compiled class can call into those types/methods.

---

## 3. Where Script Content Lives (Storage and Ingestion)

### 3.1 Database and API

| What | Where | Example (France) |
|------|--------|-------------------|
| **Script source** | **[Script]** table, column **Value** (NVARCHAR(MAX)) | One row per script: Name = `FR.Rules` or `FR.Employee`, Value = full file content of Rules.cs or Employee.cs. |
| **valueFile** | Only at **import** time (client) | Console/ExchangeImport reads `valueFile` (e.g. `Rules.cs`, `Scripts/Employee.cs`), reads the file, sends content as script **Value** to the API. The path is **not** stored in the DB. |
| **Wage type valueExpression** | **WageType** row (or derived from exchange) | e.g. `ValueExpression = "return social_security_ceiling();"`. |
| **Precompiled binary** | **WageType**, **Collector**, **Case**, **Report**, **Payrun** tables (column **Binary**) | One assembly per “script object” (e.g. per wage type); written after first compile. |

So: **Script.Value** = C# source (e.g. full Rules.cs or Employee.cs). **WageType.ValueExpression** = small snippet (e.g. `return social_security_ceiling();`). **WageType.Binary** = compiled assembly for that wage type (template + expression + referenced scripts).

### 3.2 Ingestion path (France Rules example)

1. **DSL/build** produces `DSLOutput/FR/Rules/`: Rules.json, Rules.cs, WageTypes.json, Collectors.json, Lookups, etc.
2. **Console** runs e.g. **PayrollImportFromDsl** or **PayrollImport** with those files.
3. **Client** (ExchangeImport):
   - Reads Rules.json → sees script `FR.Rules` with `valueFile: "Rules.cs"`.
   - Reads **Rules.cs** from disk and sets script **Value** = that file’s contents.
   - Sends regulation + scripts to the backend API.
4. **Backend** persists:
   - **[Script]** row: Name = `FR.Rules`, FunctionTypeMask = Payroll, **Value** = full Rules.cs text.
   - **WageType** rows from WageTypes.json: WageTypeNumber, Name, **ValueExpression** (e.g. `return social_security_ceiling();`), Collectors, etc.
5. **Binary is filled at import**: When **WageTypes.json** (or other JSONs with valueExpression referencing Rules.cs) is imported, the backend’s persistence layer (ScriptTrackChildDomainRepository) compiles the scripts and stores Binary on each WageType/Collector. So compilation happens **at import**, not at payrun.

---

## 4. Compilation: From Expression + Rules to One Assembly

### 4.1 When compilation runs

Compilation runs when a **script object** (e.g. a **WageType**) is **created or updated** during **import**—e.g. when WageTypes.json is imported via PayrollImport. The **persistence layer** (ScriptTrackChildDomainRepository) calls **SetupBinaryAsync** in `CreateAsync` and `UpdateAsync`, which compiles the script and stores Binary on the script object. So compilation happens **at import time**, not when “preparing for execution” or at payrun.

So for France wage type 1500 (“social_security_ceiling”):

1. **At import** (when WageTypes.json is imported), the backend creates/updates **WageType** rows (including ValueExpression and reference to FR.Rules).
2. **ScriptTrackChildDomainRepository.CreateAsync/UpdateAsync** runs **SetupBinaryAsync** for each wage type.
3. Repository sees WageType has **ValueExpression** and (typically) a **Script** (FR.Rules); it calls **ScriptCompiler** with:
   - **scriptObject**: the WageType (implements IScriptObject).
   - **functionScripts**: map FunctionType → script string; for WageTypeValue, the string is the **ValueExpression**, e.g. `"return social_security_ceiling();"`.
   - **scripts**: list of **Script** rows to include (e.g. the FR.Rules row, so Rules.cs content).
   - **embeddedScriptNames**: base templates (e.g. WageTypeValueFunction.cs).

### 4.2 What ScriptCompiler does (conceptually)

**ScriptCompiler** (payroll-engine-backend Domain.Scripting):

1. **BuildObjectCodes**: Load **embedded C# templates** from **CodeFactory** (in payroll-engine-client-scripting). For wage type value, that includes the **WageTypeValueFunction** template, which looks like:

```csharp
public partial class WageTypeValueFunction : WageTypeFunction
{
    public object GetValue()
    {
        #region ActionInvoke
        #endregion

        #region Function
        #endregion

        return null;
    }
}
```

2. **BuildFunctionCodes**: For **WageTypeValue**, take that template and **inject** the wage type’s script into `#region Function`. The “function” for this wage type is `"return social_security_ceiling();"`. So the compiler replaces `#region Function` / `#endregion` with:

```csharp
#region Function
return social_security_ceiling();
#endregion
```

3. **Collect codes** to compile:
   - The modified **WageTypeValueFunction** (with injected expression).
   - All **Script** contents passed in — e.g. the full **Rules.cs** (partial class WageTypeValueFunction with `gross_salary()`, `social_security_ceiling()`, etc.).

4. **CSharpCompiler.CompileAssembly(codes)** (Roslyn):
   - Builds a single assembly from those C# sources (same namespace/partial class, so the injected `GetValue()` and the methods from Rules.cs end up in one type).
   - Returns **ScriptCompileResult** (full source string, **Binary** byte[]).

5. Repository stores **Binary** and **ScriptHash** on the **WageType** (and optionally persists them to the DB).

So the **compiled assembly** for wage type 1500 contains:

- A **WageTypeValueFunction** whose `GetValue()` is `return social_security_ceiling();`.
- The **Rules.cs** partial class that defines `social_security_ceiling()` and all other methods (`gross_salary()`, `health_insurance_low_salary()`, etc.). Those methods use the **runtime API** (`get_field_value`, `set_value`, `get_slab`, etc.) provided by the base **WageTypeFunction** / **PayrollFunction** and the **IRuntime** passed into the script.

### 4.3 Lookups and runtime context (France example)

Rules.cs uses **get_slab("Constants2025", "monthly_smic_2025")** and similar. The lookup data comes from regulation **lookups** (e.g. **Lookup.Constants2025.json** in DSLOutput/FR/Rules/):

```json
{
  "lookups": [{
    "name": "FR.Constants2025",
    "values": [
      { "key": "monthly_smic_2025", "value": "1801.80", "rangeValue": 0.0 },
      { "key": "monthly_ss_ceiling", "value": "3925", "rangeValue": 0.0 }
    ]
  }]
}
```

At **runtime**, the script’s base class (and **IRuntime**) provide:

- **get_field_value(name)** — case/employee/company fields and values set during the payrun (e.g. `employee_gross_salary`, `employee.contract_type`).
- **set_value(name, value)** — set case or intermediate values (e.g. `monthly_ss_ceiling`, `health_contribution_employer`).
- **get_slab(lookupName, key)** — lookup value (e.g. Constants2025, monthly_smic_2025).

So the **same** Rules.cs runs in a context where employee/company data and lookups are provided by the engine; the script only contains the regulation logic.

---

## 5. Loading and Caching the Assembly at Payrun Time

When the engine actually runs a wage type value:

1. **PayrunProcessor** runs **CalculateEmployeeAsync** for an employee.
2. For each **DerivedWageType**, it calls **PayrunProcessorRegulation.CalculateWageTypeValue**.
3. That uses **WageTypeScriptController.GetValue** → **WageTypeValueRuntime.EvaluateValue(wageType)**.
4. **WageTypeValueRuntime.EvaluateValue**:
   - **CreateScript(typeof(WageTypeValueFunction), wageType)**:
     - Resolves the **script object** (the wage type, with its Binary and ScriptHash and any script references).
     - **FunctionHost.GetObjectAssembly(typeof(WageTypeValueFunction), scriptObject)**:
       - **AssemblyCache** key = `(Type, scriptObject.ScriptHash)`.
       - If not cached: **ScriptProvider.GetBinaryAsync(context, scriptObject)** loads **Binary** from the DB (from the **WageType** row); **CollectibleAssemblyLoadContext.LoadFromBinary(binary)** loads the assembly; cache it.
     - **Activator.CreateInstance(type, runtime)** creates an instance of the compiled type, passing **IRuntime** (context: tenant, payroll, employee, case values, lookups, period, etc.).
   - **script.GetValue()** is called on that instance.
5. The compiled **GetValue()** runs `return social_security_ceiling();`, which executes the method from Rules.cs in the same assembly, using the runtime context (get_field_value, set_value, get_slab).
6. The result is converted to decimal and stored as **WageTypeResult**; collector apply and rest of the lifecycle follow.

So at **payrun time** there is **no compilation** for that wage type — only **load assembly from cache or DB** (binary) and **invoke** the method. Compilation happened at **import time** when the wage type (script object) was created/updated and Binary was stored.

---

## 6. End-to-End Example: Wage Type “social_security_ceiling” (France)

| Step | Where | What happens |
|------|--------|----------------|
| **1. Data** | WageTypes.json | Wage type 1500, name `social_security_ceiling`, **valueExpression** = `"return social_security_ceiling();"`. |
| **2. Data** | Rules.json + Rules.cs | Script **FR.Rules** with **Value** = full Rules.cs; Rules.cs defines **social_security_ceiling()** using get_field_value/set_value. |
| **3. Ingestion** | Console → API | Script FR.Rules and wage types (with valueExpression) are stored in DB (Script.Value, WageType rows). |
| **4. Compile** | **At import** (when WageTypes.json is imported) | ScriptCompiler builds one assembly: WageTypeValueFunction with `GetValue() { return social_security_ceiling(); }` + Rules.cs. Binary and ScriptHash stored on WageType. |
| **5. Payrun** | PayrunProcessor | For wage type 1500: load assembly (from cache or DB), create instance with IRuntime, call GetValue() → social_security_ceiling() runs → returns decimal (e.g. monthly SS ceiling). |
| **6. Result** | PayrollResult | The decimal is stored as the wage type result for that employee/period. |

---

## 7. Collectors and Other Lifecycle Scripts

The **same pattern** applies to:

- **CollectorStart**, **CollectorApply**, **CollectorEnd** — each can have an expression or script; the compiler injects that into the corresponding template (e.g. CollectorStartFunction) and includes referenced regulation scripts. At runtime the engine loads the assembly and invokes Start/Apply/End.
- **PayrunStart**, **PayrunEnd**, **EmployeeStart**, **EmployeeEnd** — same: template + expression/script + optional regulation scripts → one assembly per “script object”; invoked via reflection.

France **Collectors** (DSLOutput/FR/Rules/Collectors.json) define names and value types; any collector script/expression would be compiled and executed the same way as wage type value.

---

## 8. Summary: How Runtime Rules/Scripting Work in .NET (with France)

| Aspect | Detail |
|--------|--------|
| **Source of rules** | **(1)** WageType/Collector **valueExpression** (e.g. `return social_security_ceiling();`) and **(2)** regulation **Script** rows (e.g. FR.Rules = full Rules.cs) or many scripts (FR.Employee, FR.WageTypes, etc.). |
| **Storage** | Script **source** in **[Script].Value**; **valueExpression** on WageType/Collector; **Binary** and **ScriptHash** on **WageType**/Collector/Case/Report/Payrun after compile. |
| **Compilation** | **At import**: **ScriptCompiler** (backend) merges embedded template (e.g. WageTypeValueFunction) + injected expression + Script contents → **Roslyn** → one assembly per script object. Binary persisted on the entity (WageType/Collector). **Not at payrun.** |
| **Load** | **FunctionHost** / **AssemblyCache** keyed by (Type, ScriptHash); if missing, load Binary from DB via ScriptProvider, then load assembly and cache. |
| **Execute** | **ScriptController** (e.g. WageTypeValueRuntime) creates instance of compiled type with **IRuntime** (context), invokes e.g. **GetValue()**; that runs the injected expression, which can call methods from Rules.cs in the same assembly. |
| **France Rules example** | Wage type 1500 has valueExpression `return social_security_ceiling();`; FR.Rules script contains Rules.cs with method `social_security_ceiling()`; compiled together; at payrun, GetValue() runs and returns the SS ceiling value. |

This is the full picture of **runtime rules and scripting** in the .NET engine, with concrete examples from **payroll-engine-regulation-France** and the **Rules** (DSL) output.
