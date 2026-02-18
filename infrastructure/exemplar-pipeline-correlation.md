# Exemplar Pipeline Correlation with Payroll Engine Backend

This document correlates how the `exp-container-image-exemplar` repository's CI/CD pipelines can improve and automate the current manual build and deployment process for the Payroll Engine Backend.

---

## Current Setup Analysis

### 1. **Current Build Process** (`shellScripts/build.sh`)

**Current Approach:**
```bash
# Copy local NuGet feed to build context
cp -r ~/local-nuget-feed ./packages

# Build Docker image
docker build --platform linux/amd64 -t payroll-engine/backend:latest .

# Cleanup
rm -rf ./packages
```

**Limitations:**
- ❌ Manual execution required
- ❌ No automated testing or linting
- ❌ No security scanning
- ❌ No version management
- ❌ Local-only build (not in CI/CD)
- ❌ Hardcoded platform (linux/amd64)
- ❌ Requires local NuGet feed setup

### 2. **Current Deployment Process** (`shellScripts/deploy.sh`)

**Current Approach:**
```bash
# Manual steps:
1. Build image locally (assumes already done)
2. Generate timestamp tag
3. ECR login
4. Tag and push to ECR
5. Manually update ECS task definition via AWS CLI
6. Update ECS service
```

**Limitations:**
- ❌ Fully manual process
- ❌ No automated versioning (uses timestamps)
- ❌ No rollback capability
- ❌ No deployment validation
- ❌ Error-prone (manual JSON manipulation)
- ❌ No multi-environment support
- ❌ No promotion workflow

### 3. **Current Infrastructure** (`template.yaml`)

**Current Approach:**
- ✅ ECS Task Definitions reference ECR images via parameters:
  ```yaml
  Image: !Sub
    - "${RepoUri}:${Tag}"
    - { RepoUri: !Ref BackendRepoUri, Tag: !Ref BackendImageTag }
  ```
- ✅ Proper IAM roles for ECR access
- ✅ CloudWatch Logs integration
- ✅ ECS Fargate services configured

**What's Missing:**
- ❌ No automated parameter updates
- ❌ Manual CloudFormation deployment
- ❌ No integration with CI/CD pipelines

---

## How Exemplar Pipelines Help

### 1. **Automated Build Pipeline** (Replaces `build.sh`)

#### Exemplar Approach:
```yaml
- name: Build container images
  uses: gp-nova/devx-pipeline-modules-containers/build@v1
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
```

#### Benefits for Payroll Engine Backend:

**Before (Manual):**
```bash
# Manual build.sh execution
docker build --platform linux/amd64 -t payroll-engine/backend:latest .
```

**After (Automated):**
- ✅ **Automated builds** on every push/PR
- ✅ **Security scanning** integrated (Snyk)
- ✅ **Multi-platform support** (handled by pipeline)
- ✅ **Consistent builds** across environments
- ✅ **Build caching** for faster builds
- ✅ **No local dependencies** required

#### Implementation Mapping:

| Current Step | Exemplar Equivalent | Improvement |
|-------------|-------------------|-------------|
| `cp -r ~/local-nuget-feed ./packages` | Handled in Dockerfile (already done) | ✅ No manual copy needed |
| `docker build --platform linux/amd64` | `devx-pipeline-modules-containers/build@v1` | ✅ Automated, multi-platform |
| Manual cleanup | Automated in pipeline | ✅ No manual cleanup |

---

### 2. **Automated Publish Pipeline** (Replaces part of `deploy.sh`)

#### Exemplar Approach:
```yaml
- name: Publish container images
  uses: gp-nova/devx-pipeline-modules-containers/publish@v1
  with:
    version: ${{ steps.set-version.outputs.version }}
```

#### Benefits for Payroll Engine Backend:

**Before (Manual):**
```bash
# Manual ECR login, tag, push
aws ecr get-login-password --region $AWS_REGION | docker login ...
docker tag $REPOSITORY_NAME:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$REPOSITORY_NAME:$IMAGE_TAG
docker push ...
```

