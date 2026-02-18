# ECS Build and Packaging Options for PayrollEngine

Complete guide for building and packaging PayrollEngine applications for AWS ECS deployment, including alternatives to local NuGet feeds.

---

## Overview

This document outlines various strategies for building PayrollEngine packages and applications for containerized deployment on AWS ECS. Each option addresses the challenge of managing internal PayrollEngine NuGet packages without relying on a local file system feed.

---

## Option 1: Docker Multi-Stage Build with Project References ⭐ **RECOMMENDED**

### Description
Build all PayrollEngine packages from source within the Docker build context, eliminating the need for external NuGet feeds for internal packages.

### Implementation

#### Unified Dockerfile for Backend

```dockerfile
# Multi-stage build with all source code
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy ALL PayrollEngine source repositories into build context
COPY PayrollEngine.Core/ ./PayrollEngine.Core/
COPY PayrollEngine.Client.Core/ ./PayrollEngine.Client.Core/
COPY PayrollEngine.Client.Scripting/ ./PayrollEngine.Client.Scripting/
COPY PayrollEngine.Client.Services/ ./PayrollEngine.Client.Services/
COPY PayrollEngine.Serilog/ ./PayrollEngine.Serilog/
COPY PayrollEngine.Document/ ./PayrollEngine.Document/
COPY payroll-engine-backend/ ./payroll-engine-backend/

# Build packages in dependency order
WORKDIR /src/PayrollEngine.Core
RUN dotnet pack -c Release -o /packages

WORKDIR /src/PayrollEngine.Client.Core
RUN dotnet pack -c Release -o /packages

WORKDIR /src/PayrollEngine.Client.Scripting
RUN dotnet pack -c Release -o /packages

WORKDIR /src/PayrollEngine.Client.Services
RUN dotnet pack -c Release -o /packages

WORKDIR /src/PayrollEngine.Serilog
RUN dotnet pack -c Release -o /packages

WORKDIR /src/PayrollEngine.Document
RUN dotnet pack -c Release -o /packages

# Build backend application using local packages
WORKDIR /src/payroll-engine-backend
RUN dotnet restore PayrollEngine.Backend.sln \
    --source /packages \
    --source https://api.nuget.org/v3/index.json

RUN dotnet publish Backend.Server/PayrollEngine.Backend.Server.csproj \
    -c Release \
    -o /app/backend \
    --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app

# Install runtime dependencies
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

COPY --from=build /app/backend .
COPY --from=build /src/payroll-engine-backend/Database ./Database

ENV DISPLAY=:99
ENV FONTCONFIG_PATH=/etc/fonts

ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

#### Build Script

```bash
#!/bin/bash
# build-backend.sh

# Create unified build context
BUILD_DIR="docker-build-context"
rm -rf $BUILD_DIR
mkdir -p $BUILD_DIR

# Copy all required repositories
cp -r ../PayrollEngine.Core $BUILD_DIR/
cp -r ../PayrollEngine.Client.Core $BUILD_DIR/
cp -r ../PayrollEngine.Client.Scripting $BUILD_DIR/
cp -r ../PayrollEngine.Client.Services $BUILD_DIR/
cp -r ../PayrollEngine.Serilog $BUILD_DIR/
cp -r ../PayrollEngine.Document $BUILD_DIR/
cp -r . $BUILD_DIR/payroll-engine-backend/

# Build Docker image
docker build -f Dockerfile.unified \
  --build-context source=$BUILD_DIR \
  -t payroll-engine-backend:latest \
  $BUILD_DIR

