# Plan: Deploy Payroll Engine POC to Fargate and Trigger

This plan describes how to deploy the payroll-engine-poc (Java CLI payrun app) to **AWS Fargate** and trigger runs, using **exp-container-image-exemplar** as the reference for container image build, ECS task definition, and pipeline patterns.

---

## Status

| Phase | Status | Notes |
|-------|--------|--------|
| **Phase A: Containerize the engine** | **Done** | Dockerfile, .dockerignore, template.yaml (ImageBuildConfiguration), scripts/build-image.sh, images/payroll-engine-poc/README.md |
| **Phase B: SAM/CloudFormation (task definition, roles, S3, trigger)** | **Done** | template.yaml: EcsTaskExecutionRole, EcsTaskRole/EcsTaskRoleNoS3, LogGroup, ECS cluster, SecurityGroup, TaskDefinition; samconfig.toml.example; scripts/run-fargate-task.sh. No deployment triggered. |
| **Phase C: Pipeline (build image, deploy stack)** | **Done** | .github/workflows/main-build.yaml, deploy.yaml, run-fargate-task.yaml; .github/workflows/README.md. Build/deploy not triggered (deploy job disabled, workflows dispatch-only or push-ignore). |
| **Phase D: Decoupled JAR builds + CodeArtifact + image from artifact** | **Done** | build-engine-jar.yaml, build-regulation-jar.yaml (regulations-poc), main-build.yaml (image from CodeArtifact or source), entrypoint CodeArtifact fetch, template params + task role. Local build unchanged. **Verified:** JARs published to CodeArtifact (domain `gp-prod`, repo `payroll-engine`): generic `payroll-engine-poc`/`engine-dist`, Maven `com.payroll:regulation-api`, `com.payroll:poc-regulation`. |

---

## 1. References: exp-container-image-exemplar

| Concern | Exemplar location | What it shows |
|--------|--------------------|----------------|
| **Container image** | `images/exp-container-image-exemplar/Dockerfile` | Dockerfile and build context. |
| **Image build config** | `template.yaml` → `Mappings.ImageBuildConfiguration.BusyBoxService` | `buildContext`, `dockerfile`, `imageName`, `platform` (e.g. `linux/amd64`). |
| **ECS Fargate task** | `template.yaml` → `TestContainerDefinition` (lines ~276–302) | Fargate task definition: CPU/memory, `ExecutionRoleArn`, image from ECR via `RepositoryPrefix` + `ImageNameAndTag`, `LogConfiguration` (awslogs), `Command`. |
| **Task execution role** | `template.yaml` → `EcsTaskExecutionRole` | AssumeRole for `ecs-tasks.amazonaws.com`, `AmazonECSTaskExecutionRolePolicy`, ECR pull policy. |
| **Log group** | `template.yaml` → `TestContainerLogGroup` | CloudWatch log group for container stdout/stderr. |
| **Build & publish** | `.github/workflows/main-build.yaml` | Uses `devx-pipeline-modules-containers/build@v1` and `publish@v1`; image tagged with env + version. |
| **Deploy** | `scripts/deploy.sh`, `samconfig.toml` | `sam deploy --config-env <env>`; `RepositoryPrefix` from SSM (e.g. `/account/ecr/main/registry`). |
| **Trigger** | (Not in exemplar) | Exemplar only defines the task; it does **not** define an ECS cluster or a trigger. Trigger is assumed to be manual (`aws ecs run-task`) or added separately. |

The exemplar does **not** include: ECS cluster creation, EventBridge rule to run the task, or S3. Those need to be added for this POC.

---

## 2. High-level architecture (target)

- **Container image**: Payroll engine JAR + regulation JAR + runtime (e.g. OpenJDK), plus optional entrypoint that (1) runs the payrun, (2) uploads `results/*.json` to S3.
- **ECR**: Image built in CI (e.g. GitHub Actions using devx-pipeline-modules-containers or a custom build), pushed to ECR with a tag (e.g. `dev-<version>`).
- **ECS Fargate**: One **task definition** (family e.g. `payroll-engine-poc-dev`) that runs the container. Task **role** (not execution role) needs S3 read/write for input stub data and output results.
- **Trigger**: One or more of:
  - **Manual**: `aws ecs run-task --cluster <cluster> --task-definition <family>`.
  - **EventBridge (schedule)**: Rule that runs the task on a cron (e.g. daily).
  - **Lambda + API / EventBridge**: Lambda invokes `ecs:RunTask` when an event or API call occurs.
