# Core vs Dynamic Separation: Is It Necessary? Alternatives, Pros and Cons

This document is about the **architectural choice** of having a **core (constant engine) + dynamic (ingested/pluggable) content** at all. It is not about *how* to implement the dynamic part (e.g. Roslyn vs external service vs DSL). The question is: **Do we need this separation? What are the alternatives, and what are the trade-offs?**

---

## 1. What “Core vs Dynamic Separation” Means Here

**Separation** means:

- **Core**: A single, generic payroll execution engine (same codebase and deployable for every tenant and every country). It defines *how* payruns run: lifecycle, persistence, API, script contract. It contains **no** country-specific or tenant-specific rules.
- **Dynamic**: Country/tenant-specific content (regulations, wage types, collectors, scripts, lookups) that is **ingested or plugged in** after deploy—via import, config, or plugins. The engine loads and uses this content at runtime. Adding or changing a country does **not** require changing or redeploying the core.

So the separation is: **one reusable engine** vs **many possible “regulation contents”** that the engine interprets or executes. The alternative would be **not** having one generic engine and instead baking logic into the product in some other way.

---

## 2. Is the Separation Necessary?

**No.** It is a design choice, not a requirement. Many payroll systems work without it:

- **Monolithic products**: One codebase with all country logic in source (e.g. `if (country == France) ... else if (country == India) ...`). One deploy; “new country” = code change.
- **Separate products per country**: France product, India product—each its own codebase and deploy. No “one engine, many contents.”
- **Config-only**: One product; “regulation” is just configuration (tables, formulas) that the same code evaluates. There is no distinct “plug in new country” model—you add config, not “content” in the sense of ingestible regulation packs.

The separation is **useful** when you want:

- One engine to maintain and deploy while supporting many countries/tenants.
- New or changed regulations without redeploying the engine.
- Clear boundary between “platform” and “content” (e.g. for compliance, auditing, or multi-team ownership).

So: **necessary? No. Beneficial in many cases? Yes.** Below we compare this choice to alternatives.

---

## 3. Alternative Architectures (No Core/Dynamic Separation, or Different Split)

### 3.1 Monolithic: All country logic in one codebase (no ingested “content”)

**Description**: There is **one** payroll product. All country-specific logic lives **in the product’s source code** (same repo, same deploy). “Regulation” is not ingested data or scripts; it is branches, modules, or configuration flags in code. Adding a country means adding code (and possibly config) and redeploying.

**Example**:  
- Codebase has `FranceWageTypeCalculator`, `IndiaWageTypeCalculator`, `SwissWageTypeCalculator`. PayrunProcessor does `switch (payroll.RegulationCountry) { case "FR": return FranceCalculator.Evaluate(...); case "IN": ... }`.  
- Or: one big `WageTypeEngine` with `if (wageTypeNumber == 1000 && regulation == "FR") return CaseValue("ContractualMonthlyWage"); else if ...`.  
- New country (e.g. Germany) = new class or new branch + release + deploy. No “import France regulation”; France is just code.

**Pros**

- **Simple mental model**: Everything is in one place. No “where does this rule live—in the engine or in the data?”
- **Type safety and refactoring**: All logic in one language and one codebase; compiler and IDE help. No contract between “engine” and “ingested content.”
- **No ingestion pipeline**: No Exchange format, no import step, no “content” to version separately from the product.
- **Easier debugging**: Stack traces and logs are all in your codebase; no “script from DB” or external service.

**Cons**

- **Every country change = release**: Bug fix or rule change for France requires a product release and deploy. Cannot “update France” without touching the engine.
- **Codebase grows with every country**: Can become very large and hard to navigate (e.g. 50 countries = 50 modules or huge switch).
- **Tight coupling**: All countries share the same release cycle and the same bugs. One country’s regression can block others.
- **Ownership**: Hard to let “France team” own “France logic” independently if it’s all in the same repo and deploy.

---

