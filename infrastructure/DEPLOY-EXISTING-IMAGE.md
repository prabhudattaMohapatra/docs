# Deploy Existing Image to ECS - Step by Step Guide

This guide outlines the steps to deploy an already-pushed container image to your ECS service using GitHub Actions.

## Current Situation

**Image Location:**
- **Registry**: `237156726900.dkr.ecr.us-east-1.amazonaws.com`
- **Repository**: `payroll-engine/payroll-engine-backend`
- **Tag**: `build-b744cca` (or your specific tag)
- **Full URI**: `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`

**ECS Service:**
- **Cluster**: `payroll-engine-cluster`
- **Service**: `feature-payroll-engine-backend`
- **Account**: `258215414239`
- **Region**: `us-east-1`

## Issue Identified

The current `deploy.yaml` workflow is configured for:
- **ECR Account**: `258215414239` (from `AWS_ACCOUNT_ID` env var)
- **ECR Repository**: `payroll-engine/backend` (from `ECR_REPOSITORY` env var)

But the image is actually in:
- **ECR Account**: `237156726900` (shared ECR account)
- **ECR Repository**: `payroll-engine/payroll-engine-backend` (matches template.yaml imageName)

## Required Changes to Deploy Workflow

### Step 1: Update `deploy.yaml` Environment Variables

**File**: `payroll-engine-backend/.github/workflows/deploy.yaml`

**Current Configuration (lines 40-45):**
```yaml
env:
  AWS_REGION: us-east-1
  AWS_ACCOUNT_ID: 258215414239
  ECR_REPOSITORY: payroll-engine/backend
  ECS_CLUSTER: payroll-engine-cluster
  ECS_SERVICE: payroll-engine-backend
```

**Needs to Change To:**
```yaml
env:
  AWS_REGION: us-east-1
  AWS_ACCOUNT_ID: 258215414239  # Keep this for ECS operations
  ECR_ACCOUNT_ID: 237156726900  # Add this for ECR image location
  ECR_REPOSITORY: payroll-engine/payroll-engine-backend  # Update to match actual repo
  ECS_CLUSTER: payroll-engine-cluster
  ECS_SERVICE: feature-payroll-engine-backend
```

### Step 2: Update ECR Login Step

**Current (lines 68-70):**
```yaml
- name: Login to Amazon ECR
  id: login-ecr
  uses: aws-actions/amazon-ecr-login@v2
```

**Needs to Change To:**
```yaml
- name: Login to Amazon ECR (Shared Account)
  id: login-ecr
  uses: aws-actions/amazon-ecr-login@v2
  with:
    registry-id: ${{ env.ECR_ACCOUNT_ID }}
```

**Note**: The `aws-actions/amazon-ecr-login@v2` action will automatically use the AWS credentials from the OIDC role. However, the OIDC role in account `258215414239` needs cross-account permissions to pull images from account `237156726900`.

### Step 3: Update Image Tag Determination Step

**Current (lines 72-94):**
```yaml
- name: Determine image tag
  id: image-tag
  run: |
    if [ -z "${{ inputs.image-tag }}" ]; then
      # Get latest image tag from ECR
      echo "Fetching latest image tag from ECR..."
      TAG=$(aws ecr describe-images \
        --repository-name ${{ env.ECR_REPOSITORY }} \
        --region ${{ env.AWS_REGION }} \
        --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
        --output text)
```

**Needs to Change To:**
```yaml
- name: Determine image tag
  id: image-tag
  run: |
    if [ -z "${{ inputs.image-tag }}" ]; then
      # Get latest image tag from ECR in shared account
      echo "Fetching latest image tag from ECR..."
      TAG=$(aws ecr describe-images \
        --repository-name ${{ env.ECR_REPOSITORY }} \
        --region ${{ env.AWS_REGION }} \
        --registry-id ${{ env.ECR_ACCOUNT_ID }} \
        --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageTags[0]' \
        --output text)
```

### Step 4: Update Image Verification Step

**Current (lines 96-106):**
```yaml
- name: Verify image exists in ECR
  run: |
    IMAGE_URI="${{ env.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/${{ env.ECR_REPOSITORY }}:${{ steps.image-tag.outputs.tag }}"
    echo "Verifying image exists: $IMAGE_URI"
    
    aws ecr describe-images \
      --repository-name ${{ env.ECR_REPOSITORY }} \
      --image-ids imageTag=${{ steps.image-tag.outputs.tag }} \
      --region ${{ env.AWS_REGION }} \
      --query 'imageDetails[0].{tags:imageTags,pushed:imagePushedAt}' \
      --output table
```