# Cleanup
rm -rf $BUILD_DIR
```

### Pros
- ✅ **No external NuGet feed needed** for PayrollEngine packages
- ✅ Self-contained build process
- ✅ Version control for all dependencies
- ✅ Works in any CI/CD pipeline
- ✅ No additional AWS services required
- ✅ Fastest to implement

### Cons
- ❌ Larger Docker build context (slower uploads)
- ❌ Longer build times (compiles all packages)
- ❌ Requires all repos in one location
- ❌ Still needs nuget.org for third-party packages (Serilog, Swashbuckle, etc.)

### When to Use
- Quick setup for development/testing
- When you want full control over dependencies
- When you don't want to manage a NuGet feed
- For CI/CD pipelines without artifact storage

---

## Option 2: AWS CodeArtifact (Private NuGet Feed)

### Description
Use AWS CodeArtifact as a managed private NuGet feed to store and serve PayrollEngine packages.

### Setup

#### 1. Create CodeArtifact Domain and Repository

```bash
# Create domain
aws codeartifact create-domain \
    --domain payroll-engine \
    --region us-east-1

# Create NuGet repository
aws codeartifact create-repository \
    --domain payroll-engine \
    --repository nuget-packages \
    --description "PayrollEngine NuGet packages" \
    --region us-east-1

# Get repository endpoint
aws codeartifact get-repository-endpoint \
    --domain payroll-engine \
    --repository nuget-packages \
    --format nuget \
    --region us-east-1 \
    --query repositoryEndpoint \
    --output text
```

#### 2. Configure Upstream Repository (Optional)

```bash
# Connect to nuget.org for third-party packages
aws codeartifact associate-external-connection \
    --domain payroll-engine \
    --repository nuget-packages \
    --external-connection public:nuget-org \
    --region us-east-1
```

#### 3. Build and Push Packages

```bash
# Get authorization token
export CODEARTIFACT_TOKEN=$(aws codeartifact get-authorization-token \
    --domain payroll-engine \
    --query authorizationToken \
    --output text \
    --region us-east-1)

# Configure NuGet
export CODEARTIFACT_REPO="https://payroll-engine-123456789.d.codeartifact.us-east-1.amazonaws.com/nuget/nuget-packages/v3/index.json"

dotnet nuget add source $CODEARTIFACT_REPO \
    --name codeartifact \
    --username aws \
    --password $CODEARTIFACT_TOKEN \
    --store-password-in-clear-text