### 3.2 Separate product (or deploy) per country

**Description**: There is **no** single generic engine. Instead, there is a **separate** payroll product (or deployable) per country (or per region). Each has its own codebase and/or deploy. “France engine” only knows France; “India engine” only knows India. No “ingest regulation into generic engine”—each product *is* the regulation.

**Example**:  
- `payroll-france` service (or app): Contains only French rules, French wage types, French tax logic. Deployed and scaled independently.  
- `payroll-india` service: Same idea for India.  
- A “router” or tenant config might send “tenant in France” to payroll-france and “tenant in India” to payroll-india. There is no shared “core” that runs both; there are two (or N) products.

**Pros**

- **Full independence**: France can be rewritten, upgraded, or scaled without touching India. No shared codebase to conflict over.
- **No generic engine**: You only build what each country needs; no abstraction cost for “any regulation.”
- **Clear ownership**: One team per product/country. No “core” team vs “regulation” team.

**Cons**

- **Duplication**: Common behaviour (e.g. payrun lifecycle, case values, persistence model) is duplicated or reimplemented per product unless you extract a shared library (which starts to look like a “core”).
- **N products to maintain**: Every bug fix or platform feature (e.g. security, observability) must be applied N times, or you end up with a shared “platform” layer—again, a form of core.
- **Operational cost**: N deployables, N pipelines, N runtimes. Hard to justify for many countries unless each is very different or owned by different orgs.

---

### 3.3 Config-only product (no “regulation” as separate content)

**Description**: There is **one** product. “Regulation” is not a first-class concept; it is just **configuration**—tables, formulas, rule definitions—that the product reads and evaluates. There is no “core engine + ingestible regulation pack.” The product and its config are deployed together; “new country” might mean “add config” (e.g. new rows, new files) but the *model* of “core vs dynamic” does not exist—it’s just “app + config.”

**Example**:  
- Product has a fixed schema: WageType table, Collector table, Formula table. Formula table has rows like `WageTypeId=1000, Expression="BaseSalary * 1.0"`.  
- The product evaluates expressions at payrun time. There is no “import a France regulation”; there is “load config for tenant/country.” Config might be in DB, files, or a config service. The **same code** runs for everyone; only config differs. No scripts, no “content pack”—just data.

**Pros**

- **No “content” to ingest**: No Exchange format, no script storage, no “regulation as a unit.” Simpler if your rules really are expressible as config.
- **Single deploy**: Product + config (or config in DB) is one story. No “engine release” vs “regulation release” distinction.
- **Auditable**: All behaviour is “this config row”; easy to trace and version config.

**Cons**

- **Expressiveness**: If payroll rules need real logic (branches, loops, lookups, retro), a pure “config only” model can hit limits. You either extend the config model (which can become a DSL) or you introduce “code” somewhere—at which point you may be reinventing a core/dynamic split (config = dynamic, code = core).
- **Not “plug in a country”**: Adding a country is still “add config for that country.” If config is in the same DB as the product, you’re not “ingesting” a separate artifact; you’re editing data. The *separation* (engine vs content) is weak or absent.

---

### 3.4 Core + dynamic (current style)

**Description**: **One** generic engine (core) that knows *how* to run payruns but not *what* the rules are. Rules and metadata (regulations, wage types, collectors, scripts, lookups) are **content** that is ingested or plugged in (e.g. via import, plugins, or external service). The engine loads and uses this content at runtime. Adding a country = adding content, not changing the core.

**Example**:  
- Core: PayrunProcessor, script runtime, persistence, API—same for everyone.  
- Content: “France” = regulation JSON + C# scripts imported and stored in DB. “India” = different regulation JSON + scripts. Core doesn’t know France vs India; it just “loads the regulation for this payroll” and runs it.  
- New country = author regulation pack + import; no core change.

**Pros**

