# Exemplar Container Image Workflows - Complete Guide

This document provides a comprehensive guide to the **container image-specific** workflows and patterns demonstrated in the `exp-container-image-exemplar` repository. This guide focuses exclusively on container image lifecycle management, excluding serverless Lambda patterns.

**Repository**: `gp-nova/exp-container-image-exemplar`  
**Purpose**: Reference/exemplar for container image workflows  
**Focus**: Container images, ECR, ECS, Docker

---

## Table of Contents

1. [Overview](#overview)
2. [Container Image Configuration](#container-image-configuration)
3. [Container Build Workflow](#container-build-workflow)
4. [Container Publish Workflow](#container-publish-workflow)
5. [Container Deploy Workflow](#container-deploy-workflow)
6. [Container Promotion Workflow](#container-promotion-workflow)
7. [Image Tagging Strategy](#image-tagging-strategy)
8. [ECS Infrastructure Integration](#ecs-infrastructure-integration)
9. [Security and Scanning](#security-and-scanning)
10. [Best Practices](#best-practices)

---

## Overview

The exemplar demonstrates a complete **container image lifecycle management system** using:

- **GitHub Actions** for CI/CD automation
- **AWS ECR** for container image storage
- **AWS ECS** for container orchestration
- **AWS SAM/CloudFormation** for infrastructure as code
- **Custom Actions**: `devx-pipeline-modules-containers` for container operations

### Container-Specific Features

1. ✅ **Automated Container Build**: Builds container images on push to main
2. ✅ **Container Security Scanning**: Snyk integration for vulnerability scanning
3. ✅ **Container Linting**: Dockerfile linting for best practices
4. ✅ **Image Publishing**: Automatic image publishing to ECR
5. ✅ **Image Promotion**: Manual promotion between environments
6. ✅ **ECS Deployment**: Automated deployment to ECS services
7. ✅ **Multi-Platform Support**: Builds for multiple architectures

---

## Container Image Configuration

### Image Build Configuration

**File**: `template.yaml`

**Mapping Structure**:
```yaml
Mappings:
  ImageBuildConfiguration:
    BusyBoxService:  # Service name (can be any name)
      buildContext: images/exp-container-image-exemplar
      dockerfile: images/exp-container-image-exemplar/Dockerfile
      imageName: exp-container-image-exemplar
      platform: linux/amd64
```

**Configuration Fields**:

| Field | Description | Example |
|-------|-------------|---------|
| `buildContext` | Path to build context (directory containing Dockerfile) | `images/exp-container-image-exemplar` or `.` |
| `dockerfile` | Path to Dockerfile (relative to buildContext) | `Dockerfile` or `images/exp-container-image-exemplar/Dockerfile` |
| `imageName` | Name of the container image (used for ECR repository) | `exp-container-image-exemplar` |
| `platform` | Target platform for build | `linux/amd64`, `linux/arm64`, or both |

**Multiple Images**:
You can define multiple container images in the same mapping:
```yaml
Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64
    WebappService:
      buildContext: ./webapp
      dockerfile: Dockerfile
      imageName: payroll-engine-webapp
      platform: linux/amd64
```

### Dockerfile Location

**Typical Structure**:
```
repository-root/
├── Dockerfile                    # Root-level Dockerfile
├── images/
│   └── exp-container-image-exemplar/
│       └── Dockerfile            # Service-specific Dockerfile
└── template.yaml                 # Contains ImageBuildConfiguration
```

**Example for Payroll Engine Backend**:
```
payroll-engine-backend/
├── Dockerfile                    # Backend Dockerfile
├── template.yaml                 # ImageBuildConfiguration mapping
└── ...
```

---

## Container Build Workflow

### Build Action

**Action**: `gp-nova/devx-pipeline-modules-containers/build@v1`

**Usage**:
```yaml
- name: Build container images
  uses: gp-nova/devx-pipeline-modules-containers/build@v1
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}  # Optional, for security scanning
```

### Build Process

**Step 1: Configuration Parsing**
1. Reads `template.yaml` from repository root
2. Finds `ImageBuildConfiguration` mapping
3. Extracts configuration for each image:
   - `buildContext`
   - `dockerfile`
   - `imageName`
   - `platform`

**Step 2: Docker Buildx Setup**
- Creates Docker BuildKit builder instance
- Sets up multi-platform support (if needed)
- Configures QEMU for cross-platform builds

**Step 3: ECR Login**
- Authenticates to ECR registry using OIDC credentials
- Registry: `237156726900.dkr.ecr.us-east-1.amazonaws.com` (hardcoded in action)

**Step 4: Docker Build**
For each image in `ImageBuildConfiguration`:
```bash
docker buildx build \
  --platform {platform} \
  --file {dockerfile} \
  --tag {imageName}:build \
  --load \
  {buildContext}
```

**Step 5: Security Scanning** (if `SNYK_TOKEN` provided)
- Scans built image for vulnerabilities
- Generates security report
- Fails build if critical vulnerabilities found

**Output**:
- Container image(s) built locally
- Tagged as `{imageName}:build`
- Ready for publishing

### Build Configuration Example

**For Payroll Engine Backend**:
```yaml
Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64
```

**Build Command** (executed by action):
```bash
docker buildx build \
  --platform linux/amd64 \
  --file Dockerfile \
  --tag payroll-engine-backend:build \
  --load \
  .
```

---

## Container Publish Workflow

### Publish Action

**Action**: `gp-nova/devx-pipeline-modules-containers/publish@v1`

**Usage**:
```yaml
- name: Publish container images
  uses: gp-nova/devx-pipeline-modules-containers/publish@v1
  with:
    version: ${{ steps.set-version.outputs.version }}
```

**Inputs**:
- `version`: Semantic version (e.g., `1.0.0`) or git commit SHA

### Publish Process

**Step 1: ECR Registry Determination**
- Checks `TEST_MODE` environment variable
- **Production**: `237156726900.dkr.ecr.us-east-1.amazonaws.com`
- **Test**: `890779668410.dkr.ecr.us-east-1.amazonaws.com`

**Step 2: ECR Login**
- Uses OIDC-assumed AWS credentials
- Executes: `aws ecr get-login-password | docker login`

**Step 3: Image Tagging**
- **Tag Format**: `build-{version}`
- **Example**: `build-1.0.0` or `build-b744cca`
- **Note**: The `build-` prefix is added automatically by the action

**Step 4: Repository Name Construction**
- **Pattern**: `payroll-engine/{imageName}`
- **Source**: `imageName` from `template.yaml` → `ImageBuildConfiguration`
- **Example**: `payroll-engine/payroll-engine-backend`

**Step 5: Image Push**
- Tags local image: `docker tag {imageName}:build {ECR_REGISTRY}/{REPO}:build-{version}`
- Pushes to ECR: `docker push {ECR_REGISTRY}/{REPO}:build-{version}`
- Creates image manifest in ECR

**Final Image URI**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-1.0.0
```

### Publish Configuration

**ECR Repository**:
- **Registry**: `237156726900.dkr.ecr.us-east-1.amazonaws.com` (hardcoded)
- **Repository Pattern**: `payroll-engine/{imageName}`
- **Tag Pattern**: `build-{version}`

**Example for Payroll Engine Backend**:
- **Image Name**: `payroll-engine-backend` (from `ImageBuildConfiguration`)
- **Repository**: `payroll-engine/payroll-engine-backend`
- **Tag**: `build-1.0.0`
- **Full URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-1.0.0`

---

## Container Deploy Workflow

### Deployment Method

**Primary Method**: **SAM/CloudFormation Stack Update**

The exemplar uses CloudFormation to update ECS task definitions with new container images.

### Deploy Workflow Process

**File**: `.github/workflows/deploy.yaml`

**Uses**: Shared workflow `gp-nova/devx-shared-workflows/.github/workflows/deploy-api.yaml@v6`

**Steps**:

1. **OIDC Authentication**:
   - Assumes IAM role for target environment
   - Gets AWS credentials

2. **Artifact Download**:
   - Downloads deployment artifacts from CodeArtifact
   - Includes: SAM build output, templates, configs

3. **Image Tag Resolution**:
   - Resolves image tag based on environment and version
   - **Pattern**: `{imageName}-{environment}-{version}`
   - **Example**: `payroll-engine-backend-dev-1.0.0`

4. **SSM Parameter Retrieval**:
   - Gets `RepositoryPrefix` from SSM Parameter Store
   - **Path**: `/account/ecr/{environment}/registry`
   - **Value**: ECR registry URI (e.g., `237156726900.dkr.ecr.us-east-1.amazonaws.com`)

5. **SAM Deployment**:
   - Executes: `sam deploy --config-env {environment}`
   - **Configuration**: `samconfig.toml` with environment-specific settings
   - **Parameters**:
     - `Environment`: Target environment name
     - `RepositoryPrefix`: From SSM Parameter Store
     - `DeploymentVersion`: Version to deploy
   - **Process**:
     1. Builds SAM application (if needed)
     2. Packages resources
     3. Uploads to S3
     4. Creates/updates CloudFormation stack
     5. **Updates ECS task definitions with new image URI**

6. **Stack Update**:
   - CloudFormation stack name: `{componentName}-{environment}`
   - Updates task definitions with new image reference
   - ECS services automatically pick up new task definitions

### Image Reference in Infrastructure

**Template Pattern** (`template.yaml`):
```yaml
TestContainerDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    ContainerDefinitions:
      - Name: backend
        Image: !Sub
          - "${RepositoryPrefix}/${ImageNameAndTag}"
          - {
              RepositoryPrefix: !Ref RepositoryPrefix,
              ImageNameAndTag: !Join [ ":", [
                !FindInMap [ ImageBuildConfiguration, BackendService, imageName ],
                !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
              ]]
            }
```

**Resulting Image URI**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine-backend-dev-1.0.0
```

**Components**:
- `RepositoryPrefix`: From SSM Parameter Store
- `imageName`: From `ImageBuildConfiguration` mapping
- `Environment`: CloudFormation parameter (dev, test, prod)
- `DeploymentVersion`: CloudFormation parameter (version to deploy)

---

## Container Promotion Workflow

### Purpose

Promotes container images between environments by:
1. Re-tagging the image with environment-specific tag
2. Pushing the new tag to ECR
3. Enabling deployment to target environment

### Promotion Workflow

**File**: `.github/workflows/promote.yaml`

**Trigger**:
```yaml
on:
  workflow_dispatch:
    inputs:
      version:
        description: Version
        required: true
        type: string
```

### Promotion Process

**Step 1: Checkout and Setup**
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/setup@v1
```

**Step 2: OIDC Authentication**
```yaml
- uses: gp-nova/devx-action-assume-oidc-role@v2
  with:
    environment: 'dev'
```

**Step 3: Promote Container Image**
```yaml
- uses: gp-nova/devx-pipeline-modules-containers/promote@v1
  with:
    version: ${{ github.event.inputs.version }}
```

### Promote Action Process

**Action**: `gp-nova/devx-pipeline-modules-containers/promote@v1`

**Expected Inputs**:
- `version`: Version to promote (e.g., `1.0.0`)
- `source-environment`: Source environment (e.g., `dev`)
- `target-environment`: Target environment (e.g., `test`)
- `image-name`: Image name (from `template.yaml`)

**Process**:

1. **Source Image Resolution**:
   - **Source Tag**: `build-{version}` or `{imageName}-{sourceEnvironment}-{version}`
   - **Source URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/{imageName}:{sourceTag}`

2. **Target Tag Creation**:
   - **Target Tag**: `{imageName}-{targetEnvironment}-{version}`
   - **Example**: `payroll-engine-backend-test-1.0.0`

3. **Image Re-tagging**:
   - Pulls source image from ECR
   - Tags with target tag
   - Pushes to ECR with new tag

4. **Result**:
   - Same image content, different tag
   - Available for deployment to target environment

### Promotion Flow Example

**Initial Build**:
```
Image: payroll-engine-backend:build-1.0.0
```

**Promote to Dev**:
```
Source: payroll-engine-backend:build-1.0.0
Target: payroll-engine-backend-dev-1.0.0
```

**Promote to Test**:
```
Source: payroll-engine-backend-dev-1.0.0
Target: payroll-engine-backend-test-1.0.0
```

**Promote to Prod**:
```
Source: payroll-engine-backend-test-1.0.0
Target: payroll-engine-backend-prod-1.0.0
```

### Integration with Deploy

After promotion, the promoted image tag can be used in the deploy workflow:
- Deploy workflow reads `DeploymentVersion` parameter
- Constructs image tag: `{imageName}-{environment}-{version}`
- Updates ECS task definition with promoted image

---

## Image Tagging Strategy

### Tag Format Evolution

**1. Build Phase**:
- **Format**: `build-{version}`
- **Example**: `build-1.0.0` or `build-b744cca`
- **Purpose**: Initial build artifact
- **Created By**: `publish@v1` action
- **Location**: ECR account `237156726900`

**2. Promotion Phase**:
- **Format**: `{imageName}-{environment}-{version}`
- **Examples**:
  - `payroll-engine-backend-dev-1.0.0`
  - `payroll-engine-backend-test-1.0.0`
  - `payroll-engine-backend-prod-1.0.0`
- **Purpose**: Environment-specific deployment
- **Created By**: `promote@v1` action
- **Location**: Same ECR account, different tag

### Tag Components

- **`imageName`**: From `template.yaml` → `ImageBuildConfiguration` → `imageName`
- **`environment`**: Target environment (dev, test, prod)
- **`version`**: Semantic version (e.g., `1.0.0`) or git commit SHA

### Image URI Construction

**Full Pattern**:
```
{RepositoryPrefix}/{imageName}-{environment}-{version}
```

**Example**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine-backend-dev-1.0.0
```

**Components**:
- **RepositoryPrefix**: From SSM Parameter Store (`/account/ecr/{environment}/registry`)
- **imageName**: From `ImageBuildConfiguration` mapping
- **environment**: CloudFormation parameter
- **version**: CloudFormation parameter

### Tag Lifecycle

```
Build → build-1.0.0
    ↓ (promote to dev)
payroll-engine-backend-dev-1.0.0
    ↓ (promote to test)
payroll-engine-backend-test-1.0.0
    ↓ (promote to prod)
payroll-engine-backend-prod-1.0.0
```

---

## ECS Infrastructure Integration

### ECS Task Definition

**Template Pattern**:
```yaml
BackendTaskDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    Family: payroll-engine-backend
    RequiresCompatibilities: ["FARGATE"]
    NetworkMode: awsvpc
    Cpu: "1024"
    Memory: "2048"
    ExecutionRoleArn: !Ref ECSTaskExecutionRole
    ContainerDefinitions:
      - Name: backend
        Image: !Sub
          - "${RepositoryPrefix}/${ImageNameAndTag}"
          - {
              RepositoryPrefix: !Ref RepositoryPrefix,
              ImageNameAndTag: !Join [ ":", [
                !FindInMap [ ImageBuildConfiguration, BackendService, imageName ],
                !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
              ]]
            }
        PortMappings: [{ ContainerPort: 8080 }]
        Environment:
          - Name: DB_SECRET_NAME
            Value: !Ref DBSecret
        LogConfiguration:
          LogDriver: awslogs
          Options:
            awslogs-group: !Ref BackendLogGroup
            awslogs-region: !Ref AWS::Region
            awslogs-stream-prefix: ecs
```

### ECS Service

**Template Pattern**:
```yaml
BackendService:
  Type: AWS::ECS::Service
  Properties:
    ServiceName: payroll-engine-backend
    Cluster: !Ref ECSCluster
    TaskDefinition: !Ref BackendTaskDefinition
    LaunchType: FARGATE
    DesiredCount: 1
    NetworkConfiguration:
      AwsvpcConfiguration:
        Subnets: !Ref PrivateSubnetIds
        SecurityGroups: [!Ref ECSServiceSecurityGroup]
    LoadBalancers:
      - TargetGroupArn: !Ref BackendTgArn
        ContainerName: backend
        ContainerPort: 8080
```

### ECR Repository Prefix

**Parameter**:
```yaml
RepositoryPrefix:
  Type: AWS::SSM::Parameter::Value<String>
  Default: /account/ecr/main/registry
  Description: RepoPrefix for ECR repo
```

**SSM Parameter Paths**:
- **Dev**: `/account/ecr/dev/registry` (or `/account/ecr/main/registry`)
- **Test**: `/account/ecr/test/registry`
- **Prod**: `/account/ecr/prod/registry`

**Value**: ECR registry URI (e.g., `237156726900.dkr.ecr.us-east-1.amazonaws.com`)

### Deployment Configuration

**SAM Configuration** (`samconfig.toml`):
```toml
[dev.deploy.parameters]
Environment = "dev"
RepositoryPrefix = "/account/ecr/dev/registry"
DeploymentVersion = "1.0.0"
StackName = "payroll-engine-backend-dev"
```

**Deployment Command**:
```bash
sam deploy --config-env {environment}
```

---

## Security and Scanning

### Container Security Scanning

**1. Container Security (Snyk)**:
- **Action**: `devx-pipeline-modules-containers/build@v1`
- **Trigger**: When `SNYK_TOKEN` is provided
- **Scans**: Container images for vulnerabilities
- **Output**: Security report

**Usage**:
```yaml
- name: Build container images
  uses: gp-nova/devx-pipeline-modules-containers/build@v1
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
```

### Container Linting

**2. Container Linting**:
- **Action**: `devx-pipeline-modules-containers/lint@v1`
- **Scans**: Dockerfile for best practices
- **Output**: Linting findings

**Usage**:
```yaml
- name: Container Linting
  uses: gp-nova/devx-pipeline-modules-containers/lint@v1
```

### Infrastructure Security

**3. Infrastructure Security (cfn-nag)**:
- **Workflow**: `.github/workflows/sub_lint-and-test.yaml` → `lint-infra` job
- **Tool**: `cfn-nag`
- **Scans**: CloudFormation templates for security issues
- **Output**: Security findings

---

## Best Practices

### 1. Image Tagging

✅ **Recommended**:
- Use semantic versioning (`1.0.0`)
- Use environment-specific tags for deployments
- Keep `build-{version}` tags for initial builds
- Use `{imageName}-{environment}-{version}` for promoted images

❌ **Avoid**:
- Using `latest` tag in production
- Using commit SHAs directly (use version instead)
- Mixing tag formats

### 2. Environment Promotion

✅ **Recommended Flow**:
```
build → dev → test → prod
```

✅ **Best Practices**:
- Always promote, never rebuild for higher environments
- Use same image content across environments
- Only change tags, not image content
- Test in dev before promoting

### 3. Infrastructure as Code

✅ **Recommended**:
- Use SSM Parameter Store for ECR registry prefix
- Reference images via CloudFormation parameters
- Use environment-specific SAM configs
- Version control all infrastructure changes

### 4. Security

✅ **Recommended**:
- Enable Snyk scanning for all builds
- Run security scans in PR workflows
- Review security findings before merging
- Keep base images up to date

### 5. Dockerfile Best Practices

✅ **Recommended**:
- Use multi-stage builds
- Minimize image size
- Use specific base image tags (not `latest`)
- Copy only necessary files
- Use `.dockerignore` to exclude unnecessary files

### 6. Deployment

✅ **Recommended**:
- Use concurrency control to prevent parallel deployments
- Deploy to dev automatically after build
- Require manual approval for prod
- Use infrastructure as code for all deployments

### 7. Version Management

✅ **Recommended**:
- Use semantic versioning (`MAJOR.MINOR.PATCH`)
- Update version in `package.json` or version file
- Tag Git releases with versions
- Track versions in deployment artifacts

---

## Complete Container Workflow Example

### For Payroll Engine Backend

**1. Configuration** (`template.yaml`):
```yaml
Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64
```

**2. Build Workflow** (`.github/workflows/main-build.yaml`):
```yaml
- name: Build container images
  uses: gp-nova/devx-pipeline-modules-containers/build@v1
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}

- name: Publish container images
  uses: gp-nova/devx-pipeline-modules-containers/publish@v1
  with:
    version: ${{ steps.set-version.outputs.version }}
```

**3. Deploy Workflow** (`.github/workflows/deploy.yaml`):
```yaml
- uses: gp-nova/devx-shared-workflows/.github/workflows/deploy-api.yaml@v6
  with:
    environment: ${{ inputs.environment }}
    version: ${{ inputs.version }}
    api-name: payroll-engine-backend
```

**4. Task Definition** (`template.yaml`):
```yaml
BackendTaskDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    ContainerDefinitions:
      - Image: !Sub
          - "${RepositoryPrefix}/${ImageNameAndTag}"
          - {
              RepositoryPrefix: !Ref RepositoryPrefix,
              ImageNameAndTag: !Join [ ":", [
                !FindInMap [ ImageBuildConfiguration, BackendService, imageName ],
                !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
              ]]
            }
```

**5. Result**:
- Image built: `payroll-engine-backend:build-1.0.0`
- Image published: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-1.0.0`
- Image promoted: `payroll-engine-backend-dev-1.0.0`
- Image deployed: ECS task definition updated with promoted image

---

## Key Takeaways

1. **Complete Automation**: Build, scan, publish, deploy all automated
2. **Security First**: Container security scanning integrated
3. **Infrastructure as Code**: All deployments via CloudFormation/SAM
4. **Environment Promotion**: Controlled promotion between environments
5. **Version Management**: Semantic versioning throughout
6. **Multi-Platform**: Support for multiple architectures
7. **Best Practices**: Industry-standard patterns for container workflows

---

## References

- **Repository**: `https://github.com/gp-nova/exp-container-image-exemplar`
- **Custom Actions**: `gp-nova/devx-pipeline-modules-containers`
- **Shared Workflows**: `gp-nova/devx-shared-workflows`
- **Complete Guide**: `docs/EXEMPLAR-COMPLETE-GUIDE.md` (includes serverless patterns)
- **Architecture Target**: `docs/EXEMPLAR-ARCHITECTURE-TARGET.md`

---

## Summary

The exemplar repository provides a **complete, production-ready** template for container image lifecycle management:

- ✅ **Build**: Automated container image builds with security scanning
- ✅ **Publish**: Automatic publishing to ECR with proper tagging
- ✅ **Deploy**: Infrastructure as code deployments to multiple environments
- ✅ **Promote**: Controlled image promotion between environments
- ✅ **Security**: Multi-layer security scanning
- ✅ **Best Practices**: Industry-standard patterns and recommendations

This guide focuses exclusively on **container image workflows**, making it ideal for projects like `payroll-engine-backend` that are primarily container-based.

