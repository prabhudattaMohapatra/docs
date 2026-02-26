# Java vs TypeScript POC: Final Performance Comparison

This document is the **final** comparison of the Java and TypeScript payroll engine POCs: observed performance, points of difference, reasons for the gap, whether the comparison is fair, and what can be done to bridge the gap. It consolidates [POC_TS_VS_JAVA_SPEED_THEORY.md](./POC_TS_VS_JAVA_SPEED_THEORY.md) and [POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md](./POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md).

---

## Executive summary

| Aspect | Summary |
|--------|--------|
| **Observed performance** | TypeScript (Lambda) is **~4× to ~10× faster** than Java (Fargate) on the **regulation computation** phase, depending on workload alignment. S3 phases favour TS (higher concurrency, in-memory); theory favours Java on engine (multi-threaded), but in practice TS wins. |
| **Main reasons** | Reflection in Java (dispatch cost), concurrency overhead (thread pool vs single loop), allocation/GC, JVM warm-up in the timed phase, and (when not aligned) much larger per-employee workload on Java (205 wage types + YTD vs 60, no YTD on TS). |
| **Fairness** | The comparison is **not fully fair** today: different platforms (Fargate vs Lambda), different regulation workload unless aligned (60 vs 205 wage types, YTD vs none), different I/O and concurrency models. Standardization (same regulation, same concurrency model, warm-up, constant reuse) can make it fairer; some options are benchmark-only. |
| **Bridging the gap** | Java: Method cache and constant reuse are **implemented** and give **small** engine-phase improvement (~8–12%); further gains require direct invocation (risks independence), parallelism=1 (benchmark-only), or warm-up (benchmark-only). TS: already fast; runtime regulation fetch aligns artifact model with Java. |

---

## 1. Performance comparison (observed)

### 1.1 Phases and where each POC runs

| Phase | Java (Fargate) | TypeScript (Lambda) |
|-------|----------------|----------------------|
| **S3 download** | Shell `aws s3 sync` → disk; ~10 concurrent (CLI default). | In-process, 50-way concurrency, memory. |
| **Regulation computation** | JVM: thread pool (e.g. 2 threads), 10k tasks, reflection-based dispatch, 205 wage types + 12‑month YTD (current) or 60 wage types no YTD (pre–complexity). | Single-threaded loop, direct dispatch, 60 wage types, no YTD. |
| **S3 upload** | Shell `aws s3 sync` from disk; ~10 concurrent. | In-process, 50-way concurrency, memory. |

### 1.2 Observed numbers (representative)

**Like-for-like (60 wage types, no YTD):**

| Metric | Java (Fargate, old regulation) | TypeScript (Lambda) |
|--------|--------------------------------|---------------------|
| **10k employees — engineExecutionMs** | ~6 225 | ~1 574 |
| **10k — employeesPerSecond** | ~1 606 | ~6 353 |
| **1k employees — engineExecutionMs** | ~1 881 | ~246 |

→ TypeScript **~4× (10k) to ~7.6× (1k)** faster on the engine phase despite Java using 2 threads.

**Heavy Java (205 wage types + YTD) vs TS (60, no YTD):**

| Metric | Java (Fargate) | TypeScript (Lambda) |
|--------|----------------|---------------------|
| **10k — engineExecutionMs** | ~15 863 | ~1 574 |
| **10k — employeesPerSecond** | ~630 | ~6 353 |

→ TypeScript **~10× faster**; gap amplified by the extra per-employee work on Java.

**After Java optimizations (Method cache + constant reuse, Feb 2026):**

| Run | engineExecutionMs (10k) | Note |
|-----|-------------------------|------|
| Java (before optimizations) | ~15 200–15 900 | 205 wage types + YTD |
| Java (after optimizations) | ~13 964 | Same workload |
| Improvement | ~8–12% on engine phase | Barely noticeable on **total** run (engine is ~12% of total; S3 dominates). |

So: **observed performance** strongly favours TypeScript on the engine phase; Java gains a small improvement from the implemented optimizations but the gap remains large.

---

## 2. Points of difference

### 2.1 Implementation