- **One engine to maintain**: Bug fixes and platform features (security, performance, API) are done once. All countries benefit.
- **Regulation change without engine redeploy**: Update or add regulations by re-importing or updating content. Critical for multi-tenant and multi-country with frequent rule changes.
- **Clear boundary**: “Platform team” owns core; “country/regulation team” owns content. Clean separation of concerns.
- **Scalability of countries**: Adding the 10th country does not mean 10th codebase or 10th deployable; it means one more regulation pack.

**Cons**

- **Abstraction cost**: The core must be generic (lifecycle, script contract, schema). That can make the core more complex than a single-country monolith.
- **Contract between core and content**: Content must conform to the engine’s contract (e.g. script signatures, Exchange format). Contract changes can break existing content.
- **Two “things” to version**: Engine version and regulation version; compatibility and testing (engine X + France v2) must be thought through.
- **Where does logic live?**: New joiners must understand “engine does this, content does that.” Mental overhead compared to “it’s all in the code.”

---

## 4. Comparison: Separation vs Alternatives

| Aspect | Monolithic (all in code) | Separate product per country | Config-only (no separation) | Core + dynamic (separation) |
|--------|---------------------------|------------------------------|-----------------------------|-----------------------------|
| **New country** | Code change + release | New product or new deploy | Add config | Add content + import |
| **Rule change** | Code change + release | Product change + release | Config change (maybe no deploy) | Content update + import |
| **Single codebase?** | Yes (one repo, all logic) | No (N products) | Yes (one product + config) | Yes (one core; content is data/artifacts) |
| **Who owns “France”?** | Same team as engine | France team (own product) | Same team (config) | Regulation team (content) |
| **Expressiveness** | Full (same language as product) | Full per product | Limited to config/DSL | Depends on content model (scripts, DSL, etc.) |
| **Operational load** | One deploy | N deploys | One deploy | One core deploy; content is data/import |
| **Boundary clarity** | None (all code) | Hard (per product) | Weak (app vs config) | Clear (core vs content) |

---

## 5. When the Separation Is Worth It (and When It Isn’t)

**Separation (core + dynamic) tends to be worth it when:**

- You support **many countries or tenants** and expect to add more. One engine + many contents scales better than N products or one giant monolith.
- Regulation **changes often** (legal updates, client-specific overrides). Redeploying the engine for every change is costly or slow.
- You want **clear ownership**: platform team vs regulation/country teams. Separation gives a natural boundary.
- You are willing to invest in a **generic engine** (lifecycle, contract, ingestion) and in a **content model** (Exchange, scripts, or similar). The upfront cost pays off over many countries/tenants.

**Separation is less compelling when:**

- You have **one or two countries** and little churn. A monolith (all logic in code) or a single product per country may be simpler.
- You want **everything in one place** for simplicity and refactoring. Separation adds “engine vs content” and versioning of two things.
- Your rules are **simple and stable** and can be expressed as config. A config-only product might be enough; you don’t need “ingestible regulation packs.”
- You are a **single team** and don’t need to split “platform” and “regulation” ownership. The benefit of the boundary is smaller.

---

## 6. Summary

- The **core vs dynamic separation** is **not necessary**; it is a design choice. Alternatives include: monolithic (all logic in code), separate product per country, or config-only (no regulation as separate content).
- **Pros of the separation**: One engine to maintain, regulation changes without engine redeploy, clear platform vs content boundary, scalable to many countries/tenants.
- **Cons of the separation**: Abstraction and contract cost, two “things” to version (engine + content), and the need to understand where logic lives (core vs content).
- **Alternatives** trade off simplicity (monolith, config-only), independence (separate product per country), and flexibility (core + dynamic). Choose based on number of countries/tenants, frequency of change, and how you want to split ownership and operations.

This document is about **whether** to have the separation. For **how** to implement the dynamic part (e.g. scripts vs external service vs DSL), see **PAYROLL_ENGINE_ARCHITECTURE_PROSCONS_AND_ALTERNATIVES.md**.
