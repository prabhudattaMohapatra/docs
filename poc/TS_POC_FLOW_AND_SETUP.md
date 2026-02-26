# TypeScript POC: Flow and Setup

This document describes the **payroll-engine-poc-ts** repository: project structure, setup steps, and the end-to-end execution flow for both local runs and Lambda deployment.

---

## 1. Project structure

### Directory layout

```
payroll-engine-poc-ts/
├── package.json                 # Root: workspaces, scripts, shared deps
├── template.yaml                # SAM template (Lambda, env, S3 params)
├── samconfig.toml               # SAM deploy config (bucket, stack name, overrides)
├── esbuild.js                   # Root esbuild for src/function/* (generic API Lambdas)
├── jest.config.ts
├── jest.config.e2e.ts
├── src/                         # Root-level function/middleware (non-payroll)
│   ├── function/hello-gp-world/
│   └── module/middleware/, util/
├── test/
├── e2e/
└── packages/
    ├── regulation-api/          # Shared contract package
    │   └── src/index.ts         # EvaluationContext, WageTypeResult, RegulationEvaluator, factory type
    ├── engine/                  # Payroll engine + Lambda
    │   ├── package.json
    │   ├── esbuild.lambda.mjs   # Bundles Lambda handler + inlines poc-regulation
    │   ├── src/
    │   │   ├── main.ts          # CLI entry: local run with file-based stub data
    │   │   ├── index.ts         # Public API of engine package
    │   │   ├── MinimalPayrun.ts
    │   │   ├── RegulationRegistry.ts
    │   │   ├── RegulationEvaluatorLoader.ts
    │   │   ├── StubEvaluationContext.ts
    │   │   ├── LocalResultWriter.ts
    │   │   ├── resultComparison.ts
    │   │   ├── resources/
    │   │   │   ├── regulations.json
    │   │   │   └── FR_business_term_mapping.json
    │   │   ├── lambda/
    │   │   │   ├── handler.ts   # Lambda entry
    │   │   │   ├── S3DataProvider.ts
    │   │   │   ├── S3ResultWriter.ts
    │   │   │   └── metrics.ts
    │   │   └── __tests__/
    │   ├── plugins/
    │   │   └── poc-regulation/
    │   │       ├── package.json
    │   │       └── src/
    │   │           ├── index.ts           # createEvaluator factory
    │   │           ├── FranceRegulationEvaluator.ts
    │   │           ├── FranceRules.ts
    │   │           ├── FranceRulesContext.ts
    │   │           └── resources/WageTypes.json
    │   └── dist-lambda/         # Output of build:lambda (handler.mjs + resources)
    └── results/                 # Sample/local result JSONs (e.g. PRff9626.json)
```

### Root package.json

- **Workspaces:** `packages/regulation-api`, `packages/engine`.
- **Type:** `"module"` (ESM).
- **Scripts:** `build` (workspaces), `build:lambda` (engine), `build:sam`, `local-exec` (sam local invoke), `test`, `ephemeral-deploy` / `ephemeral-delete`.
- **Engines:** Node `>=22.14.0`.
- **Imports:** `#function/*`, `#middleware/*`, etc. map into `src/` and `test/`.

### Engine package (packages/engine)

- **Build:** `tsc` + `copy-resources` (copies `src/resources/*` to `dist/`).
- **Lambda build:** `build:lambda` runs `tsc`, builds `poc-regulation`, then `node esbuild.lambda.mjs` to produce `dist-lambda/handler.mjs` and copy `WageTypes.json` and `FR_business_term_mapping.json` into `dist-lambda/resources/`.
- **Start (local):** `npm run start` → `node dist/main.js`.

### Config files