**Needs to Change To:**
```yaml
- name: Verify image exists in ECR
  run: |
    IMAGE_URI="${{ env.ECR_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/${{ env.ECR_REPOSITORY }}:${{ steps.image-tag.outputs.tag }}"
    echo "Verifying image exists: $IMAGE_URI"
    
    aws ecr describe-images \
      --repository-name ${{ env.ECR_REPOSITORY }} \
      --image-ids imageTag=${{ steps.image-tag.outputs.tag }} \
      --region ${{ env.AWS_REGION }} \
      --registry-id ${{ env.ECR_ACCOUNT_ID }} \
      --query 'imageDetails[0].{tags:imageTags,pushed:imagePushedAt,size:imageSizeInBytes,digest:imageDigest}' \
      --output table
```

### Step 5: Update Task Definition Image URI

**Current (line 125):**
```yaml
IMAGE_URI="${{ env.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/${{ env.ECR_REPOSITORY }}:${{ steps.image-tag.outputs.tag }}"
```

**Needs to Change To:**
```yaml
IMAGE_URI="${{ env.ECR_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/${{ env.ECR_REPOSITORY }}:${{ steps.image-tag.outputs.tag }}"
```

---

## Prerequisites Before Deployment

### 1. Verify ECR Image Exists

**Command to verify:**
```bash
aws ecr describe-images \
  --repository-name payroll-engine/payroll-engine-backend \
  --image-ids imageTag=build-b744cca \
  --region us-east-1 \
  --registry-id 237156726900
```

**Expected Output:**
- Should return image details including tags, pushed date, size, and digest
- If it fails, the image may not exist or you may not have permissions

### 2. Verify ECS Service Exists

**Command to verify:**
```bash
aws ecs describe-services \
  --cluster payroll-engine-cluster \
  --services feature-payroll-engine-backend \
  --region us-east-1
```

**Expected Output:**
- Should return service details including status, task definition, running count
- If it fails, the service may not exist or you may not have permissions

### 3. Verify Cross-Account ECR Permissions

**Critical**: The ECS task execution role in account `258215414239` needs permissions to pull images from account `237156726900`.

**Check ECS Task Execution Role:**
```bash
# Get the task execution role from the current task definition
aws ecs describe-task-definition \
  --task-definition feature-payroll-engine-backend \
  --region us-east-1 \
  --query 'taskDefinition.executionRoleArn' \
  --output text
```

**Required IAM Policy:**
The task execution role needs:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    }
  ]
}
```

**Note**: ECR permissions are typically account-wide (Resource: "*"), so cross-account access should work if:
1. The ECR repository in account `237156726900` allows cross-account access, OR
2. The task execution role has the standard ECR permissions (which work across accounts)

### 4. Verify OIDC Role Permissions

**Check**: The GitHub Actions OIDC role (`258215414239-gh-action-oid`) needs:
- `ecr:DescribeImages` permission for account `237156726900`
- `ecr:GetAuthorizationToken` permission for account `237156726900`
- `ecs:DescribeServices`, `ecs:DescribeTaskDefinition`, `ecs:RegisterTaskDefinition`, `ecs:UpdateService` permissions in account `258215414239`

---

## Deployment Steps

### Option 1: Manual Deployment via GitHub Actions UI

1. **Navigate to GitHub Actions**:
   - Go to: `https://github.com/gp-nova/payroll-engine-backend/actions`
   - Click on "Deploy to ECS" workflow

2. **Run Workflow**:
   - Click "Run workflow" button
   - Select:
     - **Environment**: `dev` (or your target environment)
     - **Image tag**: `build-b744cca` (or your specific tag)
     - **Version**: (optional, can leave empty)
   - Click "Run workflow"

3. **Monitor Execution**:
   - Watch the workflow steps execute
   - Check for any errors in:
     - OIDC authentication
     - ECR image verification
     - Task definition update
     - ECS service update

### Option 2: Deployment via GitHub CLI

**Command:**
```bash
gh workflow run deploy.yaml \
  --repo gp-nova/payroll-engine-backend \
  -f environment=dev \
  -f image-tag=build-b744cca
```

**Monitor:**
```bash
gh run watch \
  --repo gp-nova/payroll-engine-backend
```

---

## Expected Workflow Execution Flow

1. **Checkout code** ✅
2. **Assume OIDC Role** ✅
   - Authenticates to AWS account `258215414239`
