# Leveraging Cursor for the Java Payroll Engine POC — 10‑Minute Presentation Write-up

Use this as speaker notes or to build slides. Each section is roughly 1 minute; total ~10 minutes.

---

## 1. Title & intent (30 sec)

**Slide:** “How I used Cursor to deliver the Java Payroll Engine POC”

**Say:** This POC is a minimal payroll engine that runs on AWS Fargate, processes employee stub data from S3, applies France regulation logic from a plugin JAR, and writes results back to S3. Almost all of it—requirements capture, design, implementation, CI/CD, deployment, debugging, and performance comparison—was done with Cursor as my primary pair-programming tool.

---

## 2. Requirements (1 min)

**What we had to nail down:**
- Engine: config-driven loader, employee-by-employee payrun, no payroll rules in the engine.
- Contract: regulation-api (interfaces) so engine and regulation JARs stay decoupled.
- Regulation: plugin JAR (e.g. France) implementing the contract; discovered via `regulations.json`.
- Runtime: container on Fargate, S3 for input (stub list + JSONs) and output (results).
- Comparison: same pipeline and metrics as the TypeScript POC for fair comparison.

**How Cursor helped:** I described the goal (“engine that loads regulation from JARs, runs payruns from stub data, outputs to S3”) and referenced existing docs. Cursor helped turn that into structured requirements and a design doc (e.g. `PAYROLL_ENGINE_POC_DESIGN.md`), and kept the contract vs engine vs regulation boundaries clear in the codebase.

---

## 3. Planning (1 min)

**Planned in docs, then implemented:**
- **Design:** `PAYROLL_ENGINE_POC_DESIGN.md` — engine, regulation-api, plugin loading, stub data flow.
- **Deployment:** `FARGATE_DEPLOYMENT_PLAN.md` — Phase A (containerize) → B (SAM/task definition) → C (pipeline) → D (CodeArtifact + image from artifact).
- **Fair comparison:** `POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md` — same regulation workload, metrics, and run conditions for Java vs TS.
- **Precompiled JAR flow:** `poc_precompiled_jar_local_development_guide.md` — how engine and regulation JARs are built, published to CodeArtifact, and used in the image.

**How Cursor helped:** I asked for “a deployment plan for Fargate” and “how to standardize Java vs TS comparison.” Cursor proposed phased plans and checklist-style docs; I refined and committed them. Planning stayed in markdown next to the code, so Cursor could use it when generating or changing code.

---

## 4. Implementation (1.5 min)

**What was built:**
- **Engine (payroll-engine-poc):** regulation-api module, engine module (Main, registry, loader, MinimalPayrun, stub context), `regulations.json`, Dockerfile, entrypoint (S3 sync → Java → sync).
- **Regulation (payroll-regulations-poc):** France regulation evaluator, wage types, rules; Method cache and BigDecimal constant reuse for performance; optional MethodHandle-based direct dispatch (see precompiled JAR guide).
- **Infrastructure:** SAM template — ECS task definition, roles, S3 bucket, security group, log group; image build config in `template.yaml` (including ARM64/Graviton).

**How Cursor helped:** I described the contract (e.g. “RegulationEvaluator with evaluate(context, wageTypeId)”) and asked for the engine loader and payrun loop. Cursor generated registry, classloader usage, and per-employee loop. For the regulation, I asked for “method cache and constant reuse to reduce reflection and allocation”; Cursor added the cache and `c()` helper. For ARM64, I asked to “change arch to arm64 (Graviton)”; Cursor updated the template and Dockerfile (including multi-arch AWS CLI via `TARGETARCH`).

---

## 5. Pipelines (1 min)

**CI/CD:**
- **main-build.yaml:** Checkout → OIDC assume role → resolve version from CodeArtifact (or from source) → download engine dist (and optionally regulation) → set image tag → **QEMU + Buildx** → build ARM64 image → push to ECR → upload deploy artifacts.
- **deploy.yaml:** Consumes build output; runs SAM deploy (stack update) for the Fargate task and related resources.
- **run-fargate-task.yaml:** Workflow_dispatch to run the ECS task once (e.g. for testing).

**How Cursor helped:** The first ARM64 build failed with “exec format error” on the x86 runner. I asked Cursor to “check the GitHub pipeline with gh cli and say why it failed.” Cursor ran `gh run list` and `gh run view --log-failed`, identified cross-build without emulation, and added `docker/setup-qemu-action` and `docker/setup-buildx-action` plus `docker buildx build --platform linux/arm64 --load` so the same workflow produces a valid ARM64 image.

---

## 6. Deployments (1 min)

**How we deploy:**
- Build and push are done in GitHub Actions (main-build); deploy is a separate job (deploy.yaml) that runs `sam deploy` with the right config env.
- SAM uses `template.yaml`: task definition (CPU, memory, ARM64, env vars for S3 bucket/prefixes, CodeArtifact), execution role, task role (S3 access), cluster, security group, log group.
- Image comes from ECR; tag is e.g. `dev-<version>` from CodeArtifact or from the build.

