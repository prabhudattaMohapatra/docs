# TypeScript vs Java POC: Theoretical Speed Across the Three Phases

This doc compares the two POCs **theoretically** for the three timed phases: **S3 download**, **S3 upload**, and **regulation computation**. It is based on how each implementation and metrics are configured, not on benchmark results.

---

## 1. How each phase is implemented and measured

### Java POC (Fargate + entrypoint.sh)

| Phase | Implementation | What is timed |
|-------|----------------|---------------|
| **S3 download** | Shell: `aws s3 sync "s3://…" "$STUB_DIR/"` | Wall time of the sync (DL_START → DL_END). |
| **Regulation computation** | JVM: `Main` loads all stub files from disk, then runs a **fixed thread pool** (`availableProcessors()` threads), each thread processes employees via `MinimalPayrun.run()` + writes result JSON to disk. | Wall time from JVM start to JVM exit (ENGINE_START → ENGINE_END). Includes: reading stub files, parallel payrun, writing result files. |
| **S3 upload** | Shell: `aws s3 sync "$RESULTS_DIR/" "s3://…"` | Wall time of the sync (UL_START → UL_END). |

- S3 is **outside** the JVM: download then run JAR then upload. So `s3DownloadMs` and `s3UploadMs` are pure shell `aws s3 sync` times.
- AWS CLI `s3 sync` uses internal parallelism (default `max_concurrent_requests` is typically 10).

### TypeScript POC (Lambda + handler)

| Phase | Implementation | What is timed |
|-------|----------------|---------------|
| **S3 download** | In-process: `S3DataProvider.downloadAllRecords(files, concurrency)` with **default concurrency 50**. Each file = one `GetObject`; up to 50 in flight. Data goes to **memory** (no disk). | `metrics.startPhase('s3Download')` → `metrics.endPhase('s3Download')`. |
| **Regulation computation** | In-process: single **sequential** `for (const record of records) { processEmployee(...) }`. One employee at a time, one evaluator, same thread. | `metrics.startPhase('engineExecution')` → `metrics.endPhase('engineExecution')`. |
| **S3 upload** | In-process: `S3ResultWriter.uploadResults(..., concurrency)` with **default concurrency 50**. Each result = one `PutObject`; up to 50 in flight. Data from **memory**. | `metrics.startPhase('s3Upload')` → `metrics.endPhase('s3Upload')`. |

- All three phases run inside one Lambda invocation; phases are separated by `MetricsCollector` start/end.

---

## 2. Theoretical comparison: which is faster?

### S3 download

| Factor | Java (Fargate) | TypeScript (Lambda) |
|--------|----------------|---------------------|
| Concurrency | `aws s3 sync` — typically ~10 concurrent requests (CLI default). | **50** concurrent `GetObject` calls (configurable via `event.concurrency`). |
| Data path | Network → **disk** (STUB_DIR), then JVM reads from disk. | Network → **memory** (no disk). |
| Process boundary | Separate process (CLI); then JVM starts and reads files. | Single process; no handoff. |

**Theoretical winner: TypeScript.** Higher concurrency (50 vs ~10) and no disk round-trip in the timed path. For many small files, TS can saturate the connection pool better. Java’s sync is still efficient but uses lower default parallelism and an extra disk write/read.

---

### S3 upload

| Factor | Java (Fargate) | TypeScript (Lambda) |
|--------|----------------|---------------------|
| Concurrency | `aws s3 sync` — again ~10 concurrent. | **50** concurrent `PutObject` calls. |
| Data path | JVM writes result JSONs to **disk** (RESULTS_DIR); then shell runs sync (disk → S3). | Build JSON in memory → **direct PutObject** (no result disk write in the timed upload phase). |

**Theoretical winner: TypeScript.** Higher concurrency and no intermediate disk write for results. Java’s engine phase includes writing to disk; upload phase then reads that disk — so upload is “disk → S3” with sync’s parallelism.

---

### Regulation computation

