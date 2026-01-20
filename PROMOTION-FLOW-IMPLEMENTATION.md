# Promotion Flow Implementation Guide

This document outlines the changes required to implement the exemplar's promotion flow pattern for container image deployments.

## Overview

The promotion flow pattern enables:
- **Environment-based image tagging**: `{imageName}-{environment}-{version}`
- **Controlled promotion**: Images move from dev → test → prod
- **Version tracking**: Clear visibility of which version is deployed in each environment
- **Infrastructure as Code**: Image references managed through CloudFormation/SAM parameters

## Current State vs. Target State

### Current Pattern
- **Image Tag Format**: `build-{commitSHA}` (e.g., `build-b744cca`)
- **Task Definition**: Direct updates via AWS CLI
- **Image Reference**: `{BackendRepoUri}:{BackendImageTag}`
- **Deployment**: Manual tag selection or latest from ECR

### Target Pattern (Exemplar)
- **Image Tag Format**: `{imageName}-{environment}-{version}` (e.g., `payroll-engine-backend-dev-1.0.0`)
- **Task Definition**: Managed via CloudFormation/SAM
- **Image Reference**: `{RepositoryPrefix}/{imageName}-{environment}-{version}`
- **Deployment**: Promotion workflow re-tags and deploys

---

## Required Changes

### 1. Infrastructure Template (`gp-nova-payroll-engine-infra/template.yaml`)

**Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/gp-nova-payroll-engine-infra/template.yaml`

#### Current Parameters (Lines 16-27)
```yaml
Parameters:
  BackendRepoUri:
    Type: String
  BackendImageTag:
    Type: String
  WebappRepoUri:
    Type: String
  WebappImageTag:
    Type: String
  ConsoleRepoUri:
    Type: String
  ConsoleImageTag:
    Type: String
```

#### Change To:
```yaml
Parameters:
  RepositoryPrefix:
    Type: AWS::SSM::Parameter::Value<String>
    Default: /account/ecr/main/registry
    Description: ECR registry prefix from SSM parameter
  DeploymentVersion:
    Type: String
    Default: latest
    Description: Deployment version tag (e.g., 1.0.0, v2.1.0)
  Environment:
    Type: String
    Description: Environment name (dev, test, prod)
    AllowedValues: [dev, test, prod]
```

#### Current Image Reference (Lines 389-403, 362-376, 417-431)
```yaml
Image: !Sub
  - "${RepoUri}:${Tag}"
  - { RepoUri: !Ref BackendRepoUri, Tag: !Ref BackendImageTag }
```

#### Change To:
```yaml
Image: !Sub
  - "${RepositoryPrefix}/${ImageNameAndTag}"
  - {
      RepositoryPrefix: !Ref RepositoryPrefix,
      ImageNameAndTag: !Join [ ":", [ 
        "payroll-engine-backend", 
        !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ] 
      ] ]
    }
```

**Apply this pattern to:**
- `BackendTaskDefinition` (lines 389-403)
- `WebappTaskDefinition` (lines 362-376)
- `ConsoleTaskDefinition` (lines 417-431)

**Note**: Replace `"payroll-engine-backend"` with `"payroll-engine-webapp"` and `"payroll-engine-console"` for respective services.

---

### 2. Infrastructure `samconfig.toml` (`gp-nova-payroll-engine-infra/samconfig.toml`)

**Location**: `/Users/pmohapatra/repos/payroll/prabhu_aws/gp-nova-payroll-engine-infra/samconfig.toml`

#### Current Configuration
```toml
BackendRepoUri = "" # Populated by script
BackendImageTag = "latest"
WebappRepoUri = ""  # Populated by script
WebappImageTag = "latest"
ConsoleRepoUri = "" # Populated by script
ConsoleImageTag = "latest"
```

#### Add Environment-Specific Sections:
```toml
[dev.deploy.parameters]
parameter_overrides = [
    "Environment=dev",
    "RepositoryPrefix=/account/ecr/main/registry",
    "DeploymentVersion=latest",
]

[test.deploy.parameters]
parameter_overrides = [
    "Environment=test",
    "RepositoryPrefix=/account/ecr/main/registry",
    "DeploymentVersion=latest",
]

