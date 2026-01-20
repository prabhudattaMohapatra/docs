# Detailed Pipeline Execution Flow

This document provides a comprehensive, step-by-step breakdown of what happened in both the Build and Deploy pipeline runs, including all connections, imports, exports, and data flows.

## Pipeline Run Information

- **Build Pipeline**: https://github.com/gp-nova/payroll-engine-backend/actions/runs/21060652033
- **Deploy Pipeline**: https://github.com/gp-nova/payroll-engine-backend/actions/runs/21065397740
- **Build Commit**: `b744cca` (from `github-workflows` branch)
- **Deploy Commit**: `a1cb645` (from `github-workflows` branch)

---

## PART 1: BUILD PIPELINE (`main-build.yaml`)

### Pipeline Overview
**Workflow**: `Main Build`  
**Trigger**: Manual workflow dispatch (or push to `main` branch)  
**Branch**: `github-workflows`  
**Job**: `build-and-publish`  
**Duration**: ~3 minutes 20 seconds

---

### Step-by-Step Execution Flow

#### **Phase 1: Job Initialization**

**1.1. GitHub Actions Runner Setup**
- **Runner**: Ubuntu 24.04.3 LTS (GitHub-hosted)
- **Runner Version**: 2.331.0
- **Worker ID**: `{2d5bc1de-58d6-4acf-a398-5dd9dad1d089}`
- **Working Directory**: `/home/runner/work/payroll-engine-backend/payroll-engine-backend`
- **Permissions Granted**:
  - `actions: read`
  - `contents: read`
  - `id-token: write` (for OIDC authentication)

**1.2. Action Downloads**
The runner downloads all required GitHub Actions:
- `actions/checkout@v4` (SHA: `34e114876b0b11c390a56381ad16ebd13914f8d5`)
- `gp-nova/devx-action-assume-oidc-role@v2` (SHA: `9a331dd97a3616061610a83702275a700c76d4af`)
- `gp-nova/devx-pipeline-modules-containers@v1` (SHA: `dfe030b94664a9021491e4a6b0137964192684ff`)
- `aws-actions/configure-aws-credentials@v4` (SHA: `7474bc4690e29a8392af63c5b98e7449536d5c3a`)
- `docker/setup-qemu-action@v3` (SHA: `c7c53464625b32c7a7e944ae62b3e17d2b600130`)
- `docker/setup-buildx-action@v3` (SHA: `8d2750c68a42422c14e847fe6c8ac0403b4cbd6f`)
- `actions/upload-artifact@v4` (SHA: `ea165f8d65b6e75b540449e92b4886f43607fa02`)

**Connections**:
- **GitHub API**: Downloads actions from GitHub repositories
- **Storage**: Actions cached in `/home/runner/work/_actions/`

---

#### **Phase 2: Code Checkout**

**2.1. Checkout Step**
- **Action**: `actions/checkout@v4`
- **Repository**: `gp-nova/payroll-engine-backend`
- **Branch**: `refs/heads/github-workflows`
- **Authentication**: Uses `GITHUB_TOKEN` (automatically provided)

**2.2. Git Operations**
```
1. Initialize empty Git repository
2. Add remote: https://github.com/gp-nova/payroll-engine-backend
3. Fetch branch: github-workflows (depth=1, no tags)
4. Checkout: refs/remotes/origin/github-workflows
5. Commit SHA: b744cca887e66d582bb59b00c9f0b95256d92313
```

**Connections**:
- **GitHub Repository**: `https://github.com/gp-nova/payroll-engine-backend`
- **Protocol**: HTTPS with authentication token
- **Data Flow**: Repository code → Runner workspace

**Files Retrieved**:
- All repository files including:
  - `Dockerfile`
  - `template.yaml`
  - `.gp/roles.yaml`
  - Source code files
  - Project files (`.csproj`, `.sln`)

---

#### **Phase 3: OIDC Authentication**

**3.1. OIDC Role Assumption**
- **Action**: `gp-nova/devx-action-assume-oidc-role@v2`
- **Environment**: `dev`
- **Config Location**: `./.gp/roles.yaml` (found in workspace)

**3.2. Configuration File Processing**
- **File**: `/home/runner/work/payroll-engine-backend/payroll-engine-backend/./.gp/roles.yaml`
- **Content**:
  ```yaml
  dev:
    account-id: '258215414239'
    assume-role: '258215414239-gh-action-oid'
    exec-role: '258215414239-gh-execution-oid'
    region: 'us-east-1'
  ```

**3.3. AWS Role Assumption Process**
1. **Get OIDC Token from GitHub**:
   - GitHub provides JWT token via `ACTIONS_ID_TOKEN_REQUEST_TOKEN` and `ACTIONS_ID_TOKEN_REQUEST_URL`
   - Token includes repository claim: `repo:gp-nova/payroll-engine-backend:*`

2. **Assume IAM Role**:
   - **Role ARN**: `arn:aws:iam::258215414239:role/258215414239-gh-action-oid`
   - **Action**: `sts:AssumeRoleWithWebIdentity`
   - **Trust Policy Check**: IAM validates GitHub repository claim
   - **Result**: Temporary AWS credentials (Access Key, Secret Key, Session Token)

3. **Assume Execution Role**:
   - **Role ARN**: `arn:aws:iam::258215414239:role/258215414239-gh-execution-oid`
   - **Action**: `sts:AssumeRole` (using credentials from step 2)
   - **Result**: Final AWS credentials for workflow execution

