# TypeScript POC: Runtime Pull of Regulation Artifact — Implementation Guide

This document describes (1) **how to publish** a regulation artifact so it can be pulled at runtime, and (2) **what changes to make** in the payroll-engine-poc-ts repo to support runtime pull (S3) and run any regulation/version/country. Use it to implement and test the flow end-to-end.

---

## Status

| Part | Status | Notes |
|------|--------|--------|
| **A** | Done | Regulation tarball built and uploaded to `s3://gp-aws-sam-cli-managed-source-bucket-258215414239/regulations/france-regulation/1.0.0.tgz`. Pack script: `packages/engine/plugins/poc-regulation/scripts/pack-for-s3.sh`. |
| **B** | Done | Engine changes: `RegulationArtifactFetcher.ts`, `RegulationRegistry.registerResolved`, Lambda handler supports optional runtime pull with fallback to bundled regulation. |
| **C** | Done | S3 key convention `{prefix}/{regulationId}/{version}.tgz` implemented in fetcher. |

---

## Part A: Publishing the Regulation Artifact

The regulation plugin must be built and published as a **tarball** that, when extracted, has the same layout the engine’s loader expects: a root directory containing `dist/index.js` and `dist/resources/WageTypes.json` (the France plugin loads `WageTypes.json` via `join(__dirname, 'resources', 'WageTypes.json')` where `__dirname` is the `dist/` folder at runtime).

### A.1 Artifact layout (after extract)

```
<extract-dir>/
  dist/
    index.js          # Entry; must export factory (e.g. createEvaluator)
    index.d.ts
    resources/
      WageTypes.json
    FranceRegulationEvaluator.js
    FranceRules.js
    ...
```

### A.2 Build the regulation package

From the **payroll-engine-poc-ts** repo root:

```bash
cd packages/engine/plugins/poc-regulation
npm install
npm run build
```

This runs `tsc` and `copy-resources`, producing `dist/` with compiled JS and `dist/resources/WageTypes.json`.

### A.3 Create the tarball

From `packages/engine/plugins/poc-regulation`:

```bash
# Version from package.json or env (e.g. 1.0.0)
VER=1.0.0
REGULATION_ID=france-regulation
tar -czf "../../../dist-regulations/${REGULATION_ID}-${VER}.tgz" dist
```

Or from repo root, with a directory for built artifacts:

```bash
mkdir -p dist-regulations
cd packages/engine/plugins/poc-regulation
npm run build
tar -czf ../../../dist-regulations/france-regulation-1.0.0.tgz dist
cd ../../..
```

The tarball must extract so that the **root of the extracted tree** contains `dist/`. So we tar the `dist` directory **by name**, so that when you `tar -xzf file.tgz` you get a single top-level folder `dist/`. For the engine loader, the **package path** will be the directory that **contains** `dist/`. So we have two options:

- **Option 1:** Tar the **parent** so the root of the archive is a folder named e.g. `france-regulation-1.0.0` and inside it we have `dist/`. Then extract to `/tmp/regulations/` and packagePath = `/tmp/regulations/france-regulation-1.0.0`.
- **Option 2:** Tar only `dist/` so the root of the archive is `dist/`. Then extract to `/tmp/regulations/france-regulation-1.0.0/` and the loader must look for `index.js` at `packagePath/index.js` (not `packagePath/dist/index.js`).

The current **RegulationEvaluatorLoader** expects `join(packagePath, 'dist', 'index.js')`. So the extracted layout must be:

```
<packagePath>/
  dist/
    index.js
    resources/
      WageTypes.json
```

So create the tarball so that when extracted into a directory `<packagePath>`, that directory contains `dist/`. Easiest: from the plugin directory, create a folder that will become the package root, then tar it:

```bash
# From packages/engine/plugins/poc-regulation
npm run build
mkdir -p .artifact
cp -r dist .artifact/
# So .artifact/dist/ exists. Then tar .artifact contents so extract gives one dir with dist inside.
tar -czf ../../../dist-regulations/france-regulation-1.0.0.tgz -C .artifact .
rm -rf .artifact
```

