# OIDC Authentication Setup Required

## Issue
The workflow is failing with error: `Not authorized to perform sts:AssumeRoleWithWebIdentity`

## Root Cause
The IAM role's trust policy does not include this repository (`payroll-engine-backend`) in its allowed repositories list.

## Solution
The IAM role `258215414239-gh-action-oid` trust policy needs to be updated to allow this repository.

### Required IAM Trust Policy Update

The trust policy for role `arn:aws:iam::258215414239:role/258215414239-gh-action-oid` must include a condition that allows this repository:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::258215414239:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:gp-nova/payroll-engine-backend:*",
            "repo:gp-nova/payroll-engine-backend:pull_request",
            "repo:gp-nova/payroll-engine-backend:ref:refs/heads/main",
            "repo:gp-nova/payroll-engine-backend:ref:refs/heads/github-workflows"
          ]
        }
      }
    }
  ]
}
```

### Steps to Fix

1. **Contact AWS Administrator** or DevOps team
2. **Provide them with**:
   - Repository: `gp-nova/payroll-engine-backend`
   - IAM Role: `258215414239-gh-action-oid`
   - Account ID: `258215414239`
   - Region: `us-east-1`

3. **They need to**:
   - Update the IAM role trust policy to include this repository
   - Or add this repository to an existing condition that allows multiple repositories

### Verify Repository Name

If the repository is under a different organization or has a different name, update the condition accordingly:
- Organization: `gp-nova` (or your actual GitHub org)
- Repository: `payroll-engine-backend` (or your actual repo name)

The full repository path should be: `repo:ORGANIZATION/REPOSITORY:*`

### Alternative: Check Existing Trust Policy

If other repositories (like `gp-nova-payroll-engine-infra`) are working, check their trust policy to see the pattern and add this repository to the same condition.

### Verification

After the IAM role trust policy is updated:
1. Re-run the workflow
2. OIDC authentication should succeed
3. The workflow should proceed to build/publish steps

## Current Configuration

- **IAM Role**: `258215414239-gh-action-oid`
- **Execution Role**: `258215414239-gh-execution-oid`
- **Account ID**: `258215414239`
- **Region**: `us-east-1`
- **Environment**: `dev`
- **Roles Config**: `.gp/roles.yaml` ✅ (exists and correct)

## Workflow Configuration Status

✅ `.gp/roles.yaml` - Created and configured correctly  
✅ `environment: dev` - Set at job level in all workflows  
✅ `permissions: id-token: write` - Set correctly  
✅ OIDC action usage - Matches working workflows  

**Only remaining issue**: IAM role trust policy needs to include this repository.

