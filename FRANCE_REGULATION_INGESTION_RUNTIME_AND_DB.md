# France Regulation Example: Ingestion, Runtime Scripting, Compiling, Execution & DB Storage

This document uses **payroll-engine-regulation-France** as a concrete example to explain how ingestion, runtime scripting, compiling, and execution work, and what ends up stored in the database.

Related: `PAYRUN_PROCESSOR_DETAILS.md`, `RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md`, `PAYROLL_ENGINE_END_TO_END_LEARNING_PATHWAY.md`.

---

## 1. What the France Regulation Repo Contains

The France regulation is a **regulation template**: JSON exchange files plus C# script files.

| Item | Location | Purpose |
|------|----------|---------|
| **Regulation.json** | `Regulation/Regulation.json` | Tenant, calendar, division, regulation **FR.Regulation**, payroll **FR.Payroll**, payrun **FR.Payrun**. |
| **Scripts.json** | `Regulation/Scripts.json` | List of regulation scripts: name, `functionTypes`, **valueFile** (path to .cs file). |
| **Collectors.json** | `Regulation/Collectors.json` | Collectors (e.g. **FR.Income**, **FR.Deduction**). |
| **WageTypes.json**, **Lookups.json**, **CompanyCases.json**, **EmployeeCases.json**, etc. | `Regulation/*.json` | Wage types, lookups, case definitions. |
| **C# scripts** | `Scripts/*.cs` | Source for each script; e.g. **FR.Employee** → `Scripts/Employee.cs`. |

**Example from Scripts.json** (one script entry):

```json
{
  "name": "FR.Employee",
  "functionTypes": ["Payroll"],
  "valueFile": "Scripts/Employee.cs"
}
```

**Example script file** (`Scripts/Employee.cs`): C# partial class in namespace `FR`, extending `FunctionObjectBase`, used as a composite “employee” function at runtime.

Setup is done via **.pecmd** scripts (e.g. `Setup.Scripts.pecmd`) that run **PayrollImport** with the exchange JSON:

```text
PayrollImport Regulation/Scripts.json
```

---

## 2. Ingestion: From Repo to Database

### 2.1 Flow

1. **Console** runs `PayrollImport Regulation/Scripts.json` (or other exchange files).
2. **PayrollImportCommand** (`payroll-engine-console`):
   - Reads the JSON with **ExchangeReader.ReadAsync(fileName, namespace)**.
   - Builds an **Exchange** object (tenants → regulations → scripts, etc.).
   - Uses **ScriptParser** and **ExchangeImportOptions**.
   - Calls **ExchangeImport** (from `PayrollEngine.Client.Exchange`) to push data to the backend.
