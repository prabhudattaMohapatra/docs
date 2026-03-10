# TS Fargate POC — Optimisations to Improve Time Metrics

These changes target the gaps identified in [TS_POC_LAMBDA_VS_FARGATE_ANALYSIS.md](./TS_POC_LAMBDA_VS_FARGATE_ANALYSIS.md). Implement in order for maximum impact.

---

## Implementation status

| # | Change | Status |
|---|--------|--------|
| 1a | S3 download in Node via `S3DataProvider` (concurrency 50) | **Done** |
| 1b | S3 upload in Node via `S3ResultWriter` (concurrency 50) | **Done** |
| 1c | Node reports S3 phases; simplify entrypoint | **Done** |
| 2 | Engine loop over in-memory `records` only; no disk I/O in loop | **Done** |
| 3 | Env-driven S3 concurrency (`S3_CONCURRENCY`, default 50) | **Done** |
| 4 | ARM64 task + image | **Done** |
| 5 | Pre-bundle regulation in image | **Skipped** (per request) |

**Summary:** Optimisations 1–4 are implemented. Fargate runner now uses parallel S3 download/upload in Node (same as Lambda), engine runs on in-memory records only, entrypoint only runs Node, task definition uses ARM64 and `S3_CONCURRENCY=50`. Rebuild image with `scripts/fargate/build-image.sh` (arm64), deploy stack, then run a task and compare metrics.

---

## 1. Move S3 I/O into Node (largest impact)

**Current:** Entrypoint runs `aws s3 sync` to download stubs and upload results; Node reads/writes disk.  
**Goal:** Do S3 in Node with the same parallel pattern as Lambda so **s3DownloadMs** and **s3UploadMs** drop sharply.

### 1a. S3 download in Node

- In **`fargate-runner.ts`**:
  - Read from env: `STUB_DATA_BUCKET`, `STUB_DATA_PREFIX` (already available from task def).
  - After regulation load, **replace** “read index from disk + loop readFileSync” with:
    - `S3DataProvider(s3, bucket, prefix).downloadIndex()` → list of file names.
    - `provider.downloadAllRecords(filesToProcess, concurrency)` → `StubDataRecord[]` in memory.
  - Use **concurrency 50** (same as Lambda) or make it configurable via env (e.g. `S3_CONCURRENCY=50`).
- **Entrypoint:** Remove the stub-data `aws s3 sync` (or make it optional so Node is the default path). Ensure `STUB_DATA_BUCKET` and `STUB_DATA_PREFIX` are still passed so Node can download.

**Effect:** s3DownloadMs should move from ~55–106 s toward Lambda-like ~15 s, and stub data is in memory for the engine.

### 1b. S3 upload in Node

- In **`fargate-runner.ts`**:
  - Read from env: `RESULTS_BUCKET`, `RESULTS_PREFIX` (already in task def).
  - Build **`EmployeeResult[]`** in memory (same shape as Lambda: `{ record, wageResults }`).
  - In the engine phase, instead of `writeFileSync(..., employeeId.json)`, push `{ record, wageResults }` into an array.
  - After the loop, call **`S3ResultWriter(s3, bucket, outputPrefix).uploadResults(employeeResults, wageTypeNames, concurrency)`** with the same concurrency (e.g. 50).
  - Upload metrics with **`writer.uploadMetrics(metrics)`** (reuse the same `S3ResultWriter` pattern; you may need to extend it or call `putObject` for `metrics.json` on the same prefix).
- **Entrypoint:** Remove the results `aws s3 sync` (or make it optional). Node is the only writer to the results prefix.

**Effect:** s3UploadMs should move from ~50 s toward Lambda-like ~18 s.

### 1c. Metrics and entrypoint

- Node now reports **s3DownloadMs** and **s3UploadMs** itself (time the two phases in `fargate-runner.ts`).
- **totalMs** = wall time of the whole Node run (or sum of phases).
- Entrypoint can be reduced to: set env, run Node, `exit $?`. No merge step for S3 timings if Node does all S3.

