# POC: Fargate and Code Optimisations

Summary of **architecture change to ARM64 (Graviton)** and other **code** and **Fargate** optimisations for the Java and TypeScript POCs.

---

## 1. ARM64 (Graviton) — Java POC

**Done:**

- **Task definition** (`payroll-engine-poc/template.yaml`): `RuntimePlatform.CpuArchitecture` set to **ARM64** (was X86_64).
- **Image build**: `Mappings.ImageBuildConfiguration.PayrollEnginePoc.platform` set to **linux/arm64**; CI (`main-build.yaml`) builds with `docker build --platform linux/arm64`.
- **Dockerfile**: AWS CLI install is **multi-arch** (`TARGETARCH` → `aarch64` or `x86_64` zip) so the same Dockerfile works for both ARM64 and AMD64.

**Effect:** Lower cost and often better performance on Fargate Graviton; engine phase can benefit from ARM64. Rebuild and push the Java POC image, then run a task to compare metrics.

**Note:** TypeScript POC Fargate is already ARM64 (see [TS_POC_FARGATE_OPTIMIZATIONS.md](./TS_POC_FARGATE_OPTIMIZATIONS.md)).

---

## 2. Fargate optimisations (both POCs)

| Optimisation | Description |
|--------------|-------------|
| **Right-size CPU/memory** | Use CloudWatch (Container Insights if enabled) to check CPU and memory utilisation. If consistently low, reduce `Cpu`/`Memory` in the task definition to cut cost; if near limit, increase to avoid throttling or OOM. |
| **Fargate Spot** | For non-critical or batch runs, use **Fargate Spot** in the task definition (`CapacityProviderStrategy` with `FARGATE_SPOT`) to reduce cost (with possible interruption). |
| **Container Insights** | Enable ECS Container Insights on the cluster for CPU/memory and task-level metrics; use for right-sizing and debugging. TS POC already uses this where configured. |
| **S3 and network** | Ensure the task has sufficient network bandwidth for S3; same region for bucket and Fargate reduces latency. For Java, S3 is still via CLI sync; consider higher concurrency (e.g. parallel sync or moving S3 into the JVM/Node) if S3 phase dominates. |

---

## 3. Code optimisations

### 3.1 Java POC (already in place or documented)

| Change | Status | Reference |
|--------|--------|-----------|
| **Method cache** (reduce `getMethod` cost) | Done | [POC_JAVA_VS_TS_PERFORMANCE_FINAL.md](./POC_JAVA_VS_TS_PERFORMANCE_FINAL.md), precompiled JAR guide |
| **BigDecimal constant reuse** | Done | Same |
| **Direct dispatch (MethodHandle)** in regulation | Done (optional path) | precompiled JAR guide Part 4.6 |
| **ARM64 image + task** | Done | This doc §1 |

**Further (optional):**

- **Align regulation workload** with TS (e.g. 60 wage types, no YTD) for fair comparison and sometimes faster runs.
- **Benchmark-only:** Warm-up run before timed run; `parallelism=1` for like-for-like with single-threaded TS.

### 3.2 TypeScript POC

See [TS_POC_FARGATE_OPTIMIZATIONS.md](./TS_POC_FARGATE_OPTIMIZATIONS.md): S3 in Node with concurrency 50, in-memory engine loop, ARM64, env-driven `S3_CONCURRENCY`. Optional: pre-bundle regulation in image to reduce regulation load time.

### 3.3 Java POC — S3 phase

**Current:** Entrypoint uses `aws s3 sync` (disk, limited concurrency). **Largest gain** would be moving S3 download/upload into the JVM (or a small Node wrapper) with higher concurrency and in-memory buffers, similar to the TS POC. This is a larger change (new code path, possibly Node script that calls Java, or Java S3 client with parallel transfers).

---

## 4. Quick reference

| Area | Java POC | TS POC |
|------|----------|--------|
| **Architecture** | ARM64 (Graviton) — updated | ARM64 — already in use |
| **Fargate** | Right-size, Spot (optional), Container Insights | Same |
| **Engine code** | Method cache, constant reuse, MethodHandle | In-memory loop, direct dispatch |
| **S3** | CLI sync (consider in-process + concurrency later) | In-Node, concurrency 50 |