3. **ExchangeImport** (in **payroll-engine-client-core**):
   - Walks the exchange model (tenant → regulation → scripts).
   - For each script in the JSON that has **valueFile**, the **script value** is resolved: the client reads the file (e.g. `Scripts/Employee.cs`) and sets the script’s **Value** to that file’s contents (C# source).
   - Sends HTTP requests to the backend API (e.g. `POST/PUT` tenants, regulations, **regulation scripts**).
4. **Backend API** receives regulation and script payloads and persists them via repositories.

So: **valueFile** is resolved **at import time on the client**. The backend never sees the path; it only receives and stores **script name, function types, and Value (full C# source)**.

### 2.2 What Gets Ingested for France

- **Tenant**: e.g. FR.Tenant (from Regulation.json).
- **Regulation**: FR.Regulation (shared regulation).
- **Payroll**: FR.Payroll, linked to FR.Regulation.
- **Payrun**: FR.Payrun.
- **Scripts**: One row per script in Scripts.json (e.g. FR.Employee, FR.Core, FR.Collectors, FR.WageTypes, FR.Case, …). Each row has **Name**, **FunctionTypeMask**, **Value** = contents of the corresponding .cs file.
- **Collectors**: FR.Income, FR.Deduction (from Collectors.json).
- **Wage types, lookups, cases**, etc., from their respective JSON files.

---

## 3. Runtime Scripting: What Runs at Payrun Time

Regulation logic runs as **C# code** that was stored in the DB at ingestion. At payrun time:

1. The backend loads **script metadata and source** (and optionally precompiled **binary**) for the payroll’s derived regulation(s).
2. If there is no (or stale) binary, it **compiles** the C# source (Roslyn) into an in-memory assembly.
3. It **caches** the assembly (or uses stored binary) keyed by script version/hash.
4. It **invokes** lifecycle methods (PayrunStart, EmployeeStart, WageTypeValue, CollectorStart/End, etc.) via reflection on the compiled types.

So: **runtime scripting** = “compile and run C# stored in the DB at payrun time.”

---

## 4. Compiling: When and How

- **When**: When a **script object** is built for execution (e.g. when loading **DerivedWageType** or **DerivedCollector** for a payrun) and the object has script content but **no Binary** (or binary is stale). The persistence layer (e.g. **ScriptTrackChildDomainRepository** / **ScriptChildDomainRepository**) calls **ScriptCompiler** and then stores **Binary** and **ScriptHash** on the **script object** (e.g. WageType, Collector). Regulation-level **Script** rows store **Value** (source) only; **Binary** and **ScriptHash** live on the **track objects** (WageType, Collector, etc.) that are built for a given payroll/regulation date.
- **How**:
  1. **BuildObjectCodes**: Embedded C# templates (e.g. WageTypeValueFunction, CollectorStartFunction) from **CodeFactory** (PayrollEngine.Client.Scripting).
  2. **BuildActionResults**: Action code from wage type/collector metadata.
  3. **BuildFunctionCodes**: For each function type, inject user expression or **Script.Value** (e.g. FR.Employee, FR.WageTypes) into the template’s `#region Function`.
  4. **Collect**: Object templates + function codes + any **Script** contents (e.g. FR.Core, FR.Employee) as full C# files.
  5. **CSharpCompiler.CompileAssembly(codes)**: Roslyn compiles all code into one in-memory DLL; result is **ScriptCompileResult** (source string, **Binary** byte[]).
- The repository sets the script object’s **Binary** and **ScriptHash** and can persist them so the next run can load from binary without recompiling.

So for France: when the payrun builds **DerivedWageType** / **DerivedCollector** for FR.Regulation, the compiler merges embedded templates + regulation scripts (e.g. FR.Employee, FR.WageTypes, FR.Collectors) into one assembly per “script object” (e.g. per wage type or collector).

---

## 5. Execution: Payrun Flow with France

1. **PayrunJobWorkerService** dequeues a job and calls **PayrunProcessor.Process(jobInvocation)**.
2. **PayrunProcessor**:
   - Loads payrun, payroll, division, **derived regulations** (including FR.Regulation for the payroll/period).
   - Builds **PayrunContext** (calculator, period, case values, lookups, etc.).
   - Creates **PayrunProcessorRegulation** and loads **DerivedWageTypes** and **DerivedCollectors** (for FR.Payroll / period).
3. **PayrunStart(context)** — if the payrun has a start script, it is executed (compiled + invoked).
4. For each **employee**:
   - **EmployeeStart** — e.g. **FR.Employee** composite and related scripts run if configured.
   - **CalculateEmployeeAsync**: for each **DerivedWageType**, **PayrunProcessorRegulation.CalculateWageTypeValue** runs the wage type value script (template + regulation script); for each **DerivedCollector**, CollectorStart → wage type loop → collector apply → CollectorEnd (all script-driven).
   - **EmployeeEnd**.
5. **PayrunEnd(context)**.
6. Results are written to **PayrollResult** (and related) tables.

So: France’s **FR.Employee**, **FR.WageTypes**, **FR.Collectors**, etc., are loaded as script **Value** from the DB, compiled (if needed) with the templates, and then invoked by the **ScriptController** / **FunctionHost** during the payrun.

---

## 6. What Is Stored in the Database (Examples)

### 6.1 Regulation & Scripts (from France ingestion)

- **Tenant**: Id, Identifier (e.g. FR.Tenant), Culture, Calendar, etc.
- **Regulation**: Id, TenantId, Name (e.g. FR.Regulation), SharedRegulation, etc.
- **Script** (one row per regulation script):
  - **RegulationId**, **Name** (e.g. FR.Employee, FR.Core, FR.Collectors), **FunctionTypeMask**, **Value** (full C# source of the .cs file), **OverrideType**.  
  - The **Script** table does **not** store Binary in the core domain model; **Binary** and **ScriptHash** are on **script-track** entities (e.g. WageType, Collector) when they are built for execution.

### 6.2 Payroll / Payrun

- **Payroll**: Id, TenantId, Name (FR.Payroll), DivisionId, CalendarName, layers (regulation name, level, priority).
- **Payrun**: Id, PayrollId, Name (FR.Payrun), DefaultReason.

### 6.3 Collectors & Wage Types (from France)

- **Collector**: RegulationId, Name (e.g. FR.Income, FR.Deduction), Negated, plus any script/expression references. When loaded as **DerivedCollector**, the engine may attach **Binary** and **ScriptHash** after compilation.
- **WageType**: RegulationId, Name, numeric identifier, value expression or script reference; **DerivedWageType** can carry **Binary** and **ScriptHash**.

### 6.4 Payrun Job & Results (at execution time)

- **PayrunJob**: TenantId, PayrunId, PeriodStart/End, CycleName, JobStatus, TotalEmployeeCount, ProcessedEmployeeCount, JobStart, Message, etc.
- **PayrollResult** / **PayrollConsolidatedResult**: PayrunJobId, EmployeeId, wage type results, collector results, custom result sets (retrieved e.g. via **GetPayrollResultValues**, **GetWageTypeResults**, **GetCollectorResults**). These store the **output** of the scripts (amounts, counts, tags, etc.), not the script source.

### 6.5 Example Rows (conceptual)

**Script (after France Scripts import):**

| Id | RegulationId | Name        | FunctionTypeMask | Value                    |
|----|--------------|-------------|------------------|---------------------------|
| 1  | 10           | FR.Employee | 4 (Payroll)      | /* Employee */\nusing …  |

**PayrunJob (after a run):**

| Id | TenantId | PayrunId | PeriodStart | PeriodEnd | JobStatus | TotalEmployeeCount | ProcessedEmployeeCount |
|----|----------|----------|-------------|-----------|-----------|--------------------|------------------------|
| 1  | 1        | 1        | 2025-01-01  | 2025-01-31| Completed | 50                  | 50                     |

**PayrollResult** (simplified): PayrunJobId, EmployeeId, WageTypeNumber/CollectorName, Value, Start, End, Tags, etc.

---

## 7. Exact DB Format: Where Scripts vs Pre-compiled Binaries Are Stored

### 7.1 Script source only: `[Script]` table

**Table**: `dbo.[Script]` (SQL Server; equivalent in other DBs).

**Columns** (from `GetDerivedScripts.sql` and `ScriptRepository`):

| Column           | Type / format        | Content |
|------------------|----------------------|--------|
| Id               | INT                  | PK. |
| Status           | INT                  | 0 = Active. |
| Created          | DATETIME2(7)         | Row creation. |
| Updated          | DATETIME2(7)         | Row update. |
| RegulationId     | INT                  | FK to Regulation. |
| Name             | VARCHAR(128)          | Script name (e.g. FR.Employee, FR.Core). |
| FunctionTypeMask | BIGINT               | Bitmask of FunctionType (e.g. 4 = Payroll). |
| Value            | NVARCHAR(MAX)         | **Full C# source** (exact file contents). |
| OverrideType     | INT                  | Override behaviour. |

**No Binary or ScriptHash** on `Script`. Script **source** is stored only in **Value** (NVARCHAR(MAX)); one row per regulation script from Scripts.json.

**Example row** (France, after `PayrollImport Regulation/Scripts.json`):

- **Name**: `FR.Employee`
- **FunctionTypeMask**: `4` (Payroll)
- **Value**: exact contents of `Scripts/Employee.cs` (full C# text, including `/* Employee */`, `using …`, `namespace FR;`, class body, etc.)

---

### 7.2 Pre-compiled binaries: script-track tables (WageType, Collector, Case, Report, Payrun)

**Pre-compiled binaries** are stored only on **script-track** entities, not in `[Script]`.  
**ScriptProviderRepository** loads `Binary` from these tables: **Case**, **Collector**, **WageType**, **Report**, **Payrun** (and **CaseRelation** for CaseSet).

**Common columns** on each of these tables (from domain `ScriptTrackDomainObject` and repos):

| Column        | Type / format   | Content |
|---------------|-----------------|--------|
| Binary        | VARBINARY(MAX)  | **Pre-compiled .NET assembly** (byte[] from Roslyn). |
| ScriptHash    | INT             | Hash of compiled script source; cache key. |
| Script        | NVARCHAR(MAX)   | Full generated source (optional; e.g. DEBUG). |
| ScriptVersion | VARCHAR(128)    | Scripting version (e.g. "1.0"). |

**When Binary is written**: On **Create/Update** of a WageType, Collector, Case, Report, or Payrun, **ScriptTrackChildDomainRepository.SetupBinaryAsync** runs: it compiles (ScriptCompiler + Roslyn), then sets `Binary` and `ScriptHash` on the entity and persists them (e.g. **WageTypeRepository** passes `Binary` and `ScriptHash` to the DB).

**When Binary is read**: At payrun time, if the assembly is not in **AssemblyCache**, **ScriptProvider.GetBinaryAsync** loads `Binary` from the correct table by **Id** and **ScriptHash** (ScriptProviderRepository).

**Example tables** (from stored procedures):

- **dbo.[WageType]**: Id, RegulationId, Name, WageTypeNumber, ValueExpression, ResultExpression, ValueActions, ResultActions, **Script**, **ScriptVersion**, **Binary**, **ScriptHash**, Attributes, Clusters, …
- **dbo.[Collector]**: Id, RegulationId, Name, CollectMode, Negated, StartExpression, ApplyExpression, EndExpression, StartActions, ApplyActions, EndActions, **Script**, **ScriptVersion**, **Binary**, **ScriptHash**, …

Note: In **GetDerivedWageTypes** / **GetDerivedCollectors**, the **Binary** column is commented out in the SELECT (to avoid loading large blobs when only metadata is needed); the column still exists and is read by ScriptProvider when loading the assembly.

---

### 7.3 Summary: scripts vs binaries in DB

| Stored in DB        | Table(s)                         | Column(s)           | Format / content |
|---------------------|----------------------------------|---------------------|------------------|
| **Script source**   | **Script**                       | **Value**           | NVARCHAR(MAX): full C# file content. |
| **Pre-compiled DLL**| **WageType**, **Collector**, **Case**, **Report**, **Payrun** | **Binary**, **ScriptHash** | VARBINARY(MAX): assembly bytes; INT: hash. |

Regulation scripts (e.g. FR.Employee, FR.WageTypes) live as **source in [Script].Value**. The **compiled** result (one assembly per script **object**, e.g. per wage type or collector) is stored in that object’s table as **Binary** + **ScriptHash**.

---

## 8. Mapping: payroll-engine-regulation-France Repo → DB

### 8.1 Repo layout (relevant to scripts)

```
payroll-engine-regulation-France/
├── Regulation/
│   ├── Regulation.json      → Tenant, Regulation, Payroll, Payrun (names, links)
│   ├── Scripts.json         → One script entry per regulation script (name, functionTypes, valueFile)
│   ├── Collectors.json      → Collector definitions (FR.Income, FR.Deduction)
│   ├── WageTypes.json       → Wage type definitions
│   └── …
└── Scripts/
    ├── Employee.cs          → Source for script name "FR.Employee"
    ├── Core.cs              → Source for "FR.Core"
    ├── Collectors.cs        → Source for "FR.Collectors"
    ├── WageTypes.cs         → Source for "FR.WageTypes"
    ├── Composite/
    │   ├── Payroll.cs       → "FR.Payroll"
    │   ├── Case.cs          → "FR.Case"
    │   ├── WageTypeValue.cs → "FR.WageTypeValue"
    │   └── …
    └── Register/
        └── …
```

### 8.2 Scripts.json → [Script] table

Each **script** object inside `Regulation/Scripts.json` under `tenants[].regulations[].scripts[]` becomes **one row** in **dbo.[Script]**.

| Repo (Scripts.json)     | DB ([Script])   | Content in DB |
|-------------------------|-----------------|---------------|
| `"name": "FR.Employee"`, `"valueFile": "Scripts/Employee.cs"` | Name = `FR.Employee`, FunctionTypeMask = Payroll | **Value** = full file content of `Scripts/Employee.cs` |
| `"name": "FR.Core"`, `"valueFile": "Scripts/Core.cs"`         | Name = `FR.Core`, FunctionTypeMask = Payroll     | **Value** = full file content of `Scripts/Core.cs` |
| `"name": "FR.Collectors"`, `"valueFile": "Scripts/Collectors.cs"` | Name = `FR.Collectors`, FunctionTypeMask = Payroll | **Value** = full file content of `Scripts/Collectors.cs` |
| `"name": "FR.WageTypes"`, `"valueFile": "Scripts/WageTypes.cs"`   | Name = `FR.WageTypes`, FunctionTypeMask = Payroll   | **Value** = full file content of `Scripts/WageTypes.cs` |
| … (all other script entries in Scripts.json) | One row per script | **Value** = contents of the file in **valueFile** |

**valueFile** is resolved at **import time** by the client (ExchangeImport): it reads the file and sends its contents as the script **Value**; the path is not stored in the DB.

### 8.3 Scripts/*.cs → [Script].Value only

| Repo file                | Script name (in JSON & DB) | Stored in DB as |
|--------------------------|----------------------------|------------------|
| Scripts/Employee.cs      | FR.Employee                | [Script].Value = entire file content (NVARCHAR(MAX)) |
| Scripts/Core.cs          | FR.Core                    | [Script].Value = entire file content |
| Scripts/Company.cs       | FR.Company                 | [Script].Value = entire file content |
| Scripts/Collectors.cs    | FR.Collectors              | [Script].Value = entire file content |
| Scripts/WageTypes.cs     | FR.WageTypes               | [Script].Value = entire file content |
| Scripts/Composite/Payroll.cs | FR.Payroll             | [Script].Value = entire file content |
| Scripts/Composite/Case.cs    | FR.Case                 | [Script].Value = entire file content |
| Scripts/Composite/WageTypeValue.cs | FR.WageTypeValue  | [Script].Value = entire file content |
| … (every valueFile in Scripts.json) | Corresponding Name | [Script].Value = that file’s content |

**Pre-compiled binaries** do **not** come from these files directly. They are produced when **WageType**, **Collector**, **Case**, **Report**, or **Payrun** rows are created/updated: the engine merges templates + regulation scripts (from [Script].Value) and compiles; the result is stored in **[WageType].Binary**, **[Collector].Binary**, etc., with **[WageType].ScriptHash**, **[Collector].ScriptHash**, etc.

### 8.4 Where pre-compiled binaries live (France)

| DB table     | Binary column | When populated | Relation to France repo |
|--------------|---------------|----------------|--------------------------|
| **WageType** | Binary, ScriptHash | Create/Update of a wage type (e.g. from WageTypes.json) that has expressions/scripts | Compilation includes regulation scripts (e.g. FR.WageTypes, FR.Employee) loaded from [Script].Value. |
| **Collector**| Binary, ScriptHash | Create/Update of a collector (e.g. FR.Income, FR.Deduction from Collectors.json) | Compilation includes FR.Collectors, FR.CollectorStart/Apply/End, etc., from [Script].Value. |
| **Case**     | Binary, ScriptHash | Create/Update of case/case set | Can use FR.Case, FR.CaseAvailable, etc. |
| **Report**   | Binary, ScriptHash | Create/Update of report | Report scripts. |
| **Payrun**   | Binary, ScriptHash | Create/Update of payrun (e.g. FR.Payrun) | PayrunStart/End, EmployeeStart/End; uses FR.Payroll, FR.PayrunStart, etc. |

So: **Script** table = France **Scripts.json** + **Scripts/*.cs** (source only). **Pre-compiled binaries** = in **WageType**, **Collector**, **Case**, **Report**, **Payrun** (one compiled assembly per entity, built from templates + those regulation scripts).

---

## 9. End-to-End Summary (France Example)

| Stage        | What happens |
|-------------|------------------------------------------------------------------------------------------------|
| **Ingestion** | Console runs `PayrollImport Regulation/Scripts.json` (and other exchange files). Client reads JSON + **valueFile** → file contents become script **Value**; API persists Tenant, Regulation, **Script** rows (Name, FunctionTypeMask, **Value**), Collectors, WageTypes, etc. |
| **Runtime**   | Payrun job runs; backend loads derived regulation (FR.Regulation), scripts (Value from **[Script]**), and builds script objects (e.g. DerivedWageType, DerivedCollector). |
| **Compiling** | For each script object without (or with stale) Binary: ScriptCompiler merges templates + regulation script sources → Roslyn → byte[] and ScriptHash; stored on the script object and persisted in **WageType** / **Collector** / **Case** / **Report** / **Payrun** (**Binary**, **ScriptHash**). |
| **Execution** | FunctionHost/AssemblyCache loads assembly by (Type, ScriptHash); ScriptProvider reads **Binary** from the script-track table; ScriptControllers create runtime instances and call PayrunStart, EmployeeStart, WageTypeValue, CollectorStart/End, EmployeeEnd, PayrunEnd. |
| **DB storage**| **Script**: **Value** only (C# source; one row per script in Scripts.json). **WageType**, **Collector**, **Case**, **Report**, **Payrun**: **Binary** (pre-compiled assembly) + **ScriptHash**. **PayrunJob**, **PayrollResult** (and related): job metadata and calculation results (amounts, tags, etc.). |

This ties the **payroll-engine-regulation-France** repo (JSON + C# files) to ingestion, runtime scripting, compiling, execution, exact DB format, and where scripts vs pre-compiled binaries are stored.