**3.4. AWS Credentials Exported**
Environment variables set:
- `AWS_ACCOUNT_ID=258215414239`
- `AWS_ACCESS_KEY_ID=***` (temporary)
- `AWS_SECRET_ACCESS_KEY=***` (temporary)
- `AWS_SESSION_TOKEN=***` (temporary, expires)
- `AWS_DEFAULT_REGION=us-east-1`
- `OIDC_ROLE=258215414239-gh-action-oid`
- `EXEC_ROLE=258215414239-gh-execution-oid`
- `DEVX_ACCOUNT_ID=258215414239`
- `DEVX_ENVIRONMENT=dev`

**Connections**:
- **GitHub OIDC Provider**: `token.actions.githubusercontent.com`
- **AWS STS Service**: `sts.amazonaws.com` (us-east-1)
- **IAM Service**: Validates trust policy and issues credentials
- **Data Flow**: GitHub JWT → AWS STS → Temporary AWS Credentials

---

#### **Phase 4: Version Determination**

**4.1. Set Version Step**
- **Command**: `git rev-parse --short HEAD`
- **Result**: `b744cca` (7-character commit SHA)
- **Output**: `version=b744cca` → `$GITHUB_OUTPUT`

**Data Flow**: Git repository → Version variable → Used in subsequent steps

---

#### **Phase 5: Container Image Build**

**5.1. Build Action Invocation**
- **Action**: `gp-nova/devx-pipeline-modules-containers/build@v1`
- **Working Directory**: `.` (repository root)
- **Environment Variables**:
  - `SNYK_TOKEN=***` (from GitHub secrets, for security scanning)

**5.2. Build Configuration Parsing**
- **Script**: `parse-build-config.sh`
- **Input**: `template.yaml` from repository root
- **Parsing Process**:
  1. Detects CloudFormation/SAM template format
  2. Extracts `ImageBuildConfiguration` mapping
  3. Finds `BackendService` configuration:
     - `buildContext: .`
     - `dockerfile: Dockerfile`
     - `imageName: payroll-engine-backend`
     - `platform: linux/amd64`
  4. Writes parsed config to: `/tmp/build-config.W1sl7F.yaml`

**5.3. ECR Registry Determination**
- **Logic**: Checks `TEST_MODE` environment variable
- **Result**: `ECR_REGISTRY="237156726900.dkr.ecr.us-east-1.amazonaws.com"` (production registry)
- **Alternative**: If `TEST_MODE=true`, would use `890779668410.dkr.ecr.us-east-1.amazonaws.com`

**5.4. ECR Login**
- **Command**: `aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 237156726900.dkr.ecr.us-east-1.amazonaws.com`
- **Authentication**: Uses AWS credentials from OIDC role
- **Result**: Docker client authenticated to ECR registry

**Connections**:
- **AWS ECR Service**: `ecr.us-east-1.amazonaws.com`
- **Account**: `237156726900` (shared ECR account)
- **Data Flow**: AWS Credentials → ECR Login Token → Docker Client

**5.5. Docker Buildx Setup**
- **Action**: `docker/setup-buildx-action@v3`
- **Process**:
  1. Creates builder instance: `builder-7d299672-678f-4ac7-8dc1-b699093f7404`
  2. Pulls BuildKit image: `moby/buildkit:buildx-stable-1` from Docker Hub
  3. Creates BuildKit container for multi-platform builds
  4. BuildKit version: `v0.26.3`
  5. Platforms supported: `linux/amd64`, `linux/arm64`, etc.

**Connections**:
- **Docker Hub**: `docker.io` (for BuildKit base image)
- **Image**: `moby/buildkit:buildx-stable-1`
- **Data Flow**: Docker Hub → BuildKit Container → Build Environment

**5.6. QEMU Setup (Multi-Platform Support)**
- **Action**: `docker/setup-qemu-action@v3`
- **Process**:
  1. Pulls binfmt image: `docker.io/tonistiigi/binfmt:latest`
  2. Installs QEMU emulators for cross-platform builds
  3. Enables ARM64 emulation on AMD64 runner

**Connections**:
- **Docker Hub**: `docker.io/tonistiigi/binfmt:latest`
- **Digest**: `sha256:30cc9a4d03765acac9be2ed0afc23af1ad018aed2c28ea4be8c2eb9afe03fbd1`
- **Data Flow**: Docker Hub → QEMU Emulators → Build Environment

**5.7. Docker Build Execution**

**Build Command** (constructed by build action):
```bash
docker buildx build \
  --platform linux/amd64 \
  --file Dockerfile \
  --tag payroll-engine-backend:build \
  --load \
  .
```

**5.8. Dockerfile Execution - Build Stage**

**Step 1: Base Image Pull**
- **FROM**: `mcr.microsoft.com/dotnet/sdk:10.0`
- **Platform**: `$BUILDPLATFORM` (linux/amd64)
- **Source**: Microsoft Container Registry (MCR)
- **Connection**: `mcr.microsoft.com` → Pulls .NET SDK 10.0 image
- **Data Flow**: MCR → Docker Build Context

**Step 2: Set Working Directory**
- **WORKDIR**: `/src`
- **Action**: Creates directory structure in container