- **template.yaml:** SAM app; single Lambda `PayrollEngineFunction` (nodejs22.x, arm64), env from parameters (bucket, prefixes, memory, timeout), VPC, S3 policies, OTEL layer, CodeDeploy.
- **samconfig.toml:** `dev` stack name, region, `parameter_overrides` for `PayrollDataBucket`, `StubDataPrefix`, `ResultsPrefix`, memory, timeout.
- **packages/engine/src/resources/regulations.json:** List of regulations (id, version, packageDir, factoryName) used only by local `main.ts`.

### Entry points

| Entry            | File                                | Invocation                                              |
|------------------|-------------------------------------|---------------------------------------------------------|
| Local CLI        | `packages/engine/src/main.ts`       | `npm run start` from engine (runs `node dist/main.js`) |
| Lambda           | `packages/engine/src/lambda/handler.ts` | SAM/Lambda: Handler `handler.handler`               |
| Result comparison| `packages/engine/src/resultComparison.ts` | `npm run compare` → `node dist/resultComparison.js` |

---

## 2. Setup

### Prerequisites

- **Node:** >= 22.14.0
- **AWS CLI:** for CodeArtifact and deployment
- **SAM CLI:** for `sam build` / `sam deploy` / `sam local invoke`
- **Credentials:** Role that can get a CodeArtifact token (see README)

### Install

```bash
# Optional: CodeArtifact token (if using private registry)
export ARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token --domain gp-prod --domain-owner 237156726900 --region us-east-1 --query authorizationToken --output text)

cd payroll-engine-poc-ts
npm i
```

### Build

```bash
npm run build                    # Build all workspaces (regulation-api, engine)
cd packages/engine && npm run build:lambda   # Engine Lambda bundle + copy resources to dist-lambda
npm run build:sam                # build:lambda && sam build
```

Engine build copies `src/resources/*` to `dist/`. Lambda build copies `plugins/poc-regulation/src/resources/WageTypes.json` and `src/resources/FR_business_term_mapping.json` into `dist-lambda/resources/`.

### Run locally (file-based)

1. **Stub data:** Set `STUB_DATA_DIR` to a directory containing `index.txt` and one JSON file per line, or rely on the default path used in `main.ts`: `../../../../payroll-engine-poc/s3-stub-data` (relative to engine `dist/`).
2. **Run:**

```bash
cd packages/engine
npm run start
```

This runs `node dist/main.js`: loads regulations from `regulations.json`, loads stub list from `index.txt`, loads each stub JSON, runs one payrun per employee, writes results under `results/` (or engine-relative `../results`).

### Environment variables

| Env               | Where              | Purpose                                                                 |
|-------------------|--------------------|-------------------------------------------------------------------------|
| `STUB_DATA_DIR`   | main.ts             | Override directory for `index.txt` and stub JSONs (local run).          |
| `RESULT_JSONS_DIR`| resultComparison.ts| Expected (G2N) JSONs directory.                                          |
| `POC_RESULTS_DIR` | resultComparison.ts| POC result JSONs directory.                                              |
| `OUT_HTML`        | resultComparison.ts| Comparison report path.                                                 |
| Lambda (template) | S3_BUCKET, etc.    | Not used by handler; event payload carries `s3Bucket`, `s3InputPrefix`, `s3OutputPrefix`. |

---

## 3. Execution flow

### Local run (main.ts)

1. **Bootstrap**
   - Resolve `plugins` dir (`packages/engine/plugins` or `packages/engine/../plugins`).
   - Load `regulations.json` from `packages/engine/src/resources/regulations.json` (at runtime: `dist/resources/regulations.json`).
   - Create `RegulationRegistry`, register each regulation (id, version, packageDir, factoryName).
   - Create `RegulationEvaluatorLoader(registry)` and `MinimalPayrun(loader)`.
   - Load first regulation’s evaluator via `loader.getEvaluator(first.id, first.version)`; get `wageTypeNumbers` and `wageTypeNames`.
   - Instantiate `LocalResultWriter`, resolve results dir.
   - Load `FR_business_term_mapping.json` and build `caseValueMap` for input field name → internal case name.

