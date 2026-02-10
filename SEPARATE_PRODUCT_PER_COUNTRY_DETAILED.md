# Separate Product per Country — Detailed Option

This document expands the **separate product (or deploy) per country** architectural option from `PAYROLL_ENGINE_CORE_DYNAMIC_SEPARATION_ALTERNATIVES.md` and `rfc_core_dynamic_separation_alternatives.md`. It covers what “separate product” means in practice, repository and deploy strategies, and whether the backend can stay reusable or will diverge per country.

---

## 1. What “Separate Product per Country” Means

- **No single generic engine** that runs “any” regulation. Instead, there is one **deployable product** (or service) per country (or per region).
- Each product **is** the regulation: the France deployable contains only French rules, wage types, tax logic; the India deployable only Indian. A router or tenant config sends “tenant in France” → `payroll-france`, “tenant in India” → `payroll-india`.
- There is no shared runtime that “loads France content” and “loads India content”; there are **N separate runtimes**, one per country (or group of countries).

Within this model, two dimensions matter:

1. **Repository and build:** Are backend and regulation in one repo per country, or in separate repos combined at build time?
2. **Backend reusability:** Is there one backend codebase (or library) used by all country products, or does each country have its own backend that may diverge?

The rest of this doc answers those explicitly.

---

## 2. How This Option Differs from the Current Structure (One Backend, Ingested Regulations)

In the **current** setup you have **one** backend. France and India (and other) regulations are **ingested**—imported and stored (e.g. in the DB). The same backend serves all tenants: for each payrun it looks up which regulation applies (e.g. by tenant/payroll/regulationId), loads that regulation’s content from storage, and runs it. So France and India “run separately” only in the sense that **each payrun uses one regulation at a time**—but they run in the **same** backend process and deployable.

**Separate product per country** is a different model:

| Aspect | Current (core + dynamic, ingestion) | Separate product per country |
|--------|-------------------------------------|------------------------------|
| **Number of deployables / processes** | **One** backend for all countries | **N** backends (e.g. payroll-france, payroll-india), one per country |
| **Where regulation lives** | In **shared storage** (DB). Import France, import India; backend loads the right one per payrun. | In or with **each deployable**. France regulation is built into/bundled with the France deployable; India with the India deployable. No shared “ingestion” store that holds all countries. |
| **How France vs India are used** | Same backend **selects** regulation at runtime (e.g. by regulationId). One process holds many regulations as data; each payrun uses one. | **Routing**: “tenant in France” → payroll-france service; “tenant in India” → payroll-india service. Each process only knows one country. |
| **Adding a country** | Add regulation content → import into the same system. **No** new deployable; same backend now serves the new country from stored content. | Add a **new deployable** (build backend + new country regulation → new service) and route traffic to it. |
| **Updating a regulation** | Re-import or update content in DB; same backend picks it up. No backend redeploy. | Rebuild/redeploy that country’s deployable (or update the bundle/config that deployable loads). |
| **Isolation** | Logical: same process, different data per payrun. | Physical: different processes (and possibly scaling, blast radius, and ownership per country). |

So the crucial difference: in the **current** structure you have **one backend** that can run **many** regulations (France, India, …) by **ingesting** them and choosing which to use per payrun. In the **separate product** option you have **many backends** (one per country), each of which only runs **one** regulation; there is no “ingest France and India into one engine”—each engine is built for (or bound to) a single country.

---

## 3. Repository and Deploy: Merged vs Independent Repos

**Short answer:** It does **not** have to mean “backend and regulation merged into a single repository.” You can do either:

- **Option A — Single repo per country:** Backend + regulation for that country live in one repository; one CI/CD produces one deployable for that country.
- **Option B — Independent repos, combined in CI/CD:** Backend lives in one (or more) **shared** repo(s); each country’s regulation lives in its **own** repo. CI/CD **combines** backend + country regulation to produce **one deployable artifact per country**.

Both are valid “separate product per country” models: the *product* (the deployable) is per country; the *source layout* can be merged or split.

---

### 3.1 Option A: Single Repository per Country (Merged)

- **Layout:** One repo per country, e.g. `payroll-france`, `payroll-india`. Each repo contains:
  - Backend (API, payrun engine, persistence, lifecycle) — possibly copied or subtree-split from a “template” at the start.
  - Regulation (wage types, collectors, scripts, lookups) for that country, as code or config in the same repo.
- **Build:** CI/CD for `payroll-france` builds that repo → one artifact (e.g. Docker image or JAR) → deploy to `payroll-france` environment.
- **Implications:**
  - **Pro:** Simple: one repo = one product; no “combine two repos” step. Clear ownership (France team owns the France repo).
  - **Con:** Backend code is **duplicated** across repos (copy or fork). Bug fixes and features in “the backend” must be applied to each country repo (or you introduce a shared library and move toward Option B).

So: “separate product per country” **can** mean backend and regulation in a **single repo per country**, but then the backend tends to be copied/forked unless you extract it into a shared dependency.

---

### 3.2 Option B: Independent Repos, Combined in CI/CD

- **Layout:**
  - **Backend repo(s):** e.g. `payroll-engine-backend` (and maybe shared libraries). No country-specific logic; only generic engine: lifecycle, API, persistence, script/rules contract.
  - **Regulation repo(s) per country:** e.g. `payroll-regulation-france`, `payroll-regulation-india`. Each contains only that country’s rules, wage types, collectors, scripts (as code or as config consumed by the engine).
- **Build:** CI/CD **per country** does:
  1. Build backend (from backend repo) → backend artifact (e.g. JAR or image base).
  2. Build regulation (from that country’s repo) → regulation artifact (e.g. JAR, bundle, or config package).
  3. **Combine** them into **one deployable** for that country (e.g. Docker image with backend + France regulation JAR on classpath, or backend that loads France bundle at startup).
  4. Deploy that combined artifact as the “France product” (or “India product”).