3. **Login to Amazon ECR** ✅
   - Logs into ECR registry `237156726900`
4. **Determine image tag** ✅
   - Uses provided tag or fetches latest
   - Verifies image exists in ECR
5. **Get current task definition** ✅
   - Fetches existing task definition from ECS
6. **Update task definition** ✅
   - Creates new task definition revision
   - Updates image URI to point to `237156726900` account
7. **Update ECS service** ✅
   - Updates service with new task definition
   - Forces new deployment
8. **Wait for service to stabilize** ✅
   - Waits for ECS service to reach stable state
9. **Verify deployment** ✅
   - Shows deployment summary and service status

---

## Troubleshooting

### Issue 1: ECR Image Not Found

**Error**: `An error occurred (ImageNotFoundException) when calling the DescribeImages operation`

**Solutions**:
- Verify the image tag exists: `build-b744cca`
- Check the repository name: `payroll-engine/payroll-engine-backend`
- Verify you're checking the correct account: `237156726900`
- Ensure the image was pushed successfully

### Issue 2: Cross-Account ECR Access Denied

**Error**: `User: arn:aws:sts::258215414239:assumed-role/... is not authorized to perform: ecr:DescribeImages`

**Solutions**:
- Verify OIDC role has ECR permissions for account `237156726900`
- Check if ECR repository allows cross-account access
- Verify IAM role trust policy includes the repository

### Issue 3: ECS Task Cannot Pull Image

**Error**: Task fails to start, logs show "CannotPullContainerError"

**Solutions**:
- Verify ECS task execution role has ECR permissions
- Check if the image URI is correct in the task definition
- Verify cross-account ECR access is allowed
- Check ECS task execution role IAM policy

### Issue 4: ECS Service Not Found

**Error**: `Service not found`

**Solutions**:
- Verify cluster name: `payroll-engine-cluster`
- Verify service name: `feature-payroll-engine-backend`
- Check if service exists in the correct region: `us-east-1`
- Verify OIDC role has ECS permissions

---

## Verification After Deployment

### 1. Check ECS Service Status

```bash
aws ecs describe-services \
  --cluster payroll-engine-cluster \
  --services feature-payroll-engine-backend \
  --region us-east-1 \
  --query 'services[0].{status:status,runningCount:runningCount,desiredCount:desiredCount,taskDefinition:taskDefinition}' \
  --output table
```

**Expected**:
- `status`: `ACTIVE`
- `runningCount`: Should match `desiredCount`
- `taskDefinition`: Should show new revision with image from account `237156726900`

### 2. Check Running Tasks

```bash
aws ecs list-tasks \
  --cluster payroll-engine-cluster \
  --service-name feature-payroll-engine-backend \
  --region us-east-1
```

### 3. Check Task Definition Image

```bash
aws ecs describe-task-definition \
  --task-definition feature-payroll-engine-backend \
  --region us-east-1 \
  --query 'taskDefinition.containerDefinitions[0].image' \
  --output text
```

**Expected**: Should show `237156726900.dkr.ecr.us-east-1.amazonaws.com/payroll-engine/payroll-engine-backend:build-b744cca`

### 4. Check Task Logs

```bash
# Get task ID
TASK_ID=$(aws ecs list-tasks \
  --cluster payroll-engine-cluster \
  --service-name feature-payroll-engine-backend \
  --region us-east-1 \
  --query 'taskArns[0]' \
  --output text | cut -d'/' -f3)

# Get log stream
aws logs describe-log-streams \
  --log-group-name /ecs/payroll-engine-backend \
  --region us-east-1 \
  --order-by LastEventTime \
  --descending \
  --max-items 1 \
  --query 'logStreams[0].logStreamName' \
  --output text

# Get logs
aws logs get-log-events \
  --log-group-name /ecs/payroll-engine-backend \
  --log-stream-name <log-stream-name> \
  --region us-east-1 \
  --limit 50
```

---

## Summary

**Key Points:**
1. Image is in account `237156726900`, repository `payroll-engine/payroll-engine-backend`
2. ECS service is in account `258215414239`, cluster `payroll-engine-cluster`, service `feature-payroll-engine-backend`
3. Deploy workflow needs updates to reference correct ECR account and repository
4. Cross-account ECR permissions must be configured
5. After updates, workflow can be triggered manually with image tag `build-b744cca`

**Next Steps:**
1. Update `deploy.yaml` with changes outlined above
2. Verify prerequisites (ECR image, ECS service, permissions)
3. Run the deployment workflow
4. Verify deployment success

