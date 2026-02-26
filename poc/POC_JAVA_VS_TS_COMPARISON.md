# Java POC vs TypeScript POC: Comparison

This document compares the two payroll engine POCs: **payroll-engine-poc** (Java) and **payroll-engine-poc-ts** (TypeScript). It covers what is the same and what differs in structure, flow, regulation loading, deployment, and behavior.

---

## 1. Executive summary

| Aspect | Java POC | TypeScript POC |
|--------|----------|----------------|
| **Runtime** | JVM (Java) | Node.js (TypeScript compiled to JS) |
| **Local entry** | `Main.java` → JAR | `main.ts` → `node dist/main.js` |
| **Cloud run** | Fargate task + shell entrypoint | Lambda (SAM) |
| **Regulation format** | JAR (Java class in URLClassLoader) | TS package (dynamic import or bundled) |
| **Employee processing** | Parallel (thread pool = CPU cores) | Sequential (per employee) |
| **Input mapping** | None (expects `employee.*` keys) | `FR_business_term_mapping.json` (LPP/business terms → case names) |
| **Stub data source (local)** | `STUB_DATA_DIR` or classpath `stub-data/` | `STUB_DATA_DIR` or hardcoded path to `payroll-engine-poc/s3-stub-data` |
| **Stub data source (cloud)** | S3 sync in entrypoint.sh | S3 GetObject in Lambda (index.txt + per-file GET) |
| **Metrics** | entrypoint.sh (ECS metadata + awk) | Lambda handler (MetricsCollector + process APIs) |

---

## 2. Project structure

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Repo** | `payroll-engine-poc` | `payroll-engine-poc-ts` |
| **Engine code** | `engine/src/main/java/` + `engine/src/main/resources/` | `packages/engine/src/` + `packages/engine/plugins/` |
| **Regulation plugin** | Separate repo `payroll-regulations-poc` (JAR published to CodeArtifact) | In-repo `packages/engine/plugins/poc-regulation/` (TS) |
| **Shared API** | Java API JAR (e.g. `regulation-api`) used by engine + regulation JAR | `packages/regulation-api` (TS) used by engine + poc-regulation |
| **Deployment** | Docker image + `images/payroll-engine-poc/entrypoint.sh` | SAM `template.yaml` (no Dockerfile) |

---

## 3. Entry points and execution flow

### Java POC

- **Local:** `Main.main()` → load `regulations.json` from classpath → `RegulationRegistry` + `RegulationEvaluatorLoader` → load JAR from `plugins/` or `engine/plugins/`, instantiate evaluator class via reflection → load stub list from `STUB_DATA_DIR/index.txt` or classpath `stub-data/index.txt` → load all stub JSONs → **parallel** payrun (thread pool = `Runtime.getRuntime().availableProcessors()`) with `ThreadLocal` evaluator per thread → write one JSON per employee to results dir.
- **Fargate:** `entrypoint.sh` → optional CodeArtifact JAR fetch → optional S3 sync for stub data → run JAR (e.g. `java -jar …`) → post-run ECS container stats → optional S3 sync for results + upload `metrics.json`.

### TypeScript POC

- **Local:** `main.ts` → load `regulations.json` from `dist/resources/` → `RegulationRegistry` + `RegulationEvaluatorLoader` → dynamic `import()` of plugin `dist/index.js`, call factory → load stub list from `STUB_DATA_DIR/index.txt` or hardcoded path to `payroll-engine-poc/s3-stub-data` → **sequential** loop over employees → payrun per employee → write one JSON per employee to results dir.
- **Lambda:** `handler.handler` → lazy `createEvaluator()` (bundled France TS) → load case mapping from `FR_business_term_mapping.json` → S3: get index.txt, then get each stub JSON (with concurrency) → **sequential** loop over employees → payrun per employee → S3 upload per-employee JSONs + `metrics.json`.

---

## 4. What is the same

- **Stub data format:** Both use the same JSON shape: `{ tenantId, employeeId, periodStart, periodEnd, caseValues: Record<string, string> }` and an `index.txt` listing relative file paths.
- **Result format:** Per-employee result JSON with `employeeId`, `tenantId`, period, `wageTypes` (number → string), `wageTypeResults` (array of `{ wageTypeNumber, wageTypeName, value }`). Same structure in both.
- **Regulation contract:** Both use the same logical contract: evaluator exposes `evaluateWageType(wageTypeNumber, context)`, `getWageTypeNumbers()`, `getWageTypeNames()`, and optional collector lifecycle. Context provides tenant, employee, period, and case values.
- **Regulation config (local):** Both use a `regulations.json` that lists the regulation to load (Java: `id`, `version`, `jar`, `evaluatorClass`; TS: `id`, `version`, `packageDir`, `factoryName`).
- **France regulation logic:** Same business rules (wage types, collectors, formulas); Java in `payroll-regulations-poc` (JAR), TS in `packages/engine/plugins/poc-regulation` (TS). TS is a port; behavior is aligned.
- **WageTypes.json:** Same schema (wageTypeNumber, name, methodName, collectors) used by the France evaluator in both.
- **Decimal handling:** Java uses `BigDecimal`; TS uses `decimal.js` (Decimal). Both avoid float for money.
- **S3 I/O (cloud):** Both support reading stub data from S3 and writing results + metrics to S3. Metrics include duration, throughput, CPU, memory, cost.

---

## 5. What is different