| Factor | Java (Fargate) | TypeScript (Lambda) |
|--------|----------------|---------------------|
| Per-employee work | Same logical workload (France rules, wage types; Java has 205 wage types + 12‑month YTD simulation). | Same contract; TS regulation may have fewer wage types / no YTD — if aligned, same workload. |
| Parallelism | **Multi-threaded:** `Executors.newFixedThreadPool(availableProcessors())`, one employee per task, `ThreadLocal` evaluator per thread. On a 2‑vCPU Fargate task, 2 employees in parallel. | **Single-threaded:** one `for` loop over employees; one employee at a time. |
| CPU available | Fargate: N vCPU (e.g. 2). Java uses N threads. | Lambda: allocated vCPU scales with memory (e.g. 1–2 vCPU at typical configs). Only one thread used for computation. |

**Theoretical winner: Java** — *when per-employee workload is the same.* For the same per-employee workload and same CPU size, Java uses multiple cores for multiple employees at once; TS uses one core. So in theory **engine execution time** (wall clock) could be **lower in Java** for the same number of employees (e.g. ~2× on a 2‑vCPU task). **In practice** (see §4), when regulations are aligned (60 wage types, no YTD), TS is still **~4× (10k) to ~7× (1k) faster** in the sampled bucket runs — likely due to reflection and JVM overhead in Java. When Java uses the heavy regulation (205 wage types + YTD), TS is ~10× faster because of the workload difference.

#### Platform impact: Fargate vs Lambda on regulation computation

**Yes — the platform (Fargate vs Lambda) does affect this phase.**

| Aspect | Java on Fargate | TypeScript on Lambda |
|--------|------------------|----------------------|
| **Cold start** | No per-run cold start for the engine. Container starts once; JVM starts once and runs the full batch. Any JVM boot/JIT cost is amortized over the whole run. | **Cold start** applies to the invocation: new execution environment must load Node runtime, handler, and dependencies (e.g. regulation module). That cost usually appears in **initialization** (and total duration); the timed `engineExecution` phase starts after init. So the *first* invocation can have much higher **total** time; reported `engineExecutionMs` may be similar once the phase starts. On **warm** invocations, no cold start. |
| **CPU model** | **Dedicated** vCPU and memory for the task. You choose e.g. 2 vCPU, 4096 MB; you get that for the entire run. Predictable. | **Proportional** CPU: vCPU scales with memory (e.g. 1769 MB ≈ 1 vCPU). More memory = more CPU for that single thread. TS still uses only one thread for the loop, so extra vCPU improves that thread’s speed but does not add parallelism across employees. |
| **Predictability** | Same task size and task definition → very similar engine duration across runs. No shared-environment throttling. | Lambda runs in a shared environment. Under load or with many concurrent invocations, CPU can be throttled or noisier. Engine duration may vary more run-to-run than on Fargate. |
| **Duration limit** | No practical limit for a single task (hours if needed). One container does the whole batch. | **15-minute** max per invocation. For very large batches you must chunk into multiple invocations (or use Step Functions, etc.), which adds overhead and can make “regulation computation” time harder to compare to a single Fargate run. |

**Summary:** Fargate gives dedicated, predictable CPU and no invocation cold start for the JVM, so **regulation computation** duration is mainly driven by code (multi-threaded Java) and task size. Lambda adds possible cold start (worse total/init on first run), variable CPU (shared environment), and a 15‑min cap; the *reported* engine phase may still be accurate if measured after init, but total time and consistency can be worse than Fargate for this phase.

**How this actually changes regulation computation duration:**

- **Java on Fargate → shorter engine duration.** The task has dedicated vCPU (e.g. 2); Java uses a thread per vCPU and processes that many employees in parallel. So wall-clock engine time ≈ (number of employees ÷ vCPU) × time per employee, plus overhead. More vCPU → shorter duration. The platform doesn’t throttle or share that CPU, so the same batch gives roughly the same engine duration every run.