**Step 3: Copy Solution and Project Files**
- **Files Copied**:
  - `PayrollEngine.Backend.sln`
  - `Api/Api.Controller/PayrollEngine.Api.Controller.csproj`
  - `Api/Api.Core/PayrollEngine.Api.Core.csproj`
  - `Api/Api.Map/PayrollEngine.Api.Map.csproj`
  - `Api/Api.Model/PayrollEngine.Api.Model.csproj`
  - `Backend.Controller/PayrollEngine.Backend.Controller.csproj`
  - `Backend.Server/PayrollEngine.Backend.Server.csproj`
  - `Domain/Domain.Application/PayrollEngine.Domain.Application.csproj`
  - `Domain/Domain.Model/PayrollEngine.Domain.Model.csproj`
  - `Domain/Domain.Model.Tests/PayrollEngine.Domain.Model.Tests.csproj`
  - `Domain/Domain.Scripting/PayrollEngine.Domain.Scripting.csproj`
  - `Persistence/Persistence/PayrollEngine.Persistence.csproj`
  - `Persistence/Persistence.SqlServer/PayrollEngine.Persistence.SqlServer.csproj`
  - `Directory.Build.props`

**Step 4: NuGet Package Restore**

**⚠️ IMPORTANT: NuGet Package Sources**

Based on the current `Dockerfile` (lines 29-34), the restore command is:
```dockerfile
RUN if [ "$TARGETARCH" = "arm64" ]; then \
      dotnet restore "PayrollEngine.Backend.sln" --runtime linux-arm64; \
    else \
      dotnet restore "PayrollEngine.Backend.sln" --runtime linux-x64; \
    fi
```

**NuGet Package Sources Used**:
1. **Default NuGet.org Source**: `https://api.nuget.org/v3/index.json`
   - **Purpose**: Public NuGet packages (third-party dependencies)
   - **Connection**: HTTPS to `api.nuget.org`
   - **Authentication**: None (public feed)
   - **Packages**: Microsoft.*, System.*, and other public packages

2. **No Local Feed in Current Dockerfile**:
   - The Dockerfile does NOT copy a local NuGet feed
   - The Dockerfile does NOT configure custom NuGet sources
   - All packages are restored from the default `nuget.org` source

**Restore Process**:
- **Command**: `dotnet restore "PayrollEngine.Backend.sln" --runtime linux-x64`
- **Runtime**: `linux-x64` (since platform is `linux/amd64`)
- **Process**:
  1. Reads `.csproj` files to determine package dependencies
  2. Queries `https://api.nuget.org/v3/index.json` for package metadata
  3. Downloads packages from NuGet.org CDN
  4. Caches packages in container's NuGet cache
  5. Resolves dependencies and creates `project.assets.json` files

**Connections**:
- **NuGet.org API**: `https://api.nuget.org/v3/index.json`
- **NuGet CDN**: Downloads package `.nupkg` files
- **Data Flow**: 
  - `.csproj` files → NuGet.org API → Package metadata
  - Package metadata → NuGet CDN → Package files → Container cache

**Step 5: Copy All Source Files**
- **Command**: `COPY . .`
- **Action**: Copies remaining source code files into container
- **Includes**: All `.cs`, `.json`, configuration files, etc.

**Step 6: Copy Database Folder**
- **Command**: `COPY ["Database/", "Database/"]`
- **Purpose**: Database schema files for migration

**Step 7: Restore Again (After Full Copy)**
- **Command**: Same as Step 4
- **Purpose**: Ensures all assets are correct after full file copy
- **Sources**: Same as Step 4 (NuGet.org only)

**Step 8: Publish Application**
- **WORKDIR**: `/src/Backend.Server`
- **Command**: 
  ```bash
  dotnet publish "PayrollEngine.Backend.Server.csproj" \
    -c Release \
    -o /app/publish \
    --runtime linux-x64 \
    --self-contained false \
    --no-restore
  ```
- **Process**:
  1. Compiles C# source code to IL (Intermediate Language)
  2. Links assemblies and dependencies
  3. Copies published files to `/app/publish`
  4. Creates `PayrollEngine.Backend.Server.dll` and dependencies

**Connections**:
- **Input**: Source code files, restored NuGet packages
- **Output**: Compiled application in `/app/publish`
- **Data Flow**: Source Code + NuGet Packages → .NET Compiler → Compiled DLLs

**5.9. Dockerfile Execution - Final Stage**

**Step 1: Base Image Pull**
- **FROM**: `mcr.microsoft.com/dotnet/aspnet:10.0`
- **Source**: Microsoft Container Registry (MCR)
- **Connection**: `mcr.microsoft.com` → Pulls .NET ASP.NET runtime image
- **Purpose**: Runtime image (smaller than SDK, no build tools)

**Step 2: Install System Dependencies**
- **Command**: `apt-get update && apt-get install -y ...`
- **Packages Installed**:
  - `libgdiplus` (for System.Drawing.Common)
  - `fonts-liberation`, `fonts-dejavu-core`, `fonts-freefont-ttf` (for FastReport)
  - `fontconfig` (font configuration)
  - `libc6-dev`, `libx11-dev`, `libxext-dev`, `libxrender-dev`, `libxtst-dev`
  - `libxrandr-dev`, `libasound2-dev`, `libcairo2-dev`, `libpango1.0-dev`
  - `libatk1.0-dev`, `libgtk-3-dev` (for FastReport PDF generation)

