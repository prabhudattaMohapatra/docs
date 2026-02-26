# Why Lambda Is Much Faster Than Fargate (TS POC 10k Run)

## Observed metrics (10k employees, same input/output)

| Phase / metric           | Lambda (results-ts/10k) | Fargate (results-ts-fargate) | Ratio (Fargate / Lambda) |
|--------------------------|--------------------------|------------------------------|---------------------------|
| **totalMs**              | ~35,000                  | ~115,000 – 163,000           | **3.3x – 4.7x** slower    |
| **s3DownloadMs**         | ~15,000                  | ~55,000 – 106,000            | **3.7x – 7x** slower      |
| **engineExecutionMs**   | ~1,570                   | ~5,800 – 6,800               | **3.7x – 4.3x** slower    |
| **s3UploadMs**          | ~18,000                  | ~50,000 – 52,000             | **2.8x – 2.9x** slower    |
| **employeesPerSecond**  | ~6,350                   | ~1,465 – 1,725               | **3.7x – 4.3x** slower    |

Lambda config: 4096 MB, ~2.32 vCPU (arm64).  
Fargate config: 4 vCPU, 8192 MB (x86_64) — already increased from 2 vCPU / 4 GB.

---

## 1. S3 download and upload: concurrency and I/O path

### Lambda

- **Same Node app** does S3 in process:
  - **Download:** `S3DataProvider.downloadAllRecords(filesToProcess, 50)` → up to **50 concurrent** `GetObject` requests.
  - **Upload:** `S3ResultWriter.uploadResults(..., 50)` → up to **50 concurrent** `PutObject` requests.
- Data stays **in memory** (no stub/result files on disk in the timed path).
- Single process, one S3 client, connection pooling and parallelism inside the Node runtime.

### Fargate

- **Entrypoint (shell)** does S3:
  - **Download:** `aws s3 sync ... "$STUB_DIR/"` before Node runs.
  - **Upload:** `aws s3 sync "$RESULTS_DIR/" ...` after Node runs.
- **`aws s3 sync`** is a single process with limited parallelism (listing, then transferring objects in a relatively sequential or low-concurrency way). For **10k small files** this becomes **many round-trips** without the 50-way parallelism Lambda uses.
- **Extra I/O path:** sync writes to **disk** (`/tmp/stub-data`), Node **reads from disk**; Node **writes results to disk** (`/app/results`), then sync **reads from disk** to upload. So Fargate pays for **disk read/write** and **two processes** (CLI + Node) instead of in-memory streams in one process.

**Conclusion:** Most of the S3 time gap is due to **no parallel S3 in Fargate** (sync vs 50-way concurrent Get/Put) and the **disk round-trip** instead of in-memory handling.

---

## 2. Engine execution: architecture and runtime

- **Same compute logic:** both run the same single-threaded Node loop (`processEmployee` per record). More vCPU helps only to the extent the runtime or OS use other cores for GC, libuv, etc.
- **Lambda:** **arm64 (Graviton)**. Often better single-thread performance and efficiency than x86 at similar vCPU. Runtime is tuned for short-lived, high-throughput execution.
- **Fargate:** **x86_64**. We doubled CPU (2 → 4 vCPU) and saw engine time improve (~6.8 s → ~5.8 s, ~15%), but still **~3.6x** slower than Lambda’s ~1.6 s. So:
  - **Architecture (ARM vs x86)** likely explains a large part of the per-core gap.
  - **Lambda’s optimized environment** (cache, memory subsystem, no container overhead in the timed section) can also contribute.
- **Data source:** Lambda runs on **in-memory** records. Fargate **reads each stub from disk** (`readFileSync(join(STUB_DATA_DIR, stubPath))`) in a loop, so engine phase includes **10k small file reads** from the filesystem, which adds latency and can reduce effective CPU utilization.

**Conclusion:** Engine is slower on Fargate due to **x86 vs ARM**, **disk-bound stub reads** instead of in-memory data, and possibly runtime/VM differences.

---

## 3. Other factors

- **Network path:** Both use S3 in the same region. Lambda has a highly optimized path to AWS services; Fargate runs in a customer VPC and may have different latency/bandwidth characteristics.
- **Cold start:** Not included in the phase timings. Lambda cold start is amortized if the same function is invoked multiple times; Fargate pays a full container start each task.
- **Regulation load:** Lambda ~0–1 ms (cached after first pull); Fargate ~500–665 ms (S3 pull + load each run). Small relative to total but consistent with “warm” Lambda vs “fresh” container.

---

## Summary: why Lambda is so much faster

| Factor                         | Effect |
|--------------------------------|--------|
| **S3: 50-way parallel in Node** (Lambda) vs **`aws s3 sync`** (Fargate) | **Largest** – explains most of the S3 download/upload gap (roughly 3–7x). |
| **In-memory S3 data** (Lambda) vs **disk round-trip** (Fargate)         | Extra disk I/O and two-process design on Fargate add latency and reduce throughput. |
| **ARM (Lambda)** vs **x86 (Fargate)**                                   | Explains a large part of the **engine** gap (~3.6x) and some of the perceived “less CPU” feel. |
| **Stub data from memory** (Lambda) vs **read from disk per employee** (Fargate) | Keeps Fargate engine phase more I/O-bound and less CPU-bound. |
| **Optimized Lambda runtime and path to S3**                             | Further reduces S3 and compute latency compared to a generic container. |

---

## What would narrow the gap

1. **Use the same S3 pattern as Lambda in Fargate:** Move S3 download/upload into the Node app using `S3DataProvider` and `S3ResultWriter` with the same concurrency (e.g. 50). Remove or minimize `aws s3 sync` for the 10k stub/result files so Fargate also does in-memory (or streamed) S3 I/O with many parallel requests.
2. **Optionally use ARM Fargate** (and an ARM image) so compute characteristics are closer to Lambda’s Graviton.
3. **Keep regulation in memory** (or preloaded) so regulation load time doesn’t add to every run.

Even with these changes, Lambda may still be faster due to its runtime and network path, but the current 3–5x total-time gap is largely explained by the S3 and data-path differences above.
