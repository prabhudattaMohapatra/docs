# Standardizing Java vs TypeScript POC for a Fair Comparison

This document focuses on the **five factors** that make TypeScript regulation computation faster than Java in practice (see [POC_TS_VS_JAVA_SPEED_THEORY.md](./POC_TS_VS_JAVA_SPEED_THEORY.md) §5). For each factor, we state the current difference and **recommend how to standardize** across both runtimes so that engine execution time is comparable and the comparison is fair.

**Section 6** flags which suggestions are **not** best/good practices, **unlikely for production**, or **risk regulation artifact independence**. **Section 7** factors in the TypeScript **runtime regulation fetch** (S3 artifact pull) and its impact on the analysis.

---

## 1. Reflection vs direct dispatch

- **Current difference:** Java calls `FranceRules.class.getMethod(entry.methodName(), ...)` and `m.invoke(null, ctx)` on **every** wage type evaluation (60×N or 205×N times). TypeScript does a map lookup and a direct function call `FranceRules[entry.methodName](ctx)`. Reflection in Java is costly and prevents inlining; TS has no reflection in this path.

### How to standardize

**Option A (recommended): Reduce Java dispatch cost so it is closer to TS.**

- **Java (payroll-regulations-poc):** Cache `Method` instances by method name instead of calling `getMethod` every time.
  - In `FranceRegulationEvaluator`, maintain a `ConcurrentHashMap<String, Method>` (or similar) keyed by `entry.methodName()`.
  - On first use for a method name, call `FranceRules.class.getMethod(name, FranceRulesContext.class)` once and store it; on subsequent uses, call `cachedMethod.invoke(null, ctx)` only.
  - This removes 60×N (or 205×N) `getMethod` calls and leaves only `invoke`, which is still reflective but much cheaper than getMethod+invoke.
- **TypeScript:** No change. Direct dispatch remains the baseline; the goal is to make Java’s dispatch cost comparable.

**Option B (optional): Use direct invocation in Java.**

- Replace the reflective dispatch with a **generated** or **explicit** call path: e.g. a switch on wage type number that calls the corresponding `FranceRules` static method directly, or a `Map<Integer, MethodHandle>` populated once at load time. That aligns Java with “one lookup + one call” semantics and allows the JIT to optimize better.
- **TypeScript:** No change.
- **⚠️ Independence:** Option B (direct invocation / switch) can **affect regulation artifact independence** if the generated code lives in the engine: the engine would be coupled to the regulation’s wage type set. Independence is preserved only if the generated code lives **inside** the regulation artifact. Prefer Option A (Method cache) for production.

**Verification:** Run the same batch (e.g. 10k employees, 60 wage types) before and after the Java cache; compare `engineExecutionMs`. After standardization, the dispatch component of the gap should shrink.

---

## 2. Concurrency overhead vs single hot loop

- **Current difference:** Java uses a fixed thread pool (e.g. 2 threads) and submits one task per employee; there is task submission, scheduling, and ThreadLocal evaluator access per employee. TypeScript uses a single thread and one sequential loop. For small per-employee work, Java’s concurrency overhead can dominate.

### How to standardize

**Option A (recommended): Run both in single-threaded mode for the engine phase.**

- **Java (payroll-engine-poc):** Make parallelism configurable (e.g. env var `PAYRUN_PARALLELISM` or system property). When set to `1`, use `Executors.newFixedThreadPool(1)` for the payrun so that the engine phase runs on a single thread, like TS. Default can remain `Runtime.getRuntime().availableProcessors()` for production.
- **TypeScript:** Already single-threaded; no change. Document that for “fair comparison” runs, Java should be configured with parallelism=1.

- **⚠️ Not for production:** Running Java with parallelism=1 is **benchmark-only**. Production would use N threads; use parallelism=1 only to remove concurrency as a variable for comparison.

**Option B: Run both in multi-threaded mode.**