Then when you extract with `tar -xzf france-regulation-1.0.0.tgz -C /tmp/regulations/france-regulation-1.0.0`, you get `/tmp/regulations/france-regulation-1.0.0/dist/index.js`. So **packagePath** = `/tmp/regulations/france-regulation-1.0.0` and the loader’s `join(packagePath, 'dist', 'index.js')` is correct.

Recommended script (e.g. `packages/engine/plugins/poc-regulation/scripts/pack-for-s3.sh`):

```bash
#!/usr/bin/env bash
set -e
REGULATION_ID="${REGULATION_ID:-france-regulation}"
VER="${npm_package_version:-1.0.0}"   # or read from package.json
npm run build
mkdir -p .artifact
cp -r dist .artifact/
tar -czf "../../../dist-regulations/${REGULATION_ID}-${VER}.tgz" -C .artifact .
rm -rf .artifact
echo "Created ../../../dist-regulations/${REGULATION_ID}-${VER}.tgz"
```

### A.4 Upload to S3

Use a bucket and prefix dedicated to regulation artifacts (e.g. same bucket as stub data, or a separate bucket). Suggested key pattern:

```
s3://<bucket>/<regulations-prefix>/<regulationId>/<version>.tgz
```

Example (bucket used for POC: `gp-aws-sam-cli-managed-source-bucket-258215414239`):

```bash
BUCKET=gp-aws-sam-cli-managed-source-bucket-258215414239
PREFIX=regulations
REGULATION_ID=france-regulation
VER=1.0.0
aws s3 cp dist-regulations/france-regulation-1.0.0.tgz \
  "s3://${BUCKET}/${PREFIX}/${REGULATION_ID}/${VER}.tgz"
```

### A.5 IAM and bucket

- The **Lambda** (or local runner) that will pull the artifact needs **s3:GetObject** on `s3://<bucket>/<regulations-prefix>/*`.
- If you use the same bucket as stub data/results, add a policy for the regulations prefix; no new bucket is required.

### A.6 Optional: GitHub Actions (or CI) to publish

Example job to build and publish the regulation artifact on push (e.g. tag or path change):

```yaml
# .github/workflows/publish-regulation.yaml (example)
name: Publish regulation artifact
on:
  push:
    paths:
      - 'packages/engine/plugins/poc-regulation/**'
  workflow_dispatch:
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
      - run: npm ci
        working-directory: packages/engine/plugins/poc-regulation
      - run: |
          mkdir -p dist-regulations
          cd packages/engine/plugins/poc-regulation
          npm run build
          mkdir -p .artifact && cp -r dist .artifact/
          tar -czf ../../../dist-regulations/france-regulation-1.0.0.tgz -C .artifact .
          rm -rf .artifact
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_ROLE_ARN }}
      - run: |
          aws s3 cp dist-regulations/france-regulation-1.0.0.tgz \
            "s3://${{ vars.REGULATION_ARTIFACT_BUCKET }}/regulations/france-regulation/1.0.0.tgz"
```

Adjust version (e.g. from `package.json` or env) and bucket/prefix to match your convention.

---

## Part B: Changes in the TS POC Repo (Engine) for Runtime Pull

These changes allow the engine (local and Lambda) to optionally pull the regulation from S3 by **regulationId** and **version**, then load it and run the payrun. If no pull config is provided, behavior can fall back to the current bundled (or on-disk) regulation.

### B.1 Event / config for “which regulation”

**Lambda event** — Extend the payload so the handler can request a regulation by id and version and optionally where to fetch it:

- `regulationId` (optional): e.g. `"france-regulation"`. If omitted, use default/bundled.
- `regulationVersion` (optional): e.g. `"1.0.0"`. If omitted, use default.
- `regulationArtifactBucket` (optional): S3 bucket for regulation tarballs. If omitted, do not pull from S3.
- `regulationArtifactPrefix` (optional): S3 prefix, e.g. `"regulations"`. Final key = `{prefix}/{regulationId}/{version}.tgz`.

**Local / env** — For local runs, support env vars (e.g. `REGULATION_ARTIFACT_BUCKET`, `REGULATION_ARTIFACT_PREFIX`) or a small config file so the same resolution logic can be used when testing locally.