- **S3**: Optional but recommended: input prefix (stub list + stub JSONs), output prefix (result JSONs per run). If not using S3, keep using classpath/local stub data and add a post-run upload step in the container.

---

## 3. Step-by-step plan

### Phase A: Containerize the engine

1. **Add Dockerfile** (in payroll-engine-poc, e.g. `images/payroll-engine-poc/Dockerfile`):
   - Base image: OpenJDK (e.g. `eclipse-temurin:21-jre` or same as exemplar's base source if using a mirrored ECR base).
   - Copy engine JAR (with dependencies) and regulation JAR into the image (e.g. `engine/target/engine-1.0.0-SNAPSHOT.jar` and `engine/plugins/poc-regulation-1.0.0.jar`), or build inside Docker.
   - Set working directory so that `plugins/` is next to the engine JAR (engine expects `plugins/` relative to cwd when running `java -jar engine.jar`).
   - **CMD** (or **ENTRYPOINT**): `java -jar /app/engine-1.0.0-SNAPSHOT.jar` (adjust path to match layout).
   - Optionally: add a wrapper script that runs the JAR then uploads `results/` to S3 using AWS CLI or SDK (requires task role with S3 permissions).

2. **Image build configuration** (for SAM/pipeline):
   - In the template that will deploy this service, add a mapping similar to exemplar:
     - `buildContext`: e.g. `payroll-engine-poc` (root of repo) or `images/payroll-engine-poc` if Dockerfile is there and context is minimal.
     - `dockerfile`: path to the Dockerfile.
     - `imageName`: e.g. `payroll-engine-poc`.
     - `platform`: `linux/amd64` (or arm64 if desired).

3. **Multi-stage or pre-built JARs**:
   - Either build JARs in CI and pass them into the Docker build (e.g. copy from build job), or use a multi-stage Dockerfile: stage 1 Maven build engine + regulation, stage 2 copy JARs + JRE and set CMD. Exemplar keeps the image trivial (BusyBox); for Java, multi-stage keeps the final image smaller.

### Phase B: SAM/CloudFormation (task definition, roles, S3, optional trigger)

4. **Where to define infrastructure**:
   - **Option A**: New SAM (or raw CloudFormation) stack in **payroll-engine-poc** (e.g. `template.yaml` at repo root), similar to exemplar. Use `Transform: AWS::Serverless-2016-10-31` only if you need SAM resources; ECS resources are plain CloudFormation.
   - **Option B**: Add resources to an existing infra repo (e.g. **gp-nova-payroll-engine-infra**) that already has VPC, ECS cluster, and SSM parameters for `RepositoryPrefix`, `VpcId`, `VpcSubnetIds`.

5. **Resources to add** (reference: exemplar `template.yaml`):
   - **EcsTaskExecutionRole**: Same pattern as exemplar—assume `ecs-tasks.amazonaws.com`, attach `AmazonECSTaskExecutionRolePolicy`, add ECR pull policy. PermissionsBoundary if required by org.
   - **EcsTaskRole** (new): Role assumed by the **task** (not execution). Policy: `s3:GetObject` on input bucket/prefix, `s3:PutObject` on output bucket/prefix (and optionally `s3:ListBucket`). Attach this role to the task definition's `TaskRoleArn`.
   - **S3 bucket** (optional): One bucket with two prefixes, e.g. `input/` and `results/`, or separate buckets. If using existing buckets, only IAM and possibly bucket policies are needed.
   - **Log group**: e.g. `/ecs/<Environment>-payroll-engine-poc`, retention per org policy.
   - **Task definition** (Fargate):
     - Family: e.g. `!Sub "${Environment}-payroll-engine-poc"`.
     - Cpu / Memory: e.g. `512` / `1024` (adjust to fit JVM + payrun).
     - `NetworkMode: awsvpc`.
     - `RequiresCompatibilities: [ FARGATE ]`.
     - `ExecutionRoleArn`: EcsTaskExecutionRole.
     - `TaskRoleArn`: EcsTaskRole (for S3).
     - Container: image from ECR using the same substitution as exemplar: `!Sub "${RepositoryPrefix}/${ImageNameAndTag}"` with `ImageNameAndTag` = `payroll-engine-poc:${Environment}-${DeploymentVersion}`.
     - `LogConfiguration`: awslogs to the log group above.
     - `Command` (optional): override CMD to pass S3 bucket/prefix via env or arguments if the image supports it.
   - **Parameters**: Reuse or mirror exemplar (e.g. `Environment`, `RepositoryPrefix`, `DeploymentVersion`, `VpcId`, `VpcSubnetIds`). Add parameters for S3 bucket name/prefix if needed.

6. **ECS cluster**:
   - Exemplar does **not** create a cluster. Use an existing shared ECS cluster (e.g. from network/infra stack) or add `AWS::ECS::Cluster` in this stack. When triggering, you must pass the cluster name/ARN to `RunTask`.

7. **Triggering the task** (choose one or more):
   - **Manual**: Document or script `aws ecs run-task --cluster <ClusterName> --task-definition <TaskFamily> --launch-type FARGATE --network-configuration "awsvpcConfiguration={subnets=[...],securityGroups=[...]}"`. Use same subnets/SGs as task definition (typically from SSM).
   - **EventBridge schedule**: Add `AWS::Events::Rule` (schedule) and `AWS::Events::Targets::EcsTask`. Target the cluster and task definition; set network config and task count (1). Requires IAM role for EventBridge to run ECS tasks.
   - **Lambda + API or EventBridge**: Lambda that calls `ecs.runTask(...)` with cluster and task definition; invoke Lambda via API Gateway or from EventBridge. Gives on-demand or event-driven runs.

### Phase C: Pipeline (build image, deploy stack)

8. **Build and publish image** (reference: exemplar `main-build.yaml`):
   - After build/test of the engine (and regulation JAR if in same repo or as dependency):
     - Use `devx-pipeline-modules-containers/build@v1` (or equivalent) with the same `ImageBuildConfiguration` mapping so the correct Dockerfile and context are used.
     - Use `devx-pipeline-modules-containers/publish@v1` with version (e.g. `dev-<version>`).
   - Ensure the built image is tagged and pushed to ECR at `RepositoryPrefix/payroll-engine-poc:<env>-<version>`.

9. **Deploy stack**:
   - Use `sam deploy --config-env <env>` (or deploy from infra repo) so that the updated task definition points at the new image tag. If using SAM with `image_repositories` (or similar) for ECS image URI, align with how the pipeline publishes the image.

10. **Run task after deploy** (optional):
    - Add a step in the deploy workflow or a separate "run payrun" workflow that calls `aws ecs run-task` with the deployed cluster and task definition, so each deploy can trigger a one-off run for validation.

---

## 4. S3 integration (optional but recommended)

- **Input**: Place `stub-data/index.txt` and `stub-data/*.json` in S3 (e.g. `s3://<bucket>/input/stub-data/`). Container startup script can:
  - Download `index.txt`, then for each line download the corresponding JSON to a local `stub-data/` directory, then run the JAR (engine reads from classpath or a configurable path). **Or**
  - Keep using classpath stub data in the image for simplicity and only use S3 for output.
- **Output**: After the JAR exits, upload `results/*.json` to e.g. `s3://<bucket>/results/<run-id>/` using AWS CLI (`aws s3 cp/sync`) or a small script. Run-id can be a timestamp or task ID from environment variable `ECS_TASK_ID` or similar.
- **Task role**: Ensure the task role has `s3:PutObject` (and `s3:GetObject` if reading from S3) on the chosen bucket/prefixes.

---

## 5. Checklist (summary)

- [x] **Phase A:** Dockerfile for engine + regulation JARs (and optional S3 upload step).
- [x] **Phase A:** Image build mapping (buildContext, dockerfile, imageName, platform) in template or pipeline.
- [x] **Phase B:** ECS Fargate task definition (ExecutionRole, TaskRole for S3, log group, image from ECR).
- [x] **Phase B:** ECS cluster (existing or new) — cluster created in template.
- [x] **Phase B:** S3 bucket/prefixes and task role S3 permissions (if using S3) — task role with S3 when ResultsBucketName set.
- [x] **Phase B:** Trigger: manual (doc/script) — scripts/run-fargate-task.sh; EventBridge/Lambda not added.
- [x] **Phase C:** Pipeline: build image → publish to ECR → deploy stack (and optionally run task) — workflows added; enable deploy job and set secrets to use.
- [x] **Phase B:** Parameter/store for `RepositoryPrefix`, VPC, subnets (template parameters; samconfig.toml.example).

---

## 5a. How to build the image and push to ECR

To build the **payroll-engine-poc** container image and push it to ECR using the pipeline:

1. **Repo configuration (GitHub)**  
   In **payroll-engine-poc** → Settings → Secrets and variables → Actions, ensure:
   - **Secret:** `AWS_ROLE_ARN` = ARN of the IAM role used for OIDC (e.g. `arn:aws:iam::237156726900:role/YourGitHubActionsRole`).  
   - **Variables:**  
     - `CODEARTIFACT_DOMAIN` = `gp-prod`  
     - `CODEARTIFACT_REPOSITORY` = `payroll-engine`  
     - `CODEARTIFACT_DOMAIN_OWNER` = `237156726900`  
     - `ECR_REGISTRY` = your ECR registry host (e.g. `237156726900.dkr.ecr.us-east-1.amazonaws.com`).

2. **Trigger the Main Build workflow**  
   - Actions → **Main Build** → **Run workflow**.  
   - Optionally set **Engine package version** to a specific CodeArtifact version (e.g. `1.0.0-645c4e6a6d2be2120f97a0878dd486c482ff8dd2`); leave empty to use the latest.

3. **What the workflow does**  
   - If `CODEARTIFACT_DOMAIN` is set: configures AWS, resolves latest (or the given) engine version from CodeArtifact, downloads `engine-dist.zip`, unzips into `engine/target/`, then runs `docker build` and pushes to `$ECR_REGISTRY/payroll-engine-poc:<image_tag>` (e.g. `dev-1.0.0-645c4e6...`).  
   - If `ECR_REGISTRY` is not set, the image is built but **not** pushed to ECR.

4. **Other steps required before running on Fargate**  
   - **ECR repository:** Ensure an ECR repository named `payroll-engine-poc` exists in the same account/region (create manually or via infra).  
   - **Deploy stack:** The **deploy** job in main-build is currently disabled (`if: false`). To deploy the SAM stack (task definition, roles, log group, etc.), either enable it and set the deploy workflow inputs, or run `sam deploy` manually with the appropriate `samconfig.toml` and parameters.  
   - **Run task:** After the image is in ECR and the stack is deployed, use `scripts/run-fargate-task.sh` or the **Run Fargate task** workflow (with cluster name, task definition, subnets, security groups) to start a one-off Fargate task.

---

## 6. Decoupled build and CodeArtifact (Phase D)

Builds are split so that **JARs** and **container image** are produced independently. All published artifacts live in the **AWS account currently used by the CLI / pipeline** (e.g. same account as ECR).

### 6.1 Artifact stores

- **CodeArtifact (Maven)**  
  - One Maven repository in the account, e.g. **`payroll-engine`** (name and domain are placeholders; use repo vars or samconfig).  
  - Used for: **engine** JAR (and its dependency libs) and **poc-regulation** JAR.  
  - Account: the account you are logged into with the CLI (or the pipeline's AWS identity).

- **ECR**  
  - Container image is built from the **engine** JAR (and its `lib/`) only; no regulation JAR in the image.  
  - Image is pushed to ECR as today (e.g. `payroll-engine-poc:<tag>`).

### 6.2 Pipelines (no build/deploy triggered by default)

**1. Build engine JAR (payroll-engine-poc repo)**  
- Workflow: e.g. `.github/workflows/build-engine-jar.yaml`.  
- Steps: Maven build engine (and engine deps) → publish **engine** JAR (and optionally dependency JARs) to CodeArtifact Maven repo **payroll-engine**.  
- No container build.  
- Uses AWS credentials (e.g. OIDC or env) for the account that owns the CodeArtifact domain.

**2. Build regulation JAR (payroll-regulations-poc repo)**  
- Workflow: e.g. `.github/workflows/build-regulation-jar.yaml`.  
- Steps: Maven build poc-regulation → publish **poc-regulation** JAR to the same CodeArtifact Maven repo **payroll-engine**.  
- Fully independent of engine build and image build.

**3. Build image (payroll-engine-poc repo)**  
- Workflow: e.g. `.github/workflows/main-build.yaml` (or a dedicated build-image workflow).  
- Steps:  
  - **Do not** run Maven in this workflow.  
  - Authenticate to CodeArtifact (Maven) in the same account.  
  - **Download** the engine JAR (and its `lib/` dependencies) from CodeArtifact into the build context (e.g. `engine/target/`).  
  - Run `docker build` with that context (Dockerfile expects `engine/target/engine-*.jar` and `engine/target/lib/`).  
  - Push the image to ECR.  
- The image contains **only** the engine JAR + lib; **no** regulation JAR in the image.

### 6.3 Runtime: regulation JAR from CodeArtifact

- When the container starts (e.g. on Fargate), the **entrypoint** (or a small wrapper):  
  - If configured (e.g. env vars for CodeArtifact domain, repo, regulation package/version), uses **AWS CLI** or SDK to fetch the **regulation** JAR from CodeArtifact (e.g. `aws codeartifact get-package-version-asset` for the Maven asset) and writes it to `plugins/` (e.g. `poc-regulation-1.0.0.jar`).  
  - Then runs the engine JAR as today (`java -jar engine-*.jar`).  
- The **task role** must have CodeArtifact read permission (e.g. `codeartifact:GetPackageVersionAsset`, and typically `codeartifact:GetAuthorizationToken` for the domain) so the container can pull the regulation JAR in the account.

### 6.4 Local build and run (unchanged)

- **Run app directly on your machine (no Docker):**  
  - In **payroll-engine-poc**: `mvn install` (builds engine).  
  - In **payroll-regulations-poc**: `mvn package` (builds regulation JAR).  
  - Copy `poc-regulation-1.0.0.jar` into `engine/plugins/`.  
  - From repo root or `engine/`: `java -jar engine/target/engine-1.0.0-SNAPSHOT.jar` (and ensure `plugins/` is on the classpath or next to CWD as today).  
- **Build and run the container locally:**  
  - Use the existing **`./scripts/build-image.sh [path-to-regulation-jar]`**: it runs `mvn clean install`, copies the regulation JAR into `engine/plugins/`, then `docker build` and optionally run.  
  - No CodeArtifact required for this path; all JARs come from local Maven and the script.

### 6.5 Placeholders and configuration

- **CodeArtifact:** Use repo vars (e.g. `CODEARTIFACT_DOMAIN`, `CODEARTIFACT_REPOSITORY`, `CODEARTIFACT_DOMAIN_OWNER`) in workflows; template parameters (e.g. `CodeArtifactDomain`, `CodeArtifactRepository`) for the task. Set to real values in the account you use (CLI or pipeline).  
- **Maven / generic:** Engine: generic package `payroll-engine-poc` / `engine-dist` with version e.g. `1.0.0-<sha>`. Regulation: Maven `com.payroll:poc-regulation:1.0.0`, `com.payroll:regulation-api`.  
- **ECR:** As today (e.g. `RepositoryPrefix`, image tag from version). Set repo var **`ECR_REGISTRY`** (e.g. `237156726900.dkr.ecr.us-east-1.amazonaws.com`) for the main-build workflow to push the image.

### 6.7 Verified CodeArtifact contents (gp-prod / payroll-engine)

- **Generic:** `payroll-engine-poc` / `engine-dist` — versions like `1.0.0-<git-sha>` (e.g. `1.0.0-645c4e6a6d2be2120f97a0878dd486c482ff8dd2`).  
- **Maven:** `com.payroll:regulation-api`, `com.payroll:poc-regulation` (and upstream Maven packages).  
- **Domain owner:** `237156726900` (us-east-1).

### 6.6 Implemented files (Phase D)

| Repo / location | File | Purpose |
|-----------------|------|--------|
| payroll-engine-poc | `.github/workflows/build-engine-jar.yaml` | Build engine, zip engine+lib, publish to CodeArtifact (generic). |
| payroll-engine-poc | `.github/workflows/main-build.yaml` | Build image: from CodeArtifact (download engine dist) or from source (Maven); push to ECR. |
| payroll-regulations-poc | `.github/workflows/build-regulation-jar.yaml` | Build regulation JAR, publish to CodeArtifact (Maven). |
| payroll-engine-poc | `images/.../entrypoint.sh` | Optional fetch of regulation JAR from CodeArtifact into `plugins/` before run. |
| payroll-engine-poc | `template.yaml` | Params: CodeArtifactDomain, CodeArtifactRepository, RegulationVersion; task role CodeArtifact read. |
| payroll-engine-poc | `scripts/build-image.sh` | Unchanged: local Maven + copy regulation + docker build. |

---

## 7. Exemplar file reference

| File | Use for |
|------|--------|
| `exp-container-image-exemplar/template.yaml` | EcsTaskExecutionRole, TestContainerDefinition, TestContainerLogGroup, Mappings.ImageBuildConfiguration, Parameters (RepositoryPrefix, Environment, DeploymentVersion, VpcId, VpcSubnetIds). |
| `exp-container-image-exemplar/images/exp-container-image-exemplar/Dockerfile` | Structure of Dockerfile (for POC, replace with Java base + JAR + CMD). |
| `exp-container-image-exemplar/.github/workflows/main-build.yaml` | Build container images, publish, then deploy. |
| `exp-container-image-exemplar/samconfig.toml` | Deploy parameters, `image_repositories`, `parameter_overrides`. |
| `exp-container-image-exemplar/scripts/deploy.sh` | How to run `sam deploy` per environment. |

This plan, when implemented, will make the payroll engine POC runnable on Fargate with results written to S3 and triggerable manually, on a schedule, or via an API/event.
