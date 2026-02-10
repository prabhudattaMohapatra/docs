# Payroll Engine: Core vs Dynamic Architecture

Short summary of how the system is split into a **constant core** (backend + libraries) and **dynamic components** (regulations, scripts) that are ingested or plugged in and can change without changing the core.

---

## Constant core (fixed, common for all use cases)

**Location**: `payroll-engine-backend` and its dependency libraries (`payroll-engine-core`, `payroll-engine-client-core`, `payroll-engine-client-scripting`, etc.).

**What it does**:

- **Orchestration**: Runs payruns (PayrunProcessor), resolves employees, periods, calendars, case value caches.
- **Execution pipeline**: Fixed order of lifecycle (PayrunStart → EmployeeStart → collector start → wage type value → collector apply → collector end → EmployeeEnd → PayrunEnd).
- **Script runtime**: Compiles regulation scripts at **import** (when WageTypes etc. that reference scripts are saved); at **payrun** only loads precompiled binaries and executes (Roslyn, FunctionHost, script controllers). The *mechanism* is fixed; the *content* is not.
- **Persistence**: Stores and loads tenants, payrolls, regulations, scripts, case values, payrun jobs, results. Schema and repositories are part of the core.
- **API**: REST endpoints for tenants, regulations, payrolls, payruns, employees, case values, results, etc.
- **Domain model**: Cases, wage types, collectors, regulations, payrun jobs, results — the *shapes* and *relationships* are defined in the core; the *actual* wage types and collectors come from regulations.

**Important**: The core does **not** hard-code country logic. It provides the engine: “run these scripts in this order, with this context, and persist these results.” It stays the same whether the regulation is France, India, Switzerland, or a custom tenant pack.

---

## Dynamic components (ingested or plugged in, changeable)

**Location**: Regulation repos (e.g. `payroll-engine-regulation-France`, `payroll-engine-regulations-INDIA`, `payroll-engine-regulations-swiss`, `payroll-engine-rules`), or any Exchange JSON/zip produced by DSL or hand.

**What they are**:

- **Regulation metadata**: Wage types, collectors, cases, lookups, report definitions — JSON (or equivalent) that describes *what* exists for a country or tenant.
- **Scripts**: C# (or other) code for WageTypeValue, CollectorStart/Apply/End, PayrunStart/End, EmployeeStart/End, case availability, etc. Stored as source (e.g. in DB `Script.Value`) and compiled at runtime by the core.
- **Lookup data**: Tables and values (e.g. tax rates, insurance codes) referenced by scripts.

**How they reach the core**:

- **Import**: Console (or API client) runs **PayrollImport** / **PayrollImportFromDsl** with Exchange JSON/zip. Backend API receives and persists regulations and scripts. No backend redeploy.
- **Update**: Re-import updated regulation JSON and scripts; core compiles and runs the new version on the next payrun. Backend stays constant.

So the **dynamic** part is the *content* of regulations and scripts; the **core** is the *engine* that loads, compiles, and runs that content.

---

## Summary diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│  CONSTANT CORE (payroll-engine-backend + libraries)                     │
│  • PayrunProcessor, script compilation, persistence, API                 │
│  • Same for every tenant and every country                               │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                │  ingests / loads at runtime
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  DYNAMIC COMPONENTS (regulations, scripts)                               │
│  • Regulation JSON (wage types, collectors, cases, lookups)             │
│  • Scripts (C# etc.) — stored in DB or referenced; compiled on demand    │
│  • Can be updated or added via import without changing the core          │
└─────────────────────────────────────────────────────────────────────────┘
```

**One sentence**: The core is a **generic payroll execution engine**; country and tenant specifics live in **regulation data and scripts** that are ingested or plugged in and can change while the backend remains constant.