# Build and push packages
cd PayrollEngine.Core
dotnet pack -c Release -o ./nupkg
dotnet nuget push ./nupkg/*.nupkg \
    --source codeartifact \
    --skip-duplicate

# Repeat for all packages
```

#### 4. Dockerfile with CodeArtifact

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Configure CodeArtifact NuGet source
ARG CODEARTIFACT_TOKEN
ARG CODEARTIFACT_REPO

RUN dotnet nuget add source $CODEARTIFACT_REPO \
    --name codeartifact \
    --username aws \
    --password $CODEARTIFACT_TOKEN \
    --store-password-in-clear-text

# Add nuget.org as fallback for third-party packages
RUN dotnet nuget add source https://api.nuget.org/v3/index.json \
    --name nuget-org

COPY . .
RUN dotnet restore PayrollEngine.Backend.sln \
    --source codeartifact \
    --source nuget-org

RUN dotnet publish Backend.Server/PayrollEngine.Backend.Server.csproj \
    -c Release \
    -o /app/publish \
    --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

#### 5. Build with Secrets

```bash
# Get token (refresh every 12 hours)
export CODEARTIFACT_TOKEN=$(aws codeartifact get-authorization-token \
    --domain payroll-engine \
    --query authorizationToken \
    --output text)

export CODEARTIFACT_REPO="https://payroll-engine-123456789.d.codeartifact.us-east-1.amazonaws.com/nuget/nuget-packages/v3/index.json"

docker build \
    --build-arg CODEARTIFACT_TOKEN=$CODEARTIFACT_TOKEN \
    --build-arg CODEARTIFACT_REPO=$CODEARTIFACT_REPO \
    -t payroll-engine-backend:latest .
```

### Pros
- ✅ **Managed AWS service** - no infrastructure to maintain
- ✅ Secure, private packages with IAM access control
- ✅ Versioning and package management
- ✅ Can proxy nuget.org (upstream repository)
- ✅ Integrates with AWS ecosystem
- ✅ Cost-effective for teams

### Cons
- ❌ Additional AWS service cost (~$0.05/GB storage + $0.05/GB transfer)
- ❌ Requires token management (12-hour expiration)
- ❌ Setup overhead
- ❌ Still needs nuget.org for third-party packages (unless using upstream)

### When to Use
- Production deployments
- Teams requiring package versioning
- When you need IAM-based access control
- Long-term solution

### Cost Estimate
- Storage: ~$0.05/GB/month
- Transfer: ~$0.05/GB
- Requests: First 2M free, then $0.05/10K requests
- **Estimated monthly cost**: $5-20 for small teams

---

## Option 3: GitHub Packages (NuGet Registry)

### Description
Use GitHub Packages as a private NuGet feed, integrated with your GitHub repositories.

### Setup

#### 1. Create GitHub Personal Access Token

```bash
# Create token with 'write:packages' and 'read:packages' permissions
# Settings → Developer settings → Personal access tokens → Tokens (classic)
```

#### 2. Configure NuGet

```bash
export GITHUB_TOKEN="ghp_your_token_here"
export GITHUB_OWNER="your-org"

dotnet nuget add source https://nuget.pkg.github.com/$GITHUB_OWNER/index.json \
    --name github \
    --username $GITHUB_USERNAME \
    --password $GITHUB_TOKEN \
    --store-password-in-clear-text
```

#### 3. Build and Push Packages

```bash
cd PayrollEngine.Core

# Update .csproj to include GitHub package info
# Add to .csproj:
# <PropertyGroup>
#   <PackageId>PayrollEngine.Core</PackageId>
#   <RepositoryUrl>https://github.com/$GITHUB_OWNER/PayrollEngine.Core</RepositoryUrl>
# </PropertyGroup>

dotnet pack -c Release -o ./nupkg
dotnet nuget push ./nupkg/*.nupkg \
    --source github \
    --skip-duplicate
```

#### 4. Dockerfile

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

ARG GITHUB_TOKEN
ARG GITHUB_OWNER

RUN dotnet nuget add source https://nuget.pkg.github.com/$GITHUB_OWNER/index.json \
    --name github \
    --username $GITHUB_USERNAME \
    --password $GITHUB_TOKEN \
    --store-password-in-clear-text

RUN dotnet nuget add source https://api.nuget.org/v3/index.json \
    --name nuget-org

COPY . .
RUN dotnet restore PayrollEngine.Backend.sln \
    --source github \
    --source nuget-org

RUN dotnet publish -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

### Pros
- ✅ **Free for public packages**
- ✅ Integrated with GitHub repositories
- ✅ Versioning via Git tags
- ✅ Familiar workflow for GitHub users

### Cons
- ❌ Requires GitHub token management
- ❌ Private packages have storage limits (500MB free, then $0.008/GB)
- ❌ Still needs nuget.org for third-party packages
- ❌ Less integrated with AWS ecosystem

### When to Use
- Teams already using GitHub
- Open-source projects
- Small teams with limited package storage needs

---

## Option 4: S3-Based NuGet Feed

### Description
Host NuGet packages in S3 and serve them via CloudFront or S3 static website hosting.

### Setup

#### 1. Create S3 Bucket and Structure

```bash
# Create bucket
aws s3 mb s3://payroll-engine-nuget --region us-east-1

# Create directory structure
mkdir -p nuget-feed/v3/index.json
```

#### 2. Create NuGet Server Index

```json
{
  "version": "3.0.0",
  "resources": [
    {
      "@id": "https://d1234567890.cloudfront.net/v3/package",
      "@type": "PackageBaseAddress/3.0.0"
    },
    {
      "@id": "https://d1234567890.cloudfront.net/v3/query",
      "@type": "SearchQueryService/3.0.0"
    }
  ]
}
```

#### 3. Upload Packages

```bash
# Build packages
cd PayrollEngine.Core
dotnet pack -c Release -o ./nupkg

# Upload to S3
aws s3 cp ./nupkg/PayrollEngine.Core.0.9.0-beta.13.nupkg \
    s3://payroll-engine-nuget/v3/package/payrollengine.core/0.9.0-beta.13/

# Create package metadata (simplified - full implementation requires NuGet server)
```

#### 4. Setup CloudFront Distribution

```bash
# Create CloudFront distribution pointing to S3 bucket
# Enable HTTPS and configure caching
```

#### 5. Dockerfile

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

ARG S3_NUGET_FEED="https://d1234567890.cloudfront.net/v3/index.json"

RUN dotnet nuget add source $S3_NUGET_FEED --name s3-feed
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

COPY . .
RUN dotnet restore --source s3-feed --source nuget-org
RUN dotnet publish -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

### Pros
- ✅ Uses existing S3 infrastructure
- ✅ Cost-effective storage ($0.023/GB)
- ✅ CloudFront CDN for fast access
- ✅ No additional services

### Cons
- ❌ Requires custom NuGet server implementation
- ❌ Complex setup (package metadata, search, etc.)
- ❌ Not a standard NuGet feed (needs custom tooling)
- ❌ Still needs nuget.org for third-party packages

### When to Use
- When you have S3/CloudFront expertise
- For simple package storage (not full NuGet server)
- Cost-sensitive deployments

---

## Option 5: Build Artifacts in CI/CD

### Description
Build packages in CI/CD pipelines, store as artifacts, and use them in Docker builds.

### GitHub Actions Example

#### 1. Build Packages Workflow

```yaml
# .github/workflows/build-packages.yml
name: Build PayrollEngine Packages

on:
  push:
    branches: [main]
    paths:
      - 'PayrollEngine.Core/**'
      - 'PayrollEngine.Client.Core/**'

jobs:
  build-packages:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          submodules: recursive
      
      - name: Setup .NET
        uses: actions/setup-dotnet@v3
        with:
          dotnet-version: '10.0.x'
      
      - name: Build PayrollEngine.Core
        run: |
          cd PayrollEngine.Core
          dotnet pack -c Release -o ./artifacts
      
      - name: Build PayrollEngine.Client.Core
        run: |
          cd PayrollEngine.Client.Core
          dotnet pack -c Release -o ./artifacts
      
      - name: Upload Artifacts
        uses: actions/upload-artifact@v3
        with:
          name: nuget-packages
          path: |
            PayrollEngine.Core/artifacts/*.nupkg
            PayrollEngine.Client.Core/artifacts/*.nupkg
          retention-days: 30
```

#### 2. Build Application Workflow

```yaml
# .github/workflows/build-backend.yml
name: Build Backend

on:
  workflow_run:
    workflows: ["Build PayrollEngine Packages"]
    types: [completed]

jobs:
  build-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Download Packages
        uses: actions/download-artifact@v3
        with:
          name: nuget-packages
          path: ./packages
      
      - name: Setup .NET
        uses: actions/setup-dotnet@v3
        with:
          dotnet-version: '10.0.x'
      
      - name: Restore with Local Packages
        run: |
          dotnet nuget add source ./packages --name local
          dotnet restore payroll-engine-backend/PayrollEngine.Backend.sln \
            --source local \
            --source https://api.nuget.org/v3/index.json
      
      - name: Build
        run: |
          dotnet build payroll-engine-backend/PayrollEngine.Backend.sln -c Release
      
      - name: Build Docker Image
        run: |
          docker build -t payroll-backend:latest .
```

### AWS CodeBuild Example

```yaml
# buildspec-packages.yml
version: 0.2
phases:
  build:
    commands:
      - echo Building PayrollEngine packages
      - cd PayrollEngine.Core && dotnet pack -c Release -o ../artifacts
      - cd ../PayrollEngine.Client.Core && dotnet pack -c Release -o ../artifacts
artifacts:
  files:
    - artifacts/*.nupkg
  name: payroll-engine-packages
```

### Pros
- ✅ Integrates with existing CI/CD
- ✅ Version control via Git
- ✅ Automated builds
- ✅ Artifact retention policies

### Cons
- ❌ Requires CI/CD infrastructure
- ❌ Artifact download adds build time
- ❌ Still needs nuget.org for third-party packages
- ❌ Complex workflow management

### When to Use
- Existing CI/CD pipelines
- Automated deployments
- When packages change frequently

---

## Option 6: Docker Build Context with All Repos

### Description
Use a single Docker build context containing all repositories, building packages on-the-fly.

### Implementation

#### Build Script

```bash
#!/bin/bash
# create-build-context.sh

BUILD_CONTEXT="docker-build"
rm -rf $BUILD_CONTEXT
mkdir -p $BUILD_CONTEXT

# Copy all repositories
cp -r ../PayrollEngine.Core $BUILD_CONTEXT/
cp -r ../PayrollEngine.Client.Core $BUILD_CONTEXT/
cp -r ../PayrollEngine.Client.Scripting $BUILD_CONTEXT/
cp -r ../PayrollEngine.Client.Services $BUILD_CONTEXT/
cp -r ../PayrollEngine.Serilog $BUILD_CONTEXT/
cp -r ../PayrollEngine.Document $BUILD_CONTEXT/
cp -r . $BUILD_CONTEXT/payroll-engine-backend/

# Create .dockerignore to exclude unnecessary files
cat > $BUILD_CONTEXT/.dockerignore << EOF
**/bin/
**/obj/
**/.git/
**/node_modules/
**/.vs/
**/.idea/
EOF