- **TypeScript:** Add an optional path that uses a fixed pool of workers (e.g. `worker_threads` or a pool of Promises with a concurrency limit) to process employees in parallel, similar to Java’s model. Then compare Java (N threads) vs TS (N workers) for the same N.
- **Java:** No change. This is more work and changes TS’s current design; use only if the goal is to compare multi-threaded vs multi-threaded.

**Verification:** For a fair comparison, run Java with `PAYRUN_PARALLELISM=1` and the same batch size as TS. Compare `engineExecutionMs` and `avgMsPerEmployee`. Both stacks then reflect “one thread doing all regulation work,” so the difference is not due to thread-pool overhead.

---

## 3. Object allocation and decimal handling

- **Current difference:** Java creates many temporary `BigDecimal` instances (immutable operations) and has multi-threaded allocation; TS uses decimal.js in a single-threaded hot path. Allocation and GC in Java can add cost that TS avoids or amortizes differently.

### How to standardize

**Option A (recommended): Reduce unnecessary allocation on both sides and document semantics.**

- **Java:**
  - Use **static final** `BigDecimal` constants wherever a rule uses a fixed value (e.g. rates, ceilings). Reuse the same instance instead of `new BigDecimal("0.02")` in hot paths.
  - In the regulation rules, avoid creating temporaries in loops where a single reused variable can be reassigned (e.g. reuse one `BigDecimal` for intermediate results where the API allows, or use a small pool for very hot paths). Keep semantics identical; only reduce allocation volume.
- **TypeScript:**
  - Similarly, use **shared** `Decimal` instances for constants (e.g. module-level `const RATE = new Decimal('0.02')`) instead of creating new Decimals for the same value in every call.
  - Document that both runtimes use immutable decimal semantics; the goal is to make allocation cost more comparable, not to change results.

**Option B (optional): Add allocation or GC metrics.**

- **Java:** Optionally log or report approximate allocation rate or GC time during the engine phase (e.g. via MXBeans or a simple allocation counter). This does not change performance but makes it visible when comparing runs.
- **TypeScript:** Optionally report heap usage delta or a simple allocation metric if available (e.g. from `process.memoryUsage()` before/after the phase). Again for visibility, not for changing behavior.

**Verification:** Run the same batch before and after constant reuse; compare `engineExecutionMs` and, if available, GC or allocation metrics. Standardization here is “same decimal semantics, fewer allocations where possible.”

---

## 4. JVM warm-up and measurement

- **Current difference:** Java’s `engineExecutionMs` is wall time from JVM start to JVM exit, so it includes class loading and JIT warm-up. TypeScript’s engine phase is timed after the handler and modules are loaded, so it is closer to steady-state execution. The first part of the Java run can be slower.

### How to standardize

**Option A (recommended): Add an explicit warm-up phase before the timed engine run.**

- **Java (payroll-engine-poc):** Before starting the wall-clock timer for the engine phase:
  - Run a **warm-up** pass over a small subset of employees (e.g. first 100 or 1% of the list, configurable). Do not include this in the reported `engineExecutionMs`.
  - Then start the timer, run the full payrun (or the remainder), and stop the timer. Report only this duration as `engineExecutionMs`.
  - Alternatively: run the **full** payrun twice; report the **second** run’s duration as the “warm” metric (and optionally report the first as “cold” for transparency).
- **TypeScript:** Optionally do the same: run a small warm-up batch (e.g. 100 employees) before starting the `engineExecution` phase timer, so both sides measure “warm” execution. If TS is already warm due to Lambda reuse, this keeps the comparison consistent when TS is cold (e.g. first invocation).
- **⚠️ Benchmark-only:** Warm-up or "run twice" is for **fair comparison** only. In production you do not run the payrun twice or discard the first portion; use this only when comparing runs.

**Option B: Report both cold and warm.**

- **Java:** Report two metrics: `engineExecutionMsCold` (first full run) and `engineExecutionMsWarm` (second full run, or run after warm-up). Document which is used for comparison.
- **TypeScript:** If a warm-up is added, report `engineExecutionMs` after warm-up; optionally report “first batch” vs “rest” to show warm-up effect.