### B.2 New module: Regulation artifact fetcher

Add a module (e.g. `packages/engine/src/lambda/RegulationArtifactFetcher.ts` or `packages/engine/src/RegulationArtifactFetcher.ts`) that:

1. **Input:** `regulationId`, `version`, `bucket`, `prefix`, `S3Client`, and a **base directory** for extraction (e.g. `/tmp/regulations` in Lambda).
2. **Logic:**
   - Derive S3 key: `{prefix}/{regulationId}/{version}.tgz` (or `{version}.tgz` if you use a trailing slash in prefix).
   - Compute a local path for this id+version, e.g. `{baseDir}/{regulationId}-{version}`.
   - If that directory already exists (and optionally is not stale), return it as the **package path** (the directory that contains `dist/`).
   - Otherwise: `GetObject` the tarball, write to a temp file, extract (e.g. using `tar` via `child_process` or a library like `tar`) into `{baseDir}/{regulationId}-{version}` so that the resulting directory has a `dist/` subdirectory (see Part A for tarball layout).
   - Return the path to that directory (package path).
3. **Output:** Absolute path to the extracted package root (the directory that contains `dist/index.js`).

Use a single extraction base dir (e.g. `/tmp/regulations`) so multiple id/version combinations can coexist. Cache by id+version in memory so the same container does not re-download and re-extract for the same regulation in the same invocation (or across invocations if you keep the fetcher in a module-level variable).

**Dependencies:** `@aws-sdk/client-s3` (already used by the Lambda). For extraction, use Node’s `tar` (built-in in Node 18+) or add a dependency like `tar` for older Node.

Example API:

```ts
// RegulationArtifactFetcher.ts
export async function fetchAndExtract(
  s3: S3Client,
  bucket: string,
  prefix: string,
  regulationId: string,
  version: string,
  extractBaseDir: string
): Promise<string>  // returns packagePath (directory that contains dist/)
```

### B.3 Registry / loader integration

**Option A — Registry holds only “resolved” paths:**  
Before creating the loader, resolve the regulation to a package path:

- If **runtime pull** is requested (event or env has bucket + prefix + regulationId + version):
  - Call the fetcher; get back `packagePath`.
  - Register that path in the registry for the given `regulationId` and `version` (e.g. add a method on `RegulationRegistry` like `registerResolved(regulationId, version, packagePath, factoryName)` so the registry maps id+version to that path and to `createEvaluator`).
- If **no** runtime pull: keep current behavior (e.g. from `regulations.json` use `packageDir` and resolve `packagePath` from `pluginsBase` + `packageDir`).

**Option B — Resolver in front of registry:**  
A small “resolver” that, given id+version and optional S3 config, returns the effective package path (either from fetcher or from existing `pluginsBase` + `packageDir`). The loader then takes that path and loads `join(packagePath, 'dist', 'index.js')` as it does today.

The existing **RegulationEvaluatorLoader** already takes a registry and calls `getPackagePath(id, version)` and `getFactoryName(id, version)`. So the minimal change is to make the **registry** supply a path that either (1) comes from the filesystem (current) or (2) comes from the fetcher. So:

- Extend **RegulationRegistry** (or add a wrapper) so that for a given id+version you can set the “resolved” package path (from S3 extract). Then the loader does not need to change; it still calls `registry.getPackagePath(id, version)` and loads from that path.
- Alternatively, keep the registry as “config-only” and add a **resolution step**: “resolve(id, version) → packagePath”. That could be a new class `RegulationResolver` that checks S3 config, calls the fetcher if needed, else falls back to registry’s packageDir. The loader then needs to get the path from the resolver instead of the registry, or the registry’s `getPackagePath` internally calls the resolver.

Recommended: add a **RegulationResolver** that:
- If S3 pull is configured and id+version are given: call fetcher, return extracted path.
- Else: return `join(pluginsBase, packageDir)` from config (current behavior).

