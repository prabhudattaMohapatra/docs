# Detailed Migration Path: Manual Scripts to Automated CI/CD Pipelines

This document provides a comprehensive, step-by-step guide for migrating from manual build and deployment scripts to automated GitHub Actions workflows following the `exp-container-image-exemplar` pattern.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Phase 0: Pre-Migration Setup](#phase-0-pre-migration-setup)
3. [Phase 1: Create GitHub Actions Workflows](#phase-1-create-github-actions-workflows)
4. [Phase 2: Update Infrastructure Template](#phase-2-update-infrastructure-template)
5. [Phase 3: Add Deployment Workflow](#phase-3-add-deployment-workflow)
6. [Phase 4: Add Promotion Workflow](#phase-4-add-promotion-workflow)
7. [Phase 5: Testing and Validation](#phase-5-testing-and-validation)
8. [Phase 6: Deprecate Manual Scripts](#phase-6-deprecate-manual-scripts)
9. [Troubleshooting Guide](#troubleshooting-guide)
10. [Rollback Procedures](#rollback-procedures)

---

## Prerequisites

### 1. Required Access and Permissions

- ✅ **GitHub Repository Access**: Write access to `payroll-engine-backend` repository
- ✅ **AWS Account Access**: Appropriate IAM roles for:
  - ECR (push/pull images)
  - ECS (update services)
  - CloudFormation (deploy stacks)
  - SSM Parameter Store (read parameters)
- ✅ **GitHub Secrets**: Ability to configure repository secrets
- ✅ **GitHub Environments**: Ability to create environments (dev, test, prod)

### 2. Required Tools and Knowledge

- ✅ **Git**: Basic git operations
- ✅ **Docker**: Understanding of container builds
- ✅ **AWS CLI**: Basic familiarity (for validation)
- ✅ **CloudFormation/SAM**: Understanding of infrastructure as code
- ✅ **GitHub Actions**: Basic understanding of workflow syntax

### 3. Required Information

Before starting, gather the following:

```bash
# Current values from deploy.sh
AWS_REGION="us-east-1"
AWS_ACCOUNT_ID="258215414239"
REPOSITORY_NAME="payroll-engine/backend"
ECS_CLUSTER="payroll-engine-cluster"
ECS_SERVICE="payroll-engine-backend"

# Infrastructure stack name
INFRA_STACK_NAME="payroll-engine-ecs-application"  # From gp-nova-payroll-engine-infra

# ECR Repository URI pattern
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
ECR_REPO_URI="${ECR_REGISTRY}/${REPOSITORY_NAME}"
```

### 4. Required GitHub Secrets

You'll need to configure these secrets in GitHub:

- `SNYK_TOKEN` - For security scanning (optional but recommended)
- AWS credentials are handled via OIDC (no secrets needed)

### 5. Required GitHub Variables

Configure these in repository settings → Variables:

- `AWS_REGION` - `us-east-1`
- `BACKEND_IMAGE_TAG` - `latest` (default, will be overridden by pipeline)

---

## Phase 0: Pre-Migration Setup

### Step 0.1: Verify Current Setup

**Objective**: Ensure current manual process works before migration

**Actions**:

1. **Test current build script**:
   ```bash
   cd /Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend
   ./shellScripts/build.sh
   ```
   Expected: Docker image builds successfully as `payroll-engine/backend:latest`

2. **Verify Dockerfile**:
   ```bash
   docker images | grep payroll-engine/backend
   ```
   Expected: Image exists locally

3. **Test current deployment** (optional - only if you want to verify):
   ```bash
   # Note: This will actually deploy, so only do this if safe
   # ./shellScripts/deploy.sh
   ```

4. **Verify infrastructure template**:
   ```bash
   cd /Users/pmohapatra/repos/payroll/prabhu_aws/gp-nova-payroll-engine-infra
   # Verify template.yaml exists and has correct parameters
   ```

**Validation Checklist**:
- [ ] Build script executes without errors
- [ ] Docker image is created successfully
- [ ] Infrastructure template is accessible
- [ ] ECR repository exists and is accessible
- [ ] ECS cluster and service exist

### Step 0.2: Create GitHub Environment

**Objective**: Set up GitHub environments for deployment

**Actions**:

1. **Navigate to GitHub Repository Settings**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/settings/environments`
   - Or: Repository → Settings → Environments

2. **Create `dev` environment**:
   - Click "New environment"
   - Name: `dev`
   - Add protection rules (optional):
     - Required reviewers: None (for dev)
     - Wait timer: 0 minutes
   - Save environment

3. **Create `test` environment** (optional, for later):
   - Name: `test`
   - Add protection rules:
     - Required reviewers: Add team members
     - Wait timer: 0 minutes

4. **Create `prod` environment** (optional, for later):
   - Name: `prod`
   - Add protection rules:
     - Required reviewers: Add team members
     - Wait timer: 5 minutes (recommended)

**Validation Checklist**:
- [ ] `dev` environment created
- [ ] Environment protection rules configured (if needed)
- [ ] Team members have appropriate access

### Step 0.3: Configure GitHub Secrets

**Objective**: Set up required secrets for workflows

**Actions**:

1. **Navigate to GitHub Secrets**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/settings/secrets/actions`
   - Or: Repository → Settings → Secrets and variables → Actions

2. **Add Snyk Token** (optional but recommended):
   - Name: `SNYK_TOKEN`
   - Value: Get from Snyk dashboard or skip if not using Snyk
   - Note: If not using Snyk, the build step will still work but without security scanning

3. **Verify OIDC Setup**:
   - OIDC authentication is handled automatically by `gp-nova/devx-action-assume-oidc-role@v2`
   - No AWS credentials needed as secrets
   - Verify your AWS account has OIDC provider configured

**Validation Checklist**:
- [ ] `SNYK_TOKEN` secret added (or confirmed not needed)
- [ ] OIDC authentication verified with AWS

### Step 0.4: Verify ECR Repository Exists

**Objective**: Ensure ECR repository is accessible

**Actions**:

1. **Check ECR repository**:
   ```bash
   aws ecr describe-repositories \
     --repository-names payroll-engine/backend \
     --region us-east-1 \
     --query 'repositories[0].repositoryUri' \
     --output text
   ```
   Expected output: `258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/backend`

2. **Verify repository permissions**:
   ```bash
   aws ecr get-repository-policy \
     --repository-name payroll-engine/backend \
     --region us-east-1
   ```

3. **Test ECR login**:
   ```bash
   aws ecr get-login-password --region us-east-1 | \
     docker login --username AWS --password-stdin \
     258215414239.dkr.ecr.us-east-1.amazonaws.com
   ```
   Expected: `Login Succeeded`

**Validation Checklist**:
- [ ] ECR repository exists
- [ ] Repository URI matches expected pattern
- [ ] ECR login works
- [ ] Permissions are correct

---

## Phase 1: Create GitHub Actions Workflows

### Step 1.1: Create Workflow Directory Structure

**Objective**: Set up directory structure for GitHub Actions

**Actions**:

1. **Create `.github` directory** (if it doesn't exist):
   ```bash
   cd /Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend
   mkdir -p .github/workflows
   ```

2. **Verify directory structure**:
   ```bash
   ls -la .github/workflows/
   ```
   Expected: Directory exists (may be empty)

**Validation Checklist**:
- [ ] `.github/workflows/` directory exists

### Step 1.2: Create Image Build Configuration

**Objective**: Create configuration file for container image builds (following exemplar pattern)

**Actions**:

1. **Create `image-build-config.yaml`** in repository root:
   ```yaml
   # Image Build Configuration
   # This file defines how container images are built
   # Following the exemplar pattern from exp-container-image-exemplar
   
   ImageBuildConfiguration:
     BackendService:
       buildContext: .
       dockerfile: Dockerfile
       imageName: payroll-engine-backend
       platform: linux/amd64
   ```

   **File Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend/image-build-config.yaml`

2. **Alternative: Add to existing file**:
   - If using SAM/CloudFormation, you can add this to `template.yaml` in a `Mappings` section
   - For now, we'll reference it in the workflow

**Validation Checklist**:
- [ ] Configuration file created
- [ ] Image name matches repository structure

### Step 1.3: Create Main Build Workflow

**Objective**: Create automated build workflow that replaces `build.sh`

**Actions**:

1. **Create `.github/workflows/main-build.yaml`**:

```yaml
name: Main Build

on:
  workflow_dispatch:
  push:
    branches:
      - "main"
    paths-ignore:
      - "*.md"
      - ".github/workflows/feature*.yaml"
      - ".github/workflows/pull-request.yaml"
      - ".github/workflows/perf-test.yaml"
      - ".github/workflows/delete.yaml"

permissions:
  actions: read
  contents: read
  id-token: write

env:
  COMPONENT_NAME: ${{ github.event.repository.name }}
  AWS_REGION: us-east-1

jobs:
  build-and-publish:
    name: Build and Publish Container Image
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.set-version.outputs.version }}
      image-tag: ${{ steps.set-version.outputs.version }}
    permissions:
      contents: write
      id-token: write
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Assume OIDC Role
        id: assume-oidc-role
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: 'dev'

      - name: Set version
        id: set-version
        run: |
          # Use git commit SHA as version (short)
          VERSION=$(git rev-parse --short HEAD)
          # Alternative: Use semantic versioning if you have tags
          # VERSION=$(git describe --tags --always)
          echo "version=$VERSION" >> $GITHUB_OUTPUT
          echo "Image version: $VERSION"

      - name: Build container images
        uses: gp-nova/devx-pipeline-modules-containers/build@v1
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          # Configuration for the build
          # The build module will look for Dockerfile in root by default
          build-context: .
          dockerfile: Dockerfile

      - name: Publish container images
        uses: gp-nova/devx-pipeline-modules-containers/publish@v1
        with:
          version: ${{ steps.set-version.outputs.version }}
          image-name: payroll-engine-backend
          # ECR repository will be auto-detected or can be specified
          ecr-repository: payroll-engine/backend
```

**File Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend/.github/workflows/main-build.yaml`

**Key Points**:
- Uses OIDC for AWS authentication (no secrets needed)
- Builds on push to main branch
- Uses git commit SHA for versioning
- Integrates security scanning (Snyk)
- Publishes to ECR automatically

2. **Commit the workflow**:
   ```bash
   git add .github/workflows/main-build.yaml
   git commit -m "Add automated build workflow for container images"
   git push origin main
   ```

**Validation Checklist**:
- [ ] Workflow file created with correct syntax
- [ ] Committed to repository
- [ ] Workflow appears in GitHub Actions tab

### Step 1.4: Test Build Workflow

**Objective**: Verify the build workflow works correctly

**Actions**:

1. **Trigger workflow manually**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Main Build" workflow
   - Click "Run workflow" → Select branch: `main` → Click "Run workflow"

2. **Monitor workflow execution**:
   - Watch the workflow run
   - Check each step for errors
   - Verify build completes successfully

3. **Verify image in ECR**:
   ```bash
   aws ecr list-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'imageIds[*].imageTag' \
     --output table
   ```
   Expected: New image tag appears (git commit SHA)

4. **Verify image details**:
   ```bash
   # Get the latest image tag
   LATEST_TAG=$(aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
     --output text)
   
   echo "Latest image tag: $LATEST_TAG"
   
   # Verify image exists
   aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --image-ids imageTag=$LATEST_TAG \
     --region us-east-1
   ```

**Troubleshooting**:
- If build fails, check workflow logs
- Verify OIDC role assumption works
- Check ECR permissions
- Verify Dockerfile builds locally first

**Validation Checklist**:
- [ ] Workflow executes without errors
- [ ] Container image is built successfully
- [ ] Image is pushed to ECR
- [ ] Image tag matches git commit SHA
- [ ] Security scan completes (if Snyk token configured)

### Step 1.5: Create Pull Request Workflow (Optional)

**Objective**: Add workflow for pull requests (build only, no publish)

**Actions**:

1. **Create `.github/workflows/pull-request.yaml`**:

```yaml
name: Pull Request Build

on:
  pull_request:
    branches:
      - "main"

permissions:
  actions: read
  contents: read
  id-token: write

env:
  COMPONENT_NAME: ${{ github.event.repository.name }}
  AWS_REGION: us-east-1

jobs:
  build-only:
    name: Build Container Image (No Publish)
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Assume OIDC Role
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: 'dev'

      - name: Build container images (test only)
        uses: gp-nova/devx-pipeline-modules-containers/build@v1
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          build-context: .
          dockerfile: Dockerfile
          # Note: No publish step for PRs
```

2. **Commit and test**:
   ```bash
   git add .github/workflows/pull-request.yaml
   git commit -m "Add PR build workflow (build only, no publish)"
   git push origin main
   ```

**Validation Checklist**:
- [ ] PR workflow created
- [ ] Workflow runs on pull requests
- [ ] Builds but doesn't publish (as expected)

---

## Phase 2: Update Infrastructure Template

### Step 2.1: Review Current Infrastructure Template

**Objective**: Understand current template structure

**Actions**:

1. **Examine infrastructure template**:
   ```bash
   cd /Users/pmohapatra/repos/payroll/prabhu_aws/gp-nova-payroll-engine-infra
   cat template.yaml | grep -A 10 "BackendRepoUri\|BackendImageTag"
   ```

2. **Note current parameter structure**:
   - `BackendRepoUri`: Currently passed as parameter
   - `BackendImageTag`: Currently passed as parameter (defaults to 'latest')

3. **Check how it's used in task definition**:
   ```bash
   grep -A 5 "BackendTaskDefinition" template.yaml
   ```

**Current Structure** (from template.yaml):
```yaml
Parameters:
  BackendRepoUri:
    Type: String
  BackendImageTag:
    Type: String

BackendTaskDefinition:
  ContainerDefinitions:
    - Image: !Sub
        - "${RepoUri}:${Tag}"
        - { RepoUri: !Ref BackendRepoUri, Tag: !Ref BackendImageTag }
```

### Step 2.2: Add SSM Parameter Support (Optional Enhancement)

**Objective**: Make ECR repository URI configurable via SSM (following exemplar pattern)

**Actions**:

1. **Update `template.yaml` in infrastructure repo**:

   **Option A: Keep current approach** (simpler, no changes needed)
   - Current approach works fine
   - Repository URI is passed as parameter
   - No changes required

   **Option B: Use SSM Parameter** (more flexible, follows exemplar):
   ```yaml
   Parameters:
     # Keep existing or replace with SSM lookup
     BackendRepoUri:
       Type: AWS::SSM::Parameter::Value<String>
       Default: /account/ecr/main/registry/payroll-engine-backend
       Description: ECR repository URI for backend image
     # OR keep as String parameter (current approach)
   
     BackendImageTag:
       Type: String
       Default: latest
       Description: Image tag/version for backend container
   
     DeploymentVersion:
       Type: String
       Default: latest
       Description: Deployment version (environment-version format)
   ```

2. **Recommendation**: 
   - **Keep current approach** for Phase 2 (simpler migration)
   - Can enhance to SSM later if needed
   - Current parameter-based approach works with pipelines

**Validation Checklist**:
- [ ] Reviewed current template structure
- [ ] Understood parameter usage
- [ ] Decided on approach (keep current or enhance)

### Step 2.3: Verify Image Tag Parameter Format

**Objective**: Ensure image tag parameter supports version format from pipeline

**Actions**:

1. **Check current deployment script**:
   - Current: Uses timestamp format `YYYYMMDD-HHMMSS`
   - Pipeline: Will use git commit SHA (short)
   - Both are valid image tags

2. **Update parameter description** (optional):
   ```yaml
   BackendImageTag:
     Type: String
     Default: latest
     Description: Image tag/version (git commit SHA, timestamp, or semantic version)
   ```

3. **Test parameter acceptance**:
   - Image tags can be: `abc1234`, `20240101-120000`, `dev-1.0.0`, `latest`
   - All are valid ECR tags
   - No changes needed to template

**Validation Checklist**:
- [ ] Parameter accepts various tag formats
- [ ] Template doesn't need changes for tag format

---

## Phase 3: Add Deployment Workflow

### Step 3.1: Create Deployment Workflow

**Objective**: Create workflow that replaces `deploy.sh` functionality

**Actions**:

1. **Create `.github/workflows/deploy.yaml`**:

```yaml
name: Deploy to ECS

on:
  workflow_dispatch:
    inputs:
      environment:
        description: Target environment
        required: true
        type: choice
        options:
          - dev
          - test
          - prod
      image-tag:
        description: Image tag to deploy (leave empty for latest from main branch)
        required: false
        type: string
      version:
        description: Deployment version (optional, defaults to image-tag)
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
      image-tag:
        required: false
        type: string

permissions:
  contents: read
  id-token: write

env:
  AWS_REGION: us-east-1
  INFRA_REPO: gp-nova/payroll-engine-infra  # Adjust to actual repo name
  INFRA_STACK_NAME: payroll-engine-ecs-application

jobs:
  deploy:
    name: Deploy to ${{ inputs.environment || 'dev' }}
    runs-on: ubuntu-latest
    environment: ${{ inputs.environment || 'dev' }}
    permissions:
      contents: read
      id-token: write
    steps:
      - name: Checkout infrastructure repository
        uses: actions/checkout@v4
        with:
          repository: ${{ env.INFRA_REPO }}
          path: infrastructure
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Assume OIDC Role
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: ${{ inputs.environment || 'dev' }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Setup AWS SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Determine image tag
        id: image-tag
        run: |
          if [ -z "${{ inputs.image-tag }}" ]; then
            # Get latest image tag from ECR
            TAG=$(aws ecr describe-images \
              --repository-name payroll-engine/backend \
              --region ${{ env.AWS_REGION }} \
              --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
              --output text)
            echo "tag=$TAG" >> $GITHUB_OUTPUT
            echo "Using latest image tag: $TAG"
          else
            echo "tag=${{ inputs.image-tag }}" >> $GITHUB_OUTPUT
            echo "Using provided image tag: ${{ inputs.image-tag }}"
          fi

      - name: Fetch Network Parameters from SSM
        id: network-params
        run: |
          REGION="${{ env.AWS_REGION }}"
          VPC_ID=$(aws ssm get-parameter --name /network/vpc/main/id --query "Parameter.Value" --output text --region "$REGION")
          PRIVATE_SUBNETS=$(aws ssm get-parameter --name /network/subnet/app/main/ids --query "Parameter.Value" --output text --region "$REGION")
          VPC_CIDR=$(aws ec2 describe-vpcs --vpc-ids "$VPC_ID" --query "Vpcs[0].CidrBlock" --output text --region "$REGION")

          echo "VPC_ID=$VPC_ID" >> $GITHUB_ENV
          echo "PRIVATE_SUBNETS=$PRIVATE_SUBNETS" >> $GITHUB_ENV
          echo "VPC_CIDR=$VPC_CIDR" >> $GITHUB_ENV

      - name: Derive ECR Repository URI
        run: |
          echo "BACKEND_REPO_URI=${{ steps.login-ecr.outputs.registry }}/payroll-engine/backend" >> $GITHUB_ENV

      - name: Deploy infrastructure stack
        run: |
          cd infrastructure
          sam deploy \
            --template-file template.yaml \
            --stack-name ${{ env.INFRA_STACK_NAME }} \
            --capabilities CAPABILITY_NAMED_IAM \
            --region ${{ env.AWS_REGION }} \
            --no-fail-on-empty-changeset \
            --parameter-overrides \
              VpcId=${{ env.VPC_ID }} \
              PrivateSubnetIds="${{ env.PRIVATE_SUBNETS }}" \
              VpcCidr=${{ env.VPC_CIDR }} \
              DBUsername=${{ secrets.DB_USERNAME }} \
              FoundationalStackName="payroll-engine-foundational-resources" \
              BackendRepoUri=${{ env.BACKEND_REPO_URI }} \
              WebappRepoUri=${{ steps.login-ecr.outputs.registry }}/payroll-engine/webapp \
              ConsoleRepoUri=${{ steps.login-ecr.outputs.registry }}/payroll-engine/console \
              BackendImageTag=${{ steps.image-tag.outputs.tag }} \
              WebappImageTag=${{ vars.WEBAPP_IMAGE_TAG || 'latest' }} \
              ConsoleImageTag=${{ vars.CONSOLE_IMAGE_TAG || 'latest' }}

      - name: Verify deployment
        run: |
          echo "Deployment completed successfully"
          echo "Image tag deployed: ${{ steps.image-tag.outputs.tag }}"
          echo "Environment: ${{ inputs.environment || 'dev' }}"
          
          # Verify ECS service is running
          aws ecs describe-services \
            --cluster payroll-engine-cluster \
            --services payroll-engine-backend \
            --region ${{ env.AWS_REGION }} \
            --query 'services[0].{status:status,runningCount:runningCount,desiredCount:desiredCount}' \
            --output table
```

**File Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend/.github/workflows/deploy.yaml`

**Key Points**:
- Can be triggered manually with environment selection
- Can be called from other workflows
- Automatically determines latest image tag if not provided
- Updates CloudFormation stack with new image tag
- Verifies deployment after completion

2. **Update main-build.yaml to call deploy** (optional):

   Add to the end of `main-build.yaml`:
   ```yaml
   deploy:
     needs:
       - build-and-publish
     name: Deploy to Development
     uses: ./.github/workflows/deploy.yaml
     with:
       environment: dev
       version: ${{ needs.build-and-publish.outputs.version }}
       image-tag: ${{ needs.build-and-publish.outputs.version }}
     secrets: inherit
   ```

3. **Commit the workflow**:
   ```bash
   git add .github/workflows/deploy.yaml
   git commit -m "Add automated deployment workflow"
   git push origin main
   ```

**Validation Checklist**:
- [ ] Deployment workflow created
- [ ] Workflow syntax is correct
- [ ] Committed to repository

### Step 3.2: Test Deployment Workflow

**Objective**: Verify deployment workflow works correctly

**Actions**:

1. **Get a valid image tag from ECR**:
   ```bash
   aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
     --output text
   ```
   Note the tag (e.g., `abc1234`)

2. **Trigger deployment workflow manually**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Deploy to ECS" workflow
   - Click "Run workflow"
   - Select:
     - Environment: `dev`
     - Image tag: (use the tag from step 1, or leave empty for latest)
     - Version: (optional, leave empty)
   - Click "Run workflow"

3. **Monitor deployment**:
   - Watch workflow execution
   - Check CloudFormation deployment step
   - Verify ECS service update

4. **Verify ECS service updated**:
   ```bash
   # Check current task definition
   aws ecs describe-task-definition \
     --task-definition payroll-engine-backend \
     --region us-east-1 \
     --query 'taskDefinition.containerDefinitions[0].image' \
     --output text
   ```
   Expected: Should show the new image tag

5. **Verify ECS service status**:
   ```bash
   aws ecs describe-services \
     --cluster payroll-engine-cluster \
     --services payroll-engine-backend \
     --region us-east-1 \
     --query 'services[0].{status:status,runningCount:runningCount,desiredCount:desiredCount,deployments:deployments[*].{status:status,taskDefinition:taskDefinition}}' \
     --output json
   ```

**Troubleshooting**:
- If deployment fails, check CloudFormation events
- Verify SSM parameters exist
- Check IAM permissions for CloudFormation
- Verify ECR image tag exists

**Validation Checklist**:
- [ ] Deployment workflow executes successfully
- [ ] CloudFormation stack updates
- [ ] ECS task definition updated with new image
- [ ] ECS service starts new tasks
- [ ] Old tasks are stopped gracefully

### Step 3.3: Integrate Build and Deploy

**Objective**: Automatically deploy after successful build

**Actions**:

1. **Update `.github/workflows/main-build.yaml`**:

   Add deploy job at the end:
   ```yaml
   # ... existing build-and-publish job ...

   deploy:
     needs:
       - build-and-publish
     name: Deploy to Development
     uses: ./.github/workflows/deploy.yaml
     with:
       environment: dev
       version: ${{ needs.build-and-publish.outputs.version }}
       image-tag: ${{ needs.build-and-publish.outputs.version }}
     secrets: inherit
   ```

2. **Test integrated workflow**:
   - Make a small change to trigger build
   - Push to main branch
   - Verify build completes
   - Verify deployment triggers automatically
   - Monitor full pipeline execution

**Validation Checklist**:
- [ ] Build triggers deployment automatically
- [ ] Deployment uses image from build
- [ ] Full pipeline completes successfully

---

## Phase 4: Add Promotion Workflow

### Step 4.1: Create Image Promotion Workflow

**Objective**: Enable promoting images between environments

**Actions**:

1. **Create `.github/workflows/promote.yaml`**:

```yaml
name: Promote Container Image

on:
  workflow_dispatch:
    inputs:
      source-environment:
        description: Source environment
        required: true
        type: choice
        options:
          - dev
          - test
        default: dev
      target-environment:
        description: Target environment
        required: true
        type: choice
        options:
          - test
          - prod
        default: test
      image-tag:
        description: Image tag to promote (leave empty for latest from source)
        required: false
        type: string

permissions:
  actions: read
  contents: read
  id-token: write

env:
  AWS_REGION: us-east-1

jobs:
  promote:
    name: Promote Image ${{ inputs.source-environment }} → ${{ inputs.target-environment }}
    runs-on: ubuntu-latest
    environment: ${{ inputs.target-environment }}
    permissions:
      contents: read
      id-token: write
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Assume OIDC Role (Source)
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: ${{ inputs.source-environment }}

      - name: Determine source image tag
        id: source-tag
        run: |
          if [ -z "${{ inputs.image-tag }}" ]; then
            # Get latest image tag from source environment
            TAG=$(aws ecr describe-images \
              --repository-name payroll-engine/backend \
              --region ${{ env.AWS_REGION }} \
              --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
              --output text)
            echo "tag=$TAG" >> $GITHUB_OUTPUT
            echo "Using latest image tag from ${{ inputs.source-environment }}: $TAG"
          else
            echo "tag=${{ inputs.image-tag }}" >> $GITHUB_OUTPUT
            echo "Using provided image tag: ${{ inputs.image-tag }}"
          fi

      - name: Promote Container Image
        uses: gp-nova/devx-pipeline-modules-containers/promote@v1
        with:
          version: ${{ steps.source-tag.outputs.tag }}
          source-environment: ${{ inputs.source-environment }}
          target-environment: ${{ inputs.target-environment }}
          image-name: payroll-engine-backend

      - name: Trigger deployment to target environment
        uses: ./.github/workflows/deploy.yaml
        with:
          environment: ${{ inputs.target-environment }}
          version: ${{ steps.source-tag.outputs.tag }}
          image-tag: ${{ steps.source-tag.outputs.tag }}
        secrets: inherit
```

**File Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend/.github/workflows/promote.yaml`

**Key Points**:
- Promotes images between environments
- Can be triggered manually
- Requires environment approval (if configured)
- Automatically triggers deployment after promotion

2. **Commit the workflow**:
   ```bash
   git add .github/workflows/promote.yaml
   git commit -m "Add image promotion workflow"
   git push origin main
   ```

**Validation Checklist**:
- [ ] Promotion workflow created
- [ ] Workflow syntax is correct
- [ ] Committed to repository

### Step 4.2: Test Promotion Workflow

**Objective**: Verify image promotion works

**Actions**:

1. **Ensure you have an image in dev environment**:
   - Run build workflow if needed
   - Verify image exists in ECR

2. **Trigger promotion workflow**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Promote Container Image"
   - Click "Run workflow"
   - Select:
     - Source environment: `dev`
     - Target environment: `test`
     - Image tag: (leave empty for latest)
   - Click "Run workflow"

3. **Monitor promotion**:
   - Watch workflow execution
   - Verify image is promoted
   - Verify deployment to target environment

**Validation Checklist**:
- [ ] Promotion workflow executes successfully
- [ ] Image is available in target environment
- [ ] Deployment to target environment succeeds

---

## Phase 5: Testing and Validation

### Step 5.1: End-to-End Testing

**Objective**: Verify complete pipeline works from code to deployment

**Actions**:

1. **Test complete workflow**:
   ```bash
   # Make a small change
   echo "# Test change" >> README.md
   git add README.md
   git commit -m "Test: Trigger automated build and deploy"
   git push origin main
   ```

2. **Monitor pipeline**:
   - Build workflow should trigger
   - Image should be built and published
   - Deployment should trigger automatically
   - ECS service should update

3. **Verify in AWS**:
   ```bash
   # Check ECR
   aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'sort_by(imageDetails,& imagePushedAt)[-1]' \
     --output json

   # Check ECS
   aws ecs describe-services \
     --cluster payroll-engine-cluster \
     --services payroll-engine-backend \
     --region us-east-1 \
     --query 'services[0].{status:status,taskDefinition:taskDefinition}' \
     --output json
   ```

**Validation Checklist**:
- [ ] Code push triggers build
- [ ] Build completes successfully
- [ ] Image is published to ECR
- [ ] Deployment triggers automatically
- [ ] ECS service updates
- [ ] Application is accessible

### Step 5.2: Compare Manual vs Automated

**Objective**: Verify automated process produces same results as manual

**Actions**:

1. **Compare image tags**:
   - Manual: Uses timestamp format
   - Automated: Uses git commit SHA
   - Both are valid, automated is more traceable

2. **Compare deployment process**:
   - Manual: Updates task definition via AWS CLI
   - Automated: Updates via CloudFormation
   - Both achieve same result, automated is more reliable

3. **Verify functionality**:
   - Test application endpoints
   - Verify logs
   - Check metrics

**Validation Checklist**:
- [ ] Automated process works correctly
- [ ] Application functions as expected
- [ ] No regressions introduced

### Step 5.3: Performance Testing

**Objective**: Ensure pipeline performance is acceptable

**Actions**:

1. **Measure pipeline duration**:
   - Build: Should complete in ~5-15 minutes
   - Deploy: Should complete in ~5-10 minutes
   - Total: ~10-25 minutes

2. **Compare with manual process**:
   - Manual: ~5-10 minutes (but requires human)
   - Automated: ~10-25 minutes (but hands-off)

3. **Optimize if needed**:
   - Enable build caching
   - Parallelize steps
   - Optimize Dockerfile layers

**Validation Checklist**:
- [ ] Pipeline completes in reasonable time
- [ ] Performance is acceptable
- [ ] No unnecessary delays

---

## Phase 6: Deprecate Manual Scripts

### Step 6.1: Update Script Documentation

**Objective**: Document that scripts are for local development only

**Actions**:

1. **Update `shellScripts/build.sh`**:
   Add header comment:
   ```bash
   #!/bin/bash
   # 
   # LOCAL DEVELOPMENT BUILD SCRIPT
   # 
   # This script is for local development and testing only.
   # For CI/CD, use GitHub Actions workflows (.github/workflows/main-build.yaml)
   # 
   # Usage:
   #   ./shellScripts/build.sh
   # 
   # Note: This script requires local NuGet feed at ~/local-nuget-feed
   # 

   # Copy local NuGet feed to build context
   cp -r ~/local-nuget-feed ./packages

   # Build Docker image
   docker build --platform linux/amd64 -t payroll-engine/backend:latest .

   # Cleanup
   rm -rf ./packages
   ```

2. **Update `shellScripts/deploy.sh`**:
   Add header comment:
   ```bash
   #!/bin/bash
   # 
   # DEPRECATED: MANUAL DEPLOYMENT SCRIPT
   # 
   # ⚠️  WARNING: This script is DEPRECATED
   # 
   # For deployments, use GitHub Actions workflows:
   #   - .github/workflows/deploy.yaml (automated deployment)
   #   - .github/workflows/promote.yaml (image promotion)
   # 
   # This script is kept for emergency manual deployments only.
   # Prefer using automated workflows for all deployments.
   # 
   # Usage (only if automated workflows are unavailable):
   #   ./shellScripts/deploy.sh
   # 

   # ... rest of script ...
   ```

3. **Update README.md**:
   Add section about CI/CD:
   ```markdown
   ## CI/CD and Deployment

   This repository uses GitHub Actions for automated builds and deployments.

   ### Automated Workflows
   - **Build**: Automatically builds and publishes container images on push to main
   - **Deploy**: Deploys to ECS via CloudFormation
   - **Promote**: Promotes images between environments

   See `.github/workflows/` for workflow definitions.

   ### Local Development
   For local development and testing, use:
   - `./shellScripts/build.sh` - Build Docker image locally
   - `./shellScripts/deploy.sh` - Manual deployment (deprecated, use workflows instead)
   ```

**Validation Checklist**:
- [ ] Scripts are documented as deprecated
- [ ] README updated with CI/CD information
- [ ] Clear guidance on when to use scripts vs workflows

### Step 6.2: Create Migration Completion Document

**Objective**: Document the migration completion

**Actions**:

1. **Update `MIGRATION.md`** or create new section:

```markdown
## CI/CD Migration (Date: [Current Date])

### Migration Summary
Migrated from manual build/deploy scripts to automated GitHub Actions workflows.

### What Changed
- ✅ Added automated build workflow (replaces manual build.sh in CI/CD)
- ✅ Added automated deployment workflow (replaces manual deploy.sh)
- ✅ Added image promotion workflow (new capability)
- ✅ Integrated with existing CloudFormation infrastructure
- ✅ Maintained backward compatibility (scripts still work for local dev)

### Workflows Added
- `.github/workflows/main-build.yaml` - Automated build and publish
- `.github/workflows/deploy.yaml` - Automated deployment
- `.github/workflows/promote.yaml` - Image promotion
- `.github/workflows/pull-request.yaml` - PR build validation

### Scripts Status
- `shellScripts/build.sh` - **Local development only** (still functional)
- `shellScripts/deploy.sh` - **Deprecated** (use workflows instead)

### Benefits
- Automated builds on every push
- Security scanning integrated
- Consistent deployments
- Multi-environment support
- Image promotion workflow
- Reduced human error
```

**Validation Checklist**:
- [ ] Migration documented
- [ ] Team informed of changes
- [ ] Documentation is clear

---

## Troubleshooting Guide

### Common Issues and Solutions

#### Issue 1: Build Workflow Fails - OIDC Authentication

**Symptoms**:
- Workflow fails at "Assume OIDC Role" step
- Error: "Failed to assume role"

**Solutions**:
1. Verify OIDC provider is configured in AWS account
2. Check GitHub repository settings → Actions → General → Workflow permissions
3. Verify environment has correct OIDC role configured
4. Check AWS IAM role trust policy includes GitHub OIDC provider

**Commands to verify**:
```bash
# Check OIDC provider exists
aws iam list-open-id-connect-providers

# Check role trust policy
aws iam get-role --role-name <role-name> --query 'Role.AssumeRolePolicyDocument'
```

#### Issue 2: Build Workflow Fails - ECR Push

**Symptoms**:
- Build succeeds but push fails
- Error: "unauthorized" or "access denied"

**Solutions**:
1. Verify ECR repository exists
2. Check IAM role has ECR push permissions
3. Verify repository name matches exactly
4. Check region is correct

**Commands to verify**:
```bash
# List ECR repositories
aws ecr describe-repositories --region us-east-1

# Check repository policy
aws ecr get-repository-policy \
  --repository-name payroll-engine/backend \
  --region us-east-1
```

#### Issue 3: Deployment Fails - CloudFormation

**Symptoms**:
- Deployment workflow fails at CloudFormation step
- Error: "Stack update failed"

**Solutions**:
1. Check CloudFormation events for specific error
2. Verify all required parameters are provided
3. Check SSM parameters exist
4. Verify IAM permissions for CloudFormation

**Commands to verify**:
```bash
# Check CloudFormation events
aws cloudformation describe-stack-events \
  --stack-name payroll-engine-ecs-application \
  --region us-east-1 \
  --max-items 10

# Check SSM parameters
aws ssm get-parameter --name /network/vpc/main/id --region us-east-1
```

#### Issue 4: Image Tag Not Found

**Symptoms**:
- Deployment fails with "image not found"
- Error: "Image tag does not exist in ECR"

**Solutions**:
1. Verify image was built and pushed successfully
2. Check image tag format matches
3. Verify ECR repository name is correct
4. Check image exists in ECR

**Commands to verify**:
```bash
# List images in ECR
aws ecr list-images \
  --repository-name payroll-engine/backend \
  --region us-east-1

# Describe specific image
aws ecr describe-images \
  --repository-name payroll-engine/backend \
  --image-ids imageTag=<tag> \
  --region us-east-1
```

#### Issue 5: ECS Service Not Updating

**Symptoms**:
- Deployment completes but ECS service doesn't update
- Old image still running

**Solutions**:
1. Check ECS service events
2. Verify task definition was updated
3. Check service deployment status
4. Verify service has correct task definition

**Commands to verify**:
```bash
# Check ECS service events
aws ecs describe-services \
  --cluster payroll-engine-cluster \
  --services payroll-engine-backend \
  --region us-east-1 \
  --query 'services[0].events[:5]'

# Check current task definition
aws ecs describe-task-definition \
  --task-definition payroll-engine-backend \
  --region us-east-1 \
  --query 'taskDefinition.containerDefinitions[0].image'
```

### Getting Help

If issues persist:
1. Check workflow logs in GitHub Actions
2. Review CloudFormation stack events
3. Check ECS service events
4. Review ECR image tags
5. Contact DevOps team with:
   - Workflow run URL
   - Error messages
   - Relevant logs

---

## Rollback Procedures

### Rollback Scenario 1: Failed Deployment

**Objective**: Rollback to previous working version

**Actions**:

1. **Identify previous working image tag**:
   ```bash
   # List recent images
   aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'sort_by(imageDetails,& imagePushedAt)[-5:].imageTags[0]' \
     --output table
   ```

2. **Redeploy with previous image**:
   - Go to GitHub Actions
   - Run "Deploy to ECS" workflow manually
   - Select environment: `dev`
   - Enter previous image tag
   - Run workflow

3. **Verify rollback**:
   ```bash
   # Check ECS service
   aws ecs describe-services \
     --cluster payroll-engine-cluster \
     --services payroll-engine-backend \
     --region us-east-1
   ```

### Rollback Scenario 2: Disable Automated Workflows

**Objective**: Temporarily disable workflows and use manual scripts

**Actions**:

1. **Disable workflows** (temporary):
   - Go to repository settings
   - Actions → General
   - Disable workflows (temporary measure)

2. **Use manual scripts**:
   ```bash
   ./shellScripts/build.sh
   ./shellScripts/deploy.sh
   ```

3. **Re-enable workflows** after issue resolved

### Rollback Scenario 3: Revert Infrastructure Changes

**Objective**: Revert CloudFormation stack to previous version

**Actions**:

1. **Identify previous stack version**:
   ```bash
   aws cloudformation describe-stacks \
     --stack-name payroll-engine-ecs-application \
     --region us-east-1 \
     --query 'Stacks[0].StackId'
   ```

2. **Revert via CloudFormation**:
   ```bash
   # Use previous template version
   # Or update parameters to previous values
   ```

3. **Or use CloudFormation console**:
   - Go to CloudFormation console
   - Select stack
   - Update stack with previous parameters

---

## Success Criteria

### Migration is Complete When:

- [ ] ✅ Build workflow executes successfully on code push
- [ ] ✅ Images are published to ECR with proper tags
- [ ] ✅ Deployment workflow updates ECS service
- [ ] ✅ Application runs correctly with new image
- [ ] ✅ Promotion workflow works between environments
- [ ] ✅ Manual scripts are documented as deprecated
- [ ] ✅ Team is trained on new workflows
- [ ] ✅ Documentation is updated
- [ ] ✅ No regressions in functionality
- [ ] ✅ Performance is acceptable

### Post-Migration Tasks:

1. **Monitor for 1 week**:
   - Watch for any issues
   - Collect feedback from team
   - Make adjustments as needed

2. **Team Training**:
   - Document workflow usage
   - Train team on new processes
   - Share best practices

3. **Optimization**:
   - Optimize build times if needed
   - Add additional validation steps
   - Enhance monitoring

---

## Next Steps After Migration

### Potential Enhancements:

1. **Add More Environments**:
   - Add staging environment
   - Add production environment
   - Configure environment-specific settings

2. **Enhance Security**:
   - Add more security scanning
   - Implement image signing
   - Add vulnerability scanning

3. **Improve Monitoring**:
   - Add deployment notifications
   - Integrate with monitoring tools
   - Add deployment metrics

4. **Optimize Builds**:
   - Enable build caching
   - Optimize Dockerfile layers
   - Parallelize build steps

5. **Add Testing**:
   - Add integration tests
   - Add end-to-end tests
   - Add performance tests

---

## Conclusion

This migration path provides a comprehensive guide to transitioning from manual build and deployment scripts to automated GitHub Actions workflows. Follow each phase carefully, validate at each step, and don't hesitate to rollback if issues arise.

The automated approach provides:
- ✅ Consistent builds
- ✅ Automated deployments
- ✅ Security scanning
- ✅ Multi-environment support
- ✅ Reduced human error
- ✅ Better traceability

For questions or issues, refer to the troubleshooting guide or contact the DevOps team.