**Connections**:
- **Ubuntu Package Repositories**: `archive.ubuntu.com` or mirrors
- **Data Flow**: Package Repositories → APT → Installed Packages

**Step 3: Copy Published Application**
- **Command**: `COPY --from=build /app/publish .`
- **Source**: Build stage output (`/app/publish`)
- **Destination**: `/app` in final image
- **Includes**: `PayrollEngine.Backend.Server.dll` and all dependencies

**Step 4: Copy Database Folder**
- **Command**: `COPY --from=build /src/Database ./Database`
- **Purpose**: Database schema files available at runtime

**Step 5: Set Environment Variables**
- `DISPLAY=:99` (for FastReport)
- `FONTCONFIG_PATH=/etc/fonts` (for font configuration)

**Step 6: Set Entrypoint**
- **ENTRYPOINT**: `["dotnet", "PayrollEngine.Backend.Server.dll"]`

**5.10. Build Completion**
- **Result**: `Successfully built payroll-engine-backend:build`
- **Image Tag**: `payroll-engine-backend:build` (local tag)
- **Image Size**: ~280 MB (293,981,050 bytes)

**Connections Summary - Build Phase**:
- **GitHub Repository** → Code checkout
- **Microsoft Container Registry** → Base images (SDK, ASP.NET)
- **NuGet.org** → Package restore
- **Ubuntu Package Repositories** → System dependencies
- **Docker BuildKit** → Image construction
- **Local Docker** → Image storage

---

#### **Phase 6: Security Scanning (Optional)**

**6.1. Snyk Scanning** (if `SNYK_TOKEN` is provided)
- **Action**: Integrated in build action
- **Process**: Scans container image for vulnerabilities
- **Connection**: Snyk API (if token provided)

**Note**: Scanning may be skipped if token is not available

---

#### **Phase 7: Container Image Publishing**

**7.1. Publish Action Invocation**
- **Action**: `gp-nova/devx-pipeline-modules-containers/publish@v1`
- **Input**: `version=b744cca`

**7.2. ECR Registry Determination**
- **Same Logic**: Checks `TEST_MODE`
- **Result**: `ECR_REGISTRY="237156726900.dkr.ecr.us-east-1.amazonaws.com"`

**7.3. ECR Login**
- **Command**: `aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 237156726900.dkr.ecr.us-east-1.amazonaws.com`
- **Authentication**: Uses AWS credentials from OIDC role
- **Connection**: AWS ECR Service → Login Token

**7.4. Image Tagging**
- **Local Image**: `payroll-engine-backend:build`
- **ECR Image Name**: `payroll-engine/payroll-engine-backend` (constructed as `payroll-engine/{imageName}`)
- **Tag Format**: `build-{version}` → `build-b744cca`
- **Full Image URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`

**Tagging Command**:
```bash
docker tag payroll-engine-backend:build \
  237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca
```

**7.5. Image Push**
- **Command**: `docker push 237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **Process**:
  1. Docker client pushes image layers to ECR
  2. ECR receives and stores image layers
  3. ECR creates image manifest
  4. Image is available in ECR repository

**7.6. Push Result**
- **Digest**: `sha256:5535ea46189c912626a2af7163b124c79a78a0349858dc418bf9337c6e4d346a`
- **Size**: 293,981,050 bytes (~280 MB)
- **Pushed At**: 2026-01-16T14:11:47.149000+05:30
- **Tag**: `build-b744cca`

**Connections**:
- **Docker Client** → **AWS ECR** (`237156726900.dkr.ecr.us-east-1.amazonaws.com`)
- **Account**: `237156726900` (shared ECR account)
- **Repository**: `payroll-engine/payroll-engine-backend`
- **Data Flow**: Local Docker Image → ECR API → ECR Storage

**7.7. Image Verification**
- **Command**: `aws ecr describe-images --repository-name payroll-engine/payroll-engine-backend --image-ids imageTag=build-b744cca --registry-id 237156726900`
- **Result**: Image verified in ECR
- **Note**: Initial verification failed because it checked wrong account (`258215414239` instead of `237156726900`), but image was successfully pushed

---

## PART 2: DEPLOY PIPELINE (`deploy.yaml`)

### Pipeline Overview
**Workflow**: `Deploy to ECS`  
**Trigger**: Push to `github-workflows` branch  
**Branch**: `github-workflows`  
**Commit**: `a1cb6455a02f002d625b977d2ff41ed314b506c0`  
**Job**: `deploy`  
**Environment**: `dev` (default)  
**Duration**: ~3 minutes 14 seconds

---

### Step-by-Step Execution Flow

#### **Phase 1: Job Initialization**

**1.1. GitHub Actions Runner Setup**
- **Runner**: Ubuntu 24.04.3 LTS (GitHub-hosted)
- **Runner Version**: 2.331.0
- **Worker ID**: `{2527982d-2bb2-4b6a-ba80-381ff1875deb}`
- **Working Directory**: `/home/runner/work/payroll-engine-backend/payroll-engine-backend`
- **Permissions**: `contents: read`, `id-token: write`

**1.2. Action Downloads**
- `actions/checkout@v4`
- `gp-nova/devx-action-assume-oidc-role@v2`
- `aws-actions/amazon-ecr-login@v2`
- `aws-actions/configure-aws-credentials@v4`

---

#### **Phase 2: Code Checkout**