Then the loader (or the code that creates the loader) uses the resolver to obtain the path for id+version. To avoid changing the loader’s interface, the resolver can **pre-populate** the registry with the resolved path for the requested id+version before the loader is used (e.g. `registry.registerResolved(id, version, packagePath, factoryName)`).

### B.4 Lambda handler changes

In `packages/engine/src/lambda/handler.ts`:

1. **Parse event:** Read `regulationId`, `regulationVersion`, `regulationArtifactBucket`, `regulationArtifactPrefix` from the event (all optional).
2. **Decide source of regulation:**
   - If bucket (and optionally prefix) and id and version are present: **runtime pull**. Call the fetcher with S3 client, bucket, prefix, id, version, and extract dir (e.g. `/tmp/regulations`). Get back `packagePath`. Register this path for id+version (e.g. via `registry.registerResolved(id, version, packagePath, 'createEvaluator')`), then create the loader and call `loader.getEvaluator(id, version)`. Use that evaluator for the payrun.
   - Else: **fallback**. Use the current static import of `createEvaluator` (bundled France regulation) so existing invocations without new fields keep working.
3. **Caching:** Keep a module-level cache keyed by `regulationId:version`. If the same id+version was already resolved in this container, reuse the same evaluator (or same packagePath and call `loader.getEvaluator` again — the loader already caches by id+version).
4. **Resources:** Ensure `regulations.json` (and any default id/version) is still available in the Lambda bundle if you use it for fallback; for “pull” path you only need the fetcher and the loader.

Important: when using runtime pull, the Lambda **must not** bundle the regulation plugin into the handler (so do not inline `poc-regulation` when S3 pull is used). So you need two build modes, or always build the handler **without** inlining the regulation and always load it either from S3 (pull) or from a Lambda layer / bundled fallback. Simplest for testing: keep the current single bundle that includes the France plugin as fallback; when event has pull params, prefer the pulled artifact and use the loader with the resolved path; when not, use the static import. That way one deployment works for both “bundled” and “pull” invocations.

### B.5 Loader usage in Lambda

Today the handler does not use `RegulationRegistry` or `RegulationEvaluatorLoader`; it uses a static import. To support runtime pull:

- **When pull is used:** Build a registry, call the fetcher, register the resolved path for id+version, create `RegulationEvaluatorLoader(registry)`, then `await loader.getEvaluator(regulationId, regulationVersion)`. Use that evaluator for `getWageTypeNumbers()`, `getWageTypeNames()`, and `evaluateWageType` in `processEmployee`.
- **When pull is not used:** Keep using the current `getEvaluator()` that returns the statically imported evaluator.

So the handler has two code paths for “get evaluator”; both end up with an object that implements `RegulationEvaluator`.

### B.6 Local run (main.ts) — optional

To test runtime pull locally:

- Support env vars, e.g. `REGULATION_ARTIFACT_BUCKET`, `REGULATION_ARTIFACT_PREFIX`, `REGULATION_ID`, `REGULATION_VERSION`.
- If set, create an S3 client (with default credentials), call the same fetcher, register the resolved path, and use the loader with that path. If not set, keep current behavior (regulations.json + plugins on disk).

### B.7 Lambda bundle and /tmp

- **Extract directory:** Use `/tmp/regulations` (or similar). Lambda provides 512 MB in `/tmp`; regulation tarballs should be small.
- **Build:** Do **not** external the S3 client (it’s already used). Include the new fetcher and the loader in the Lambda bundle. When using “pull”, the handler must not depend on the bundled regulation package for that path (only for fallback). So the bundle will contain both the fallback static import and the loader + fetcher code.

### B.8 Summary of files to add/change

| Item | Action |
|------|--------|
| `packages/engine/src/lambda/RegulationArtifactFetcher.ts` (or under `src/`) | **Add.** Fetches `{prefix}/{regulationId}/{version}.tgz` from S3, extracts to a base dir, returns package path. |
| `packages/engine/src/RegulationRegistry.ts` | **Optionally extend.** Add `registerResolved(id, version, packagePath, factoryName)` so a path from the fetcher can be registered. |
| `packages/engine/src/lambda/handler.ts` | **Change.** Parse regulationId, version, bucket, prefix from event. If present, call fetcher, register path, use loader to get evaluator; else use static import. Cache evaluator by id+version. |
| `packages/engine/package.json` | Add `tar` (or use Node built-in) if you need to extract .tgz in Node. |
| `packages/engine/plugins/poc-regulation/scripts/pack-for-s3.sh` | **Add (optional).** Script to build and create the tarball as in A.3. |
| CI workflow | **Add (optional).** Job to build regulation and upload to S3 as in A.6. |

