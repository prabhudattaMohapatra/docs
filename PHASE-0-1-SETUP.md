# Phase 0 & 1 Setup Checklist

Quick setup guide for GitHub Actions workflows (Build, Publish, Deploy).

## Phase 0: Prerequisites Setup

### ✅ Step 1: Verify GitHub Repository Access
- [ ] You have write access to `payroll-engine-backend` repository
- [ ] You can access repository settings

### ✅ Step 2: Create GitHub Environment
1. Navigate to: `https://github.com/gp-nova/payroll-engine-backend/settings/environments`
   - Or: Repository → Settings → Environments

2. Create `dev` environment:
   - [ ] Click "New environment"
   - [ ] Name: `dev`
   - [ ] Protection rules: None (for dev)
   - [ ] Save environment

3. (Optional) Create `test` and `prod` environments:
   - [ ] `test` environment created
   - [ ] `prod` environment created with protection rules

### ✅ Step 3: Configure GitHub Secrets (Optional)
1. Navigate to: `https://github.com/gp-nova/payroll-engine-backend/settings/secrets/actions`
   - Or: Repository → Settings → Secrets and variables → Actions

2. Add Snyk Token (optional):
   - [ ] Click "New repository secret"
   - [ ] Name: `SNYK_TOKEN`
   - [ ] Value: (Get from Snyk dashboard, or skip if not using Snyk)
   - [ ] Note: Workflow works without this, but won't have security scanning

### ✅ Step 4: Verify OIDC Setup
- [ ] OIDC provider is configured in AWS account
- [ ] GitHub repository can assume OIDC roles
- [ ] Test OIDC authentication (workflow will test this)

### ✅ Step 5: Verify AWS Resources
Run these commands to verify resources exist:

```bash
# Verify ECR repository
aws ecr describe-repositories \
  --repository-names payroll-engine/backend \
  --region us-east-1 \
  --query 'repositories[0].repositoryUri' \
  --output text
# Expected: 258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/backend

# Verify ECS cluster
aws ecs describe-clusters \
  --clusters payroll-engine-cluster \
  --region us-east-1 \
  --query 'clusters[0].clusterName' \
  --output text
# Expected: payroll-engine-cluster

# Verify ECS service
aws ecs describe-services \
  --cluster payroll-engine-cluster \
  --services payroll-engine-backend \
  --region us-east-1 \
  --query 'services[0].serviceName' \
  --output text
# Expected: payroll-engine-backend
```

- [ ] ECR repository exists and is accessible
- [ ] ECS cluster exists
- [ ] ECS service exists

### ✅ Step 6: Test ECR Login
```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  258215414239.dkr.ecr.us-east-1.amazonaws.com
# Expected: Login Succeeded
```

- [ ] ECR login works

---

## Phase 1: Workflow Setup and Testing

### ✅ Step 1: Verify Workflow Files Created
Check that these files exist:
- [ ] `.github/workflows/main-build.yaml`
- [ ] `.github/workflows/pull-request.yaml`
- [ ] `.github/workflows/deploy.yaml`
- [ ] `.github/workflows/README.md`

### ✅ Step 2: Commit Workflows to Repository
```bash
cd /Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-backend

# Check status
git status

# Add workflow files
git add .github/workflows/

# Commit
git commit -m "Add GitHub Actions workflows for build, publish, and deploy"

# Push
git push origin main
```

- [ ] Workflows committed to repository
- [ ] Workflows pushed to remote

### ✅ Step 3: Test Build Workflow

1. **Trigger Build Workflow**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Main Build" workflow
   - Click "Run workflow" button
   - Select branch: `main`
   - Click "Run workflow"

2. **Monitor Execution**:
   - [ ] Workflow starts successfully
   - [ ] "Checkout code" step completes
   - [ ] "Assume OIDC Role" step completes
   - [ ] "Set version" step completes
   - [ ] "Build container images" step completes
   - [ ] "Publish container images" step completes
   - [ ] "Verify image in ECR" step completes
   - [ ] Workflow completes successfully (green checkmark)

3. **Verify Image in ECR**:
   ```bash
   # Get the latest image tag (git commit SHA)
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
     --region us-east-1 \
     --query 'imageDetails[0].{tags:imageTags,pushed:imagePushedAt,size:imageSizeInBytes}' \
     --output table
   ```

   - [ ] Image exists in ECR
   - [ ] Image tag matches git commit SHA format
   - [ ] Image has correct metadata

### ✅ Step 4: Test Deploy Workflow

1. **Get Image Tag**:
   ```bash
   # Get latest image tag from ECR
   aws ecr describe-images \
     --repository-name payroll-engine/backend \
     --region us-east-1 \
     --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
     --output text
   ```
   Note the tag (e.g., `abc1234`)

2. **Trigger Deploy Workflow**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Deploy to ECS" workflow
   - Click "Run workflow" button
   - Select:
     - Environment: `dev`
     - Image tag: (use the tag from step 1, or leave empty for latest)
     - Version: (leave empty)
   - Click "Run workflow"

