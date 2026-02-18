# RFC: Core vs Dynamic Separation — Architectural Alternatives

**Status:** in progress / ready for review / approved

**Author:**

**Open Date:**

**Closing Date for Comments:**

---

## Motivation

The current payroll engine is built around a **core vs dynamic** separation: a single generic engine (core) plus ingested or pluggable regulation content (dynamic). When building a new engine or evolving the architecture, we must decide whether this separation is necessary and what alternatives exist. This RFC captures the architectural choice of **having** (or not having) a core/dynamic split, documents alternatives (monolithic, separate product per country, config-only, core+dynamic), and provides pros/cons and a comparison so stakeholders can decide.

**References:** `PAYROLL_ENGINE_CORE_VS_DYNAMIC_ARCHITECTURE.md`, `PAYROLL_ENGINE_FUNDAMENTAL_ARCHITECTURE_DETAILED.md`, `PAYROLL_ENGINE_CORE_DYNAMIC_SEPARATION_ALTERNATIVES.md`.

---

## Context

- **Current system:** The .NET payroll engine has a **constant core** (payroll-engine-backend + libraries): one codebase that defines lifecycle, persistence, API, and script contract but contains no country-specific rules. **Dynamic content** (regulations, wage types, collectors, scripts, lookups) is ingested via Exchange import and stored in the DB; the core loads and executes it at payrun time. Adding or changing a country does not require changing or redeploying the core.
- **Target:** A decision on whether the new or evolved system should retain this separation or adopt a different architecture (e.g. monolithic, separate product per country, config-only).
- **Scope:** This RFC is about **whether** to have the core/dynamic separation at all. It is **not** about how to implement the dynamic part (e.g. Roslyn vs external service vs DSL)—that is covered in `rfc_runtime_rules_scripting_options.md` and `PAYROLL_ENGINE_ARCHITECTURE_PROSCONS_AND_ALTERNATIVES.md`.

---

## Problem Statement

**Goal:** Decide whether the payroll system should be architected with a **core (constant engine) + dynamic (ingested/pluggable) content** separation, or with an alternative (monolithic, separate product per country, config-only). The decision must be explicit and documented with:

1. A clear definition of “core vs dynamic separation.”
2. Alternatives with description, example, and pros/cons.
3. A comparison table and guidance on when each approach is appropriate.

This is a **design/architecture** requirement with implications for **functional** (how new countries and rule changes are delivered) and **non-functional** (operational load, ownership, versioning) concerns.

---

## Design

- **Functional:** The system must support multiple countries/tenants and allow rule changes. The **mechanism** for delivering country logic and changes can be: (1) one generic engine + ingested content (core+dynamic), (2) all logic in one codebase (monolithic), (3) separate product per country, or (4) one product + config only (no “regulation” as first-class content).
- **Non-functional:** Operability (one vs N deployables), ownership (platform vs regulation teams), versioning (engine vs content), and simplicity (single mental model vs two “things”).
- **SCORP:** **Security** (who can change what), **Cost** (build/deploy/maintain N products vs one engine), **Operational excellence** (release cadence, rollback), **Reliability** (coupling between countries), **Performance** (N/A for this choice).

---

## Solution Highlights

Four architectural options are detailed below. Summary:

| Option | Description | New country | Rule change | Single codebase? | Boundary |
|--------|-------------|-------------|-------------|------------------|----------|
| **1. Monolithic** | All country logic in product source code | Code change + release | Code change + release | Yes | None |
| **2. Separate product per country** | One deployable per country; no generic engine | New product/deploy | Product change + release | No (N products) | Per product |
| **3. Config-only** | Regulation = config (tables, formulas); no ingestible “content” | Add config | Config change | Yes (product + config) | Weak (app vs config) |
| **4. Core + dynamic** | One generic engine + ingested/pluggable content | Add content + import | Content update + import | Yes (core; content = data) | Clear (core vs content) |

**Recommendation (default):** Retain **core + dynamic** when supporting many countries/tenants with frequent rule changes and when platform vs regulation ownership should be separate. Prefer **monolithic** or **config-only** for one or two countries with stable rules and a single team.

---

## Solution Details

### Definition: “Core vs Dynamic Separation”

- **Core:** A single, generic payroll execution engine (same codebase and deployable for every tenant and country). It defines *how* payruns run: lifecycle, persistence, API, script contract. It contains **no** country-specific or tenant-specific rules.
- **Dynamic:** Country/tenant-specific content (regulations, wage types, collectors, scripts, lookups) that is **ingested or plugged in** after deploy—via import, config, or plugins. The engine loads and uses this content at runtime. Adding or changing a country does **not** require changing or redeploying the core.

**Is the separation necessary?** No. It is a design choice. Many payroll systems work without it (monolithic or separate products).

---

### Option 1: Monolithic — All Country Logic in One Codebase

**Description:** One payroll product. All country-specific logic lives in the product’s source code (same repo, same deploy). “Regulation” is not ingested data or scripts; it is branches, modules, or configuration flags in code. Adding a country means adding code (and possibly config) and redeploying.