**How Cursor helped:** I asked for “ARM64 and any other Fargate/code optimisations.” Cursor changed the task definition and image platform to ARM64, made the Dockerfile multi-arch, and added a short optimisations doc (right-size CPU/memory, Spot, Container Insights, S3 concurrency). All deployment-related edits were done in the same session as the code and docs.

---

## 7. Debug & run task (1 min)

**Debug:**
- Pipeline: `gh run list` and `gh run view <id> --log-failed` to see why the ARM64 build failed; fix was QEMU + Buildx.
- Runtime: CloudWatch Logs for the ECS task; entrypoint and JVM logs show S3 sync, regulation load, and payrun progress.

**Run task:**
- **Script:** `scripts/run-fargate-task.sh [stack-name]` — uses AWS CLI to run the task once.
- **Or:** GitHub Actions `run-fargate-task.yaml` (workflow_dispatch) with stack name input.

**How Cursor helped:** I didn’t have to remember the exact `gh` or `aws ecs run-task` options; I asked “check the pipeline and why it failed” and “how do I run the Fargate task,” and Cursor ran the commands and pointed to the right scripts and workflows.

---

## 8. Result comparison (1.5 min)

**Docs:**
- `POC_JAVA_VS_TS_PERFORMANCE_FINAL.md` — observed numbers, reasons for the gap (reflection, concurrency, allocation, S3 path), fairness, and what was done (method cache, constant reuse).
- `POC_FARGATE_AND_CODE_OPTIMISATIONS.md` — ARM64, right-sizing, Spot, code optimisations (Java + TS), and the option to move S3 into the JVM later.

**Findings:**
- TS (Lambda) is ~4×–10× faster on the engine phase depending on workload alignment; S3 is much faster on TS (in-process, concurrency 50 vs CLI sync).
- Java improvements: method cache + constant reuse gave ~8–12% on the engine phase; ARM64 and optional direct dispatch (MethodHandle) in the regulation can help further.
- Fair comparison: same regulation (e.g. 60 wage types, no YTD), same metrics; document platform (Fargate vs Lambda) and concurrency differences.

**How Cursor helped:** I asked for “a final performance comparison and what can bridge the gap.” Cursor pulled from the existing theory and standardization docs and produced a consolidated comparison plus a short “further changes” section. The optimisations doc was generated in the same flow as the ARM64 change.

---

## 9. Further changes (1 min)

**Already done:** ARM64 (Graviton), method cache, constant reuse, MethodHandle path in regulation, QEMU/Buildx for CI, optimisations doc.

**Possible next steps:**
- **Fargate:** Right-size CPU/memory from CloudWatch; Fargate Spot for batch; Container Insights.
- **Code:** Align regulation workload (60 wage types, no YTD) for fair comparison; consider direct dispatch by default in the regulation JAR.
- **S3:** Move S3 download/upload into the JVM (or a small Node wrapper) with higher concurrency to close the S3 gap with the TS POC.

**How Cursor helped:** All of these were suggested in the same Cursor session that did the ARM64 and optimisations doc; I could say “what other optimisations?” and get a concise list plus doc updates.

---

## 10. Wrap-up (30 sec)

**Slide:** “Takeaways”

**Say:** Using Cursor, we went from requirements to a running Fargate POC with clear docs, CI/CD, and a fair performance comparison with the TS POC. Cursor was used for: drafting and refining design and deployment docs; implementing engine, contract, and regulation; fixing the ARM64 pipeline (QEMU/Buildx); and summarizing results and next steps. The key was keeping context in markdown and in the repo so Cursor could suggest code and doc changes that stayed consistent with the design. I’m happy to go deeper on any section or do a short demo (e.g. run a build, run the task, or show the comparison doc).

---

## Quick reference — doc map

| Topic | Doc |
|-------|-----|
| Design | `PAYROLL_ENGINE_POC_DESIGN.md` |
| Fargate plan | `FARGATE_DEPLOYMENT_PLAN.md` |
| Fair comparison | `POC_TS_VS_JAVA_FAIR_COMPARISON_STANDARDIZATION.md`, `POC_JAVA_VS_TS_PERFORMANCE_FINAL.md` |
| Precompiled JAR & local dev | `poc_precompiled_jar_local_development_guide.md` |
| Optimisations (ARM64, Fargate, code) | `POC_FARGATE_AND_CODE_OPTIMISATIONS.md` |
| TS Fargate optimisations | `TS_POC_FARGATE_OPTIMIZATIONS.md` |
| Run task | `scripts/run-fargate-task.sh`, `.github/workflows/run-fargate-task.yaml` |