**After (Automated):**
- ✅ **Semantic versioning** instead of timestamps
- ✅ **Automated ECR authentication** (OIDC-based)
- ✅ **Proper image tagging** with version + environment
- ✅ **Multi-environment support** (dev, test, prod)
- ✅ **Image promotion** workflow

#### Version Management Comparison:

| Current | Exemplar | Benefit |
|---------|----------|---------|
| `$(date +%Y%m%d-%H%M%S)` | Semantic versioning (1.0.0) | ✅ Traceable, meaningful versions |
| Manual tag generation | Automated from git tags/commits | ✅ Consistent versioning |
| Single environment | Multi-environment tags | ✅ Environment-specific images |

---

### 3. **Automated Deployment Pipeline** (Replaces `deploy.sh`)

#### Exemplar Approach:
```yaml
deploy:
  uses: gp-nova/devx-shared-workflows/.github/workflows/deploy-api.yaml@v6
  with:
    environment: ${{ inputs.environment }}
    version: ${{ inputs.version }}
```

#### Benefits for Payroll Engine Backend:

**Before (Manual):**
```bash
# Manual task definition update
aws ecs describe-task-definition --task-definition $ECS_SERVICE ...
jq ".containerDefinitions[0].image = \"...\"" ...
aws ecs register-task-definition ...
aws ecs update-service ...
```

**After (Automated):**
- ✅ **Automated CloudFormation/SAM deployment**
- ✅ **Task definition updates** handled automatically
- ✅ **ECS service updates** via infrastructure as code
- ✅ **Deployment validation** and rollback
- ✅ **Multi-environment deployments**
- ✅ **No manual JSON manipulation**

#### Deployment Flow Comparison:

| Current Manual Steps | Exemplar Automated | Benefit |
|---------------------|-------------------|---------|
| 1. Build locally | ✅ Automated build | No manual intervention |
| 2. ECR login | ✅ OIDC authentication | More secure |
| 3. Tag & push | ✅ Automated publish | Consistent tagging |
| 4. Update task def (manual JSON) | ✅ CloudFormation update | Infrastructure as code |
| 5. Update ECS service | ✅ CloudFormation update | Idempotent deployments |
| 6. Manual verification | ✅ Automated validation | Faster feedback |

---

### 4. **Infrastructure Integration**

#### Current Template.yaml Structure:
```yaml
BackendTaskDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    ContainerDefinitions:
      - Image: !Sub
          - "${RepoUri}:${Tag}"
          - { RepoUri: !Ref BackendRepoUri, Tag: !Ref BackendImageTag }
```

#### How Exemplar Integrates:

**Exemplar Pattern:**
```yaml
RepositoryPrefix:
  Type: AWS::SSM::Parameter::Value<String>
  Default: /account/ecr/main/registry
  Description: RepoPrefix for ECR repo

Image: !Sub
  - "${RepositoryPrefix}/${ImageNameAndTag}"
  - {
      RepositoryPrefix: !Ref RepositoryPrefix,
      ImageNameAndTag: !Join [ ":", [ 
        !FindInMap [ ImageBuildConfiguration, BusyBoxService, imageName ], 
        !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ] 
      ] ]
    }
```

#### Recommended Improvements:

1. **Use SSM Parameters for ECR Repository:**
   ```yaml
   BackendRepoUri:
     Type: AWS::SSM::Parameter::Value<String>
     Default: /account/ecr/main/registry/payroll-engine-backend
   ```

2. **Standardized Image Tagging:**
   ```yaml
   BackendImageTag:
     Type: String
     Default: latest
     # Pipeline will pass: {environment}-{version}
   ```

3. **Automated Parameter Updates:**
   - Pipeline passes `BackendImageTag` parameter
   - CloudFormation updates task definition automatically
   - No manual AWS CLI calls needed

---

## Complete Workflow Comparison

### Current Manual Workflow:
```
Developer → build.sh → deploy.sh → Manual ECS Update → Verify
   ↓           ↓           ↓              ↓              ↓
  Local     Local      ECR Push    Manual JSON    Manual Check
  Build     Image      Manual Tag   Manipulation
```