### 5.1 Regulation loading

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Artifact** | JAR file (e.g. `poc-regulation.jar`) | TS package (directory with `dist/index.js` or bundled into Lambda) |
| **Discovery** | Path from registry (plugins dir + jar name from config) | Package path from registry (plugins dir + packageDir); entry `dist/index.js`. |
| **Instantiation** | `URLClassLoader` + `Class.forName(evaluatorClass).getConstructor().newInstance()` | Dynamic `import(entryPoint)` then `module[factoryName]()`. |
| **Lambda** | N/A (Fargate runs full image with JAR in image or from CodeArtifact) | France regulation is bundled into the Lambda; no dynamic plugin loading. |

### 5.2 Concurrency

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Per-employee** | Parallel: fixed thread pool size = `Runtime.getRuntime().availableProcessors()`, one evaluator per thread via `ThreadLocal`. | Sequential: one employee after another in a single thread. |
| **S3 (cloud)** | Bulk sync in shell (`aws s3 sync`). | Lambda: concurrent GETs for stub files (concurrency limit, default 50); concurrent PUTs for result files. |

### 5.3 Input mapping and defaults

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Case value mapping** | None. Expects stub JSON keys to already match engine/internal names (e.g. `employee.*`). | Uses `FR_business_term_mapping.json`: maps business terms and LPP column names to internal case field names. |
| **Defaults** | Not applied in engine. | Defaults applied when building context (e.g. `employee.monthly_ss_ceiling`, `employee.work_percentage`) if missing. |

So the TS POC can consume “Excel/LPP-style” column names and normalize them; the Java POC assumes pre-normalized stub data.

### 5.4 Stub data source (local)

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Override** | `STUB_DATA_DIR` → `{STUB_DATA_DIR}/index.txt` and `{STUB_DATA_DIR}/{line}.json`. | Same. |
| **Default** | Classpath: `stub-data/index.txt` and `stub-data/{line}`. | Hardcoded path: `../../../../payroll-engine-poc/s3-stub-data` (relative to engine `dist/`). |

So without `STUB_DATA_DIR`, Java uses classpath resources; TS uses a sibling repo path.

### 5.5 Deployment and metrics

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Execution environment** | Fargate (ECS task). Container runs entrypoint.sh then JAR. | Lambda (single invocation). Handler runs in Node. |
| **Metrics collection** | Bash: ECS task metadata (CPU, memory), pre/post container stats, phase timers (S3 dl, engine, S3 ul), awk to compute utilization and cost. Written to `metrics.json` and optionally uploaded to S3. | TypeScript: `MetricsCollector` in handler; phase timers, `process.memoryUsage()`, `process.cpuUsage()`, Lambda memory limit. Built-in cost (Lambda pricing). Written to `metrics.json` and uploaded to S3. |
| **Metrics schema** | Fargate-oriented: taskArn, taskConfig (cpuUnits, memoryMb, vCpu), duration, throughput, cpu (onlineCpus, utilizationPercent, utilizationOfAllocatedPercent), memory (usage, peak, limit, rss, utilizationPercent, peakUtilizationPercent), network, cost, engineExitCode. | Lambda-oriented: functionArn, requestId, lambdaConfig (memoryMb, allocatedVCpu, timeoutSec, runtime, architecture), duration, throughput, cpu (availableParallelism, utilizationPercent), memory (rss, heap, lambdaLimitMb, utilizationPercent, peakUtilizationPercent), network (s3 download/upload bytes), cost, lambda (coldStart, invokeCount). |

### 5.6 Build and artifact

| | Java POC | TypeScript POC |
|---|----------|----------------|
| **Build** | Maven: engine JAR; regulation JAR built in `payroll-regulations-poc` and published to CodeArtifact. Main build fetches regulation JAR into image. | npm workspaces: `regulation-api`, `engine`; engine Lambda bundle via esbuild (handler + inlined poc-regulation). |
| **CI** | GitHub Actions: build regulation JAR → deploy to CodeArtifact; build engine image (fetch regulation JAR) → push to ECR. | GitHub Actions (typical): build Lambda (e.g. `build:lambda`, `sam build`), deploy SAM stack. |
| **Regulation update** | New JAR version in CodeArtifact; engine image rebuild to pull new JAR (or entrypoint fetches from CodeArtifact at task start). | Code change in `plugins/poc-regulation`; rebuild and redeploy Lambda. |

---

## 6. Feature parity summary

| Feature | Java POC | TS POC |
|--------|----------|--------|
| Local run with file-based stubs | Yes | Yes |
| Cloud run with S3 input/output | Yes (Fargate) | Yes (Lambda) |
| Single regulation (France) | Yes (JAR) | Yes (TS bundle) |
| Config-driven regulation (local) | Yes (regulations.json + JAR) | Yes (regulations.json + packageDir) |
| Parallel employee processing | Yes (thread pool) | No (sequential) |
| Input (business term) mapping | No | Yes (FR_business_term_mapping.json) |
| Default case values | No | Yes |
| Result comparison vs G2N | No | Yes (resultComparison.ts) |
| Metrics to S3 | Yes | Yes |
| Optional CodeArtifact JAR at runtime | Yes (entrypoint) | N/A |

---

## 7. When to use which

- **Java POC:** Use when you want Fargate, maximum per-task throughput via multi-threaded employee processing, or when regulation is delivered as a versioned JAR (e.g. from CodeArtifact). Stub data should already use engine-friendly case names.
- **TypeScript POC:** Use when you want Lambda (scale-to-zero, pay-per-invocation), simpler deployment (SAM, no container build), or need input mapping/defaults for LPP/business-term inputs. Result comparison and local default path to `payroll-engine-poc/s3-stub-data` are convenient for cross-engine testing.

For a fair performance comparison (Java vs TS), note that Java runs employees in parallel and TS runs them sequentially; comparing “employees per second” should account for this (e.g. same employee set, measure wall-clock and throughput per vCPU or per dollar).