### B.9 Testing the flow

1. **Publish:** Build the regulation tarball (A.2–A.3), upload to S3 (A.4). Confirm the object exists at e.g. `s3://<bucket>/regulations/france-regulation/1.0.0.tgz`.
2. **Invoke Lambda** with event including `regulationId: "france-regulation"`, `regulationVersion: "1.0.0"`, `regulationArtifactBucket`, `regulationArtifactPrefix`. Check logs for successful fetch and load; run a small stub set and confirm results.
3. **Invoke without** those fields; confirm the handler still works with the bundled regulation (fallback).
4. **Local:** Set env vars and run `main.ts` (if you added local pull support); confirm the engine loads the regulation from the extracted S3 artifact.

---

## Part C: S3 key and versioning convention

Implemented in the engine:

- **Key pattern:** `{regulationArtifactPrefix}/{regulationId}/{version}.tgz`  
  Example: `regulations/france-regulation/1.0.0.tgz`  
  (Prefix defaults to `regulations` when not set in the event.)
- **Version:** Use semantic versions (e.g. from plugin’s `package.json`) so you can run different versions without changing the engine deployment.

---

## How to deploy and test

### Deploy

1. **Build the engine (Lambda bundle)**  
   From repo root:
   ```bash
   cd payroll-engine-poc-ts
   npm install   # if not already done (registry auth may be required)
   npm run build
   cd packages/engine && npm run build:lambda
   ```
   This produces `packages/engine/dist-lambda/handler.mjs` and `dist-lambda/resources/`.

2. **Deploy with SAM**  
   ```bash
   cd payroll-engine-poc-ts
   npm run build:sam   # build:lambda + sam build
   sam deploy --guided  # first time; use stack name e.g. payroll-engine-poc-ts-dev
   ```
   Or use your existing `samconfig.toml` and deploy with:
   ```bash
   sam deploy --config-env dev
   ```

3. **IAM**  
   Ensure the Lambda execution role has **s3:GetObject** on:
   - The stub data bucket/prefix (existing).
   - The regulation artifact bucket/prefix, e.g.  
     `s3://gp-aws-sam-cli-managed-source-bucket-258215414239/regulations/*`

### Test

1. **Fallback (bundled regulation)**  
   Invoke without runtime-pull fields so the handler uses the bundled France regulation:
   ```bash
   aws lambda invoke --function-name <YourPayrollEngineFunctionName> \
     --cli-binary-format raw-in-base64-out \
     --payload '{"s3Bucket":"<stub-bucket>","s3InputPrefix":"input/stub-data","s3OutputPrefix":"results-ts","employeeLimit":1}' \
     /tmp/out.json
   ```
   Check logs and `/tmp/out.json` for success.

2. **Runtime pull**  
   Invoke with regulation artifact location so the handler fetches the regulation from S3 and loads it from `/tmp/regulations`:
   ```bash
   aws lambda invoke --function-name <YourPayrollEngineFunctionName> \
     --cli-binary-format raw-in-base64-out \
     --payload '{
       "s3Bucket":"<stub-bucket>",
       "s3InputPrefix":"input/stub-data",
       "s3OutputPrefix":"results-ts",
       "employeeLimit":1,
       "regulationId":"france-regulation",
       "regulationVersion":"1.0.0",
       "regulationArtifactBucket":"gp-aws-sam-cli-managed-source-bucket-258215414239",
       "regulationArtifactPrefix":"regulations"
     }' \
     /tmp/out.json
   ```
   Check CloudWatch logs for “fetch and extract” and that the payrun completes. Results and metrics should be in the stub bucket under `s3OutputPrefix`.

