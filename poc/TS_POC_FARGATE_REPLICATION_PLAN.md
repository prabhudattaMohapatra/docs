# Plan: Replicate Fargate Setup for TypeScript POC

This document details a plan to replicate the Java POC’s Fargate-based deployment in the TypeScript POC repo (`payroll-engine-poc-ts`), so the TS engine can run as an ECS Fargate task. **No GitHub pipelines** are required: build image, deploy stack, and trigger the task are done via **local scripts** (AWS CLI + SAM). OIDC is not used when running scripts locally; the default AWS CLI profile or environment credentials are used.

---

## 1. Current State

### 1.1 Java POC (payroll-engine-poc) – Fargate

| Component | Description |
|-----------|-------------|
| **Image** | `images/payroll-engine-poc/Dockerfile`: Eclipse Temurin JRE, AWS CLI v2, engine JAR + libs, plugins, `entrypoint.sh` |
| **Entrypoint** | `entrypoint.sh`: optional CodeArtifact regulation fetch → S3 stub sync → run JAR → S3 results sync → build/upload `metrics.json` |
| **Template** | `template.yaml`: S3 bucket, ECS cluster, Fargate task definition, task/execution roles, security group, log group. No Lambda. |
| **Run script** | `scripts/run-fargate-task.sh`: reads stack outputs, invokes `aws ecs run-task`. |

### 1.2 TypeScript POC (payroll-engine-poc-ts) – Lambda only

| Component | Description |
|-----------|-------------|
| **Runtime** | Lambda (Node 22, arm64). No container image in repo. |
| **Entry** | `packages/engine/src/lambda/handler.ts`: S3 input/output; optional **runtime regulation pull from S3** (bucket/prefix). |
| **Local/CLI** | `packages/engine/src/main.ts`: reads `STUB_DATA_DIR`, `regulations.json`, plugins from filesystem; writes to local dir. |
| **Regulation** | S3 only: artifact at `s3://<RegulationArtifactBucket>/regulations/<regulationId>/<version>.tgz` (e.g. `gp-aws-sam-cli-managed-source-bucket-258215414239`, prefix `regulations`). **No CodeArtifact.** |

---

## 2. Target Architecture (TS POC on Fargate)

- **Build**: Local script builds a Node-based Docker image (TS engine + entrypoint), then pushes to a **dedicated ECR repository** in the current AWS account (not the Java POC repo).
- **Registry**: ECR repo **payroll-engine/payroll-engine-poc-ts** in account **258215414239** (created via AWS CLI). Full URI: `258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-poc-ts`.
- **Deploy**: Local script deploys a **separate** Fargate-only CloudFormation/SAM stack (`template-fargate.yaml`) that creates ECS cluster, task definition, S3 access (data bucket + regulation bucket), task/execution roles, security group, log group.
- **Regulation**: Same as TS Lambda — **S3 only**. Task role has `s3:GetObject` on the regulation artifact bucket/prefix and on the data bucket (input/output). No CodeArtifact.
- **Run**: Local script runs the Fargate task via `aws ecs run-task`; container entrypoint: S3 stub sync → (optional) S3 regulation fetch → run Node engine → S3 results + metrics upload.

---

## 3. Resolved Values (via AWS CLI or repo)

These are **not placeholders**; they were resolved in the current account/repo.

| Item | Resolved value | Source |
|------|----------------|--------|
| **Node version** | **22** (>=22.14.0 per `package.json`; `.nvmrc`: `v22`) | Repo `package.json`, `.nvmrc` |
| **Docker base image** | **`node:22-bookworm-slim`** | Node 22 LTS, Debian Bookworm slim (has `bash` for entrypoint) |
| **AWS account** | **258215414239** | `aws sts get-caller-identity` |
| **ECR repository** | **payroll-engine/payroll-engine-poc-ts** | New repo created in account (not shared with Java POC) |
| **ECR URI** | **258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-poc-ts** | `aws ecr create-repository` + account/region |
| **VpcId** | **vpc-0b9606068b5ce8843** | `aws ec2 describe-vpcs` (only VPC in account) |
| **VpcSubnetIds** | **subnet-0f8443f6d412a8143,subnet-07448dca2d39626fa,subnet-03781e3dbc406fc7f** | Same three as Java POC samconfig; `aws ec2 describe-subnets` |
| **PermissionsBoundaryArn** | **arn:aws:iam::258215414239:policy/gp-boundary** | `aws iam get-policy` confirmed present |
| **Regulation (S3)** | Bucket: **gp-aws-sam-cli-managed-source-bucket-258215414239**, prefix: **regulations** | Same as TS POC Lambda (samconfig-ephemeral / template) |
| **Data bucket (existing)** | **payroll-engine-poc-dev-258215414239-us-east-1** | TS POC ephemeral config (input/output) |