2. **Stub list**
   - If `STUB_DATA_DIR` is set: read `{STUB_DATA_DIR}/index.txt`.
   - Else: read `S3_STUB_DATA_DIR/index.txt` (hardcoded path to `payroll-engine-poc/s3-stub-data`).
   - Lines (trimmed, non-empty) are relative stub file paths.

3. **Per employee (sequential)**
   - For each line in the stub list:
     - `loadStubData(stubPath)`: read JSON from that path, parse as `StubDataRecord`.
     - If keys in `record.caseValues` don’t start with `employee.`, run `translateCaseValues(record.caseValues, caseValueMap)` (and apply defaults).
     - Build `StubEvaluationContext(tenantId, employeeId, periodStart, periodEnd, caseValues)`.
     - `payrun.run(regulationId, version, wageTypeNumbers, context)` → list of `WageTypeResult`.
     - `resultWriter.writeResultJson(resultsDir, record, results, wageTypeNames)` → one JSON file per employee under results dir.

4. **Wage type evaluation (inside payrun)**
   - `MinimalPayrun.run` gets the evaluator from the loader, then for each wage type number in order calls `evaluator.evaluateWageType(num, context)` and collects `WageTypeResult { wageTypeNumber, value }`. No explicit collector lifecycle at engine level; the France plugin accumulates into its own context and returns the wage type value.

### Lambda run (handler.ts)

1. **Event**
   - `PayrollLambdaEvent`: `s3Bucket`, `s3InputPrefix`, `s3OutputPrefix`, optional `employeeLimit`, optional `concurrency` (default 50).

2. **Initialization**
   - Lazy-create evaluator: `createEvaluator()` from bundled `poc-regulation` (France TS implementation).
   - Load `wageTypeNumbers`, `wageTypeNames`, and case-value mapping from `FR_business_term_mapping.json` in `dist-lambda/resources/`.

3. **S3 download**
   - `S3DataProvider(s3, bucket, inputPrefix)`.
   - `downloadIndex()`: GET `{prefix}index.txt`, split lines → list of stub file names.
   - Optionally slice by `employeeLimit`.
   - `downloadAllRecords(filesToProcess, concurrency)`: GET each key `{prefix}{fileName}`, parse JSON as `StubDataRecord`; concurrency limit applied (default 50). Tracks `totalBytesDownloaded`.

4. **Engine execution (sequential over employees)**
   - For each record: `processEmployee(record, evaluator, wageTypeNumbers, mapping)`:
     - Translate case values if keys don’t start with `employee.`, build `StubEvaluationContext`, then for each wage type call `evaluator.evaluateWageType(num, context)` and collect `WageTypeResult[]`.
   - Employees are processed one-by-one; S3 download/upload are parallelized with concurrency.

5. **S3 upload**
   - `runId = run-{ISO timestamp}`; output prefix `{s3OutputPrefix}/{runId}`.
   - `S3ResultWriter.uploadResults(employeeResults, wageTypeNames, concurrency)`: for each employee, build JSON (employeeId, tenantId, period, wageTypes map, wageTypeResults array), PUT to `{outputPrefix}/{employeeId}.json`.
   - `uploadMetrics(report)` → PUT `{outputPrefix}/metrics.json`.

6. **Metrics**
   - `MetricsCollector`: phases (initialization, s3Download, engineExecution, s3Upload), memory sampling, CPU usage. `buildReport()` produces `LambdaRunMetrics` (durations, throughput, CPU, memory, network, cost, cold start). This report is returned and also uploaded as `metrics.json`.

---

## 4. Stub data

- **Index:** `index.txt` under the stub data root; one line per stub file (e.g. `PRff9626.json`).
- **Format:** Each stub file is JSON: `{ tenantId, employeeId, periodStart, periodEnd, caseValues: Record<string, string> }`. Same shape as `StubDataRecord`.
- **Paths:** Local: `STUB_DATA_DIR` or hardcoded `payroll-engine-poc/s3-stub-data`. Lambda: S3 keys `{s3InputPrefix}index.txt` and `{s3InputPrefix}{fileName}`.

