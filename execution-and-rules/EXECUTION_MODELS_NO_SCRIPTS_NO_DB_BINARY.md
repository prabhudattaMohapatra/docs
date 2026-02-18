# Execution Models Without Scripts or DB-Stored Binary

Regulation logic can run without storing C# (or any code) in the DB and without loading or executing binary from the DB. Below are alternative **execution models**: what you store, where logic lives, and how the payrun engine uses it.

---

## 1. Constraints (What You Want to Avoid)

- **No Roslyn** – no runtime C# compilation.
- **No scripts in DB** – no `Script.Value` (source) or script rows holding code.
- **No executing binary from DB** – no `Script.Binary` or loading assemblies from DB/blob for regulation.

So: **no code and no compiled assemblies** stored or executed from the database. Regulation behaviour is achieved by other means.

---

## 2. Alternative Execution Models

### 2.1 Rules-as-Data + In-Process Rules Engine

**Idea**: Regulation is **data** (rules, conditions, actions) stored in the DB or in config. A **generic rules engine** (built into the backend) evaluates that data at payrun time. No custom code in DB; no binary.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Rule definitions: e.g. rule id, type (wage type, collector, case), conditions (JSON/YAML), actions (set value, lookup, formula reference). No `Script` table for code. |
| **Execution** | PayrunProcessor calls a **RulesEngine** (e.g. forward chaining or decision-table evaluator). Engine reads rule set for the regulation, applies facts (employee, period, case values, wage types), returns results. |
| **Logic location** | In the **engine implementation** (C#/Java): how to interpret conditions and actions. Country-specific behaviour = different rule **data**, not different code. |
| **Pros** | No code in DB; auditable, versionable rule data; same engine for all countries; no compile or load. |
| **Cons** | Complex logic can be verbose or limited; need a rich enough rule model (formulas, lookups, thresholds). |

**Example**: Wage type “Gross” = rule: “if case X present then base × rate; else lookup Y”. Stored as JSON; engine evaluates.

---

### 2.2 Packaged Regulation as Library (Reference Only, No DB Storage)

**Idea**: Regulation is implemented as a **normal library** (e.g. .NET DLL or Java JAR) shipped with the app or loaded from a fixed path/package. DB stores only a **reference** (e.g. “France”, “France.v2”); **no script source, no binary** in DB.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Regulation metadata only: identifier, name, **regulation package id** (e.g. `France.v2` or `PayrollEngine.Regulation.France`). No Script table for this regulation; no Binary. |
| **Execution** | Backend has a **registry**: package id → type or factory. PayrunProcessor resolves “France.v2” to a class (e.g. `FranceRegulationCalculator`), instantiates it, calls a **fixed interface** (e.g. `EvaluateWageType(...)`, `EvaluateCollector(...)`). Logic lives in the compiled library. |
| **Logic location** | In the **regulation assembly** (built and deployed with the app or loaded from a known location). Not in DB; not compiled at runtime. |
| **Pros** | Full language (C#/Java); no runtime compile; no code/binary in DB; versioning via package/deploy. |
| **Cons** | New country or change = new build/deploy (or new package version); backend must know how to load and call the package. |

**Example**: Payrun says regulation = “France”. Backend loads `FranceRegulation` from a known DLL/JAR, calls `GetWageTypeResult(context)`.

---

### 2.3 External Regulation Service (API / Serverless)

**Idea**: Regulation logic runs in a **separate service** (or serverless functions). Backend does **not** store or execute any script or binary; it only calls the service with context and uses the returned results.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Regulation metadata: identifier, **service endpoint** or **function ARN** (e.g. `https://regulation-api/tenant/FR/evaluate` or Lambda ARN). No Script, no Binary. |
| **Execution** | PayrunProcessor, for each regulation scope (wage type, collector, etc.), calls the **regulation service** with payload (tenant, regulation, employee, period, case values, wage type id, etc.). Service returns result (value, type, tags). Backend persists results and continues. |
| **Logic location** | In the **external service** (any language, any storage). DB only stores “where to call”. |
| **Pros** | No code or binary in payroll DB; regulation can be updated independently; polyglot (e.g. Python, Go). |
| **Cons** | Latency and availability; versioning and contract (payload/response); security and auth. |

**Example**: Backend POSTs `{ employeeId, wageTypeNumber, period, caseValues }` to regulation service; gets back `{ value: 1234.56, ... }`.

**Detailed design**: See **[EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md](./EXECUTION_MODEL_EXTERNAL_REGULATION_SERVICE.md)** for architecture, contract (per operation and batch), discovery, protocols, deployment, versioning, errors, security, and a minimal “evaluate employee” example.

---

### 2.4 Expression / Formula DSL (Interpreted at Runtime)

**Idea**: Regulation “logic” is **expressions** in a small, safe language (e.g. a formula DSL or JSON-based expressions). Stored as **data** (e.g. in Regulation / WageType / Collector rows). An **interpreter** in the backend evaluates them at payrun time. No C# scripts; no stored binary.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Expression **text** or **structure**: e.g. `"BaseWage * Rate"`, or `{ "op": "multiply", "left": "BaseWage", "right": "Rate" }`. Stored in wage type, collector, or rule rows—as **data**, not as “Script” code. |
| **Execution** | Backend has an **expression interpreter** (e.g. parser + AST evaluator, or a small VM). PayrunProcessor passes context (variables, case values, lookups); interpreter returns value. No compile to C#; no assembly load. |
| **Logic location** | In the **interpreter** (what operators and functions exist) plus the **expression data** in DB. |
| **Pros** | No Roslyn; no binary; expressions are data, auditable; can restrict to safe operations. |
| **Cons** | Limited compared to full C#; complex rules may need many expressions or a richer DSL. |

**Example**: Wage type has `Expression = "CaseValue('BaseSalary') * 0.2"`. Interpreter evaluates with current case values and returns the result.

---

### 2.5 Decision Tables / Lookup Flows (Pure Data)

**Idea**: All behaviour is **tables and config**: thresholds, brackets, lookups, formulas defined as data. Engine “interprets” these structures; no code, no binary.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Tables: e.g. wage type → sequence of steps; each step = “lookup table X with key Y”, “apply formula Z”, “clip to range”. Collectors/cases similar: decision tables, lookup sets, formula references. |
| **Execution** | PayrunProcessor runs a **pipeline** or **state machine**: load steps for the wage type/collector, resolve lookups, evaluate formulas (via a small expression evaluator or fixed formula set), aggregate. No script execution. |
| **Logic location** | In the **engine** (how to run the pipeline) and the **table/formula data** in DB. |
| **Pros** | No code in DB; easy to audit and change data; no compile or load. |
| **Cons** | Very complex or country-specific flows may need many tables or a more expressive data model. |

**Example**: “Social security” wage type: steps = [ lookup bracket by gross, get rate, multiply, round ]. All step definitions and lookup data in DB.

---

### 2.6 Hybrid: Metadata in DB, Logic in Library or Service

**Idea**: DB holds **only metadata and references** (which regulation, which package or endpoint, parameters). Actual logic lives in **libraries** (2.2) or **external service** (2.3). No script rows; no binary in DB.

| Aspect | Detail |
|--------|--------|
| **Stored in DB** | Regulation id, name, **provider type** (“library” | “service”), **provider id** (package name or URL). Wage types/collectors/cases: structure and **references** (e.g. “use function GrossFromBase”), not code. |
| **Execution** | Backend resolves provider: if library, call in-process (2.2); if service, call API (2.3). No storage or execution of script/binary in DB. |
| **Logic location** | In the referenced **library** or **service**. |
| **Pros** | Single “regulation contract” in DB; can mix library (fast, controlled) and service (flexible, independent). |
| **Cons** | Two integration patterns to maintain. |

---

## 3. Comparison (No Scripts, No DB Binary)

| Model | Stored in DB | Where logic runs | Roslyn / compile | Binary in DB |
|-------|----------------|------------------|------------------|--------------|
| **Rules-as-data** | Rule definitions (data) | In-process rules engine | No | No |
| **Packaged library** | Regulation id + package ref | In-process, from DLL/JAR | No (pre-built) | No |
| **External service** | Regulation id + endpoint/ARN | External API / serverless | No | No |
| **Expression DSL** | Expression text/structure (data) | In-process interpreter | No | No |
| **Decision tables** | Tables, steps, formulas (data) | In-process pipeline | No | No |
| **Hybrid** | Metadata + provider ref | Library or service | No | No |

All of these avoid: Roslyn, scripts in DB, and executing binary from DB.

---

## 4. Choosing a Direction

- **Maximum control, no code in DB**: **Rules-as-data** or **decision tables** + a strong expression/formula layer.
- **Full programming power, no DB code/binary**: **Packaged regulation library** (reference only in DB) or **external regulation service**.
- **Incremental move away from scripts**: Introduce **expression DSL** for wage type/collector “formulas”; keep complex hooks in **library** or **service** until you fully replace them.

If you say which of these you prefer (e.g. “library only” or “rules-as-data”), the next step is to sketch the **contract** (what the payrun engine sends and expects) and how it plugs into the current PayrunProcessor flow.