### Exemplar Automated Workflow:
```
Developer → Push to GitHub → Automated Pipeline → CloudFormation → ECS Update
   ↓              ↓                    ↓                  ↓              ↓
  Code      GitHub Actions      Build + Scan +      Infrastructure   Automatic
  Commit    Triggers           Publish to ECR      Update          Deployment
```

---

## Key Improvements Summary

### 1. **Build Process**
- ✅ **Automated**: No manual `build.sh` execution
- ✅ **Secure**: Integrated security scanning (Snyk)
- ✅ **Consistent**: Same build process across all environments
- ✅ **Fast**: Build caching and parallel execution

### 2. **Publish Process**
- ✅ **Automated**: No manual ECR login/push
- ✅ **Versioned**: Semantic versioning instead of timestamps
- ✅ **Tagged**: Environment-specific tags (dev-1.0.0, prod-1.0.0)
- ✅ **Promoted**: Image promotion workflow between environments

### 3. **Deployment Process**
- ✅ **Automated**: No manual `deploy.sh` execution
- ✅ **Infrastructure as Code**: CloudFormation updates instead of AWS CLI
- ✅ **Idempotent**: Safe to run multiple times
- ✅ **Validated**: Automated deployment validation
- ✅ **Rollback**: Easy rollback to previous versions

### 4. **Infrastructure**
- ✅ **Parameterized**: ECR repository from SSM
- ✅ **Versioned**: Image tags passed as parameters
- ✅ **Multi-Environment**: Support for dev, test, prod
- ✅ **Integrated**: Seamless CI/CD integration

---

## Migration Path

### Phase 1: Add GitHub Actions Workflows
1. Create `.github/workflows/main-build.yaml`
2. Integrate `devx-pipeline-modules-containers/build@v1`
3. Integrate `devx-pipeline-modules-containers/publish@v1`
4. Keep existing `build.sh` and `deploy.sh` for local development

### Phase 2: Update Infrastructure Template
1. Add SSM parameter for ECR repository prefix
2. Update image reference pattern to match exemplar
3. Add `DeploymentVersion` parameter
4. Test with manual CloudFormation deployment

### Phase 3: Add Deployment Workflow
1. Create `.github/workflows/deploy.yaml`
2. Use shared workflow or SAM deploy
3. Pass image tag as parameter
4. Automate CloudFormation updates

### Phase 4: Add Promotion Workflow
1. Create `.github/workflows/promote.yaml`
2. Enable image promotion between environments
3. Add approval gates for production

### Phase 5: Deprecate Manual Scripts
1. Keep `build.sh` for local development only
2. Document that `deploy.sh` is deprecated
3. Remove manual deployment steps from documentation

---

## Example GitHub Workflow Structure

### `.github/workflows/main-build.yaml`
```yaml
name: Main Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build container images
        uses: gp-nova/devx-pipeline-modules-containers/build@v1
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
      
      - name: Publish container images
        uses: gp-nova/devx-pipeline-modules-containers/publish@v1
        with:
          version: ${{ github.sha }}
```

### `.github/workflows/deploy.yaml`
```yaml
name: Deploy

on:
  workflow_dispatch:
    inputs:
      environment:
        type: environment
      version:
        type: string

jobs:
  deploy:
    uses: gp-nova/devx-shared-workflows/.github/workflows/deploy-api.yaml@v6
    with:
      environment: ${{ inputs.environment }}
      version: ${{ inputs.version }}
      api-name: payroll-engine-backend
```

---

## Conclusion

The exemplar repository provides a **complete, automated, and secure** approach to container image lifecycle management that would significantly improve the current manual process:

1. **Eliminates manual steps** in build and deployment
2. **Adds security scanning** and validation
3. **Provides proper versioning** and tagging
4. **Enables multi-environment** deployments
5. **Integrates with infrastructure as code** (CloudFormation)
6. **Reduces human error** through automation

The current `build.sh` and `deploy.sh` scripts can be replaced with GitHub Actions workflows that follow the exemplar pattern, while maintaining the same Dockerfile and infrastructure template structure.