---

## 5. Regulation loading

- **Local (main.ts):** Config in `regulations.json`: `{ id, version, packageDir, factoryName }`. `RegulationRegistry` maps (id, version) to package path under `pluginsDir`. `RegulationEvaluatorLoader` loads the package from `{packagePath}/dist/index.js` via dynamic `import(pathToFileURL(entryPoint))`, then calls `module[factoryName]()` to get a `RegulationEvaluator`. Only TS packages under `packages/engine/plugins` are used; no JAR.
- **Lambda:** No registry/config; the handler statically imports `createEvaluator` from the bundled poc-regulation entry, so the France TS implementation is the only regulation.

### Wage type evaluation

- **Interface:** `RegulationEvaluator` from `@payroll/regulation-api`: `evaluateWageType(wageTypeNumber, context)`, `getWageTypeNumbers()`, `getWageTypeNames()`, optional `collectorStart`/`collectorEnd`.
- **Dispatch:** France: `FranceRegulationEvaluator` reads `WageTypes.json` (wageTypeNumber → name, methodName, collectors). For each wage type it looks up the method on `FranceRules` and calls it with a `FranceRulesContext` that wraps `EvaluationContext` and adds setters, slabs, and collectors. Return value is the wage type result; non-zero results are added to collectors listed in `WageTypes.json` via `ctx.addToCollector`.

### Collectors

- **API:** Optional `collectorStart`/`collectorEnd` on `RegulationEvaluator`; France does not implement them.
- **France:** Collectors are per-employee state inside `FranceRulesContextImpl` (a `Map<string, Decimal>`). When a wage type returns a non-zero value and has `collectors` in `WageTypes.json`, the evaluator calls `ctx.addToCollector(collectorName, result)`. Later wage types read from `ctx.getCollector(...)`. No engine-level collector lifecycle; all logic is inside the regulation.

---

## 6. Results written

- **Local:** `LocalResultWriter.writeResultJson(resultsDir, record, results, wageTypeNames)` creates `results/{employeeId}.json` with employeeId, tenantId, period, `wageTypes` (number → string value), `wageTypeResults` (array of { wageTypeNumber, wageTypeName, value }).
- **Lambda:** `S3ResultWriter.uploadResults` writes the same structure to S3: `{s3OutputPrefix}/{runId}/{employeeId}.json`; `uploadMetrics` writes `{s3OutputPrefix}/{runId}/metrics.json`.

---

## 7. Key files (short reference)