**Flow / Example:**

- Codebase has `FranceWageTypeCalculator`, `IndiaWageTypeCalculator`, `SwissWageTypeCalculator`. PayrunProcessor does `switch (payroll.RegulationCountry) { case "FR": return FranceCalculator.Evaluate(...); case "IN": ... }`.
- Or: one `WageTypeEngine` with `if (wageTypeNumber == 1000 && regulation == "FR") return CaseValue("ContractualMonthlyWage"); else if ...`.
- New country (e.g. Germany) = new class or new branch + release + deploy. No “import France regulation”; France is just code.

**Pros:**

- Simple mental model: everything in one place; no “where does this rule live?”
- Type safety and refactoring: all logic in one language and one codebase; compiler and IDE help.
- No ingestion pipeline: no Exchange format, no import step, no “content” to version separately.
- Easier debugging: stack traces and logs are all in your codebase.

**Cons:**

- Every country change = release: bug fix or rule change for France requires a product release and deploy.
- Codebase grows with every country: can become very large (e.g. 50 countries = 50 modules or huge switch).
- Tight coupling: all countries share the same release cycle and bugs; one country’s regression can block others.
- Hard to let “France team” own “France logic” independently if it’s all in the same repo and deploy.

---

### Option 2: Separate Product (or Deploy) Per Country

**Description:** No single generic engine. There is a **separate** payroll product (or deployable) per country or region. Each has its own codebase and/or deploy. “France engine” only knows France; “India engine” only knows India. No “ingest regulation into generic engine”—each product *is* the regulation.

**Flow / Example:**

- `payroll-france` service: Contains only French rules, wage types, tax logic. Deployed and scaled independently.
- `payroll-india` service: Same for India.
- A router or tenant config sends “tenant in France” to payroll-france and “tenant in India” to payroll-india. No shared core that runs both; there are two (or N) products.

**Contrast with current structure (one backend, ingested regulations):** In the current setup, **one** backend ingests France and India (and other) regulations into shared storage; the same process selects the right regulation per payrun. Here, there is **no** shared ingestion: each deployable is built for (or bound to) **one** country. Regulation lives in or with each deployable; adding a country means adding a **new deployable** and routing traffic to it, not importing content into the existing backend. See `SEPARATE_PRODUCT_PER_COUNTRY_DETAILED.md` for a full comparison table.

**Repository and build (two sub-variants):**

- **Merged:** One repo per country (e.g. `payroll-france`) containing both backend and that country’s regulation. CI/CD builds that repo → one deployable per country. Backend code is duplicated or forked across country repos unless a shared library is extracted.
- **Independent repos, combined in CI/CD:** Backend in a **shared** repo (e.g. `payroll-engine-backend`); each country in its **own** repo (e.g. `payroll-regulation-france`). CI/CD **per country** builds backend + that country’s regulation and **combines** them into one deployable (e.g. Docker image or fat JAR). Backend and regulation can remain in separate repositories; “separate product per country” refers to the **deployable**, not to forcing a single monorepo per country.

**Backend reusability:** The backend **can** stay reusable across countries if there is one backend codebase and a fixed contract (e.g. plugin API) that each country’s regulation implements; build combines backend + regulation per country. Alternatively, each country can fork or copy the backend, in which case backends **diverge** and fixes/features must be applied N times. So: reusable by design (one backend + per-country regulation repos, combined in CI/CD) or divergent by design (one repo per country with backend inside).

**Pros:**

- Full independence: France can be rewritten or upgraded without touching India.
- No generic engine: build only what each country needs; no abstraction cost.
- Clear ownership: one team per product/country.
- Physical isolation: different processes per country; scaling and blast radius per country.

**Cons:**

- Duplication: common behaviour (payrun lifecycle, case values, persistence) duplicated per product unless you extract a shared library (which becomes a “core”).
- N products to maintain: every platform fix or feature must be applied N times, or you introduce a shared platform layer.
- Operational cost: N deployables, N pipelines, N runtimes.
- Regulation updates require redeploy of that country’s artifact (or update of its bundle/config), not “re-import into one engine.”

**When to use:** Strong fit when countries are owned by different orgs, have very different lifecycles, or require strict process isolation. Less compelling when you want “add country by importing content” and a single backend to operate.

**Further detail:** `SEPARATE_PRODUCT_PER_COUNTRY_DETAILED.md`.

---

### Option 3: Config-Only — No “Regulation” as Separate Content

**Description:** One product. “Regulation” is not a first-class concept; it is **configuration**—tables, formulas, rule definitions—that the product reads and evaluates. No “core engine + ingestible regulation pack.” New country = add config (e.g. new rows, new files); the model of “core vs dynamic” does not exist.

**Flow / Example:**

- Product has fixed schema: WageType table, Collector table, Formula table. Formula table has rows like `WageTypeId=1000, Expression="BaseSalary * 1.0"`.
- Product evaluates expressions at payrun time. No “import a France regulation”; there is “load config for tenant/country.” Config in DB, files, or config service. Same code runs for everyone; only config differs. No scripts, no “content pack”—just data.