echo "Build context created: $BUILD_CONTEXT"
```

#### Dockerfile

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy entire build context
COPY . .

# Build all packages in dependency order
RUN cd PayrollEngine.Core && dotnet pack -c Release -o /packages
RUN cd PayrollEngine.Client.Core && dotnet pack -c Release -o /packages
RUN cd PayrollEngine.Client.Scripting && dotnet pack -c Release -o /packages
RUN cd PayrollEngine.Client.Services && dotnet pack -c Release -o /packages
RUN cd PayrollEngine.Serilog && dotnet pack -c Release -o /packages
RUN cd PayrollEngine.Document && dotnet pack -c Release -o /packages

# Build application
WORKDIR /src/payroll-engine-backend
RUN dotnet restore PayrollEngine.Backend.sln \
    --source /packages \
    --source https://api.nuget.org/v3/index.json

RUN dotnet publish Backend.Server/PayrollEngine.Backend.Server.csproj \
    -c Release \
    -o /app/publish \
    --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

#### Build Command

```bash
./create-build-context.sh
docker build -f payroll-engine-backend/Dockerfile.unified \
    -t payroll-engine-backend:latest \
    docker-build/
```

### Pros
- ✅ Single build context
- ✅ No external feeds for PayrollEngine packages
- ✅ Simple workflow

### Cons
- ❌ Very large build context (slow uploads)
- ❌ Long build times
- ❌ Still needs nuget.org for third-party packages

### When to Use
- Development environments
- When build time is not critical
- Simple deployment scenarios

---

## Option 7: ECR-Based Package Storage

### Description
Store built `.nupkg` files as artifacts in Docker images stored in ECR, then extract during build.

### Implementation

#### 1. Package Storage Image

```dockerfile
# Dockerfile.packages
FROM scratch AS packages
COPY *.nupkg /packages/
```

#### 2. Build and Push Package Image

```bash
# Build packages locally
cd PayrollEngine.Core
dotnet pack -c Release -o ./nupkg