**Rough expected impact:** Total run time could drop from ~115–163 s to the ballpark of ~40–60 s (S3 phases ~3–4x faster; engine unchanged in this step).

---

## 2. Engine on in-memory data only

**Current:** Engine loop does `readFileSync(join(STUB_DATA_DIR, stubPath))` for each of 10k files.  
**After 1a:** Records are already in memory; no disk read in the engine phase.

- In the engine phase, iterate over **`records`** (from `downloadAllRecords`) instead of over `stubFiles` + `readFileSync`.
- Remove all `readFileSync`/`writeFileSync` for stub and result files inside the timed engine loop.

**Effect:** **engineExecutionMs** should improve (no disk I/O in the loop, better CPU utilisation). How much depends on how much of the current ~5.8 s was wait-on-disk.

---

## 3. S3 concurrency (tuning)

- Use **50** to match Lambda. If you want to experiment on Fargate (more memory/CPU), try **`S3_CONCURRENCY=100`** via env and pass it into `downloadAllRecords` / `uploadResults`.
- Keep a single shared **`S3Client`** (default config is fine; it connection-pools).

---

## 4. Optional: ARM Fargate (further engine gain)

**Current:** Task definition uses **x86_64**; Lambda uses **arm64** and shows ~3.6x faster engine time.  
**Change:**

- In **`template-fargate.yaml`**, set **`RuntimePlatform.CpuArchitecture: ARM64`**.
- Build and push an **ARM64** image (e.g. `--platform linux/arm64` in `build-image.sh`), and point the task definition image to that tag (or use a multi-arch manifest if you support both).

**Effect:** **engineExecutionMs** can move toward Lambda-like values (same ISA). S3 phases are unaffected.

---

## 5. Optional: Regulation load

- **Current:** One S3 pull + load per task (~500–650 ms). Acceptable unless you want to squeeze every second.
- **Optional:** Pre-bundle the regulation in the image so Fargate uses `getBundledEvaluator()` and **regulationLoadMs** ≈ 0 when not using runtime pull.

---

## Implementation checklist (summary)

| # | Change | Main files | Expected impact | Status |
|---|--------|------------|------------------|--------|
| 1a | S3 download in Node via `S3DataProvider` (concurrency 50) | `fargate-runner.ts`, entrypoint | s3DownloadMs: ~55–106 s → ~15–25 s | Done |
| 1b | S3 upload in Node via `S3ResultWriter` (concurrency 50) | `fargate-runner.ts`, entrypoint | s3UploadMs: ~50 s → ~18–25 s | Done |
| 1c | Node reports S3 phases; simplify entrypoint | `fargate-runner.ts`, `entrypoint.sh` | Correct metrics; less moving parts | Done |
| 2 | Engine loop over in-memory `records` only; no disk I/O in loop | `fargate-runner.ts` | engineExecutionMs: some gain (fewer syscalls, better CPU use) | Done |
| 3 | Env-driven S3 concurrency (e.g. 50 or 100) | `fargate-runner.ts`, `template-fargate.yaml` | Optional tuning | Done |
| 4 | ARM64 task + image | `template-fargate.yaml`, `build-image.sh`, Dockerfile | engineExecutionMs: large gain (~2–3x) | Done |
| 5 | Pre-bundle regulation (optional) | Dockerfile / image | regulationLoadMs → ~0 when not pulling | Skipped |

Implementing **1a + 1b + 2** (and 1c) gives the largest improvement with minimal risk by reusing the same Lambda S3 and engine patterns inside the existing Fargate Node app.

---

## Complex regulation: execution time (TS vs Java)

**Observed (Fargate, 10k employees, 2 vCPU / 4 GB):**

| Regulation | Wage types | Engine time | Employees/sec | Avg ms/employee |
|------------|------------|-------------|--------------|-----------------|
| Simple (S3 1.0.2 old) | ~few | 1.26 s | 7,911 | 0.13 |
| Complex (S3 1.0.2 new) | **205** | **155 s** | **64.5** | **15.5** |