**2.1. Checkout Step**
- **Repository**: `gp-nova/payroll-engine-backend`
- **Ref**: `a1cb6455a02f002d625b977d2ff41ed314b506c0` (specific commit from `github-workflows` branch)
- **Commit SHA**: `a1cb6455a02f002d625b977d2ff41ed314b506c0`

**Connections**: GitHub Repository → Runner Workspace

---

#### **Phase 3: OIDC Authentication**

**3.1. OIDC Role Assumption**
- **Action**: `gp-nova/devx-action-assume-oidc-role@v2`
- **Environment**: `dev`
- **Config**: `./.gp/roles.yaml` (same as build pipeline)

**3.2. AWS Credentials Obtained**
- **Account**: `258215414239`
- **Roles**: Same as build pipeline
- **Credentials**: Temporary AWS credentials for ECS and ECR operations

**Connections**: GitHub OIDC → AWS STS → AWS Credentials

---

#### **Phase 4: ECR Login (Cross-Account)**

**4.1. ECR Login Step**
- **Action**: `aws-actions/amazon-ecr-login@v2`
- **Registry ID**: `237156726900` (shared ECR account)
- **Region**: `us-east-1`

**4.2. Login Process**
1. **Get ECR Login Token**:
   - **Command**: `aws ecr get-login-password --region us-east-1`
   - **Service**: AWS ECR in account `237156726900`
   - **Authentication**: Uses OIDC-assumed role credentials
   - **Result**: ECR login token (temporary, expires)

2. **Docker Login**:
   - **Command**: `docker login --username AWS --password-stdin 237156726900.dkr.ecr.us-east-1.amazonaws.com`
   - **Result**: Docker client authenticated to ECR registry

**Connections**:
- **AWS ECR Service** (Account `237156726900`) → Login Token
- **Docker Client** → ECR Registry Authentication
- **Data Flow**: AWS Credentials → ECR API → Login Token → Docker Client

**Note**: This is a **cross-account operation** - the OIDC role in account `258215414239` needs permissions to access ECR in account `237156726900`.

---

#### **Phase 5: Image Tag Determination**

**5.1. Determine Image Tag Step**
- **Input**: No `image-tag` provided (triggered by push)
- **Logic**: Fetch latest image from ECR

**5.2. ECR Query**
- **Command**:
  ```bash
  aws ecr describe-images \
    --repository-name payroll-engine/payroll-engine-backend \
    --region us-east-1 \
    --registry-id 237156726900 \
    --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
    --output text
  ```
- **Process**:
  1. Queries ECR in account `237156726900`
  2. Lists all images in repository `payroll-engine/payroll-engine-backend`
  3. Sorts by `imagePushedAt` (push timestamp)
  4. Gets the most recent image tag
  5. **Result**: `build-b744cca`

**Connections**:
- **AWS ECR API** (Account `237156726900`) → Image Metadata
- **Data Flow**: ECR API → Image List → Sorted by Date → Latest Tag

---

#### **Phase 6: Image Verification**

**6.1. Verify Image Exists**
- **Command**:
  ```bash
  aws ecr describe-images \
    --repository-name payroll-engine/payroll-engine-backend \
    --image-ids imageTag=build-b744cca \
    --region us-east-1 \
    --registry-id 237156726900
  ```
- **Result**: Image details retrieved:
  - **Tags**: `["build-b744cca"]`
  - **Pushed At**: `2026-01-16T14:11:47.149000+05:30`
  - **Size**: `293,981,050 bytes`
  - **Digest**: `sha256:5535ea46189c912626a2af7163b124c79a78a0349858dc418bf9337c6e4d346a`