# Create package storage image
docker build -f Dockerfile.packages \
    --build-arg PACKAGES=./nupkg \
    -t 123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-engine-packages:v0.9.0-beta.13 \
    .

# Push to ECR
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-engine-packages:v0.9.0-beta.13
```

#### 3. Application Dockerfile

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Extract packages from ECR image
COPY --from=123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-engine-packages:v0.9.0-beta.13 /packages /packages

RUN dotnet nuget add source /packages --name local
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

COPY . .
RUN dotnet restore PayrollEngine.Backend.sln \
    --source local \
    --source nuget-org

RUN dotnet publish -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
```

### Pros
- ✅ Uses existing ECR infrastructure
- ✅ Versioned package storage
- ✅ Integrated with AWS

### Cons
- ❌ Complex workflow
- ❌ Requires ECR access during build
- ❌ Still needs nuget.org for third-party packages
- ❌ Not a standard approach

### When to Use
- When ECR is already in use
- For versioned package artifacts
- AWS-native deployments

---

## Comparison Matrix

| Option | NuGet Feed Required | Setup Complexity | Build Time | Cost | Best For |
|--------|-------------------|------------------|------------|------|----------|
| **1. Project References** | ❌ (for PayrollEngine)<br>✅ (for third-party) | Low | Medium | Free | Quick setup |
| **2. CodeArtifact** | ✅ | Medium | Fast | $5-20/mo | Production |
| **3. GitHub Packages** | ✅ | Low | Fast | Free/$ | GitHub teams |
| **4. S3 Feed** | ✅ | High | Fast | $1-5/mo | S3 expertise |
| **5. CI/CD Artifacts** | ✅ | Medium | Medium | Free | CI/CD users |
| **6. Build Context** | ❌ (for PayrollEngine)<br>✅ (for third-party) | Low | Slow | Free | Development |
| **7. ECR Storage** | ✅ | High | Fast | $1-5/mo | ECR users |