[prod.deploy.parameters]
parameter_overrides = [
    "Environment=prod",
    "RepositoryPrefix=/account/ecr/main/registry",
    "DeploymentVersion=latest",
]
```

**Note**: Remove or comment out the old `BackendRepoUri`, `BackendImageTag`, etc. parameters.

---

### 3. Infrastructure Deploy Workflows

**Locations**:
- `gp-nova-payroll-engine-infra/.github/workflows/deploy.yaml`
- `gp-nova-payroll-engine-infra/.github/workflows/deploy-main-split.yaml`
- `gp-nova-payroll-engine-infra/.github/workflows/deploy-custom.yaml`

#### Current Pattern (Lines 48-73 in deploy.yaml)
```yaml
- name: Derive ECR Repo URIs
  run: |
    echo "BACKEND_REPO_URI=${{ steps.login-ecr.outputs.registry }}/payroll-engine/backend" >> $GITHUB_ENV
    echo "WEBAPP_REPO_URI=${{ steps.login-ecr.outputs.registry }}/payroll-engine/webapp" >> $GITHUB_ENV
    echo "CONSOLE_REPO_URI=${{ steps.login-ecr.outputs.registry }}/payroll-engine/console" >> $GITHUB_ENV

- name: Deploy application stack
  run: |
    sam deploy \
      --parameter-overrides \
        BackendRepoUri=${{ env.BACKEND_REPO_URI }} \
        BackendImageTag=${{ vars.BACKEND_IMAGE_TAG || 'latest'}} \
        WebappRepoUri=${{ env.WEBAPP_REPO_URI }} \
        WebappImageTag=${{ vars.WEBAPP_IMAGE_TAG || 'latest'}} \
        ConsoleRepoUri=${{ env.CONSOLE_REPO_URI }} \
        ConsoleImageTag=${{ vars.CONSOLE_IMAGE_TAG || 'latest'}}
```

#### Change To:
```yaml
- name: Deploy application stack
  run: |
    sam deploy \
      --parameter-overrides \
        Environment=${{ inputs.environment || vars.ENVIRONMENT || 'dev' }} \
        RepositoryPrefix=/account/ecr/main/registry \
        DeploymentVersion=${{ vars.DEPLOYMENT_VERSION || 'latest' }} \
```

**Note**: 
- Remove the "Derive ECR Repo URIs" step (no longer needed)
- The `RepositoryPrefix` comes from SSM parameter
- `DeploymentVersion` can be set via GitHub variables or workflow inputs
- `Environment` should match the GitHub environment name

---

### 4. Create Promotion Workflow (NEW FILE)

**Location**: `payroll-engine-backend/.github/workflows/promote.yaml`

**Create this file with the following content:**

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
      version:
        description: Version to promote (e.g., 1.0.0, or build-{commitSHA})
        required: true
        type: string

permissions:
  actions: read
  contents: read
  id-token: write

env:
  AWS_REGION: us-east-1
  IMAGE_NAME: payroll-engine-backend

jobs:
  promote:
    name: Promote from ${{ inputs.source-environment }} to ${{ inputs.target-environment }}
    runs-on: ubuntu-latest
    environment: ${{ inputs.target-environment }}
    permissions:
      contents: read
      id-token: write
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      # NOTE: OIDC Authentication requires IAM role trust policy to include this repository
      # The IAM role trust policy must allow:
      #   "repo:gp-nova/payroll-engine-backend:*" or specific branches
      # Contact AWS admin to update the IAM role trust policy if authentication fails
      - name: Assume OIDC Role
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: ${{ inputs.target-environment }}

      - name: Promote Container Image
        uses: gp-nova/devx-pipeline-modules-containers/promote@v1
        with:
          version: ${{ inputs.version }}
          source-environment: ${{ inputs.source-environment }}
          target-environment: ${{ inputs.target-environment }}
          image-name: ${{ env.IMAGE_NAME }}

      - name: Output promoted image tag
        run: |
          PROMOTED_TAG="${{ env.IMAGE_NAME }}-${{ inputs.target-environment }}-${{ inputs.version }}"
          echo "Promoted image tag: $PROMOTED_TAG"
          echo "Full URI: 237156726900.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/payroll-engine/${{ env.IMAGE_NAME }}:$PROMOTED_TAG"
```

---

### 5. Backend Deploy Workflow (Optional Update)

**Location**: `payroll-engine-backend/.github/workflows/deploy.yaml`

**Option A: Keep Current Direct ECS Updates**
- No changes needed
- Continues to work with any tag format
- Bypasses promotion pattern

**Option B: Integrate with Promotion Pattern**
- Update to use environment-based tags
- Trigger infrastructure deploy workflow instead of direct ECS updates
- Or update to use promoted image tags

**If choosing Option B, update the workflow to:**
1. Use promoted image tags: `payroll-engine-backend-{env}-{version}`
2. Or trigger infrastructure deploy workflow with `DeploymentVersion` parameter

---

### 6. AWS SSM Parameter Setup

**Action Required**: Verify or create SSM parameter in AWS account `258215414239`

**Parameter Details:**
- **Parameter Name**: `/account/ecr/main/registry`
- **Parameter Value**: `237156726900.dkr.ecr.us-east-1.amazonaws.com`
- **Parameter Type**: `String`
- **Region**: `us-east-1`