- **TypeScript on Lambda → longer engine duration for the same batch.** The handler uses a single thread, so wall-clock engine time ≈ number of employees × time per employee. There is no parallelism across employees, so doubling the batch doubles the phase. Lambda’s CPU is proportional to memory (one thread can use at most one core’s worth effectively). So the **regulation computation duration** is inherently longer than Java on Fargate for the same N and similar per-employee work (often on the order of 2× or more on a 2‑vCPU Fargate task). In addition, Lambda’s shared environment can mean the same TS code sometimes gets less CPU (throttling or contention), so the same batch can take **longer** on some invocations; Fargate doesn’t have that variance.

- **Cold start does not lengthen the reported `engineExecutionMs`.** Cold start adds time to **initialization** (and total invocation time) before the engine phase starts. The metric for regulation computation is the loop over employees only, so it stays the same whether the invocation was cold or warm. What changes is **time until computation starts** and **total duration**, not the engine phase value itself.

- **15‑minute Lambda cap** doesn’t slow the phase; it limits how many employees fit in one run. For batches that would take e.g. 20 minutes single-threaded, you must split across invocations, so you get multiple shorter engine phases instead of one long one (and extra overhead between invocations).

So: **Fargate + Java** reduces regulation computation duration by using multiple cores and dedicated CPU; **Lambda + TS** keeps it single-threaded and can make it more variable, so the same workload typically yields a **longer and less consistent** engine phase on Lambda.

---

## 3. Summary table (theoretical)

| Phase | Faster (theoretical) | Main reason |
|-------|----------------------|-------------|
| **S3 download** | **TypeScript** | 50-way concurrency vs ~10, and download → memory (no disk). |
| **S3 upload** | **TypeScript** | 50-way concurrency vs ~10, and in-memory → S3 (no result disk write). |
| **Regulation computation** | **Java** | Multi-threaded (N workers) vs single-threaded loop; better CPU utilization. |

So **overall**:

- If the run is **I/O-bound** (many small S3 objects, modest per-employee CPU): **TypeScript** can win on total time because S3 phases are shorter and Lambda’s single-threaded engine may still finish quickly.
- If the run is **CPU-bound** (many employees, heavy per-employee rules): **Java** can win because engine execution is parallelized; S3 phases may be a smaller share of total time.

**Regulation computation and platform:** Java on Fargate benefits from dedicated vCPU, no Lambda-style cold start for the engine, and more predictable run-to-run duration. TypeScript on Lambda can see higher total time on cold start (init phase) and more variable engine duration due to shared CPU; the 15‑min invocation limit may also force chunking for very large batches. So the platform (Fargate vs Lambda) does impact regulation computation time and its consistency; see the “Platform impact: Fargate vs Lambda” subsection under Regulation computation above.

Metrics configuration in both POCs correctly isolates the three phases (download, engine, upload), so real runs can be compared against this theoretical picture.

---

## 4. Observed results and commit correlation

Metrics are in bucket `payroll-engine-poc-dev-258215414239-us-east-1`. Java regulation complexity was added in a single commit; correlating with that commit gives a fair comparison.

### When the Java regulation gained extra complexity

In repo **payroll-regulations-poc** (via `git log -S simulateYtd` and `git show eb723f7`):

| Commit | Date (UTC) | Change |
|--------|------------|--------|
| **eb723f7** | **2026-02-23 06:30:15** | “Add synthetic wage types and YTD simulation for France regulations” — added `simulateYtd()` and 145 synthetic wage types (FranceRules.java + WageTypes.json). Before this: **60 wage types, no YTD**. After: **205 wage types + 12‑month YTD** per wage type. |

So any Java run whose **image/task** was built from code **before** that commit used the lighter regulation (60 wage types, no YTD). Runs after the commit (and after a deploy that picked up eb723f7) use the heavy regulation.

### Like-for-like comparison: Java (old regulation) vs TypeScript

Both sides use **60 wage types, no YTD**. Java runs that **predate** the complexity commit (run times before 2026-02-23 06:30 UTC, or from earlier days) are treated as “old” regulation. TypeScript regulation has been 60 wage types and no YTD throughout.

**10k employees, 2 vCPU / 4096 MB:**