**Verification:** Compare Java “warm” engine time vs TS engine time (with TS also optionally warmed). The gap should reflect steady-state behavior rather than JIT warm-up.

---

## 5. Summary table and checklist

| # | Factor | Current advantage | Standardization (summary) |
|---|--------|-------------------|---------------------------|
| 1 | **Dispatch** | TS (direct call) | Java: cache `Method` by name (or use direct invocation); TS: no change. |
| 2 | **Concurrency** | TS (single loop) | Java: support parallelism=1 for benchmark; TS: no change. Or add TS parallelism and compare N threads vs N workers. |
| 3 | **Allocation / decimal** | TS (fewer temps, single-thread) | Both: reuse constants (static/shared); avoid redundant temporaries; optionally report allocation/GC. |
| 4 | **Warm-up** | TS (measured warm) | Java: warm-up run or “second run” before reporting engine time; TS: optional warm-up for consistency. |
| 5 | **(Summary)** | Combined effect | Apply 1–4; then re-run same batch (same employee count, same regulation: 60 wage types, no YTD) and compare `engineExecutionMs` and `avgMsPerEmployee`. |

### Fair-comparison checklist

When running a **standardized** comparison:

1. **Same regulation:** Both use 60 wage types, no YTD (or both use 205 + YTD if that is the scenario).
2. **Same batch size:** e.g. 10k employees, same input data (same S3 prefix or same stub files).
3. **Dispatch:** Java uses Method cache (or direct invocation); TS unchanged.
4. **Concurrency:** Java run with `PAYRUN_PARALLELISM=1`; TS single-threaded.
5. **Allocation:** Both use shared constants for fixed decimals; no extra temporaries in hot paths.
6. **Warm-up:** Java reports engine time after a warm-up pass or second run; TS optionally warms then times.
7. **Metrics:** Compare `engineExecutionMs`, `avgMsPerEmployee`, and optionally GC/allocation if available.

After standardization, remaining differences in engine time are more likely due to runtime (JVM vs V8), platform (Fargate vs Lambda), or intrinsic cost of BigDecimal vs decimal.js, rather than reflection, concurrency overhead, or warm-up.

---

## 6. Flags: not best practice, not for production, or affects regulation independence

The table below flags which standardization suggestions are **not** best/good practices, **unlikely to be used in production**, or **could affect regulation artifact independence**. Use it to decide what to adopt for production vs. benchmark-only.

| # | Suggestion | Flag | Reason |
|---|------------|------|--------|
| 1 | **Dispatch Option A:** Cache `Method` by name in Java | ✅ **Good practice** | Reduces redundant reflection; preserves regulation independence (engine still loads regulation by config, no coupling to method names). Safe for production. |
| 1 | **Dispatch Option B:** Direct invocation / switch in Java | ⚠️ **Risks independence** | If the switch or call map is generated in the **engine**, the engine becomes coupled to the regulation’s wage type set. Independence is preserved only if the generated code lives **inside** the regulation artifact. Prefer Option A. |
| 2 | **Concurrency Option A:** Run Java with parallelism=1 | ⚠️ **Benchmark-only; not for production** | In production you use N threads to utilize CPU. Forcing 1 thread is only to make the comparison fair; it is not a good production setting. |
| 2 | **Concurrency Option B:** Add TS multi-threaded path | Optional | Would be a real feature for production if you want parallelism on TS; not inherently bad, but not required for a fair comparison unless you compare N threads vs N workers. |
| 3 | **Allocation:** Reuse constants (static/shared BigDecimal/Decimal) | ✅ **Good practice** | Reduces allocation and is standard practice in both runtimes. Does not affect regulation independence (constants live inside the regulation). Safe for production. |
| 3 | **Allocation Option B:** Report GC/allocation metrics | ✅ **Optional, production-friendly** | Visibility only; can be used in production for diagnostics. |
| 4 | **Warm-up:** Add warm-up pass or run payrun twice, report second run | ⚠️ **Benchmark-only; not for production** | In production you do not run the payrun twice or discard the first portion of work. Use only when comparing runs to remove JVM warm-up from the metric. |