3. **Optional: local run**  
   Part B does not add local runtime-pull support in `main.ts`; that can be added later with env vars `REGULATION_ARTIFACT_BUCKET`, `REGULATION_ARTIFACT_PREFIX`, `REGULATION_ID`, `REGULATION_VERSION` and the same fetcher + loader.

### Note on regulation dependencies

The artifact in S3 is the **tsc** output (`dist/` with JS and `resources/`). When the Lambda loads it from `/tmp`, Node resolves that module’s imports (e.g. `@payroll/regulation-api`, `decimal.js`) from the extracted directory. If the Lambda fails with “Cannot find module '@payroll/regulation-api'” when using runtime pull, the regulation artifact should be published as a **single ESM bundle** (e.g. esbuild with all deps inlined) and the loader updated to support loading a single entry file, or the tarball must include `node_modules` for the regulation package.

---

## Ephemeral deploy and invoke (runtime-pull test)

Use the ephemeral deploy script and the payload below to run the Lambda with the regulation artifact pulled from S3.

### Prerequisites

- `samconfig-ephemeral.toml` exists (copy from `samconfig-ephemeral.toml copy.example`, replace `xxx` with your initials).
- Regulation artifact is in S3: `s3://gp-aws-sam-cli-managed-source-bucket-258215414239/regulations/france-regulation/1.0.0.tgz`.
- Stub data exists in the stub bucket at the configured prefix (e.g. `index.txt` and JSON files).

### Steps

1. **Build** (from repo root `payroll-engine-poc-ts`):
   ```bash
   npm install
   npm run build
   cd packages/engine && npm run build:lambda && cd ../..
   ```

2. **Ephemeral deploy** (uses `scripts/ephemeral-deploy.sh` → `sam deploy --config-file samconfig-ephemeral.toml`):
   ```bash
   npm run ephemeral-deploy
   ```
   Ensure `samconfig-ephemeral.toml` includes a parameter override for the regulation bucket so the Lambda can read the artifact, for example:
   ```ini
   parameter_overrides = [
     "Environment=dev",
     "LogLevel=debug",
     "PayrollDataBucket=payroll-engine-poc-dev-258215414239-us-east-1",
     "RegulationArtifactBucket=gp-aws-sam-cli-managed-source-bucket-258215414239",
   ]
   ```
   (Adjust bucket names to match your stub data location; `RegulationArtifactBucket` must be set for runtime pull.)

3. **Get the function name** (from deploy output or AWS console). It will be like `payroll-engine-poc-ts-<xxx>-PayrollEngine` if your stack is `payroll-engine-poc-ts-<xxx>`.

4. **Invoke with runtime-pull payload**:
   ```bash
   aws lambda invoke \
     --function-name payroll-engine-poc-ts-xxx-PayrollEngine \
     --cli-read-timeout 900 \
     --cli-binary-format raw-in-base64-out \
     --payload '{"s3Bucket":"payroll-engine-poc-dev-258215414239-us-east-1","s3InputPrefix":"input/stub-data","s3OutputPrefix":"results-ts","employeeLimit":1,"regulationId":"france-regulation","regulationVersion":"1.0.0","regulationArtifactBucket":"gp-aws-sam-cli-managed-source-bucket-258215414239","regulationArtifactPrefix":"regulations"}' \
     /tmp/out.json
   ```
   Replace `payroll-engine-poc-ts-xxx-PayrollEngine` with your stack's function name, and `s3Bucket` if your stub data is in a different bucket.

5. **Check result**:
   ```bash
   cat /tmp/out.json
   ```
   Inspect CloudWatch logs for the same function to confirm regulation fetch and payrun execution.

### Lambda payload (runtime pull, single-line)

```json
{"s3Bucket":"payroll-engine-poc-dev-258215414239-us-east-1","s3InputPrefix":"input/stub-data","s3OutputPrefix":"results-ts","employeeLimit":1,"regulationId":"france-regulation","regulationVersion":"1.0.0","regulationArtifactBucket":"gp-aws-sam-cli-managed-source-bucket-258215414239","regulationArtifactPrefix":"regulations"}
```

---