| | Java (Fargate, old regulation) | TypeScript (Lambda) |
|---|--------------------------------|---------------------|
| **Source** | `results/run-20260223-055249/metrics.json` (run at 05:52 UTC Feb 23, **before** 06:30 commit) | `results-ts/10k/run-20260225060528./metrics.json` |
| **engineExecutionMs** | **6 225** | **1 574** |
| **employeesPerSecond** | 1 606 | 6 353 |
| **avgMsPerEmployee** | 0.62 | 0.16 |
| **Config** | 2 vCPU, 4096 MB | 2.32 vCPU, 4096 MB, arm64 |

**1k employees, 2 vCPU / 4096 MB (Java); TS same config:**

| | Java (Fargate, old regulation) | TypeScript (Lambda) |
|---|--------------------------------|---------------------|
| **Source** | `results/run-20260223-051637/metrics.json` (05:16 UTC Feb 23, before commit) | `results-ts/1k/run-20260225053748./metrics.json` |
| **engineExecutionMs** | **1 881** | **246** |
| **avgMsPerEmployee** | 1.88 | 0.25 |

So with **comparable regulation** (60 wage types, no YTD):

- **10k:** TS engine time **~4× shorter** (1 574 ms vs 6 225 ms).
- **1k:** TS engine time **~7.6× shorter** (246 ms vs 1 881 ms).

So even when Java has the same workload as TS (no extra wage types, no YTD), **TypeScript regulation computation is still several times faster** in these runs. Possible reasons: no reflection (TS uses direct method calls; Java uses `Method.invoke()` per wage type), lower per-call overhead, or different JVM vs Node behaviour; the theoretical advantage of Java’s multi-threading is outweighed in practice by these effects.

### Heavy Java regulation vs TypeScript (later runs)

After the complexity commit (and deploy), Java runs use **205 wage types + YTD**:

| | Java (Fargate, new regulation) | TypeScript (Lambda) |
|---|--------------------------------|---------------------|
| **Source** | `results/run-20260223-074509/metrics.json` (07:45 UTC Feb 23, **after** 06:30 commit) | `results-ts/10k/run-20260225060528./metrics.json` |
| **engineExecutionMs** | **15 863** | **1 574** |
| **employeesPerSecond** | 630 | 6 353 |
| **avgMsPerEmployee** | 1.59 | 0.16 |

Here TS is **~10× faster** because the **per-employee workload** is much larger on Java (205 wage types + 12‑month YTD) than on TS (60 wage types, no YTD). The ~4× gap from the like-for-like comparison is then amplified by the extra work on Java.

### Conclusion

- **Commit correlation:** Complexity (145 synthetic wage types + `simulateYtd`) was added in **payroll-regulations-poc** at **eb723f7**, **2026-02-23 06:30:15 UTC**. Runs before that (or from images built before that) use the old regulation (60 wage types, no YTD).
- **Like-for-like (60 wage types, no YTD):** TypeScript regulation computation is **~4× (10k) to ~7× (1k) faster** than Java in the sampled runs, despite Java using 2 threads. So in practice, runtime/overhead (e.g. reflection, JVM) outweighs the theoretical parallelism gain for this workload.
- **Heavy Java (205 + YTD) vs TS:** TS is **~10× faster** because Java does far more work per employee; the ~4× base gap is then multiplied by the workload difference.

---

## 5. Why TypeScript regulation computation is faster than Java (in practice)

With comparable workload (60 wage types, no YTD), TS is still ~4× (10k) to ~7× (1k) faster. The following reasons, grounded in the two codebases, explain why the theoretical advantage of Java’s multi-threading is outweighed.

### 5.1 Reflection vs direct dispatch

