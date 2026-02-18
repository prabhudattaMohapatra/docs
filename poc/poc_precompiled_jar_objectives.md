# POC: Precompiled JAR — Objectives

**POC:** Minimal payroll engine (Java) with regulation via precompiled JAR; full payrun to results.  
**Related:** [poc_precompiled_jar_scope.md](poc_precompiled_jar_scope.md) | [poc_runtime_rules_precompiled_jar_java.md](poc_runtime_rules_precompiled_jar_java.md)

This document states the **objectives** of the POC. Scope is in the scope doc.

---

## Primary objective

**Build a minimal payroll engine in Java, taking inspiration from the .NET engine, to run a minimal payrun.**

- The engine is **minimal**: no production features beyond what is needed to run one payrun end-to-end.
- It is **inspired by the .NET engine**: same conceptual flow (PayrunStart, EmployeeStart/End, wage types, collectors, PayrunEnd) and result concepts (wage type results, payrun results), without replicating every .NET detail.
- **Run a minimal payrun:** Execute the full payrun flow from start through to **payrun results** (e.g. wage type results per employee, collector results, and a coherent “payrun result” output).

---

## Secondary objectives

| Objective | Description |
|-----------|-------------|
| **Prove precompiled JAR execution** | Demonstrate that regulation logic in a versioned JAR can be loaded by (regulation id, version) and invoked by the engine (evaluateWageType, collector lifecycle) with acceptable complexity and performance. |
| **Validate full payrun flow** | Validate that the engine can orchestrate PayrunStart → (per employee) EmployeeStart → wage types + collectors → EmployeeEnd → PayrunEnd and produce payrun results, using only the JAR for regulation behaviour. |
| **Inform future choice** | Produce evidence (design, metrics, failure behaviour) to support the decision between precompiled JAR and other options (e.g. external service) in the runtime rules RFC. |
| **Reusable foundation** | Deliver a minimal but complete Java engine that can be extended later (e.g. add external regulation service path, persistence, or more payrun features). |

---

## Success (what “done” looks like)

- A **minimal payroll engine** in Java runs a **full payrun** from start to **payrun results**.
- Regulation behaviour is provided **only** by a **precompiled JAR** (loaded from a plugin directory).
- The flow and results are **inspired by the .NET engine** (same concepts; minimal implementation).
- Design, payrun flow, and behaviour are **documented** for use in evaluation and next steps.