So engine time increased **~123×** when switching to the complex regulation (205 wage types). Result size increased from ~85 MB to ~269 MB (more wage types per employee).

**Why the increase is so large**

1. **Many more evaluations** – 10k × 205 = **2.05M** `evaluateWageType` calls. Simple regulation had far fewer wage types, so much fewer calls.
2. **Per-call cost in Node** – Each call goes through the regulation evaluator (method dispatch, `getCaseValue` / `getCaseValueString` Map lookups, Decimal.js work). In Java, the same loop benefits from JIT, cheaper method calls, and often primitives or reused BigDecimal.
3. **No batching** – The TS engine is strictly “for each employee, for each wage type, evaluate”. There is no shared work across employees or wage types, so cost scales linearly with (employees × wage types).
4. **Decimal and allocations** – Every result is `new Decimal(...)`; context creates two Maps and many Decimals per employee. That’s a lot of allocations and GC vs a typical Java implementation.

**Is this expected?**

- A **large** increase (tens of times) is expected when going from a handful of wage types to 205: more work per employee and more results.
- A **~120×** increase suggests that, on top of the extra work, **per-evaluation overhead in Node is much higher than in Java** (runtime, allocations, Decimal). So the jump is a combination of “more work” and “TS/Node is slower per unit of work” for this style of engine.
- If Java on the **same** complex regulation and same 10k input does not show a similar jump (e.g. only a few times slower than Java simple regulation), then the gap is **implementation/runtime**, not just “more wage types.”

**Recommendations**

1. **Compare like-for-like** – Run Java engine on the same 10k input and same complex regulation (205 wage types), same task size. Compare engine time and employees/sec. If Java stays in the “few seconds” range, the TS engine is a good candidate for optimization.
2. **Profile the TS path** – Use Node profiler or coarse timers (e.g. time per wage type or per employee) to see if a few wage types or one phase dominate.
3. **Optimize hot path** – Reuse context/Decimals where possible, avoid per-call allocations, consider caching repeated case-value lookups or wage-type results if the regulation allows.
4. **Scale resources for complex runs** – For 155 s engine time on 2 vCPU, consider 4 vCPU (or 4 vCPU + 8 GB) to bring wall time down; cost will go up but latency will drop.

---

## Optimizations to decrease execution time (TS POC)

Prioritized by impact and effort. Apply in order for best results.

### 1. Reduce Decimal allocations in the hot path (high impact, medium effort)

**Where:** `FranceRegulationEvaluator.simulateYtd`, `FranceRulesContextImpl.getSlab`, rule methods.

- **simulateYtd** runs once per wage type (2.05M times for 10k × 205). Each call does ~12 iterations with many `new Decimal(...)`. Reuse a small pool of Decimals or use static constants where possible (e.g. `Decimal(0)`, `Decimal(1)`, `Decimal(m)` for m=1..12).
- **getSlab('Constants2025', key)** returns `new Decimal(val)` every time. Cache `Decimal` instances for constants in a `Map<string, Decimal>` so repeated lookups don’t allocate.
- In **FranceRules**: where a rule only needs a simple number, use a single shared `Decimal(0)` for “zero” results instead of `new Decimal(0)` per call (if the API allows returning a shared instance; otherwise keep creating for correctness).

**Files:** `packages/engine/plugins/poc-regulation/src/FranceRegulationEvaluator.ts`, `FranceRulesContext.ts`, `FranceRules.ts`.

### 2. Make simulateYtd optional or lighter (high impact, low effort)

**Where:** `FranceRegulationEvaluator.evaluateWageType` calls `this.simulateYtd(result)` for every wage type.

- If the POC doesn’t need YTD simulation, gate it behind a flag (e.g. env `SKIP_YTD_SIMULATION=1`) and skip the call. That removes a large chunk of Decimal work.
- If you need it, consider a lighter version (e.g. fewer iterations or a closed form) for the POC and keep the full version for production.

**Files:** `FranceRegulationEvaluator.ts`.

### 3. Reuse context / avoid duplicate work per employee (medium impact, medium effort)