| File | Role |
|------|------|
| `packages/regulation-api/src/index.ts` | Defines `EvaluationContext`, `WageTypeResult`, `RegulationEvaluator`, `RegulationEvaluatorFactory`. |
| `packages/engine/src/main.ts` | Local entry: load regulations from config, stub list from index.txt, run payrun per employee, write local result JSONs. |
| `packages/engine/src/lambda/handler.ts` | Lambda entry: get evaluator, load index from S3, download stubs, process employees, upload result JSONs and metrics. |
| `packages/engine/src/MinimalPayrun.ts` | Runs wage type list for one employee: get evaluator, loop wage type numbers, call `evaluateWageType`, return `WageTypeResult[]`. |
| `packages/engine/src/RegulationRegistry.ts` | Maps (regulationId, version) to package path and factory name. |
| `packages/engine/src/RegulationEvaluatorLoader.ts` | Loads regulation package from registry via dynamic import, caches evaluator by id:version. |
| `packages/engine/src/StubEvaluationContext.ts` | Implements `EvaluationContext`: tenant/employee/period + case values (Decimal/string), optional getLookup (returns undefined). |
| `packages/engine/src/LocalResultWriter.ts` | Writes one JSON file per employee under a results directory. |
| `packages/engine/src/lambda/S3DataProvider.ts` | Fetches index.txt and stub JSONs from S3; returns `StubDataRecord[]`; tracks bytes downloaded. |
| `packages/engine/src/lambda/S3ResultWriter.ts` | Uploads per-employee result JSON and metrics.json to S3; tracks bytes uploaded. |
| `packages/engine/src/lambda/metrics.ts` | Phase timers, memory sampling, build of `LambdaRunMetrics`. |
| `packages/engine/src/resultComparison.ts` | Compares POC results to G2N/ResultJSONs using FR_business_term_mapping; writes comparison_report.html. |
| `packages/engine/src/resources/regulations.json` | Config for local run: which regulation packages to register. |
| `packages/engine/src/resources/FR_business_term_mapping.json` | Input (businessTerm/lpp column → case field) and output (businessTerm → wageTypeNumber) mapping. |
| `packages/engine/plugins/poc-regulation/src/index.ts` | Exports `createEvaluator` factory and France types. |
| `packages/engine/plugins/poc-regulation/src/FranceRegulationEvaluator.ts` | Implements `RegulationEvaluator`: loads WageTypes.json, dispatches to FranceRules methods, manages FranceRulesContext and collectors. |
| `packages/engine/plugins/poc-regulation/src/FranceRules.ts` | Static methods per wage type; use FranceRulesContext for inputs and collectors. |
| `packages/engine/plugins/poc-regulation/src/resources/WageTypes.json` | Wage type number, name, methodName, and collectors list for France. |
| `packages/engine/esbuild.lambda.mjs` | Bundles `src/lambda/handler.ts` to `dist-lambda/handler.mjs`, copies WageTypes and FR_business_term_mapping into `dist-lambda/resources/`. |
| `template.yaml` | SAM: PayrollEngineFunction (Node 22, arm64), env, VPC, S3, OTEL layer, CodeDeploy. |

---

## 8. Container / deployment

- **Docker:** No Dockerfile in this repo. Deployment is Lambda-only via SAM.
- **Entrypoint:** Lambda handler is `handler.handler` (exported `handler` in `dist-lambda/handler.mjs`). No custom entrypoint script.
- **Fargate:** Not used; single Lambda function in template.
- **S3:** Input: bucket + prefix (e.g. `input/stub-data`), `index.txt` + stub JSONs. Output: same bucket, configurable prefix (e.g. `results-ts`), then `{prefix}/{runId}/{employeeId}.json` and `metrics.json`.
- **Metrics:** `LambdaRunMetrics` built in handler and uploaded as `metrics.json`; also logged and returned. OTEL is configured via template (layer and env).

---

## 9. Data model

- **StubDataRecord:** `{ tenantId, employeeId, periodStart, periodEnd, caseValues: Record<string, string> }`. Same shape for local file and S3 JSON.
- **EvaluationContext (API):** `getTenantId()`, `getEmployeeId()`, `getPeriodStart()`, `getPeriodEnd()`, `getCaseValue(caseName)`, optional `getCaseValueString`, `getLookup`. Used by evaluators to read input.
- **StubEvaluationContext:** Implements `EvaluationContext`; built from a `StubDataRecord` (plus optional translation). Holds case values as Decimal and string maps; `getLookup` returns undefined.
- **WageTypeResult (API):** `{ wageTypeNumber: number, value: Decimal }`. One per wage type per employee.
- **FranceRulesContext:** Extension used only inside France: get/set field values, slabs, and collectors. Wraps `EvaluationContext` and adds session state.
- **Result JSON (output):** `employeeId`, `tenantId`, `periodStart`, `periodEnd`, `wageTypes: { [wageTypeNumber]: string }`, `wageTypeResults: { wageTypeNumber, wageTypeName, value }[]`. Same structure for local files and S3.

Input names can be translated from business/column names to internal case names via `FR_business_term_mapping.json`. Defaults (e.g. `employee.monthly_ss_ceiling`, `employee.work_percentage`) are applied when building the context if missing.