## Code verification checklist (runtime-pull test)

| Item | Location | Purpose |
|------|----------|--------|
| Event fields | `handler.ts` `PayrollLambdaEvent` | `regulationId`, `regulationVersion`, `regulationArtifactBucket`, `regulationArtifactPrefix` drive runtime pull. |
| Pull vs fallback | `handler.ts` init phase | `useRuntimePull` true when all four are set; then `getEvaluatorFromPull`, else `getBundledEvaluator`. |
| Fetcher | `lambda/RegulationArtifactFetcher.ts` | `fetchAndExtract`: GetObject `{prefix}/{regulationId}/{version}.tgz`, extract to `/tmp/regulations/{id}-{version}`, return package path. |
| Registry | `RegulationRegistry.ts` | `registerResolved(id, version, packagePath, factoryName)` so loader can resolve path from S3 extract. |
| Loader | `RegulationEvaluatorLoader.ts` | Loads module from `packagePath/dist/index.js` via dynamic import, calls factory, returns `RegulationEvaluator`. |
| Caching | `handler.ts` | `pulledEvaluatorCache` by `id:version`; same container reuses evaluator. |
| IAM | `template.yaml` | Parameter `RegulationArtifactBucket`; when set, Lambda gets `s3:GetObject` on `{bucket}/regulations/*`. |

---

## Engine vs regulation artifact: responsibility boundary

When you run the test with the runtime-pull payload, the following split applies.

### Engine (Lambda handler + shared runtime)

- **Orchestration:** Parse event; decide bundled vs runtime pull; create S3 client; run phases (init → S3 download stubs → engine execution → S3 upload results + metrics).
- **Regulation resolution:** If runtime pull: call `fetchAndExtract` (download tarball from S3, extract to `/tmp/regulations`), register resolved path, create `RegulationEvaluatorLoader`, obtain one `RegulationEvaluator` instance (cached by `id:version`).
- **Data I/O:** Download stub index and stub JSONs from `s3Bucket`/`s3InputPrefix`; upload per-employee result JSONs and `metrics.json` to `s3Bucket`/`s3OutputPrefix`.
- **Input shaping:** Load `FR_business_term_mapping.json`; translate case values from business/LPP names to internal names; apply defaults; build `StubEvaluationContext` per employee.
- **Payrun loop:** For each employee, call `evaluator.getWageTypeNumbers()` (once per evaluator), then for each wage type number call `evaluator.evaluateWageType(num, context)` and collect `WageTypeResult`s. No knowledge of wage type semantics or formulas.
- **Output shaping:** Build result JSON (employeeId, tenantId, period, wageTypes map, wageTypeResults array) and write to S3; build and upload `metrics.json`.
- **Contract:** Uses only the `RegulationEvaluator` interface: `evaluateWageType`, `getWageTypeNumbers`, `getWageTypeNames`. Optional `collectorStart`/`collectorEnd` are not used by the engine in this POC.

### Regulation artifact (e.g. France POC)

- **Identity:** Exposes a factory (e.g. `createEvaluator`) that returns an object implementing `RegulationEvaluator`; identified by `regulationId` + `regulationVersion` in the event.
- **Wage type list:** Defines which wage type numbers exist and in what order (`getWageTypeNumbers`, `getWageTypeNames`), e.g. from `WageTypes.json`.
- **Per–wage type logic:** For each wage type number, implements the calculation (formulas, ceilings, brackets, constants) and returns a single value; may read from context (tenant, employee, period, case values) and from internal state (e.g. collectors).
- **Collectors:** Optionally accumulates results into named collectors and uses them in later wage types (e.g. gross → deductions → net); entirely internal to the regulation.
- **Resources:** Loads its own config (e.g. `WageTypes.json`, slab tables) from paths relative to the loaded module; no assumption about engine-provided paths except the `EvaluationContext` API.
- **No I/O:** Does not perform S3 or any other I/O; does not know about stub data format, result format, or metrics.

**Summary:** The engine owns lifecycle, I/O, event parsing, context building, and the payrun loop; the regulation artifact owns wage type definitions, order, and all calculation logic for those wage types.
