# CodeArtifact NuGet Package Push Commands

This document contains commands to push `PayrollEngine.Core` NuGet packages to two different CodeArtifact repositories.

## Repository Details

### Repository 1: payroll-engine (Cross-Account)
- **Repository Name**: `payroll-engine`
- **Domain**: `gp-prod`
- **Domain Owner**: `237156726900` (different account)
- **Administrator Account**: `258215414239` (your account)
- **Region**: `us-east-1`
- **Repository Endpoint**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/nuget/payroll-engine/`

### Repository 2: payroll-engine-test (Your Account)
- **Repository Name**: `payroll-engine-test`
- **Domain**: `gp-prod`
- **Domain Owner**: `258215414239` (your account)
- **Region**: `us-east-1`
- **Repository Endpoint**: `https://gp-prod-258215414239.d.codeartifact.us-east-1.amazonaws.com/nuget/payroll-engine-test/`

---

## Push to Repository 1: payroll-engine (Cross-Account)

### Step 1: Authenticate with CodeArtifact
```bash
aws codeartifact login \
  --tool dotnet \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --repository payroll-engine \
  --region us-east-1
```

### Step 2: Push the Package
```bash
dotnet nuget push ~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg \
  --source gp-prod/payroll-engine \
  --skip-duplicate
```

**Note**: The `--domain-owner` parameter is required because the domain is owned by a different AWS account (`237156726900`).

---

## Push to Repository 2: payroll-engine-test (Your Account)

### Step 1: Authenticate with CodeArtifact
```bash
aws codeartifact login \
  --tool dotnet \
  --domain gp-prod \
  --repository payroll-engine-test \
  --region us-east-1
```

**Note**: No `--domain-owner` parameter needed since the domain is owned by your current account.

### Step 2: Push the Package
```bash
dotnet nuget push ~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg \
  --source gp-prod/payroll-engine-test \
  --skip-duplicate
```

---

## Alternative: Manual Authentication Method

If you prefer to configure NuGet sources manually instead of using `aws codeartifact login`:

### For Repository 1 (Cross-Account):
```bash
# Get authorization token
TOKEN=$(aws codeartifact get-authorization-token \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --region us-east-1 \
  --query authorizationToken \
  --output text)

# Add NuGet source
dotnet nuget add source https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/nuget/payroll-engine/ \
  --name gp-prod/payroll-engine \
  --username aws \
  --password $TOKEN \
  --store-password-in-clear-text

# Push package
dotnet nuget push ~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg \
  --source gp-prod/payroll-engine \
  --skip-duplicate
```

### For Repository 2 (Your Account):
```bash
# Get authorization token
TOKEN=$(aws codeartifact get-authorization-token \
  --domain gp-prod \
  --region us-east-1 \
  --query authorizationToken \
  --output text)

# Add NuGet source
dotnet nuget add source https://gp-prod-258215414239.d.codeartifact.us-east-1.amazonaws.com/nuget/payroll-engine-test/ \
  --name gp-prod/payroll-engine-test \
  --username aws \
  --password $TOKEN \
  --store-password-in-clear-text

# Push package
dotnet nuget push ~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg \
  --source gp-prod/payroll-engine-test \
  --skip-duplicate
```

---

## Reference: exp-container-image-exemplar Pipeline

The `exp-container-image-exemplar` repository uses the following CodeArtifact configuration:

### npm Repository (for dependencies):
- **Repository Name**: `training`
- **Domain**: `gp-prod`
- **Domain Owner**: `237156726900`
- **Repository Endpoint**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/training/`

**Note**: The pipeline publishes **container images to ECR**, not to CodeArtifact. CodeArtifact is only used for pulling npm dependencies during the build process.

The authentication command used in the pipeline:
```bash
aws codeartifact get-authorization-token \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --region us-east-1 \
  --query authorizationToken \
  --output text
```

---

## Important Notes

1. **Token Expiration**: Authorization tokens expire after 12 hours. You'll need to run `aws codeartifact login` again to refresh the token.

2. **Cross-Account Access**: For Repository 1, you must specify `--domain-owner 237156726900` because the domain is owned by a different AWS account, even though your account (`258215414239`) is the administrator.

3. **Skip Duplicate**: The `--skip-duplicate` flag prevents errors if the package version already exists in the repository.

4. **Package Location**: Update the package path (`~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg`) to match your actual package file location and version.

5. **Viewing Packages**: 
   - Repository 1 packages may not be visible in AWS Console when logged in as account `258215414239` due to cross-account domain ownership
   - Repository 2 packages will be visible in AWS Console when logged in as account `258215414239`

---

## Verify Package Push

### Check packages in Repository 1:
```bash
aws codeartifact list-packages \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --repository payroll-engine \
  --format nuget \
  --region us-east-1
```

### Check packages in Repository 2:
```bash
aws codeartifact list-packages \
  --domain gp-prod \
  --repository payroll-engine-test \
  --format nuget \
  --region us-east-1
```

### Check package versions:
```bash
# Repository 1
aws codeartifact list-package-versions \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --repository payroll-engine \
  --format nuget \
  --package payrollengine.core \
  --region us-east-1

# Repository 2
aws codeartifact list-package-versions \
  --domain gp-prod \
  --repository payroll-engine-test \
  --format nuget \
  --package payrollengine.core \
  --region us-east-1
```