3. **Monitor Execution**:
   - [ ] Workflow starts successfully
   - [ ] "Assume OIDC Role" step completes
   - [ ] "Login to Amazon ECR" step completes
   - [ ] "Determine image tag" step completes
   - [ ] "Verify image exists in ECR" step completes
   - [ ] "Get current task definition" step completes
   - [ ] "Update task definition with new image" step completes
   - [ ] "Update ECS service" step completes
   - [ ] "Wait for service to stabilize" step completes
   - [ ] "Verify deployment" step completes
   - [ ] Workflow completes successfully

4. **Verify ECS Service Updated**:
   ```bash
   # Check current task definition image
   aws ecs describe-task-definition \
     --task-definition payroll-engine-backend \
     --region us-east-1 \
     --query 'taskDefinition.containerDefinitions[0].image' \
     --output text
   # Should show: 258215414239.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/backend:<tag>
   
   # Check ECS service status
   aws ecs describe-services \
     --cluster payroll-engine-cluster \
     --services payroll-engine-backend \
     --region us-east-1 \
     --query 'services[0].{status:status,runningCount:runningCount,desiredCount:desiredCount,taskDefinition:taskDefinition}' \
     --output json
   ```

   - [ ] Task definition updated with new image
   - [ ] ECS service is running
   - [ ] Service has correct task definition

### ✅ Step 5: Test Pull Request Workflow (Optional)

1. **Create Test PR**:
   - Create a new branch
   - Make a small change (e.g., update README)
   - Create pull request to `main`

2. **Verify PR Workflow**:
   - [ ] PR workflow triggers automatically
   - [ ] Build step completes
   - [ ] No publish step (as expected)
   - [ ] Workflow completes successfully

### ✅ Step 6: Test End-to-End Flow

1. **Make a Code Change**:
   ```bash
   # Make a small change
   echo "# Test change $(date)" >> README.md
   git add README.md
   git commit -m "Test: Trigger automated build"
   git push origin main
   ```

2. **Monitor Pipeline**:
   - [ ] Build workflow triggers automatically
   - [ ] Build completes successfully
   - [ ] Image is published to ECR
   - [ ] (Optional) Deploy workflow triggers if enabled

3. **Verify Results**:
   - [ ] New image appears in ECR
   - [ ] Image tag matches new commit SHA
   - [ ] (If auto-deploy enabled) ECS service updates

---

## Troubleshooting

### Build Workflow Fails

**Issue**: OIDC authentication fails
- **Solution**: Verify OIDC provider is configured in AWS
- **Check**: Repository settings → Actions → General → Workflow permissions

**Issue**: Build fails
- **Solution**: Check Dockerfile syntax
- **Check**: Verify Dockerfile builds locally first

**Issue**: Publish fails
- **Solution**: Verify ECR repository exists and has correct permissions
- **Check**: ECR repository name matches exactly

### Deploy Workflow Fails

**Issue**: Image tag not found
- **Solution**: Verify image was built and pushed successfully
- **Check**: List images in ECR to verify tag exists

**Issue**: ECS service update fails
- **Solution**: Verify ECS service exists and has correct permissions
- **Check**: IAM role has permissions to update ECS services

**Issue**: Task definition update fails
- **Solution**: Check task definition JSON format
- **Check**: Verify jq is available in workflow runner

---

## Next Steps

After completing Phase 0 and 1:

1. ✅ **Monitor workflows** for a few days
2. ✅ **Collect feedback** from team
3. ✅ **Enable auto-deploy** (uncomment deploy job in main-build.yaml) if desired
4. ✅ **Document** any customizations needed
5. ✅ **Plan Phase 2** (infrastructure template updates, if needed)

---

## Success Criteria

Phase 0 & 1 are complete when:

- [ ] ✅ All workflows are created and committed
- [ ] ✅ Build workflow executes successfully
- [ ] ✅ Images are published to ECR
- [ ] ✅ Deploy workflow updates ECS service
- [ ] ✅ Application runs correctly with new image
- [ ] ✅ Team can use workflows for deployments
- [ ] ✅ Manual scripts still work for local development

---

## Quick Reference

### Workflow Files
- **Build**: `.github/workflows/main-build.yaml`
- **Deploy**: `.github/workflows/deploy.yaml`
- **PR Build**: `.github/workflows/pull-request.yaml`

### Key Commands
```bash
# List images in ECR
aws ecr list-images --repository-name payroll-engine/backend --region us-east-1

# Check ECS service
aws ecs describe-services --cluster payroll-engine-cluster --services payroll-engine-backend --region us-east-1

# Check task definition
aws ecs describe-task-definition --task-definition payroll-engine-backend --region us-east-1
```

### GitHub URLs
- **Actions**: `https://github.com/gp-nova/payroll-engine-backend/actions`
- **Environments**: `https://github.com/gp-nova/payroll-engine-backend/settings/environments`
- **Secrets**: `https://github.com/gp-nova/payroll-engine-backend/settings/secrets/actions`

