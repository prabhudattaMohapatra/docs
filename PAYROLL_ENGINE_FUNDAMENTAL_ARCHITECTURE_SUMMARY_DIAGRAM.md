# Payroll Engine Fundamental Architecture – Summary Diagram (Lucid/Mermaid)

Use this Mermaid code in Lucidchart: **Primary Toolbar → Diagram as code icon → + New Mermaid diagram** → paste below → Generate.

```mermaid
---
title: Payroll Engine – Core vs Dynamic Architecture
---
flowchart TB
    subgraph CORE["CONSTANT CORE"]
        direction TB
        subgraph REPOS["Repositories / libraries"]
            direction LR
            R1["payroll-engine-backend\n(Api, Domain, Persistence)"]
            R2["payroll-engine-core\npayroll-engine-client-core\npayroll-engine-client-scripting"]
        end
        subgraph DEFINES["Defines"]
            D1["Entity shapes (Tenant, Regulation, WageType, Collector, Script, Case, …)"]
            D2["Lifecycle order (PayrunStart → … → Collector End → PayrunEnd)"]
            D3["Script invocation contract (context, signatures)"]
            D4["Persistence schema (tables, FKs)"]
            D5["REST API"]
        end
        subgraph NOT_DEFINES["Does not define"]
            N1["Which wage types/collectors exist"]
            N2["Formula logic"]
            N3["Lookup data"]
        end
        subgraph RUNTIME["At runtime (payrun)"]
            RT1["Loads regulation/wage type/collector/script rows from DB (written by import)"]
            RT2["Loads precompiled binaries (from DB/cache); no compilation at payrun"]
            RT3["Invokes script methods in fixed order; persists results"]
        end
    end

    subgraph DYNAMIC["DYNAMIC COMPONENTS"]
        direction TB
        subgraph SOURCE["Source"]
            S1["Regulation repos (e.g. France, India, Swiss)\nor any Exchange producer"]
        end
        subgraph CONTENT["Content"]
            C1["Regulation metadata (name, namespace, validity)"]
            C2["Wage types (number, name, value/result script refs, collector links)"]
            C3["Collectors (name, start/apply/end script refs)"]
            C4["Cases and case fields"]
            C5["Lookups and lookup values"]
            C6["Scripts (C# source: Rules.cs, wage type value, collector start/apply/end, …)"]
        end
        subgraph REACHES["Reaches core via"]
            RH1["PayrollImport / PayrollImportFromDsl → backend API → persistence"]
            RH2["Stored in same DB as core data; no backend redeploy to add/change regulation"]
        end
    end

    CORE -->|"Import (Exchange → API → DB; scripts compiled into binaries when WageTypes etc. imported)"| DYNAMIC
    DYNAMIC -->|"Execution (DB → load binary + run; no compile at payrun)"| CORE
```

---

## Compact variant (single boxes, less detail)

If the full diagram is too dense, use this simplified version:

```mermaid
---
title: Payroll Engine – Core vs Dynamic (compact)
---
flowchart TB
    subgraph CORE["CONSTANT CORE"]
        A["payroll-engine-backend + payroll-engine-core, client-core, client-scripting"]
        B["Defines: entity shapes, lifecycle order, script contract, schema, API"]
        C["Does not define: which wage types/collectors, formula logic, lookup data"]
        D["Runtime: loads precompiled binaries from DB/cache, invokes in order, persists results"]
    end

    subgraph DYNAMIC["DYNAMIC COMPONENTS"]
        E["Regulation repos (France, India, Swiss) or Exchange producer"]
        F["Content: regulation metadata, wage types, collectors, cases, lookups, scripts (C#)"]
        G["Via: PayrollImport / PayrollImportFromDsl → API → persistence; no redeploy"]
    end

    CORE <-->|"Import (Exchange → DB; compile at import)  |  Execution (DB → load binary + run; no compile at payrun)"| DYNAMIC
```

---

**One sentence** (from doc): The **core** is the generic payroll execution engine (shapes, lifecycle, persistence, API); the **dynamic** part is the regulation metadata and script source that are ingested and stored. Scripts are **compiled at import** (when WageTypes etc. are imported); at **payrun time** only precompiled binaries are loaded and executed—so the engine stays constant while country and tenant logic change freely, with no compilation on the payrun path.