---

## 4. Implementation Plan

### 4.1 Docker Image

**Location (new):** `images/payroll-engine-poc-ts/` in the TS POC repo.

| Artifact | Purpose |
|----------|---------|
| **Dockerfile** | Single-stage: base **`node:22-bookworm-slim`**; install AWS CLI v2; copy built engine (`packages/engine/dist/`), plugins, regulation-api; copy `entrypoint.sh`; set working dir `/app`; entrypoint `/app/entrypoint.sh`. |
| **entrypoint.sh** | Bash: (1) optional S3 regulation fetch (if env set) using same convention as Lambda (`REGULATION_ARTIFACT_BUCKET`, `REGULATION_ARTIFACT_PREFIX`, `REGULATION_ID`, `REGULATION_VERSION`); (2) S3 stub sync to `/tmp/stub-data`, set `STUB_DATA_DIR`; (3) run `node /app/dist/main.js` (or fargate runner); (4) S3 upload of results dir + `metrics.json`. |

**Build context:** Repo root. Prerequisite: `npm run build` (and optionally regulation plugin build) so `packages/engine/dist/`, `packages/engine/plugins/poc-regulation/dist/`, `packages/regulation-api/dist/` exist.

**Regulation in container:** Use the **same S3 path** as the TS Lambda: artifact at `s3://${REGULATION_ARTIFACT_BUCKET}/${REGULATION_ARTIFACT_PREFIX}/${REGULATION_ID}/${REGULATION_VERSION}.tgz`. Entrypoint can download to `/tmp/regulations` and set env so the engine (or a small wrapper) loads it; or the image can bundle regulation at build time and skip runtime fetch.

---

### 4.2 Separate Fargate Template

**File:** `template-fargate.yaml` in the TS POC repo (Fargate-only; no Lambda).

**Contents (summary):**