| Dimension | Java POC | TypeScript POC |
|-----------|-----------|-----------------|
| **Rule dispatch** | Reflection: `getMethod` + `invoke` per wage type (Method cache added to reduce `getMethod` only). | Direct: map lookup + function call. |
| **Concurrency (engine)** | Fixed thread pool (e.g. 2 threads), one task per employee. | Single-threaded loop. |
| **Decimal / allocation** | `BigDecimal`, immutable; many temporaries; constant reuse via cache added. | `decimal.js`; single-threaded; shared constants. |
| **S3** | Shell `aws s3 sync` (disk); default CLI concurrency. | In-process GetObject/PutObject; concurrency 50; in-memory. |
| **Regulation artifact** | JAR in image or from CodeArtifact; loaded at startup. | Bundled or **runtime pull** from S3 (tgz); dynamic import. |

### 2.2 Platform

| Dimension | Java (Fargate) | TypeScript (Lambda) |
|-----------|----------------|---------------------|
| **Compute** | Dedicated vCPU/memory per task; predictable. | Proportional CPU; shared environment; possible throttling. |
| **Cold start** | JVM start once per container; warm-up inside engine phase. | Lambda cold start in init; engine phase often measured warm. |
| **Limit** | No practical per-task duration limit. | 15‑minute max per invocation. |

### 2.3 Workload (regulation)

| Dimension | Java (current / heavy) | TypeScript (current) |
|-----------|------------------------|----------------------|
| **Wage types** | 205 (60 core + 145 synthetic) | 60 |
| **YTD simulation** | 12‑month YTD per wage type | None |
| **Per-employee work** | Much larger (205 × YTD + rules). | Smaller (60 rules, no YTD). |

When aligned (60 wage types, no YTD), the **logical** workload is the same; implementation and platform differences still drive the performance gap.

---

## 3. Reasons for the differences

### 3.1 Why TypeScript is faster on regulation computation (in practice)

1. **Reflection vs direct dispatch**  
   Java pays for `Method.invoke()` on every wage type × every employee; TS uses a direct call. Method cache removes repeated `getMethod` but **invoke** remains. This accounts for a large share of the gap when there are many small calls.

2. **Concurrency overhead**  
   Java’s thread pool and per-employee task submission add overhead; for small per-employee work, a single-threaded loop in TS has lower overhead and can be faster overall despite using one core.

3. **Allocation and GC**  
   Java creates many temporary `BigDecimal`s and has multi-threaded allocation; constant reuse reduces some of this but not the result objects of each operation. TS in a single-threaded path can have lower allocation and GC cost.

4. **Warm-up and measurement**  
   Java’s `engineExecutionMs` includes JVM/JIT warm-up; TS engine phase is often measured after init, so it reflects steadier state. That can widen the gap.

5. **Workload mismatch (when not aligned)**  
   With 205 wage types and 12‑month YTD, Java does far more work per employee than TS (60, no YTD), which amplifies the ~4× base gap to ~10×.

### 3.2 Why TypeScript is faster on S3 (theoretical and structural)

- **Concurrency:** 50 in-flight requests vs ~10 for AWS CLI sync.  
- **Data path:** TS uses memory only; Java uses disk (sync → disk, then JVM reads; JVM writes, then sync uploads).  
So both download and upload favour TS.

### 3.3 Why the implemented optimizations gave only a small improvement

- **Method cache:** Removes `getMethod` cost but not `invoke`; reflection still dominates per-call.  
- **Constant reuse:** Cuts allocation of literal `BigDecimal`s; the main cost remains the **operations** (multiply, add, divide) and their result allocations.  
- **Share of total time:** Engine is ~12% of total run time; even a 10% engine improvement is ~1% of total, so the improvement feels “barely any.”

---

## 4. Is the comparison fair?

### 4.1 What “fair” would mean

A fair comparison would hold constant:

- **Regulation workload:** Same wage type count, same YTD or not, same rules.
- **Batch size and input:** Same employee count, same input data.
- **Concurrency model:** Either both single-threaded for the engine phase, or both N-way parallel with same N.
- **Measurement:** Engine phase measured after warm-up (or both cold); same metrics (e.g. `engineExecutionMs`, `avgMsPerEmployee`).
- **Artifact model:** Both engine + independent regulation (Java: JAR; TS: runtime pull from S3).

### 4.2 Current fairness assessment