**Full Image URI**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca
```

**Connections**: AWS ECR API → Image Verification

---

#### **Phase 7: ECS Task Definition Retrieval**

**7.1. Get Current Task Definition**
- **Command**:
  ```bash
  aws ecs describe-task-definition \
    --task-definition feature-payroll-engine-backend \
    --region us-east-1 \
    --query 'taskDefinition' > /tmp/task-def-temp.json
  ```
- **Service**: AWS ECS in account `258215414239`
- **Task Definition Family**: `feature-payroll-engine-backend`
- **Result**: Current task definition JSON saved to `/tmp/task-def-temp.json`

**7.2. Extract Current Image**
- **Command**: `jq -r '.containerDefinitions[0].image' /tmp/task-def-temp.json`
- **Result**: Previous image URI (for comparison)
- **Output**: Saved to `$GITHUB_OUTPUT` as `current-image`

**Connections**:
- **AWS ECS API** (Account `258215414239`) → Task Definition
- **Data Flow**: ECS API → Task Definition JSON → Local File

---

#### **Phase 8: Task Definition Update**

**8.1. Update Image URI**
- **New Image URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **Command**:
  ```bash
  jq ".containerDefinitions[0].image = \"$IMAGE_URI\"" /tmp/task-def-temp.json | \
    jq 'del(.taskDefinitionArn, .revision, .status, ...)' > /tmp/new-task-def-temp.json
  ```
- **Process**:
  1. Updates `containerDefinitions[0].image` field
  2. Removes metadata fields (ARN, revision, status, etc.)
  3. Creates new task definition JSON

**8.2. Register New Task Definition**
- **Command**:
  ```bash
  aws ecs register-task-definition \
    --cli-input-json file:///tmp/new-task-def-temp.json \
    --region us-east-1 \
    --query 'taskDefinition.revision' \
    --output text
  ```
- **Service**: AWS ECS in account `258215414239`
- **Result**: New revision number (e.g., `5`)
- **Output**: `revision=5` → `$GITHUB_OUTPUT`

**Connections**:
- **AWS ECS API** (Account `258215414239`) → Task Definition Registration
- **Data Flow**: Updated JSON → ECS API → New Task Definition Revision

**Task Definition Changes**:
- **Previous Image**: (whatever was in the old revision)
- **New Image**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **All Other Settings**: Unchanged (CPU, memory, environment variables, etc.)

---

#### **Phase 9: ECS Service Update**

**9.1. Update ECS Service**
- **Command**:
  ```bash
  aws ecs update-service \
    --cluster payroll-engine-cluster \
    --service feature-payroll-engine-backend \
    --task-definition feature-payroll-engine-backend:5 \
    --force-new-deployment \
    --region us-east-1
  ```
- **Cluster**: `payroll-engine-cluster` (in account `258215414239`)
- **Service**: `feature-payroll-engine-backend`
- **New Task Definition**: `feature-payroll-engine-backend:5` (revision 5)
- **Force Deployment**: `--force-new-deployment` (ensures new tasks start)

**9.2. Update Process**
1. **ECS Service Update**:
   - ECS updates service configuration
   - Service now references new task definition revision
   - ECS schedules new tasks with new image

2. **Task Scheduling**:
   - ECS starts new tasks using task definition revision 5
   - New tasks pull image from ECR account `237156726900`
   - Old tasks are drained (graceful shutdown)

3. **Load Balancer Integration**:
   - New tasks register with target group
   - Old tasks deregister from target group
   - Traffic shifts to new tasks

**Connections**:
- **AWS ECS API** (Account `258215414239`) → Service Update
- **AWS ECR** (Account `237156726900`) → Image Pull (by ECS tasks)
- **Data Flow**: 
  - ECS API → Service Configuration Update
  - ECS Tasks → ECR Image Pull → Container Start
  - Load Balancer → Health Checks → Traffic Routing

**9.3. Service Status**
- **Service Name**: `feature-payroll-engine-backend`
- **Task Definition**: `arn:aws:ecs:us-east-1:258215414239:task-definition/feature-payroll-engine-backend:5`
- **Desired Count**: `1`
- **Running Count**: `1` (after stabilization)

---

#### **Phase 10: Service Stabilization**

**10.1. Wait for Service to Stabilize**
- **Command**:
  ```bash
  aws ecs wait services-stable \
    --cluster payroll-engine-cluster \
    --services feature-payroll-engine-backend \
    --region us-east-1
  ```
- **Process**:
  1. Polls ECS service status
  2. Waits until:
     - All tasks are running
     - Health checks pass
     - No deployment errors
  3. **Duration**: ~3 minutes (typical for ECS deployment)

**10.2. Stabilization Criteria**
- **Running Count** = **Desired Count**
- **Deployment Status** = `PRIMARY` (active)
- **Task Health** = `HEALTHY`
- **No Pending Tasks**

**Connections**: AWS ECS API → Service Status Polling

---

#### **Phase 11: Deployment Verification**

**11.1. Verify Deployment**
- **Command**:
  ```bash
  aws ecs describe-services \
    --cluster payroll-engine-cluster \
    --services feature-payroll-engine-backend \
    --region us-east-1 \
    --query 'services[0].{...}'
  ```

**11.2. Verification Results**
- **Status**: `ACTIVE`
- **Running Count**: `1`
- **Desired Count**: `1`
- **Task Definition**: `arn:aws:ecs:us-east-1:258215414239:task-definition/feature-payroll-engine-backend:5`
- **Deployments**: Shows active and previous deployments

**Connections**: AWS ECS API → Service Status Verification

---

## PART 3: DATA FLOW DIAGRAM

### Build Pipeline Data Flow

```
GitHub Repository (gp-nova/payroll-engine-backend)
    ↓ [Git Checkout]
Runner Workspace (/home/runner/work/...)
    ↓ [OIDC Authentication]
AWS STS (Account 258215414239)
    ↓ [Temporary Credentials]
Docker Build Process
    ├─→ Microsoft Container Registry (mcr.microsoft.com)
    │   └─→ .NET SDK 10.0 Image
    │   └─→ .NET ASP.NET 10.0 Image
    ├─→ NuGet.org (api.nuget.org)
    │   └─→ Package Restore
    ├─→ Ubuntu Package Repositories
    │   └─→ System Dependencies
    └─→ Docker BuildKit
        └─→ Container Image (payroll-engine-backend:build)
            ↓ [Docker Tag]
            ↓ [Docker Push]
AWS ECR (Account 237156726900)
    └─→ payroll-engine/payroll-engine-backend:build-b744cca
```

### Deploy Pipeline Data Flow

```
GitHub Repository (gp-nova/payroll-engine-backend)
    ↓ [Git Checkout]
Runner Workspace
    ↓ [OIDC Authentication]
AWS STS (Account 258215414239)
    ↓ [Temporary Credentials]
    ├─→ AWS ECR (Account 237156726900) [Cross-Account]
    │   └─→ Query: Latest Image Tag
    │   └─→ Result: build-b744cca
    │   └─→ Verify: Image Exists
    ├─→ AWS ECS (Account 258215414239)
    │   ├─→ Get: Current Task Definition
    │   ├─→ Register: New Task Definition (Revision 5)
    │   └─→ Update: ECS Service
    │       └─→ ECS Tasks
    │           └─→ Pull Image from ECR (Account 237156726900) [Cross-Account]
    │           └─→ Start: Container with New Image
    └─→ Wait: Service Stabilization
        └─→ Verify: Deployment Success