**Pros:**

- No “content” to ingest: no Exchange format, no script storage; simpler if rules are expressible as config.
- Single deploy: product + config is one story; no “engine release” vs “regulation release.”
- Auditable: all behaviour is “this config row”; easy to trace and version.

**Cons:**

- Expressiveness: complex rules (branches, loops, lookups, retro) may need a richer config/DSL or “code” somewhere—then you approach a core/dynamic split.
- Not “plug in a country” in the strong sense: adding a country is “add config”; if config is in the same DB, you’re editing data, not ingesting a separate artifact. Separation (engine vs content) is weak or absent.

---

### Option 4: Core + Dynamic (Current Style)

**Description:** One generic engine (core) that knows *how* to run payruns but not *what* the rules are. Rules and metadata are **content** that is ingested or plugged in (e.g. via import, plugins, or external service). The engine loads and uses this content at runtime. Adding a country = adding content, not changing the core.

**Flow / Example:**

- Core: PayrunProcessor, script runtime, persistence, API—same for everyone.
- Content: “France” = regulation JSON + C# scripts imported and stored in DB. “India” = different regulation JSON + scripts. Core doesn’t know France vs India; it just “loads the regulation for this payroll” and runs it.
- New country = author regulation pack + import; no core change.

**Pros:**

- One engine to maintain: bug fixes and platform features done once; all countries benefit.
- Regulation change without engine redeploy: update or add regulations by re-importing or updating content.
- Clear boundary: platform team owns core; country/regulation team owns content.
- Scalability of countries: 10th country = one more regulation pack, not 10th codebase or deployable.

**Cons:**

- Abstraction cost: core must be generic (lifecycle, script contract, schema); can be more complex than a single-country monolith.
- Contract between core and content: content must conform to engine contract (script signatures, Exchange format); contract changes can break content.
- Two “things” to version: engine version and regulation version; compatibility (engine X + France v2) must be managed.
- Mental overhead: new joiners must understand “engine does this, content does that.”

---

### Comparison Table

| Aspect | Monolithic | Separate product per country | Config-only | Core + dynamic |
|--------|------------|------------------------------|-------------|-----------------|
| **New country** | Code change + release | New product or new deploy | Add config | Add content + import |
| **Rule change** | Code change + release | Product change + release | Config change (maybe no deploy) | Content update + import |
| **Single codebase?** | Yes (one repo, all logic) | No (N products) | Yes (one product + config) | Yes (one core; content is data/artifacts) |
| **Who owns “France”?** | Same team as engine | France team (own product) | Same team (config) | Regulation team (content) |
| **Expressiveness** | Full (same language as product) | Full per product | Limited to config/DSL | Depends on content model |
| **Operational load** | One deploy | N deploys | One deploy | One core deploy; content is data/import |
| **Boundary clarity** | None (all code) | Hard (per product) | Weak (app vs config) | Clear (core vs content) |

---

### When to Prefer Which

**Core + dynamic separation is worth it when:**

- You support **many countries or tenants** and expect to add more.
- Regulation **changes often** (legal updates, client overrides); redeploying the engine for every change is costly.
- You want **clear ownership**: platform team vs regulation/country teams.
- You are willing to invest in a generic engine and a content model (Exchange, scripts, etc.); upfront cost pays off over many countries/tenants.

**Separation is less compelling when:**

- You have **one or two countries** and little churn; monolith or single product per country may be simpler.
- You want **everything in one place** for simplicity; separation adds “engine vs content” and versioning of two things.
- Rules are **simple and stable** and expressible as config; config-only product may be enough.
- **Single team**; no need to split platform and regulation ownership.

---

## Observability

- **Metrics:** For core+dynamic: regulation import success/failure, regulation version in use per payrun, engine version vs content version compatibility. For monolithic: release frequency and regression rate per country. For separate products: per-product deploy and error rates.
- **Logging:** Decision point (which architecture) does not change logging strategy; each option still needs payrun logs, rule evaluation errors, and audit trails for who changed what (code vs config vs content).

---

## Compatibility, Deprecation, and Migration Plan (if any)

- If moving **from** core+dynamic **to** monolithic or config-only: existing regulation content must be re-expressed as code or config; one-time migration and testing per country.
- If moving **from** monolithic **to** core+dynamic: extract country logic into regulation packs and define ingestion (Exchange, import API); engine becomes generic; phased rollout per country.
- If moving **from** separate products **to** core+dynamic: consolidate into one engine and ingest each country’s logic as content; significant migration and regression testing.

---

## Future Iterations / Enhancements

- Revisit this choice when the number of countries/tenants or frequency of rule change shifts significantly.
- If core+dynamic is retained: consider separate RFCs for how to implement the dynamic part (runtime scripting vs precompiled vs external service vs DSL) and for regulation versioning and compatibility policy.