---

## Recommendations

### For Development/Testing
**Use Option 1 (Project References)** - Fastest to implement, no external dependencies.

### For Production
**Use Option 2 (CodeArtifact)** - Managed service, secure, integrates with AWS, cost-effective.

### For GitHub-Based Teams
**Use Option 3 (GitHub Packages)** - Free for public, integrated with GitHub workflow.

### For Cost-Sensitive Deployments
**Use Option 1 (Project References)** - No additional costs, self-contained.

---

## Third-Party Package Dependencies

All options will require connection to **nuget.org** for third-party packages used by PayrollEngine:

- `AWSSDK.SecretsManager`
- `Microsoft.AspNetCore.OpenApi`
- `Serilog.*` packages
- `Swashbuckle.AspNetCore`
- `Microsoft.Data.SqlClient`
- Other Microsoft and community packages

**Exception**: If using CodeArtifact with upstream repository configured, third-party packages can be proxied through CodeArtifact (no direct nuget.org connection needed).

---

## ECS Deployment Considerations

### Task Definition Configuration

```json
{
  "family": "payroll-engine-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-backend:latest",
      "portMappings": [{"containerPort": 8080}],
      "environment": [
        {"name": "ASPNETCORE_ENVIRONMENT", "value": "Production"},
        {"name": "ASPNETCORE_URLS", "value": "http://+:8080"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/payroll-engine-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

### Build and Push to ECR

```bash
# Build image
docker build -t payroll-engine-backend:latest .

# Tag for ECR
docker tag payroll-engine-backend:latest \
    123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-backend:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin \
    123456789012.dkr.ecr.us-east-1.amazonaws.com

# Push
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/payroll-backend:latest
```

---

## Security Considerations

1. **Secrets Management**: Use AWS Secrets Manager for database connections, API keys
2. **IAM Roles**: Use ECS task roles for AWS service access
3. **Network Security**: Use VPC endpoints for CodeArtifact/ECR access
4. **Image Scanning**: Enable ECR image scanning for vulnerabilities
5. **Token Rotation**: Rotate CodeArtifact tokens regularly (12-hour expiration)

---

## Troubleshooting

### Build Fails: Package Not Found
- Verify NuGet source configuration
- Check package versions match
- Ensure packages are built/pushed before application build

### Build Slow: Large Context
- Use `.dockerignore` to exclude unnecessary files
- Consider multi-stage builds with separate package builds
- Use BuildKit cache mounts

### CodeArtifact Token Expired
- Tokens expire after 12 hours
- Refresh token in CI/CD pipelines
- Use IAM roles where possible

---

*Document Generated: 2025-01-05*  
*PayrollEngine ECS Build and Packaging Options*