```

---

## PART 4: CROSS-ACCOUNT OPERATIONS

### Account Relationships

**Account `258215414239` (Application Account)**:
- **Resources**:
  - ECS Cluster: `payroll-engine-cluster`
  - ECS Service: `feature-payroll-engine-backend`
  - ECS Task Definitions
  - IAM Roles: `258215414239-gh-action-oid`, `258215414239-gh-execution-oid`
  - Application Infrastructure

**Account `237156726900` (Shared ECR Account)**:
- **Resources**:
  - ECR Repository: `payroll-engine/payroll-engine-backend`
  - Container Images
  - ECR Registry

### Cross-Account Permissions Required

**1. GitHub Actions OIDC Role** (`258215414239-gh-action-oid`):
- **Needs**: `ecr:GetAuthorizationToken`, `ecr:DescribeImages` in account `237156726900`
- **Purpose**: Login to ECR and query images

**2. ECS Task Execution Role** (in account `258215414239`):
- **Needs**: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage` in account `237156726900`
- **Purpose**: Pull images from shared ECR account

**3. ECR Repository Policy** (in account `237156726900`):
- **Needs**: Allow cross-account access from account `258215414239`
- **Purpose**: Enable ECS tasks to pull images

---

## PART 5: NUGET PACKAGE SOURCES - DETAILED ANALYSIS

### Current Dockerfile Configuration

**Lines 29-34** (Restore Commands):
```dockerfile
RUN if [ "$TARGETARCH" = "arm64" ]; then \
      dotnet restore "PayrollEngine.Backend.sln" --runtime linux-arm64; \
    else \
      dotnet restore "PayrollEngine.Backend.sln" --runtime linux-x64; \
    fi
```

### NuGet Package Sources Used

**1. Default NuGet.org Source**
- **URL**: `https://api.nuget.org/v3/index.json`
- **Type**: Public NuGet feed
- **Authentication**: None required
- **Packages**: All public NuGet packages

**How It Works**:
1. `.NET SDK` comes with default NuGet source configured
2. `dotnet restore` automatically uses this source
3. No explicit `--source` parameter needed

**Package Restore Process**:
```
1. dotnet restore reads .csproj files
2. Identifies package dependencies (PackageReference)
3. Queries api.nuget.org/v3/index.json for package metadata
4. Downloads .nupkg files from NuGet CDN
5. Extracts packages to NuGet cache
6. Resolves dependencies
7. Creates project.assets.json
```

**Connections**:
- **NuGet.org API**: `https://api.nuget.org/v3/index.json` (HTTPS)
- **NuGet CDN**: Downloads package files (HTTPS)
- **Data Flow**: 
  - `.csproj` → NuGet API → Package Metadata
  - Package Metadata → NuGet CDN → Package Files → Container Cache

### Packages Restored

Based on the project structure, packages likely include:
- **Microsoft.AspNetCore.*** packages
- **Microsoft.EntityFrameworkCore.*** packages
- **System.*** packages
- **Third-party packages** (e.g., FastReport, logging libraries, etc.)

**All packages come from**: `https://api.nuget.org/v3/index.json`

### Note on Local NuGet Feed

**Important**: The current `Dockerfile` does NOT use a local NuGet feed. If you have a local feed (as mentioned in `build.sh`), it is NOT being used in the GitHub Actions build.

**To use a local feed**, the Dockerfile would need:
```dockerfile
# Copy local NuGet feed
COPY packages/ /packages/

# Configure NuGet sources
RUN dotnet nuget remove source nuget.org 2>/dev/null || true
RUN dotnet nuget add source /packages --name local-feed
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Then restore with sources
RUN dotnet restore ... --source /packages --source https://api.nuget.org/v3/index.json
```

**Current State**: Only `nuget.org` is used (default source).

---

## PART 6: CONNECTIONS AND ENDPOINTS SUMMARY

### External Connections - Build Pipeline

| Connection | Protocol | Endpoint | Purpose | Authentication |
|------------|----------|----------|---------|----------------|
| GitHub API | HTTPS | `api.github.com` | Download actions, checkout code | GITHUB_TOKEN |
| GitHub OIDC | HTTPS | `token.actions.githubusercontent.com` | OIDC token for AWS | Built-in |
| AWS STS | HTTPS | `sts.amazonaws.com` | Assume IAM roles | OIDC token |
| AWS ECR | HTTPS | `237156726900.dkr.ecr.us-east-1.amazonaws.com` | Login, push image | AWS credentials |
| Microsoft Container Registry | HTTPS | `mcr.microsoft.com` | Pull .NET base images | None (public) |
| NuGet.org API | HTTPS | `api.nuget.org` | Package metadata | None (public) |
| NuGet CDN | HTTPS | `*.nuget.org` | Download packages | None (public) |
| Ubuntu Repositories | HTTPS | `archive.ubuntu.com` | System packages | None (public) |
| Docker Hub | HTTPS | `docker.io` | BuildKit, QEMU images | None (public) |

### External Connections - Deploy Pipeline

