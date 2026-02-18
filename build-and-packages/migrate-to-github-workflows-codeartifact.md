# Migration Guide: Local Builds to GitHub Workflows + CodeArtifact

Guide to migrate from local build steps to GitHub Actions workflows with AWS CodeArtifact (without upstream connection to nuget.org).

---

## Overview

This guide helps you transition from local manual builds to automated GitHub Actions workflows using AWS CodeArtifact for package storage.

**Key Constraint**: CodeArtifact cannot have upstream connection to nuget.org, so third-party packages must be explicitly added to CodeArtifact or restored from nuget.org during builds.

---

## Prerequisites

- GitHub repository access
- AWS account with CodeArtifact permissions
- AWS CLI configured
- GitHub Actions enabled for repositories

---

## Step 1: Setup AWS CodeArtifact

### 1.1 Create CodeArtifact Domain and Repository

```bash
# Set variables
export DOMAIN_NAME="payroll-engine"
export REPO_NAME="nuget-packages"
export AWS_REGION="us-east-1"

# Create domain
aws codeartifact create-domain \
    --domain $DOMAIN_NAME \
    --region $AWS_REGION

# Create repository
aws codeartifact create-repository \
    --domain $DOMAIN_NAME \
    --repository $REPO_NAME \
    --description "PayrollEngine NuGet packages" \
    --region $AWS_REGION

# Get repository endpoint
export REPO_ENDPOINT=$(aws codeartifact get-repository-endpoint \
    --domain $DOMAIN_NAME \
    --repository $REPO_NAME \
    --format nuget \
    --region $AWS_REGION \
    --query repositoryEndpoint \
    --output text)

echo "Repository endpoint: $REPO_ENDPOINT"
```

### 1.2 Create IAM Policy for CodeArtifact

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "codeartifact:GetAuthorizationToken",
        "codeartifact:ReadFromRepository",
        "codeartifact:PublishPackageVersion",
        "codeartifact:PutPackageMetadata"
      ],
      "Resource": "arn:aws:codeartifact:*:*:domain/payroll-engine"
    },
    {
      "Effect": "Allow",
      "Action": [
        "codeartifact:GetAuthorizationToken"
      ],
      "Resource": "*"
    }
  ]
}
```

### 1.3 Create GitHub Secrets

In each GitHub repository, add these secrets:

- `AWS_ACCESS_KEY_ID`: AWS access key
- `AWS_SECRET_ACCESS_KEY`: AWS secret key
- `AWS_REGION`: AWS region (e.g., `us-east-1`)
- `CODEARTIFACT_DOMAIN`: `payroll-engine`
- `CODEARTIFACT_REPO`: `nuget-packages`
- `CODEARTIFACT_REPO_ENDPOINT`: The repository endpoint URL

---

## Step 2: Create GitHub Workflow for Building Packages

### 2.1 PayrollEngine.Core Workflow

**File**: `.github/workflows/build-and-publish-package.yml` in `PayrollEngine.Core` repository

```yaml
name: Build and Publish Package

on:
  push:
    branches: [main]
    paths:
      - 'Core/**'
      - 'Directory.Build.props'
  workflow_dispatch:

jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write  # For OIDC
    
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      
      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '10.0.x'
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ secrets.AWS_REGION }}
      
      - name: Get CodeArtifact Token
        id: get-token
        run: |
          TOKEN=$(aws codeartifact get-authorization-token \
            --domain ${{ secrets.CODEARTIFACT_DOMAIN }} \
            --query authorizationToken \
            --output text)
          echo "token=$TOKEN" >> $GITHUB_OUTPUT
      
      - name: Configure NuGet for CodeArtifact
        run: |
          dotnet nuget add source ${{ secrets.CODEARTIFACT_REPO_ENDPOINT }} \
            --name codeartifact \
            --username aws \
            --password ${{ steps.get-token.outputs.token }} \
            --store-password-in-clear-text
      
      - name: Add nuget.org source
        run: |
          dotnet nuget add source https://api.nuget.org/v3/index.json \
            --name nuget-org
      
      - name: Restore dependencies
        run: dotnet restore PayrollEngine.Core.sln --source nuget-org
      
      - name: Build
        run: dotnet build PayrollEngine.Core.sln -c Release --no-restore
      
      - name: Pack
        run: dotnet pack PayrollEngine.Core.sln -c Release --no-build --output ./nupkg
      
      - name: Publish to CodeArtifact
        run: |
          dotnet nuget push ./nupkg/*.nupkg \
            --source codeartifact \
            --skip-duplicate
```

### 2.2 PayrollEngine.Client.Core Workflow

Similar workflow, but depends on PayrollEngine.Core:

```yaml
name: Build and Publish Package

on:
  push:
    branches: [main]
    paths:
      - 'Client.Core/**'
      - 'Directory.Build.props'
  workflow_dispatch:

jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      
      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '10.0.x'
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ secrets.AWS_REGION }}
      
      - name: Get CodeArtifact Token
        id: get-token
        run: |
          TOKEN=$(aws codeartifact get-authorization-token \
            --domain ${{ secrets.CODEARTIFACT_DOMAIN }} \
            --query authorizationToken \
            --output text)
          echo "token=$TOKEN" >> $GITHUB_OUTPUT
      
      - name: Configure NuGet Sources
        run: |
          dotnet nuget add source ${{ secrets.CODEARTIFACT_REPO_ENDPOINT }} \
            --name codeartifact \
            --username aws \
            --password ${{ steps.get-token.outputs.token }} \
            --store-password-in-clear-text
          dotnet nuget add source https://api.nuget.org/v3/index.json \
            --name nuget-org
      
      - name: Restore dependencies
        run: |
          dotnet restore PayrollEngine.Client.Core.sln \
            --source codeartifact \
            --source nuget-org
      
      - name: Build
        run: dotnet build PayrollEngine.Client.Core.sln -c Release --no-restore
      
      - name: Pack
        run: dotnet pack PayrollEngine.Client.Core.sln -c Release --no-build --output ./nupkg
      
      - name: Publish to CodeArtifact
        run: |
          dotnet nuget push ./nupkg/*.nupkg \
            --source codeartifact \
            --skip-duplicate
```

Repeat for all other packages (Client.Scripting, Client.Services, Serilog, Document).

---

## Step 3: Create Workflow for Building Applications

### 3.1 Backend Build Workflow

**File**: `.github/workflows/build-docker-image.yml` in `payroll-engine-backend` repository

```yaml
name: Build Docker Image

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

env:
  AWS_REGION: ${{ secrets.AWS_REGION }}
  ECR_REPOSITORY: payroll-engine-backend
  IMAGE_TAG: ${{ github.sha }}

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      
      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ secrets.AWS_REGION }}
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Get CodeArtifact Token
        id: get-token
        run: |
          TOKEN=$(aws codeartifact get-authorization-token \
            --domain ${{ secrets.CODEARTIFACT_DOMAIN }} \
            --query authorizationToken \
            --output text)
          echo "token=$TOKEN" >> $GITHUB_OUTPUT
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile.codeartifact
          push: true
          tags: |
            ${{ steps.login-ecr.outputs.registry }}/${{ env.ECR_REPOSITORY }}:${{ env.IMAGE_TAG }}
            ${{ steps.login-ecr.outputs.registry }}/${{ env.ECR_REPOSITORY }}:latest
          build-args: |
            CODEARTIFACT_TOKEN=${{ steps.get-token.outputs.token }}
            CODEARTIFACT_REPO=${{ secrets.CODEARTIFACT_REPO_ENDPOINT }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### 3.2 Backend Dockerfile for CodeArtifact

**File**: `Dockerfile.codeartifact` in `payroll-engine-backend`

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

ARG CODEARTIFACT_TOKEN
ARG CODEARTIFACT_REPO

# Configure CodeArtifact NuGet source
RUN dotnet nuget add source $CODEARTIFACT_REPO \
    --name codeartifact \
    --username aws \
    --password $CODEARTIFACT_TOKEN \
    --store-password-in-clear-text

# Add nuget.org for third-party packages
RUN dotnet nuget add source https://api.nuget.org/v3/index.json \
    --name nuget-org

# Copy solution and project files
COPY ["PayrollEngine.Backend.sln", "./"]
COPY ["Api/Api.Controller/PayrollEngine.Api.Controller.csproj", "Api/Api.Controller/"]
COPY ["Api/Api.Core/PayrollEngine.Api.Core.csproj", "Api/Api.Core/"]
COPY ["Api/Api.Map/PayrollEngine.Api.Map.csproj", "Api/Api.Map/"]
COPY ["Api/Api.Model/PayrollEngine.Api.Model.csproj", "Api/Api.Model/"]
COPY ["Backend.Controller/PayrollEngine.Backend.Controller.csproj", "Backend.Controller/"]
COPY ["Backend.Server/PayrollEngine.Backend.Server.csproj", "Backend.Server/"]
COPY ["Domain/Domain.Application/PayrollEngine.Domain.Application.csproj", "Domain/Domain.Application/"]
COPY ["Domain/Domain.Model/PayrollEngine.Domain.Model.csproj", "Domain/Domain.Model/"]
COPY ["Domain/Domain.Model.Tests/PayrollEngine.Domain.Model.Tests.csproj", "Domain/Domain.Model.Tests/"]
COPY ["Domain/Domain.Scripting/PayrollEngine.Domain.Scripting.csproj", "Domain/Domain.Scripting/"]
COPY ["Persistence/Persistence/PayrollEngine.Persistence.csproj", "Persistence/Persistence/"]
COPY ["Persistence/Persistence.SqlServer/PayrollEngine.Persistence.SqlServer.csproj", "Persistence/Persistence.SqlServer/"]
COPY ["Directory.Build.props", "./"]

# Restore with both sources
RUN dotnet restore "PayrollEngine.Backend.sln" \
    --source codeartifact \
    --source nuget-org

# Copy everything else
COPY . .
COPY ["Database/", "Database/"]

WORKDIR "/src/Backend.Server"

# Publish
RUN dotnet publish "PayrollEngine.Backend.Server.csproj" -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgdiplus \
    fonts-liberation \
    fonts-dejavu-core \
    fonts-freefont-ttf \
    fontconfig \
    libc6-dev \
    libx11-dev \
    libxext-dev \
    libxrender-dev \
    libxtst-dev \
    libxrandr-dev \
    libasound2-dev \
    libcairo2-dev \
    libpango1.0-dev \
    libatk1.0-dev \
    libgtk-3-dev \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .
COPY --from=build /src/Database ./Database

ENV DISPLAY=:99
ENV FONTCONFIG_PATH=/etc/fonts

ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

### 3.3 WebApp and Console Workflows

Create similar workflows for webapp and console with their respective Dockerfiles.

---

## Step 4: Migration Checklist

### Pre-Migration

- [ ] CodeArtifact domain and repository created
- [ ] IAM policies configured
- [ ] GitHub secrets added to all repositories
- [ ] Local packages tested and working

### Migration Steps

1. **Create package build workflows** (one per package repository)
   - [ ] PayrollEngine.Core
   - [ ] PayrollEngine.Client.Core
   - [ ] PayrollEngine.Client.Scripting
   - [ ] PayrollEngine.Client.Services
   - [ ] PayrollEngine.Serilog
   - [ ] PayrollEngine.Document

2. **Manually push initial packages to CodeArtifact**
   ```bash
   # For each package, build locally and push
   cd PayrollEngine.Core
   dotnet pack -c Release -o ./nupkg
   
   # Get token
   export TOKEN=$(aws codeartifact get-authorization-token \
       --domain payroll-engine \
       --query authorizationToken \
       --output text)
   
   # Configure and push
   dotnet nuget add source $REPO_ENDPOINT \
       --name codeartifact \
       --username aws \
       --password $TOKEN \
       --store-password-in-clear-text
   
   dotnet nuget push ./nupkg/*.nupkg --source codeartifact
   ```

3. **Create application build workflows**
   - [ ] Backend workflow
   - [ ] WebApp workflow
   - [ ] Console workflow

4. **Update Dockerfiles**
   - [ ] Backend Dockerfile.codeartifact
   - [ ] WebApp Dockerfile.codeartifact
   - [ ] Console Dockerfile.codeartifact

5. **Test workflows**
   - [ ] Trigger package build workflows
   - [ ] Verify packages in CodeArtifact
   - [ ] Trigger application build workflows
   - [ ] Verify Docker images in ECR

### Post-Migration

- [ ] Remove local build scripts
- [ ] Update documentation
- [ ] Configure branch protection rules
- [ ] Set up notifications for workflow failures

---

## Step 5: Handling Third-Party Packages

Since CodeArtifact cannot proxy nuget.org, you have two options:

### Option A: Restore from nuget.org During Build (Recommended)

This is what the workflows above do - restore PayrollEngine packages from CodeArtifact and third-party packages from nuget.org.

### Option B: Mirror Third-Party Packages to CodeArtifact

```bash
# Download package from nuget.org
nuget install Serilog.AspNetCore -Version 9.0.0 -OutputDirectory ./temp

# Push to CodeArtifact
dotnet nuget push ./temp/Serilog.AspNetCore.9.0.0/lib/net9.0/Serilog.AspNetCore.9.0.0.nupkg \
    --source codeartifact
```

**Note**: This requires manual maintenance and is not recommended unless you need complete isolation.

---

## Step 6: Workflow Dependencies

To ensure packages are built before applications, use workflow dependencies:

```yaml
# In application workflow
jobs:
  wait-for-packages:
    runs-on: ubuntu-latest
    steps:
      - name: Wait for package builds
        uses: lewagon/wait-on-check-action@v1.3.1
        with:
          ref: ${{ github.ref }}
          check-regexp: 'Build and Publish Package'
          repo: 'Payroll-Engine/PayrollEngine.Core'
          wait-interval: 10
  
  build:
    needs: wait-for-packages
    # ... rest of build steps
```

---

## Troubleshooting

### Token Expiration
CodeArtifact tokens expire after 12 hours. Workflows automatically refresh tokens on each run.

### Package Not Found
- Verify package was published to CodeArtifact
- Check package version matches
- Verify CodeArtifact source is configured correctly

### Build Failures
- Check AWS credentials and permissions
- Verify CodeArtifact domain/repository names
- Check workflow logs for detailed errors

---

## Cost Considerations

- **CodeArtifact Storage**: ~$0.05/GB/month
- **CodeArtifact Transfer**: ~$0.05/GB
- **GitHub Actions**: Free for public repos, 2000 minutes/month for private repos
- **ECR Storage**: ~$0.10/GB/month

**Estimated monthly cost**: $10-30 for small teams

---

## Next Steps

1. Start with PayrollEngine.Core workflow
2. Test package publishing
3. Create remaining package workflows
4. Create application workflows
5. Monitor and optimize

---

*Document Generated: 2025-01-05*  
*Migration Guide: Local Builds to GitHub Workflows + CodeArtifact*