- **Parameters:** Environment, DeploymentVersion, **RepositoryPrefix** (default: `258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine` — full URI without image name), **VpcId** (default: `vpc-0b9606068b5ce8843`), **VpcSubnetIds** (default: three subnets above), LogRetentionDays, StubDataPrefix, ResultsPrefix, **PayrollDataBucket** (existing bucket, e.g. `payroll-engine-poc-dev-258215414239-us-east-1`), **RegulationArtifactBucket** (e.g. `gp-aws-sam-cli-managed-source-bucket-258215414239`), RegulationArtifactPrefix (default `regulations`), RegulationId, RegulationVersion, PermissionsBoundaryArn (default: `arn:aws:iam::258215414239:policy/gp-boundary`).
- **Resources:**
  - EcsTaskExecutionRole (ECR pull, CloudWatch logs).
  - EcsTaskRole: S3 GetObject/PutObject/ListBucket on **data bucket** (input/* and results-ts/* or parameterized prefixes); S3 GetObject on **regulation bucket** (regulations/*). **No CodeArtifact.**
  - PayrollEngineLogGroup, EcsCluster, FargateSecurityGroup (egress 0.0.0.0/0 for S3, ECR).
  - PayrollEngineTaskDefinition: image `!Sub "${RepositoryPrefix}/payroll-engine-poc-ts:${Environment}-${DeploymentVersion}"`, env vars for STUB_DATA_BUCKET, STUB_DATA_PREFIX, RESULTS_BUCKET, RESULTS_PREFIX, REGULATION_ARTIFACT_BUCKET, REGULATION_ARTIFACT_PREFIX, REGULATION_ID, REGULATION_VERSION, AWS_REGION.
- **Outputs:** BucketName (or reference existing), ClusterName, TaskDefinitionArn, SecurityGroupId, SubnetIds, LogGroupName.

**Note:** Task definition image name must match the ECR repo: **payroll-engine/payroll-engine-poc-ts**, so RepositoryPrefix must be `258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine` (registry + path without image name). Image tag: `${Environment}-${DeploymentVersion}`.

---

### 4.3 Scripts (no GitHub pipelines)

All steps are done **locally** with AWS CLI (and optionally SAM CLI). No OIDC; use `aws configure` or env vars (AWS_PROFILE, AWS_ACCESS_KEY_ID, etc.).

| Script | Purpose |
|--------|---------|
| **scripts/fargate/build-image.sh** | Build TS engine (`npm run build`), then `docker build -f images/payroll-engine-poc-ts/Dockerfile -t payroll-engine-poc-ts:<tag> .`; optional: `docker push` to ECR (after `aws ecr get-login-password`). Tag e.g. `dev-$(git rev-parse --short HEAD)` or `dev-1.0.0`. |
| **scripts/fargate/deploy.sh** | Deploy the Fargate stack: `sam deploy` (or `aws cloudformation deploy`) using `template-fargate.yaml` and a config file (e.g. `samconfig-fargate.toml`) with stack_name, region, parameter_overrides. Overrides include RepositoryPrefix, VpcId, VpcSubnetIds, PayrollDataBucket, RegulationArtifactBucket, PermissionsBoundaryArn, etc. — all set to the resolved values above. |
| **scripts/fargate/run-task.sh** | Read stack outputs (ClusterName, TaskDefinitionArn, SecurityGroupId, SubnetIds) from the deployed stack; run `aws ecs run-task --cluster ... --task-definition ... --launch-type FARGATE --network-configuration ...`. Default stack name e.g. `payroll-engine-poc-ts-fargate-dev`. |

**Config file:** e.g. `samconfig-fargate.toml` with `stack_name = "payroll-engine-poc-ts-fargate-dev"`, `parameter_overrides` listing all parameters. No pipelines; scripts use local AWS identity.

---

### 4.4 Regulation: S3 Only (no CodeArtifact)

- **Source of truth:** Same as TS Lambda — regulation artifact (tarball) in S3 at `s3://<RegulationArtifactBucket>/<RegulationArtifactPrefix>/<regulationId>/<version>.tgz`.
- **Task role:** IAM policy allows `s3:GetObject` on `arn:aws:s3:::gp-aws-sam-cli-managed-source-bucket-258215414239/regulations/*` (or parameterized). No CodeArtifact permissions.
- **Entrypoint:** If `REGULATION_ARTIFACT_BUCKET` and `REGULATION_ARTIFACT_PREFIX` (and id/version) are set, download the tgz and extract; then run the engine with env pointing to the extracted path (or use in-image regulation and leave env unset).

---

### 4.5 TS Engine Changes (optional)

- **Env-driven:** `main.ts` (or a fargate-runner) should use only env for `STUB_DATA_DIR` and results dir (e.g. `RESULTS_DIR=/app/results`). Remove or parameterize any hardcoded path to `payroll-engine-poc/s3-stub-data`.
- **Results dir:** Fixed path (e.g. `/app/results`) so entrypoint knows where to upload.

---

## 5. Remaining Placeholders and Assumptions

### 5.1 Remaining placeholders

None of the following are dummy values that were “guessed”; they are either **conventions** you may want to change or **environment-specific** choices.

| Item | Value / convention | Notes |
|------|--------------------|--------|
| **Stack name** | `payroll-engine-poc-ts-fargate-dev` | Used in `run-task.sh` and `samconfig-fargate.toml`. Change if you use a different env or naming. |
| **Image tag** | e.g. `dev-$(git rev-parse --short HEAD)` or `dev-1.0.0` | Decided in `build-image.sh`; must match `DeploymentVersion` passed to deploy so task definition pulls the right image. |
| **StubDataPrefix / ResultsPrefix** | e.g. `input/stub-data`, `results-ts` | Can match Lambda (e.g. `input/10k`, `results-ts`). Set in template defaults or deploy overrides. |
| **RegulationId / RegulationVersion** | e.g. `france-regulation`, `1.0.2` | Same as TS Lambda payload; set in task definition env or template defaults. |

### 5.2 Assumptions

| Area | Assumption |
|------|------------|
| **Image** | Node 22 (node:22-bookworm-slim); AWS CLI v2 in image; build context = repo root; `dist/` and plugins from `npm run build`. |
| **Entrypoint** | Bash; S3 sync for stub/results; optional S3 regulation fetch (same key convention as Lambda); RUN_ID from ECS task id or timestamp. |
| **Engine** | Run via `node` with env (STUB_DATA_DIR, RESULTS_DIR); no Lambda-specific path required for Fargate. |
| **Credentials** | Scripts run locally; AWS credentials from default profile or env (no OIDC). |
| **ECR** | Repo **payroll-engine/payroll-engine-poc-ts** is in the **current** account (258215414239); not shared with Java POC. |
| **Regulation** | S3 only; no CodeArtifact. Same bucket/prefix and key layout as TS Lambda. |

---

## 6. Implementation Order

1. **Image and entrypoint** — Add `images/payroll-engine-poc-ts/Dockerfile` (base `node:22-bookworm-slim`) and `entrypoint.sh` (S3 stub/results; optional S3 regulation fetch; run Node; upload metrics).
2. **Fargate template** — Add `template-fargate.yaml` with parameters and resources above; task role with S3 only (data bucket + regulation bucket); no CodeArtifact.
3. **Config** — Add `samconfig-fargate.toml` with stack_name, parameter_overrides using resolved values (RepositoryPrefix, VpcId, VpcSubnetIds, PermissionsBoundaryArn, buckets, etc.).
4. **Scripts** — Add `scripts/fargate/build-image.sh`, `scripts/fargate/deploy.sh`, `scripts/fargate/run-task.sh`; document usage (build → push → deploy → run-task).
5. **Engine** — Optional: ensure `main.ts` (or fargate runner) is env-driven and writes to a fixed results dir; remove hardcoded paths.

---

## 7. Implementation Status (Done)

All planned items have been implemented with the following choices:

| Item | Value | Location |
|------|--------|----------|
| **Image tag** | **dev-1.0.0** | `scripts/fargate/build-image.sh` (TAG), `samconfig-fargate.toml` (DeploymentVersion=1.0.0 → task image tag `dev-1.0.0`) |
| **Input stub data path** | **input/10k** (match Lambda) | `template-fargate.yaml` default, `samconfig-fargate.toml` StubDataPrefix |
| **Results prefix** | **results-ts-fargate** | `template-fargate.yaml` default, `samconfig-fargate.toml` ResultsPrefix |
| **Regulation** | **france-regulation 1.0.2** (same as Lambda) | `template-fargate.yaml` RegulationId/RegulationVersion, `samconfig-fargate.toml` |

**Artifacts added/updated:**

- **images/payroll-engine-poc-ts/Dockerfile** — Node 22, AWS CLI v2, `npm ci && npm run build`, entrypoint.
- **images/payroll-engine-poc-ts/entrypoint.sh** — S3 stub sync → run Node (`node packages/engine/dist/fargate-runner.js`) → S3 results upload to `RESULTS_PREFIX/RUN_ID/`.
- **packages/engine/src/fargate-runner.ts** — Fargate entry: S3 regulation pull (same as Lambda) or bundled evaluator; read stub from `STUB_DATA_DIR`; write results + `metrics.json` to `RESULTS_DIR`.
- **packages/engine/src/lambda/handler.ts** — Exported `getEvaluatorFromPull`, `getBundledEvaluator` (async, dynamic import of plugin), `getCaseValueMap`, `translateCaseValues`, `processEmployee`; copy-resources also copies to `dist/lambda/resources`.
- **packages/engine/tsconfig.json** — Lambda included in build so `dist/lambda/` is produced for Fargate; plugin path kept dynamic to avoid pulling plugins into rootDir.
- **template-fargate.yaml** — Fargate-only stack: ECS cluster, task definition, roles (S3 data + regulation buckets), security group, log group. Params: PayrollDataBucket, StubDataPrefix, ResultsPrefix, RegulationArtifactBucket, RegulationId, RegulationVersion, etc.
- **samconfig-fargate.toml** — Stack name `payroll-engine-poc-ts-fargate-dev`; parameter_overrides for all resolved values; DeploymentVersion=1.0.0 (image tag dev-1.0.0).
- **scripts/fargate/build-image.sh** — Build image `payroll-engine-poc-ts:dev-1.0.0`; optional `push` to ECR.
- **scripts/fargate/deploy.sh** — `sam deploy` with `template-fargate.yaml` and `samconfig-fargate.toml`.
- **scripts/fargate/run-task.sh** — Read stack outputs, run Fargate task with correct network JSON.

**How to run:** From repo root:  
`./scripts/fargate/build-image.sh push` → `./scripts/fargate/deploy.sh` → `./scripts/fargate/run-task.sh`

---

## 8. References

- Java POC: `payroll-engine-poc/template.yaml`, `images/payroll-engine-poc/`, `scripts/run-fargate-task.sh`.
- TS POC Lambda: `payroll-engine-poc-ts/template.yaml`, `packages/engine/src/lambda/handler.ts`, `RegulationArtifactFetcher.ts`; S3 regulation bucket/prefix in `samconfig-ephemeral.toml`.
- TS POC local: `packages/engine/src/main.ts`, `STUB_DATA_DIR`; Node version in `package.json` (engines), `.nvmrc`.
