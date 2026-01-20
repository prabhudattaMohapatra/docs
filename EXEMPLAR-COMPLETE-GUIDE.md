# Exp Container Image Exemplar - Complete Guide

This document provides a comprehensive guide to how the `exp-container-image-exemplar` repository implements build, publish, deploy, promote, and all other features and recommendations.

**Repository**: `gp-nova/exp-container-image-exemplar`  
**Purpose**: Reference/exemplar repository demonstrating best practices for container image workflows  
**Value Stream**: `core-platform`  
**Domain**: `dev-enablement`  
**Bounded Context**: `training`

---

## Table of Contents

1. [Overview](#overview)
2. [Repository Structure](#repository-structure)
3. [Build Workflow](#build-workflow)
4. [Publish Workflow](#publish-workflow)
5. [Deploy Workflow](#deploy-workflow)
6. [Promote Workflow](#promote-workflow)
7. [Pull Request Workflow](#pull-request-workflow)
8. [Release Workflow](#release-workflow)
9. [Delete Workflow](#delete-workflow)
10. [Image Tagging Strategy](#image-tagging-strategy)
11. [Infrastructure as Code](#infrastructure-as-code)
12. [Security and Scanning](#security-and-scanning)
13. [Best Practices and Recommendations](#best-practices-and-recommendations)

---

## Overview

The exemplar repository demonstrates a complete container image lifecycle management system using:

- **GitHub Actions** for CI/CD automation
- **AWS ECR** for container image storage
- **AWS ECS** for container orchestration
- **AWS SAM/CloudFormation** for infrastructure as code
- **Custom Actions**: `devx-pipeline-modules-containers` for container operations
- **Shared Workflows**: `devx-shared-workflows` for common patterns

### Key Features

1. ✅ **Automated Build**: Builds container images on push to main
2. ✅ **Security Scanning**: Snyk integration for vulnerability scanning
3. ✅ **Linting**: Code and container linting
4. ✅ **Testing**: Unit tests with coverage reporting
5. ✅ **Publishing**: Automatic image publishing to ECR
6. ✅ **Deployment**: Automated deployment to dev environment
7. ✅ **Promotion**: Manual promotion between environments
8. ✅ **Release**: Artifact release workflow
9. ✅ **Cleanup**: Stack deletion workflow

---

## Repository Structure

```
exp-container-image-exemplar/
├── .github/
│   ├── workflows/
│   │   ├── main-build.yaml          # Main CI/CD pipeline
│   │   ├── pull-request.yaml        # PR validation
│   │   ├── deploy.yaml              # Deployment workflow
│   │   ├── promote.yaml             # Image promotion
│   │   ├── release.yaml             # Artifact release
│   │   ├── delete.yaml              # Stack deletion
│   │   └── sub_lint-and-test.yaml  # Reusable lint/test workflow
│   ├── CODEOWNERS
│   └── dependabot.yml
├── images/
│   └── exp-container-image-exemplar/
│       └── Dockerfile               # Container image definition
├── src/                             # Application source code
├── test/                            # Unit tests
├── template.yaml                     # SAM/CloudFormation template
├── samconfig.toml                   # SAM deployment configuration
└── package.json                     # NPM dependencies
```

---

## Build Workflow

**File**: `.github/workflows/main-build.yaml`

### Trigger

```yaml
on:
  workflow_dispatch:  # Manual trigger
  push:
    branches:
      - "main"
    paths-ignore:
      - "*.md"
      - ".github/workflows/feature*.yaml"
      - ".github/workflows/pull-request.yaml"
      - ".github/workflows/perf-test.yaml"
      - ".github/workflows/delete.yaml"
```

### Workflow Structure

The main build workflow consists of **4 parallel jobs**:

1. **`lint-test`**: Code linting and unit testing
2. **`security-scan`**: Security vulnerability scanning
3. **`build-and-package`**: Build application and container images
4. **`deploy`**: Deploy to dev environment (depends on build-and-package)

### Job 1: Lint and Test

**Uses**: Reusable workflow `.github/workflows/sub_lint-and-test.yaml`

**Sub-jobs**:
- **`lint-code`**: 
  - ESLint and Prettier linting
  - Container linting (using `devx-pipeline-modules-containers/lint@v1`)
- **`lint-infra`**:
  - CloudFormation template validation (`cfn-lint`)
  - CloudFormation security scanning (`cfn-nag`)
- **`unit-test`**:
  - Runs unit tests
  - Generates coverage reports (if `report_coverage: true`)

**Key Actions**:
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/setup@v1
- uses: gp-nova/devx-pipeline-modules-npm/install@v1
- uses: gp-nova/devx-pipeline-modules-npm/lint@v1
- uses: gp-nova/devx-pipeline-modules-containers/lint@v1
- uses: gp-nova/devx-pipeline-modules-npm/test@v1
```

### Job 2: Security Scan

**Uses**: Shared workflow `gp-nova/devx-shared-workflows/.github/workflows/snyk-node.yaml@v6`

**Purpose**: 
- Scans Node.js dependencies for vulnerabilities
- Scans container images for security issues
- Requires `SNYK_TOKEN` secret

### Job 3: Build and Package

**Dependencies**: Waits for `lint-test` and `security-scan` to complete

**Steps**:

1. **Checkout and Setup**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/setup@v1
   ```
   - Checks out code
   - Sets up Node.js environment

2. **Install Dependencies**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/install@v1
   ```
   - Installs NPM packages
   - Handles CodeArtifact authentication

3. **Set Version**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/version@v1
   ```
   - Reads version from `package.json`
   - Outputs version to `$GITHUB_OUTPUT`
   - **Output**: `version` (e.g., `1.0.0`)

4. **Build Application**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/build@v1
   ```
   - Runs `npm run build`
   - Compiles TypeScript to JavaScript
   - Outputs to `dist/` directory

5. **Build Container Images**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-containers/build@v1
     env:
       SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
   ```
   - **Configuration Source**: `template.yaml` → `ImageBuildConfiguration` mapping
   - **Process**:
     1. Parses `template.yaml` to find `ImageBuildConfiguration`
     2. For each image configuration:
        - Reads `buildContext`, `dockerfile`, `imageName`, `platform`
        - Executes `docker buildx build` with multi-platform support
        - Runs security scanning (Snyk) if token provided
        - Tags image as `{imageName}:build`
   - **Output**: Container image(s) built locally

6. **Publish Container Images**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-containers/publish@v1
     with:
       version: ${{ steps.set-version.outputs.version }}
   ```
   - **Process**:
     1. Logs into ECR registry (account `237156726900`)
     2. Tags image with format: `build-{version}` (e.g., `build-1.0.0`)
     3. Pushes image to ECR repository: `payroll-engine/{imageName}`
     4. **Full URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/{imageName}:build-{version}`
   - **Note**: The `publish@v1` action hardcodes the ECR registry to account `237156726900`

7. **Upload Artifact**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-generic/api-upload@v1
     with:
       version: ${{ steps.set-version.outputs.version }}
       contents: ".aws-sam/ dist/ docs/api/*.yaml scripts/ samconfig.toml catalog-info.yaml"
   ```
   - Uploads deployment artifacts to CodeArtifact
   - Includes: SAM build output, compiled code, API specs, scripts, configs

**Outputs**:
- `version`: Semantic version from `package.json`

### Job 4: Deploy to Development

**Dependencies**: Waits for `build-and-package` to complete

**Uses**: Reusable workflow `.github/workflows/deploy.yaml`

**Inputs**:
- `environment: dev`
- `version: ${{ needs.build-and-package.outputs.version }}`

**Process**: See [Deploy Workflow](#deploy-workflow) section

---

## Publish Workflow

The publish step is **integrated into the build workflow** (Job 3, Step 6).

### Publish Action Details

**Action**: `gp-nova/devx-pipeline-modules-containers/publish@v1`

**Inputs**:
- `version`: Semantic version (e.g., `1.0.0`)

**Process**:

1. **ECR Registry Determination**:
   - Checks `TEST_MODE` environment variable
   - **Production**: `237156726900.dkr.ecr.us-east-1.amazonaws.com`
   - **Test**: `890779668410.dkr.ecr.us-east-1.amazonaws.com`

2. **ECR Login**:
   - Uses OIDC-assumed AWS credentials
   - Executes: `aws ecr get-login-password | docker login`

3. **Image Tagging**:
   - **Tag Format**: `build-{version}`
   - **Example**: `build-1.0.0`
   - **Note**: The `build-` prefix is added automatically by the action

4. **Repository Name Construction**:
   - **Pattern**: `payroll-engine/{imageName}`
   - **Source**: `imageName` from `template.yaml` → `ImageBuildConfiguration`
   - **Example**: `payroll-engine/exp-container-image-exemplar`

5. **Image Push**:
   - Pushes all image layers to ECR
   - Creates image manifest
   - Image is now available in ECR

**Final Image URI**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/exp-container-image-exemplar:build-1.0.0
```

### Image Tagging Strategy

**Build Phase**:
- **Tag**: `build-{version}` (e.g., `build-1.0.0`)
- **Purpose**: Initial build artifact
- **Location**: ECR account `237156726900`

**Promotion Phase** (see [Promote Workflow](#promote-workflow)):
- **Tag**: `{imageName}-{environment}-{version}` (e.g., `exp-container-image-exemplar-dev-1.0.0`)
- **Purpose**: Environment-specific deployment
- **Location**: Same ECR account, different tag

---

## Deploy Workflow

**File**: `.github/workflows/deploy.yaml`

### Trigger

```yaml
on:
  workflow_dispatch:
    inputs:
      environment:
        description: Environment
        required: true
        type: environment  # GitHub environment selection
  workflow_call:
    inputs:
      environment:
        required: true
        type: string
      version:
        required: true
        type: string
```

### Concurrency Control

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ inputs.environment }}
  cancel-in-progress: false
```

**Purpose**: Ensures only one deployment per environment at a time

### Job: Deploy

**Uses**: Shared workflow `gp-nova/devx-shared-workflows/.github/workflows/deploy-api.yaml@v6`

**Inputs**:
- `environment`: Target environment (dev, test, prod)
- `version`: Version to deploy
- `api-name`: `exp-container-image-exemplar`
- `api-access-mode`: `"private"`

### Shared Deploy Workflow Process

The shared workflow (`deploy-api.yaml@v6`) performs:

1. **OIDC Authentication**:
   - Assumes IAM role for target environment
   - Gets AWS credentials

2. **Artifact Download**:
   - Downloads deployment artifacts from CodeArtifact
   - Includes: SAM build output, compiled code, templates

3. **Image Tag Resolution**:
   - Resolves image tag based on environment and version
   - **Pattern**: `{imageName}-{environment}-{version}`
   - **Example**: `exp-container-image-exemplar-dev-1.0.0`

4. **SSM Parameter Retrieval**:
   - Gets `RepositoryPrefix` from SSM Parameter Store
   - **Path**: `/account/ecr/{environment}/registry` (or `/account/ecr/main/registry`)
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
     2. Packages Lambda functions
     3. Uploads to S3
     4. Creates/updates CloudFormation stack
     5. Updates ECS task definitions with new image URI

6. **Stack Update**:
   - CloudFormation stack name: `{componentName}-{environment}`
   - Updates task definitions with new image reference
   - ECS services automatically pick up new task definitions

### Image Reference in Infrastructure

**Template Pattern** (`template.yaml` lines 288-294):

```yaml
Image: !Sub
  - "${RepositoryPrefix}/${ImageNameAndTag}"
  - {
      RepositoryPrefix: !Ref RepositoryPrefix,
      ImageNameAndTag: !Join [ ":", [
        !FindInMap [ ImageBuildConfiguration, BusyBoxService, imageName ],
        !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
      ]]
    }
```

**Resulting Image URI**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/exp-container-image-exemplar-dev-1.0.0
```

**Components**:
- `RepositoryPrefix`: From SSM Parameter Store
- `imageName`: From `ImageBuildConfiguration` mapping
- `Environment`: CloudFormation parameter (dev, test, prod)
- `DeploymentVersion`: CloudFormation parameter (version to deploy)

---

## Promote Workflow

**File**: `.github/workflows/promote.yaml`

### Purpose

Promotes container images between environments by:
1. Re-tagging the image with environment-specific tag
2. Pushing the new tag to ECR
3. Enabling deployment to target environment

### Trigger

```yaml
on:
  workflow_dispatch:
    inputs:
      version:
        description: Version
        required: true
        type: string
```

**Note**: The exemplar's promote workflow is simplified - it only takes a version input and promotes from `dev` to the next environment.

### Job: Promote

**Steps**:

1. **Checkout and Setup**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/setup@v1
   ```

2. **OIDC Authentication**
   ```yaml
   - uses: gp-nova/devx-action-assume-oidc-role@v2
     with:
       environment: 'dev'
   ```
   - **Note**: Uses `dev` environment for authentication (may need update for cross-environment promotion)

3. **Promote Container Image**
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-containers/promote@v1
     with:
       version: ${{ github.event.inputs.version }}
   ```

### Promote Action Process

**Action**: `gp-nova/devx-pipeline-modules-containers/promote@v1`

**Expected Inputs** (based on typical promotion patterns):
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
   - **Example**: `exp-container-image-exemplar-test-1.0.0`

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
Image: exp-container-image-exemplar:build-1.0.0
```

**Promote to Dev**:
```
Source: exp-container-image-exemplar:build-1.0.0
Target: exp-container-image-exemplar-dev-1.0.0
```

**Promote to Test**:
```
Source: exp-container-image-exemplar-dev-1.0.0
Target: exp-container-image-exemplar-test-1.0.0
```

**Promote to Prod**:
```
Source: exp-container-image-exemplar-test-1.0.0
Target: exp-container-image-exemplar-prod-1.0.0
```

### Integration with Deploy

After promotion, the promoted image tag can be used in the deploy workflow:
- Deploy workflow reads `DeploymentVersion` parameter
- Constructs image tag: `{imageName}-{environment}-{version}`
- Updates ECS task definition with promoted image

---

## Pull Request Workflow

**File**: `.github/workflows/pull-request.yaml`

### Purpose

Validates pull requests before merging:
- Code linting
- Security scanning
- Unit testing with coverage
- Container image build (no publish)

### Trigger

```yaml
on:
  pull_request:
    paths-ignore:
      - "*.md"
      - ".github/workflows/feature*.yaml"
      - ".github/workflows/main-build.yaml"
      - ".github/workflows/perf-test.yaml"
      - ".github/workflows/delete.yaml"
```

### Concurrency

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

**Purpose**: Cancels previous runs if new commits are pushed

### Jobs

1. **`lint-test`**:
   - Uses `.github/workflows/sub_lint-and-test.yaml`
   - **Input**: `report_coverage: true`
   - Posts coverage reports as PR comments

2. **`security-scan`**:
   - Uses `gp-nova/devx-shared-workflows/.github/workflows/snyk-node.yaml@v6`
   - Scans for vulnerabilities

3. **`build`**:
   - Builds application and container images
   - **Does NOT publish** to ECR
   - Validates that build succeeds

### Key Difference from Main Build

- ❌ **No publish step**: Images are built but not pushed to ECR
- ❌ **No deployment**: No automatic deployment
- ✅ **Coverage reporting**: Coverage reports posted to PR
- ✅ **Build validation**: Ensures code compiles and images build

---

## Release Workflow

**File**: `.github/workflows/release.yaml`

### Purpose

Promotes artifacts to the shared Releases CodeArtifact repository.

### Trigger

```yaml
on:
  workflow_dispatch:
```

**Manual trigger only** - typically run after a tag is created.

### Process

**Uses**: Shared workflow `gp-nova/devx-shared-workflows/.github/workflows/release.yaml@main`

**Inputs**:
- `format: 'generic'`

### Release Workflow Steps

1. **Artifact Retrieval**:
   - Downloads artifacts from Bounded Context CodeArtifact repository
   - **Note**: Artifact must exist in Bounded Context repo before promotion

2. **Release Promotion**:
   - Uploads artifacts to shared Releases repository
   - Makes artifacts available for cross-team consumption

3. **Version Tagging**:
   - Associates release with Git tag (if provided)
   - Creates release metadata

### Usage

1. Navigate to Actions → `release.yaml`
2. Click "Run workflow"
3. Select tag from "Use workflow from" dropdown
4. Run workflow
5. Artifact is promoted to Releases repository

**Prerequisite**: Artifact must be in Bounded Context CodeArtifact repository (uploaded by main-build workflow)

---

## Delete Workflow

**File**: `.github/workflows/delete.yaml`

### Purpose

Deletes CloudFormation/SAM stacks for cleanup (e.g., ephemeral environments).

### Trigger

```yaml
on:
  workflow_dispatch:
    inputs:
      environment:
        description: Environment
        required: true
        type: environment
      suffix:
        description: Suffix for the stack name
        required: false
        type: string
  workflow_call:
    inputs:
      environment:
        required: true
        type: string
      version:
        required: true
        type: string
```

### Job: Delete Stack

**Steps**:

1. **OIDC Authentication**
   ```yaml
   - uses: gp-nova/devx-action-assume-oidc-role@v2
     with:
       environment: ${{ inputs.environment }}
   ```

2. **Delete Stack**
   ```bash
   componentName=${{ github.event.repository.name }}
   suffixName=${{ inputs.suffix || inputs.environment }}
   stackName=${componentName}-${suffixName}
   sam delete --no-prompts --region us-east-1 --stack-name ${stackName}
   ```

**Stack Name Pattern**:
- **Default**: `{componentName}-{environment}`
- **With Suffix**: `{componentName}-{suffix}`

**Example**:
- Component: `exp-container-image-exemplar`
- Environment: `dev`
- Stack Name: `exp-container-image-exemplar-dev`

### Use Cases

- **Ephemeral Environment Cleanup**: Delete temporary test environments
- **Environment Reset**: Delete and recreate environments
- **Cost Optimization**: Remove unused stacks

---

## Image Tagging Strategy

### Tag Format Evolution

**1. Build Phase**:
- **Format**: `build-{version}`
- **Example**: `build-1.0.0`
- **Purpose**: Initial build artifact
- **Created By**: `publish@v1` action

**2. Promotion Phase**:
- **Format**: `{imageName}-{environment}-{version}`
- **Examples**:
  - `exp-container-image-exemplar-dev-1.0.0`
  - `exp-container-image-exemplar-test-1.0.0`
  - `exp-container-image-exemplar-prod-1.0.0`
- **Purpose**: Environment-specific deployment
- **Created By**: `promote@v1` action

### Tag Components

- **`imageName`**: From `template.yaml` → `ImageBuildConfiguration` → `imageName`
- **`environment`**: Target environment (dev, test, prod)
- **`version`**: Semantic version from `package.json` (e.g., `1.0.0`)

### Image URI Construction

**Full Pattern**:
```
{RepositoryPrefix}/{imageName}-{environment}-{version}
```

**Example**:
```
237156726900.dkr.ecr.us-east-1.amazonaws.com/exp-container-image-exemplar-dev-1.0.0
```

**Components**:
- **RepositoryPrefix**: From SSM Parameter Store (`/account/ecr/{environment}/registry`)
- **imageName**: From `ImageBuildConfiguration` mapping
- **environment**: CloudFormation parameter
- **version**: CloudFormation parameter

---

## Infrastructure as Code

### Template Structure

**File**: `template.yaml`

### Image Build Configuration

**Mapping** (lines 8-13):
```yaml
Mappings:
  ImageBuildConfiguration:
    BusyBoxService:
      buildContext: images/exp-container-image-exemplar
      dockerfile: images/exp-container-image-exemplar/Dockerfile
      imageName: exp-container-image-exemplar
      platform: linux/amd64
```

**Purpose**: Defines container images to build
- **Used By**: `devx-pipeline-modules-containers/build@v1` action
- **Parsed**: Automatically by build action

### ECR Repository Prefix

**Parameter** (lines 109-112):
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

### Image Reference in Task Definition

**Task Definition** (lines 288-294):
```yaml
TestContainerDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    ContainerDefinitions:
      - Name: busybox-test
        Image: !Sub
          - "${RepositoryPrefix}/${ImageNameAndTag}"
          - {
              RepositoryPrefix: !Ref RepositoryPrefix,
              ImageNameAndTag: !Join [ ":", [
                !FindInMap [ ImageBuildConfiguration, BusyBoxService, imageName ],
                !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
              ]]
            }
```

**Deconstruction**:
1. `RepositoryPrefix`: From SSM Parameter Store
2. `imageName`: From `ImageBuildConfiguration` mapping
3. `Environment`: CloudFormation parameter (dev, test, prod)
4. `DeploymentVersion`: CloudFormation parameter (version to deploy)
5. **Result**: `{RepositoryPrefix}/{imageName}-{environment}-{version}`

### SAM Configuration

**File**: `samconfig.toml`

**Environment-Specific Configuration**:
- Each environment has its own section (e.g., `[dev.deploy.parameters]`)
- Parameters include:
  - `Environment`: Environment name
  - `RepositoryPrefix`: SSM parameter path
  - `DeploymentVersion`: Version to deploy
  - `StackName`: CloudFormation stack name

**Deployment Command**:
```bash
sam deploy --config-env {environment}
```

---

## Security and Scanning

### Security Scanning Layers

**1. Code Security (Snyk)**:
- **Workflow**: `gp-nova/devx-shared-workflows/.github/workflows/snyk-node.yaml@v6`
- **Scans**: Node.js dependencies for vulnerabilities
- **Requires**: `SNYK_TOKEN` secret

**2. Container Security (Snyk)**:
- **Action**: `devx-pipeline-modules-containers/build@v1`
- **Trigger**: When `SNYK_TOKEN` is provided
- **Scans**: Container images for vulnerabilities
- **Output**: Security report

**3. Infrastructure Security (cfn-nag)**:
- **Workflow**: `.github/workflows/sub_lint-and-test.yaml` → `lint-infra` job
- **Tool**: `cfn-nag`
- **Scans**: CloudFormation templates for security issues
- **Output**: Security findings

### Linting Layers

**1. Code Linting**:
- **Tools**: ESLint, Prettier
- **Action**: `devx-pipeline-modules-npm/lint@v1`

**2. Container Linting**:
- **Action**: `devx-pipeline-modules-containers/lint@v1`
- **Scans**: Dockerfile for best practices

**3. Infrastructure Linting**:
- **Tools**: `cfn-lint`, `cfn-nag`
- **Validates**: CloudFormation template syntax and security

---

## Best Practices and Recommendations

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
- Keep dependencies up to date

### 5. Testing

✅ **Recommended**:
- Run unit tests in PR workflows
- Generate coverage reports
- Require tests to pass before merge
- Test container builds in PRs

### 6. Deployment

✅ **Recommended**:
- Use concurrency control to prevent parallel deployments
- Deploy to dev automatically after build
- Require manual approval for prod
- Use infrastructure as code for all deployments

### 7. Cleanup

✅ **Recommended**:
- Delete ephemeral environments after use
- Use delete workflow for stack cleanup
- Monitor stack costs
- Clean up unused images in ECR

### 8. Version Management

✅ **Recommended**:
- Use semantic versioning (`MAJOR.MINOR.PATCH`)
- Update version in `package.json`
- Tag Git releases with versions
- Track versions in deployment artifacts

---

## Workflow Dependencies

### Main Build Workflow Dependencies

```
main-build.yaml
├── lint-test (parallel)
│   └── sub_lint-and-test.yaml
│       ├── lint-code
│       ├── lint-infra
│       └── unit-test
├── security-scan (parallel)
│   └── snyk-node.yaml@v6
├── build-and-package (depends on: lint-test, security-scan)
│   ├── setup
│   ├── install
│   ├── set-version
│   ├── build
│   ├── build-container-images
│   ├── publish-container-images
│   └── upload-artifact
└── deploy (depends on: build-and-package)
    └── deploy.yaml
        └── deploy-api.yaml@v6
```

### Promotion Flow

```
promote.yaml
└── promote
    ├── setup
    ├── assume-oidc-role
    └── promote-container-image
        └── (re-tags image in ECR)
```

### Deployment Flow

```
deploy.yaml
└── deploy
    └── deploy-api.yaml@v6
        ├── assume-oidc-role
        ├── download-artifacts
        ├── resolve-image-tag
        ├── get-ssm-parameters
        └── sam-deploy
            └── (updates CloudFormation stack)
```

---

## Key Takeaways

1. **Complete Automation**: Build, test, scan, publish, deploy all automated
2. **Security First**: Multiple layers of security scanning
3. **Infrastructure as Code**: All deployments via CloudFormation/SAM
4. **Environment Promotion**: Controlled promotion between environments
5. **Version Management**: Semantic versioning throughout
6. **Reusable Components**: Shared workflows and actions
7. **Best Practices**: Follows industry standards for container workflows

---

## References

- **Repository**: `https://github.com/gp-nova/exp-container-image-exemplar`
- **Custom Actions**: `gp-nova/devx-pipeline-modules-containers`
- **Shared Workflows**: `gp-nova/devx-shared-workflows`
- **Documentation**: See repository README and workflow files

---

## Summary

The `exp-container-image-exemplar` repository provides a **complete, production-ready** template for container image lifecycle management:

- ✅ **Build**: Automated container image builds with security scanning
- ✅ **Publish**: Automatic publishing to ECR with proper tagging
- ✅ **Deploy**: Infrastructure as code deployments to multiple environments
- ✅ **Promote**: Controlled image promotion between environments
- ✅ **Release**: Artifact release workflow for cross-team consumption
- ✅ **Delete**: Stack cleanup workflow
- ✅ **Security**: Multi-layer security scanning
- ✅ **Testing**: Comprehensive testing and linting
- ✅ **Best Practices**: Industry-standard patterns and recommendations

This exemplar serves as the **gold standard** for container image workflows in the organization.