**Summary:** For **production**, adopt: (1) Method cache in Java (Option A), (2) configurable parallelism with default N threads (do **not** default to 1), (3) shared constants in both regulations, (4) optional allocation/GC metrics. Do **not** adopt for production: parallelism=1, or warm-up / run-twice for reporting. Option B direct invocation in Java: only if the generated code stays inside the regulation artifact so independence is preserved.

---

## 7. TypeScript runtime regulation fetch and impact on the comparison

The TypeScript POC now supports **fetching the regulation artifact at runtime** from S3, in addition to using a bundled regulation. This aligns the TS model with Java (engine + separate regulation artifact) and affects how we interpret “fair” comparison and **regulation artifact independence**.

### How TS runtime fetch works (current code)

- **Handler** (`handler.ts`): If the event includes `regulationArtifactBucket` (and related fields), the handler uses `getEvaluatorFromPull()` instead of `getBundledEvaluator()`.
- **RegulationArtifactFetcher** (`RegulationArtifactFetcher.ts`): `fetchAndExtract(s3, bucket, prefix, regulationId, version, extractBaseDir)` downloads a tarball from S3 at `{prefix}/{regulationId}/{version}.tgz`, extracts it to e.g. `/tmp/regulations/{regulationId}-{version}`, and returns the package path. If the entry point (`dist/index.js`) already exists at that path, it returns immediately (cache). Otherwise it uses the `tar` package to extract (no system tar; works in Lambda).
- **Loader:** The handler builds a `RegulationRegistry` with `registerResolved(regulationId, version, packagePath, 'createEvaluator')` and uses `RegulationEvaluatorLoader.getEvaluator()` to dynamic-`import()` the extracted package’s `dist/index.js` and call the factory. Pulled evaluators are cached in `pulledEvaluatorCache` by `regulationId:version`.

So the regulation can be **built and versioned independently** (e.g. from a separate repo or pipeline), published as a `.tgz` to S3, and the engine loads it at runtime by id+version. The engine depends only on the **contract** (export `createEvaluator` returning `RegulationEvaluator`), not on the regulation’s internals.

### Impact on the analysis

| Aspect | Bundled TS (default) | TS with runtime pull | Java (JAR in image or CodeArtifact) |
|--------|----------------------|----------------------|-------------------------------------|
| **Regulation artifact** | Compiled into Lambda; not independent | Separate tgz in S3; **independent** | Separate JAR; **independent** |
| **Fair comparison** | Engine + one regulation shipped together | Engine + regulation loaded by id/version; same **model** as Java | Engine + regulation JAR loaded by config |
| **Initialization cost** | Load bundled module at cold start | First time: S3 GetObject + extract + dynamic import; then cached | JAR on classpath or in plugins dir; class loading at JVM start |

- **Independence:** When TS uses **runtime pull**, the regulation is an independent artifact (tgz in S3), like the Java JAR. The engine does not need to be rebuilt when the regulation changes; only the artifact (tgz) is updated. So for **production** and for **comparing “engine + independent regulation”**, both sides should use the same model: Java loads JAR from plugins/CodeArtifact; TS uses runtime pull from S3 (with the same regulation content, e.g. 60 wage types, no YTD, or 205 + YTD).
- **Metrics:** When using runtime pull, the first invocation may include fetch+extract time in **initialization** (or in engine phase if the loader is invoked there). For a fair comparison, either (a) use a warm invocation (pulled evaluator already cached), or (b) report initialization separately so that “engine execution” is pure computation, not fetch. The current handler places fetch in the path that builds the evaluator before the timed phases; if that runs inside `initialization`, then `engineExecutionMs` remains comparable.
- **Standardization checklist:** When comparing Java vs TS with **independent** regulation artifacts, use TS **runtime pull** (event with `regulationArtifactBucket`, `regulationArtifactPrefix`, `regulationId`, `regulationVersion`) and the same regulation content (same wage types, same YTD or not) on both sides. That keeps the comparison fair and preserves regulation independence on both runtimes.