**AWS CLI Command to Create/Update:**
```bash
aws ssm put-parameter \
  --name "/account/ecr/main/registry" \
  --value "237156726900.dkr.ecr.us-east-1.amazonaws.com" \
  --type "String" \
  --region us-east-1 \
  --overwrite
```

**Verify Parameter:**
```bash
aws ssm get-parameter \
  --name "/account/ecr/main/registry" \
  --region us-east-1 \
  --query 'Parameter.Value' \
  --output text
```

---

## Implementation Checklist

### Phase 1: Infrastructure Changes
- [ ] Update `gp-nova-payroll-engine-infra/template.yaml`:
  - [ ] Replace `BackendRepoUri`/`BackendImageTag` parameters with `RepositoryPrefix`/`DeploymentVersion`/`Environment`
  - [ ] Update `BackendTaskDefinition` image reference pattern
  - [ ] Update `WebappTaskDefinition` image reference pattern
  - [ ] Update `ConsoleTaskDefinition` image reference pattern
- [ ] Update `gp-nova-payroll-engine-infra/samconfig.toml`:
  - [ ] Add environment-specific parameter overrides
  - [ ] Remove old `BackendRepoUri`/`BackendImageTag` references
- [ ] Update infrastructure deploy workflows:
  - [ ] Remove "Derive ECR Repo URIs" step
  - [ ] Update `sam deploy` parameter overrides
  - [ ] Test deployment to dev environment

### Phase 2: Promotion Workflow
- [ ] Create `payroll-engine-backend/.github/workflows/promote.yaml`
- [ ] Test promotion from dev to test
- [ ] Verify image tags in ECR after promotion

### Phase 3: AWS Configuration
- [ ] Verify/create SSM parameter `/account/ecr/main/registry`
- [ ] Verify IAM permissions for promotion workflow
- [ ] Test SSM parameter access from GitHub Actions

### Phase 4: Testing & Validation
- [ ] Test complete flow: build → promote → deploy
- [ ] Verify image tags match expected pattern
- [ ] Verify ECS services use promoted images
- [ ] Document any issues or deviations

---

## Image Tag Format Examples

### Build/Publish Phase
- **Tag Format**: `build-{commitSHA}`
- **Example**: `build-b744cca`
- **Location**: Account `237156726900`, Repository `payroll-engine/payroll-engine-backend`

### After Promotion
- **Dev**: `payroll-engine-backend-dev-1.0.0`
- **Test**: `payroll-engine-backend-test-1.0.0`
- **Prod**: `payroll-engine-backend-prod-1.0.0`

### Full Image URIs
- **Build**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`
- **Promoted Dev**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:payroll-engine-backend-dev-1.0.0`
- **Promoted Test**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:payroll-engine-backend-test-1.0.0`

---

## Migration Considerations

### Breaking Changes
1. **Existing Deployments**: Current deployments using `BackendRepoUri`/`BackendImageTag` will need to be migrated
2. **Tag Format**: Images must be re-tagged using promotion workflow
3. **Workflow Dependencies**: Infrastructure deploy workflows must be updated simultaneously

### Rollback Plan
1. Keep old parameter structure as optional (backward compatible)
2. Or maintain separate workflow branches for migration period
3. Document rollback steps in case of issues

### Testing Strategy
1. **Dev Environment First**: Test all changes in dev environment
2. **Gradual Rollout**: Deploy to test, then prod after validation
3. **Monitor**: Watch for ECS service deployment issues
4. **Verify**: Confirm images are pulled correctly from ECR

---

## Troubleshooting

### Common Issues

**Issue**: SSM parameter not found
- **Solution**: Verify parameter exists and IAM role has `ssm:GetParameter` permission

**Issue**: Image tag not found after promotion
- **Solution**: Verify promotion workflow completed successfully, check ECR for new tag

**Issue**: ECS task fails to pull image
- **Solution**: Verify ECS task execution role has ECR permissions for account `237156726900`

**Issue**: Template validation fails
- **Solution**: Verify all parameters are provided, check parameter types match

---

## References

- **Exemplar Repository**: `gp-nova/exp-container-image-exemplar`
- **Exemplar Template**: `exp-container-image-exemplar/template.yaml` (lines 109-115, 288-294)
- **Exemplar SAM Config**: `exp-container-image-exemplar/samconfig.toml`
- **Promotion Workflow**: `exp-container-image-exemplar/.github/workflows/promote.yaml`

---

## Questions or Issues?

If you encounter issues during implementation:
1. Check the exemplar repository for reference patterns
2. Verify SSM parameter and IAM permissions
3. Review GitHub Actions workflow logs
4. Test in dev environment first before promoting to test/prod

