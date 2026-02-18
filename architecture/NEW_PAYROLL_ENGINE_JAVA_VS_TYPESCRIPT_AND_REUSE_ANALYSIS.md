# New Payroll Engine: Java vs TypeScript & Reuse Strategy (Overview)

This document supports building a **new, improved payroll engine** inspired by the existing .NET engine. The content is split into two focused documents:

---

## 1. Java vs TypeScript Comparative Analysis

**File**: [PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md](./PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md)

- **Evaluation dimensions** (runtime scripting, performance, decimal math, typing, persistence, reporting, concurrency, ecosystem, deployment, team, security).
- **Pain points and options** for Java (Janino, Groovy, GraalVM) and TypeScript (JS isolate, precompiled bundles, external service).
- **Per-dimension comparison** (performance, decimal, typing, persistence, reporting, concurrency, deployment, team, security).
- **POC recommendations**: minimum scope (2–4 weeks), success criteria, and a **scoring matrix** so you can run a proof-of-concept and decide which stack is better for your context.

Use this document to choose between Java and TypeScript and to design your POC.

---

## 2. Reuse, Redesign & Reject Strategy

**File**: [REUSE_REDESIGN_REJECT_STRATEGY.md](./REUSE_REDESIGN_REJECT_STRATEGY.md)

- **Reuse**: What to carry over — domain model (entities, relationships), calculation flow (Case → WageType → Collector → Net), lifecycle hooks (names and order), exchange/API shape (as reference), regulation structure (metadata + logic), calendar and periods.
- **Redesign**: What to do differently — runtime compilation (use Janino/JS isolate or avoid scripting), script storage (versioned artifacts, not full source in DB), monolith (separate workers), script API (narrow context), assembly cache keying (content/version hash), sync-only (async-first).
- **Reject**: What not to reuse — .NET-specific types (use Java/TS types), Roslyn (no C# compile in new engine), script table as primary (use artifacts/expressions/service), exact persistence schema (design for your DB), FastReport/.NET reporting (use Java/TS libs).
- **Summary table** (Reuse | Redesign | Reject by area).

Use this document to decide what to adopt, what to change, and what to avoid when designing the new engine.

---

## Quick Links

| Topic | Document |
|-------|----------|
| Language choice, POC, scoring | [PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md](./PAYROLL_ENGINE_JAVA_VS_TYPESCRIPT_COMPARATIVE_ANALYSIS.md) |
| Reuse, redesign, reject | [REUSE_REDESIGN_REJECT_STRATEGY.md](./REUSE_REDESIGN_REJECT_STRATEGY.md) |
| Runtime rules/scripting deep dive | [RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md](./RUNTIME_RULES_SCRIPTING_DEEP_DIVE.md) |
| Execution without scripts | [EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md](./EXECUTION_MODELS_NO_SCRIPTS_NO_DB_BINARY.md) |

Related: `TYPESCRIPT_MIGRATION_PROBLEMS.md`, `JAVA_MIGRATION_BENEFITS.md`, `JAVA_MIGRATION_NEGATIVES.md`.