| Connection | Protocol | Endpoint | Purpose | Authentication |
|------------|----------|----------|---------|----------------|
| GitHub API | HTTPS | `api.github.com` | Checkout code | GITHUB_TOKEN |
| GitHub OIDC | HTTPS | `token.actions.githubusercontent.com` | OIDC token | Built-in |
| AWS STS | HTTPS | `sts.amazonaws.com` | Assume IAM roles | OIDC token |
| AWS ECR (Cross-Account) | HTTPS | `237156726900.dkr.ecr.us-east-1.amazonaws.com` | Login, query images | AWS credentials |
| AWS ECS | HTTPS | `ecs.us-east-1.amazonaws.com` | Task definition, service updates | AWS credentials |
| ECS Tasks → ECR (Cross-Account) | HTTPS | `237156726900.dkr.ecr.us-east-1.amazonaws.com` | Pull container image | ECS task execution role |

---

## PART 7: IMAGE AND TASK DEFINITION LIFECYCLE

### Image Lifecycle

**Build Phase**:
1. **Local Build**: `payroll-engine-backend:build` (local tag)
2. **ECR Tag**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
3. **ECR Push**: Image stored in account `237156726900`
4. **Digest**: `sha256:5535ea46189c912626a2af7163b124c79a78a0349858dc418bf9337c6e4d346a`

**Deploy Phase**:
1. **Image Query**: Latest image from ECR (`build-b744cca`)
2. **Image Verification**: Confirmed in ECR
3. **Task Definition Update**: Image URI added to task definition
4. **ECS Service Update**: Service references new task definition
5. **ECS Task Pull**: Tasks pull image from ECR (cross-account)
6. **Container Start**: Application runs with new image

### Task Definition Lifecycle

**Before Deployment**:
- **Family**: `feature-payroll-engine-backend`
- **Revision**: `4` (example)
- **Image**: Previous image URI

**During Deployment**:
- **New Revision**: `5`
- **Image Updated**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **Other Settings**: Unchanged

**After Deployment**:
- **Service Uses**: Revision `5`
- **Tasks Running**: New tasks with revision `5`
- **Old Tasks**: Drained and stopped

---

## PART 8: KEY METRICS AND TIMINGS

### Build Pipeline
- **Total Duration**: ~3 minutes 20 seconds
- **Checkout**: ~1 second
- **OIDC Auth**: ~2 seconds
- **Docker Build**: ~2 minutes (includes NuGet restore, compile, image creation)
- **ECR Push**: ~30 seconds
- **Verification**: ~1 second

### Deploy Pipeline
- **Total Duration**: ~3 minutes 14 seconds
- **Checkout**: ~1 second
- **OIDC Auth**: ~1 second
- **ECR Login**: ~1 second
- **Image Query**: ~1 second
- **Task Definition Update**: ~1 second
- **ECS Service Update**: ~1 second
- **Service Stabilization**: ~3 minutes (waiting for tasks to start and health checks)
- **Verification**: ~1 second

---

## PART 9: SECURITY AND AUTHENTICATION FLOW

### Authentication Chain

**Build Pipeline**:
```
GitHub Actions Runner
    ↓ (OIDC Token Request)
GitHub OIDC Provider (token.actions.githubusercontent.com)
    ↓ (JWT Token with repository claim)
AWS STS (AssumeRoleWithWebIdentity)
    ↓ (Temporary AWS Credentials)
AWS ECR (Login and Push)
```

**Deploy Pipeline**:
```
GitHub Actions Runner
    ↓ (OIDC Token Request)
GitHub OIDC Provider
    ↓ (JWT Token)
AWS STS (AssumeRoleWithWebIdentity)
    ↓ (Temporary AWS Credentials)
    ├─→ AWS ECR (Cross-Account Login)
    └─→ AWS ECS (Task Definition and Service Updates)
```

**ECS Task Image Pull**:
```
ECS Task (Account 258215414239)
    ↓ (Uses Task Execution Role)
AWS STS (AssumeRole)
    ↓ (Task Execution Role Credentials)
AWS ECR (Account 237156726900) [Cross-Account]
    ↓ (Pull Image)
Container Starts
```

---

## SUMMARY

### Build Pipeline Summary
1. ✅ Code checked out from `github-workflows` branch
2. ✅ OIDC authentication to AWS account `258215414239`
3. ✅ Docker image built using:
   - Base images from Microsoft Container Registry
   - NuGet packages from `api.nuget.org` (public feed)
   - System packages from Ubuntu repositories
4. ✅ Image tagged as `build-b744cca`
5. ✅ Image pushed to ECR account `237156726900`, repository `payroll-engine/payroll-engine-backend`

### Deploy Pipeline Summary
1. ✅ Code checked out from `github-workflows` branch
2. ✅ OIDC authentication to AWS account `258215414239`
3. ✅ Cross-account ECR login to account `237156726900`
4. ✅ Latest image tag determined: `build-b744cca`
5. ✅ Image verified in ECR
6. ✅ ECS task definition updated (revision 5)
7. ✅ ECS service updated to use new task definition
8. ✅ Service stabilized (new tasks running)
9. ✅ Deployment verified successful

### Key Points
- **NuGet Packages**: Restored from `https://api.nuget.org/v3/index.json` (public feed)
- **Image Location**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **ECS Service**: `feature-payroll-engine-backend` in account `258215414239`
- **Cross-Account**: Image in `237156726900`, ECS in `258215414239`
- **Task Definition**: Updated to revision 5 with new image URI