- **Implications:**
  - **Pro:** Backend stays in one place; one codebase, one set of fixes/features. Regulation stays independent per country; France team owns `payroll-regulation-france` only.
  - **Pro:** You still get **one deployable per country** (separate product per country), but built from two (or more) repos. No merge of backend and regulation into one repo required.
  - **Con:** CI/CD is more involved: pipeline must assemble “backend + country X regulation” and version them (e.g. backend v2.1 + France regulation v1.3 → payroll-france image tag).

So: **Backend and regulation can remain in independent repositories** and be **combined in CI/CD to produce a single deployable artifact per country.** “Separate product per country” refers to the **deployable and runtime**, not to forcing a single monorepo per country.

---

### 3.3 Summary: Repo vs Deploy

| Aspect | Single repo per country (A) | Independent repos + CI/CD (B) |
|--------|-----------------------------|------------------------------|
| Backend location | Inside each country repo (copy/fork) | Shared backend repo |
| Regulation location | Same repo as backend for that country | Separate repo per country |
| Deployable | One build per repo → one artifact per country | One build per country that combines backend + regulation → one artifact per country |
| Backend reuse | Only by copy/fork or later extracting a lib | Yes: same backend binary used in each country’s build |

---

## 4. Can the Backend Be Reusable Across Countries, or Will It Diverge?

**Short answer:** The backend **can** remain highly reusable across countries **if** you keep a strict contract and a single codebase (as in Option B above). It **will** diverge per country if each country product is allowed to fork and customize the backend freely (as in Option A without a shared library). So: **reusable by design, or divergent by design**—depending on how you structure repos and ownership.

---

### 4.1 Reusable Backend (Single Backend, Per-Country Deployables)

- **Idea:** One backend codebase (one repo or one set of repos). It exposes a **fixed contract** for “regulation”: e.g. interfaces such as `WageTypeEvaluator`, `CollectorLifecycle`, or a plugin API. Each country implements that contract in its **own** repo (regulation-france, regulation-india). Build combines backend + regulation → one deployable per country; at runtime, the backend is **identical** across countries—only the regulation implementation differs.
- **Reusability:** Backend is **fully reusable**. Bug fixes, security patches, and platform features are done once and rolled out to all country builds. No divergence.
- **Requirement:** The backend must be **generic** enough that no country-specific logic lives in it. All country-specific behaviour lives in the regulation artifact (code or config). That is exactly the “core + dynamic” **boundary**, but with “dynamic” shipped as a **build-time** dependency (per country) rather than “ingested at runtime.” So you get:
  - **One product per country** (separate deployable, separate release cycle per country).
  - **One backend** (shared, reusable), with no per-country branching in backend code.

Backend then **does not** diverge per country, as long as you resist putting country-specific logic into the backend repo.

---

### 4.2 Divergent Backend (Fork or Heavy Customization per Country)

- **Idea:** Each country product has its **own** backend codebase: either a fork of a “starter” backend or a separate implementation. France backend might add France-specific APIs, persistence, or workflow; India backend might do different things. Over time, the codebases drift.
- **Reusability:** Low. Fixes and features in “the backend” must be ported to each fork or reimplemented. Backends **diverge** significantly unless you invest in a shared library (and then you’re moving toward the reusable model).
- **When this happens:** Often when:
  - You choose “one repo per country” and copy the backend into each repo with no shared library, or
  - Product owners per country are allowed to change “the backend” for their country only (e.g. “France needs a special report API”), and those changes are not folded back into a common codebase.

So: **If you do not enforce a single backend and a strict contract, backends will diverge per country.** If you do enforce one backend and regulation-only per-country repos, the backend can stay reusable.

---

### 4.3 Spectrum

| Approach | Backend reusability | Backend divergence risk |
|----------|--------------------|--------------------------|
| One repo per country, backend copied in each | Low (unless you extract a shared lib) | High: each repo can change “its” backend |
| Independent repos: one backend repo + one regulation repo per country; CI/CD combines | High: one backend for all | Low: backend has no country-specific code |
| One backend repo + one regulation repo per country, but allow “backend overrides” or “country hooks” in backend | Medium | Medium: some divergence where overrides exist |

---

## 5. Recommended Pattern for “Separate Product per Country” Without Backend Divergence

If you want **separate deployable per country** but **reusable backend**:

1. **Keep backend and regulation in independent repositories:** e.g. `payroll-engine-backend` (single source of truth), `payroll-regulation-france`, `payroll-regulation-india`, etc.
2. **Backend defines a clear contract** (e.g. plugin API or interfaces) that each country’s regulation implements. No country-specific code in the backend repo.
3. **CI/CD per country:** Build backend once (or use a versioned backend artifact); build that country’s regulation; combine into **one deployable** (e.g. Docker image or fat JAR) for that country. Deploy that as the “France” or “India” product.
4. **Versioning:** Track compatibility (e.g. “France regulation v1.x requires backend v2.x”). When you upgrade the backend, rebuild all country artifacts with the new backend and regression-test each.

That way: backend and regulation **remain independent repositories** and are **combined to make a single deployable per country in CI/CD**; the backend **stays reusable** and does not diverge, while you still get a separate product (deployable) per country.

---

## 6. References

- **Core vs dynamic separation (overview):** `PAYROLL_ENGINE_CORE_DYNAMIC_SEPARATION_ALTERNATIVES.md`
- **RFC (alternatives including this one):** `rfc_core_dynamic_separation_alternatives.md`
- **Fundamental architecture (core + dynamic in current engine):** `PAYROLL_ENGINE_FUNDAMENTAL_ARCHITECTURE_DETAILED.md`