| Criterion | Status | Note |
|----------|--------|------|
| **Same regulation workload** | ⚠️ Only if aligned | Default Java is 205 + YTD; TS is 60, no YTD. Like-for-like possible by using Java pre-complexity or by adding 205 + YTD to TS. |
| **Same platform** | ❌ No | Fargate vs Lambda (different CPU model, cold start, limits). |
| **Same concurrency** | ❌ No | Java: multi-threaded; TS: single-threaded. |
| **Same dispatch cost** | ⚠️ Partially | Java uses Method cache but still `invoke`; TS direct call. |
| **Same allocation discipline** | ⚠️ Partially | Both use constant reuse; Java still has more temporaries from BigDecimal ops. |
| **Same warm-up treatment** | ❌ No | Java phase includes JVM warm-up; TS often measured warm. |
| **Independent regulation** | ✅ Yes (if TS uses runtime pull) | Java: JAR; TS: can use S3 pull so both are engine + separate artifact. |

**Conclusion:** The comparison is **not fully fair** today: platforms differ, concurrency differs, and (unless explicitly aligned) workload differs. It is **fairer** when: (1) regulation is aligned (e.g. 60 wage types, no YTD), (2) Java uses Method cache and constant reuse (done), (3) metrics and run conditions are documented. Making it **fully** fair would require benchmark-only choices (e.g. Java parallelism=1, warm-up pass) or structural changes (e.g. TS multi-threaded, or both on same platform).

---

## 5. Bridging the gap

### 5.1 What was done (and effect)

| Change | Where | Effect |
|--------|--------|--------|
| **Method cache** | payroll-regulations-poc (`FranceRegulationEvaluator`) | Removes repeated `getMethod`; `invoke` remains. **Small** engine-phase improvement. |
| **BigDecimal constant reuse** | payroll-regulations-poc (`FranceRules`: `DECIMAL_CACHE`, `c()`) | One instance per literal; fewer allocations. **Small** engine-phase improvement. |
| **Combined** | — | ~8–12% faster engine phase; barely noticeable on total run because S3 dominates. |

### 5.2 What could be done further

From [POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md](./POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md):

| Action | Effect on gap | Production-safe? | Note |
|--------|----------------|------------------|------|
| **Java: Method cache** | ✅ Done | ✅ Yes | Already implemented. |
| **Java: Constant reuse** | ✅ Done | ✅ Yes | Already implemented. |
| **Java: Direct invocation (switch / MethodHandle)** | Larger reduction in dispatch cost | ⚠️ Only if inside regulation artifact | If in engine, risks regulation independence. |
| **Java: parallelism=1** | Removes concurrency as variable | ❌ Benchmark-only | Production should use N threads. |
| **Java: Warm-up or “run twice”, report second** | Fairer engine metric | ❌ Benchmark-only | Production does not run twice. |
| **TS: Runtime regulation fetch** | Aligns artifact model; no speed change | ✅ Yes | Use for “engine + independent regulation” comparison. |
| **TS: Multi-threaded engine (e.g. workers)** | Compare N threads vs N workers | ✅ If desired for production | More work; closes concurrency difference. |
| **Align regulation (60, no YTD or both 205+YTD)** | Fairer workload | ✅ Yes | Needed for like-for-like. |

### 5.3 Practical takeaway

- **To make the comparison fairer:** Align regulation (same wage types, same YTD), use Java Method cache and constant reuse (done), and document platform and concurrency. For benchmark-only studies, add Java parallelism=1 and/or warm-up.
- **To reduce the Java engine gap further in production:** Keep Method cache and constant reuse. Direct invocation inside the **regulation** JAR could help but must not couple the engine to the regulation’s method set. Parallelism=1 and warm-up are not for production.
- **To interpret results:** Even with standardization, a **~4×** gap (like-for-like) can remain because of `invoke` cost, allocation/runtime (JVM vs V8), and platform. The **~10×** gap with heavy Java regulation is mostly workload; aligning workload is the first step before comparing “same work.”

---

## 6. References

- [POC_TS_VS_JAVA_SPEED_THEORY.md](./POC_TS_VS_JAVA_SPEED_THEORY.md) — Theoretical comparison, platform impact, observed results, and reasons TS is faster in practice.
- [POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md](./POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md) — Five factors, standardization options, flags (production vs benchmark-only, independence), and TS runtime regulation fetch.
- [poc_precompiled_jar_local_development_guide.md](./poc_precompiled_jar_local_development_guide.md) — Part 4.5 documents the Method cache and constant reuse in the regulation JAR.