- **Java:** Each wage type is dispatched via **reflection**. In `FranceRegulationEvaluator.dispatch()` the code does `FranceRules.class.getMethod(entry.methodName(), FranceRulesContext.class)` and `m.invoke(null, ctx)` **on every call**. So for 60 wage types × N employees you get 60×N `getMethod` + `invoke` operations. Reflection is expensive: it avoids inlining, adds checks, and is harder for the JVM to optimize.
- **TypeScript:** The evaluator does a map lookup by wage type number and then calls `(FranceRules as any)[entry.methodName](ctx)` — a **direct** property lookup and function call. No reflection. V8 can inline and optimize this path. So per wage type, TS pays the cost of one map lookup and one call; Java pays for method lookup and reflective invocation every time.

This alone can account for a large share of the gap when there are many small calls (60 or 205 per employee).

### 5.2 Concurrency overhead vs a single hot loop

- **Java:** The engine uses a fixed thread pool and submits one task per employee. Each task acquires a thread, gets a `ThreadLocal` evaluator, runs the payrun, and writes the result. So there is task submission, thread scheduling, and (for 10k employees) 10k task handoffs. When **per-employee work is small** (e.g. 60 wage types with simple rules), the fixed cost of the concurrent machinery (scheduling, context switches, cache effects) can be significant relative to the actual computation.
- **TypeScript:** A single thread runs one tight loop: for each employee, call the evaluator and push results. No thread pool, no task queue, no coordination. One vCPU is used efficiently for the whole phase. For this kind of workload, a single-threaded loop can have **lower overhead** than a multi-threaded design, so the lack of parallelism is more than offset by the simplicity of the execution path.

### 5.3 Object allocation and decimal handling

- **Java:** `BigDecimal` is immutable; operations like `add`, `multiply` typically produce new instances. So each wage type evaluation can create several temporary `BigDecimal` objects. With 60 wage types × 10k employees, that is a lot of short-lived allocations and more work for the garbage collector. The context and collector maps also involve boxing and map updates.
- **TypeScript:** `decimal.js` (Decimal) is also immutable in style, but the number of allocations and the way V8 optimizes small, hot structures can differ. In addition, the single-threaded loop keeps allocation pressure local and avoids cross-thread references, which can reduce GC and cache overhead compared to Java’s multi-threaded allocation pattern.

So even with “equivalent” decimal semantics, the **runtime cost** of the Java path (allocation + GC) can be higher for this pattern.

### 5.4 JVM warm-up and measurement

- **Java:** The reported `engineExecutionMs` is the wall time from JVM start to JVM exit. That includes class loading, JIT compilation of hot paths (e.g. `FranceRules` and the reflection path), and any warm-up before the JIT has fully optimized the inner loop. So the first portion of the run may be slower; the metric does not separate “warm” from “cold” execution.
- **TypeScript:** On Lambda, the handler (and the regulation module) are typically already loaded by the time the “engine execution” phase is timed. So the measured phase is mostly **steady-state** execution. That can make the TS number look better even if the “fully warmed” Java performance would be closer.

Warm-up is a contributing factor; it is unlikely to explain the full ~4× gap but can widen it.

### 5.5 Summary

| Factor | Java (Fargate) | TypeScript (Lambda) | Effect |
|--------|----------------|----------------------|--------|
| **Dispatch** | `getMethod` + `invoke` per wage type | Map lookup + direct call | TS avoids reflection cost on every of 60×N (or 205×N) calls. |
| **Concurrency** | Thread pool, 10k tasks, scheduling | Single loop, one thread | TS avoids task/thread overhead when per-employee work is small. |
| **Allocation / GC** | Many temporary `BigDecimal`s, multi-threaded allocation | Fewer allocations in a single-threaded hot path | TS can have lower allocation and GC cost for this workload. |
| **Warm-up** | Engine phase includes JVM/JIT warm-up | Phase measured after init; often warm | TS metric is more “steady-state”; Java includes cold start of the JVM. |

Together, these explain why **in practice** TypeScript regulation computation is several times faster than Java for the same logical workload, even though in theory Java’s multi-threading could have been an advantage. The design choices (reflection for pluggable rules, thread-per-employee) suit flexibility and scaling to many employees when each employee is expensive; for **small per-employee work**, a single-threaded, direct-dispatch design like the TS POC has lower overhead and wins on observed duration.