**Where:** `handler.ts` `processEmployee`, `StubEvaluationContext` constructor.

- **StubEvaluationContext** builds two Maps and parses every case value string into `Decimal` once per employee. That’s required today, but you can avoid re-creating Maps if you had a “context factory” that reuses Map instances and clears them (would require a small API change to support reset/reuse).
- **translateCaseValues** and **Object.entries(caseValues)** run once per employee; ensure you’re not doing extra object work (e.g. avoid copying the whole object again if it’s already in the right shape).

**Files:** `packages/engine/src/StubEvaluationContext.ts`, `packages/engine/src/lambda/handler.ts`.

### 4. Pre-allocate results array (low impact, low effort)

**Where:** `handler.ts` `processEmployee`: `const results: WageTypeResult[] = [];` then `results.push(...)` 205 times.

- Use `const results: WageTypeResult[] = new Array(wageTypeNumbers.length)` and assign by index: `results[i] = { wageTypeNumber: num, value: ... }`. Reduces array growth and allocations.

**Files:** `packages/engine/src/lambda/handler.ts`.

### 5. Parallelize the employee loop with Worker Threads (high impact, high effort) — **implemented**

**Where:** `fargate-runner.ts` engine phase: single-threaded `for (const record of records) { processEmployee(...) }`.

- **Implemented:** Records are split into N chunks (N = min(allocatedVCpu, availableParallelism(), records.length, ENGINE_WORKER_COUNT default 8)). Each worker loads its own evaluator (`getBundledEvaluator()` or `getEvaluatorFromPull` with a per-worker extract path), runs `processEmployee` for its chunk, and returns results with Decimal values serialized as strings. Main thread rehydrates and merges. Workers are terminated after use.
- **Bundled regulation:** Each worker calls `getBundledEvaluator()`. **S3 pull:** Each worker uses `getEvaluatorFromPull(..., extractBase: /tmp/regulations_${index})` so extract dirs don’t conflict.
- **Override:** Set `ENGINE_WORKER_COUNT` (e.g. `1`) to force single-threaded engine (no workers).

**Files:** `packages/engine/src/fargate-runner.ts`, `packages/engine/src/engine-worker.ts`.

### 6. Profile and optimize the heaviest wage types (medium impact, low effort after profile)

**Where:** Regulation rule methods.

- Run Node with `--cpu-prof` or add coarse timers (e.g. `Date.now()` before/after each `evaluateWageType` or per wage-type name) on a small subset of employees. Identify the 5–10 slowest wage types.
- Optimize those methods: fewer `getCaseValue` calls (cache in local vars), fewer temporary Decimals, or a different algorithm. Often a small number of wage types dominate.

**Files:** `FranceRules.ts`, `FranceRegulationEvaluator.ts`.

### 7. Scale Fargate resources (immediate, no code change)

**Where:** `template-fargate.yaml`: `Cpu`, `Memory`, env `TASK_CPU`, `TASK_MEMORY_MB`.

- Increase to 4 vCPU and 8 GB (or 16 GB if memory was close to limit). Engine time should drop (often near linearly with vCPU for CPU-bound work). Cost per run goes up but latency goes down.

**Files:** `template-fargate.yaml`.

### 8. Use a faster Decimal library or numbers where safe (medium impact, high effort)

**Where:** All regulation code using `decimal.js`.

- Evaluate swapping `decimal.js` for a lighter/faster library (e.g. `big.js` or a minimal decimal type) if the POC doesn’t need full precision everywhere. Or use primitive `number` for intermediate steps where rounding is acceptable and only convert to Decimal for final results. Requires careful audit of precision and tests.

**Files:** Regulation plugin, `StubEvaluationContext`, API types.

---

**Suggested order:** Start with **2** (skip or lighten simulateYtd) and **7** (more vCPU) for quick gains. Then **1** (Decimal reuse / constant caching) and **4** (pre-allocate results). Add **5** (worker threads) if you need the largest reduction without changing the regulation logic. Use **6** to guide further tuning in the regulation code.
