# Exemplar Repository - Target Architecture and Infrastructure

This document explains what infrastructure and architecture patterns the `exp-container-image-exemplar` repository is designed for and demonstrates.

**Repository**: `gp-nova/exp-container-image-exemplar`  
**Purpose**: Reference/exemplar for container image workflows and serverless applications  
**Primary Technology Stack**: Node.js/TypeScript, npm, AWS SAM

---

## Executive Summary

The exemplar repository is **primarily targeted at**:

1. **Serverless Applications** (Primary):
   - AWS Lambda functions
   - API Gateway REST APIs
   - TypeScript/Node.js runtime
   - npm package management

2. **Container Images** (Secondary/Demonstration):
   - ECS Fargate task definitions
   - Container image examples (BusyBox, Nginx)
   - Demonstrates container image lifecycle patterns

3. **Technology Stack**:
   - **Language**: TypeScript/Node.js
   - **Package Manager**: npm
   - **Build Tool**: SAM CLI (Serverless Application Model)
   - **Infrastructure**: CloudFormation/SAM templates

---

## Primary Architecture: Serverless (Lambda + API Gateway)

### Target Use Case

The exemplar is **primarily designed for serverless applications** that:

- Use **AWS Lambda** for compute
- Expose APIs via **API Gateway**
- Are written in **TypeScript/Node.js**
- Use **npm** for dependency management
- Deploy using **AWS SAM** (Serverless Application Model)

### Infrastructure Components

**1. Lambda Functions**:
```yaml
HelloGpFunction:
  Type: AWS::Serverless::Function
  Properties:
    Runtime: nodejs20.x
    Handler: src/handlers/hello.handler
    CodeUri: dist/
```

**2. API Gateway**:
```yaml
PayrollEngineApi:
  Type: AWS::Serverless::Api
  Properties:
    StageName: live
    DefinitionBody:
      # REST API definition
```

**3. Application Structure**:
```
src/
├── handlers/          # Lambda function handlers
├── services/          # Business logic
├── models/            # Data models
└── utils/             # Utility functions
```

### Build Process

**1. Application Build** (npm/TypeScript):
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/setup@v1
- uses: gp-nova/devx-pipeline-modules-npm/install@v1
- uses: gp-nova/devx-pipeline-modules-npm/build@v1
```
- Installs npm dependencies (from CodeArtifact)
- Compiles TypeScript to JavaScript
- Outputs to `dist/` directory

**2. SAM Build**:
```bash
sam build
```
- Packages Lambda functions
- Creates deployment artifacts
- Outputs to `.aws-sam/` directory

**3. SAM Deploy**:
```bash
sam deploy --config-env {environment}
```
- Uploads Lambda code to S3
- Creates/updates CloudFormation stack
- Deploys Lambda functions and API Gateway

### Why Serverless is Primary

**Evidence from Workflows**:

1. **Build Workflow** (`main-build.yaml`):
   - Uses `devx-pipeline-modules-npm/*` actions (npm-focused)
   - Builds application code (TypeScript → JavaScript)
   - Uploads artifacts: `.aws-sam/ dist/` (SAM and compiled code)

2. **Deploy Workflow** (`deploy.yaml`):
   - Uses shared workflow `deploy-api.yaml@v6` (API deployment)
   - Downloads artifacts from CodeArtifact
   - Executes `sam deploy` (SAM deployment)

3. **Repository Structure**:
   - `src/` directory with TypeScript source code
   - `package.json` for npm dependencies
   - `template.yaml` with `AWS::Serverless::Function` resources
   - `samconfig.toml` for SAM deployment configuration

---

## Secondary Architecture: Container Images (ECS)

### Purpose: Demonstration

The exemplar **also includes container image examples** to demonstrate:

- Container image build workflows
- ECS task definition patterns
- Image promotion between environments
- Container image lifecycle management

### Container Image Components

**1. Dockerfile Examples**:
```
images/
└── exp-container-image-exemplar/
    └── Dockerfile
```

**2. ECS Task Definition** (Example):
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

**3. Image Build Configuration**:
```yaml
Mappings:
  ImageBuildConfiguration:
    BusyBoxService:
      buildContext: images/exp-container-image-exemplar
      dockerfile: images/exp-container-image-exemplar/Dockerfile
      imageName: exp-container-image-exemplar
      platform: linux/amd64
```

### Why Containers are Secondary

**Evidence**:

1. **Container images are examples**, not the primary application
2. **Build workflow** includes container build step, but:
   - Application build (npm) is the main focus
   - Container build is additional/demonstration
3. **Deployment** primarily targets Lambda functions
4. **ECS task definitions** are included as examples of how to reference container images

---

## Technology Stack Focus: Node.js/npm

### Primary Technology: Node.js/TypeScript

**Why npm/Node.js is the target**:

1. **Package Management**:
   - Uses `package.json` for dependencies
   - Uses `devx-pipeline-modules-npm/*` actions
   - Installs from CodeArtifact (npm registry)

2. **Build Tools**:
   - TypeScript compilation
   - npm scripts for build/test/lint
   - SAM CLI for serverless deployment

3. **Workflow Actions**:
   ```yaml
   - uses: gp-nova/devx-pipeline-modules-npm/setup@v1      # Node.js setup
   - uses: gp-nova/devx-pipeline-modules-npm/install@v1   # npm install
   - uses: gp-nova/devx-pipeline-modules-npm/build@v1    # npm run build
   - uses: gp-nova/devx-pipeline-modules-npm/lint@v1     # npm run lint
   - uses: gp-nova/devx-pipeline-modules-npm/test@v1      # npm test
   ```

4. **Security Scanning**:
   - Uses `snyk-node.yaml@v6` workflow
   - Scans Node.js dependencies for vulnerabilities

### Not Targeted At

**The exemplar is NOT designed for**:

- ❌ .NET/C# applications (no NuGet support)
- ❌ Python applications (no pip/poetry support)
- ❌ Java applications (no Maven/Gradle support)
- ❌ Pure container applications (no Lambda functions)
- ❌ ECS-only deployments (ECS is secondary/demonstration)

---

## Infrastructure Patterns

### 1. Serverless Pattern (Primary)

**Architecture**:
```
API Gateway
    ↓
Lambda Functions (TypeScript/Node.js)
    ↓
AWS Services (S3, DynamoDB, etc.)
```

**Infrastructure as Code**:
- **Template**: SAM template (`template.yaml`)
- **Deployment**: `sam deploy`
- **Configuration**: `samconfig.toml` (environment-specific)

**Components**:
- Lambda functions (Node.js runtime)
- API Gateway REST API
- IAM roles and policies
- CloudWatch Logs
- Environment variables
- SSM Parameter Store integration

### 2. Container Pattern (Secondary/Demonstration)

**Architecture**:
```
ECS Fargate Service
    ↓
Task Definition
    ↓
Container Image (from ECR)
```

**Infrastructure as Code**:
- **Template**: CloudFormation (in same SAM template)
- **Deployment**: Via SAM deploy (updates task definitions)
- **Image Reference**: From SSM Parameter Store

**Components**:
- ECS Task Definitions
- ECS Services (example)
- Container images in ECR
- Image promotion workflow

---

## Deployment Architecture

### Serverless Deployment Flow

**1. Build Phase**:
```
Source Code (TypeScript)
    ↓
npm install (CodeArtifact)
    ↓
npm run build (TypeScript → JavaScript)
    ↓
sam build (Package Lambda functions)
    ↓
Artifact Upload (CodeArtifact)
```

**2. Deploy Phase**:
```
Download Artifacts (CodeArtifact)
    ↓
sam deploy --config-env {environment}
    ↓
CloudFormation Stack Update
    ↓
Lambda Functions Deployed
    ↓
API Gateway Updated
```

### Container Deployment Flow (Example)

**1. Build Phase**:
```
Dockerfile
    ↓
docker buildx build
    ↓
Container Image (local)
    ↓
docker push (ECR)
    ↓
Image Tagged: build-{version}
```

**2. Promote Phase**:
```
build-{version}
    ↓
Re-tag: {imageName}-{env}-{version}
    ↓
Push to ECR
```

**3. Deploy Phase**:
```
sam deploy --config-env {environment}
    ↓
CloudFormation Updates Task Definition
    ↓
ECS Service Picks Up New Task Definition
```

---

## Why Both Lambda and ECS?

### Dual Purpose Design

The exemplar demonstrates **two complementary patterns**:

1. **Serverless (Lambda)** - Primary application architecture
   - Fast, scalable, cost-effective
   - Event-driven, API-driven
   - No server management

2. **Containers (ECS)** - Secondary/demonstration
   - Shows container image lifecycle
   - Demonstrates promotion workflows
   - Example of ECS integration

### Use Cases

**Lambda (Primary)**:
- REST APIs
- Event processing
- Microservices
- Serverless applications

**ECS (Secondary/Example)**:
- Long-running processes
- Container-based services
- Legacy container migration
- Demonstration of container patterns

---

## Package Management: npm/CodeArtifact

### npm as Primary Package Manager

**Configuration**:
- `.npmrc` file (if needed for CodeArtifact)
- `package.json` for dependencies
- `package-lock.json` for version locking

**Package Sources**:
- **Primary**: AWS CodeArtifact (npm registry)
- **Upstream**: npmjs.com (via CodeArtifact proxy)
- **Private Packages**: Stored in CodeArtifact

**Workflow Integration**:
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/install@v1
  # Automatically:
  # 1. Gets CodeArtifact token
  # 2. Configures npm registry
  # 3. Runs npm install
```

### Why npm/CodeArtifact?

1. **Organization Standard**: CodeArtifact for all npm packages
2. **Private Packages**: Host internal packages (`@gp-nova/*`)
3. **Security**: Centralized package scanning
4. **Caching**: Faster installs via CodeArtifact cache

---

## Infrastructure as Code: SAM/CloudFormation

### SAM Template Structure

**Primary Resources** (Serverless):
```yaml
Resources:
  HelloGpFunction:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: nodejs20.x
      Handler: src/handlers/hello.handler
      CodeUri: dist/
```

**Secondary Resources** (Containers - Example):
```yaml
Resources:
  TestContainerDefinition:
    Type: AWS::ECS::TaskDefinition
    Properties:
      ContainerDefinitions:
        - Image: !Sub "${RepositoryPrefix}/${ImageNameAndTag}"
```

### Deployment Configuration

**Environment-Specific** (`samconfig.toml`):
```toml
[dev.deploy.parameters]
Environment = "dev"
RepositoryPrefix = "/account/ecr/dev/registry"
DeploymentVersion = "1.0.0"
StackName = "exp-container-image-exemplar-dev"
```

**Deployment Command**:
```bash
sam deploy --config-env {environment}
```

---

## Summary: Target Architecture

### Primary Target

**Serverless Applications**:
- ✅ AWS Lambda functions
- ✅ API Gateway REST APIs
- ✅ TypeScript/Node.js
- ✅ npm package management
- ✅ SAM deployment

### Secondary Target

**Container Image Patterns** (Demonstration):
- ✅ ECS Fargate task definitions
- ✅ Container image lifecycle
- ✅ Image promotion workflows
- ✅ ECR integration

### Technology Stack

**Required**:
- ✅ Node.js (>=20.x)
- ✅ npm
- ✅ TypeScript
- ✅ AWS SAM CLI
- ✅ CodeArtifact access

**Not Required**:
- ❌ Docker (only for container examples)
- ❌ .NET SDK
- ❌ Python
- ❌ Java

### Use Cases

**Ideal For**:
- ✅ Serverless REST APIs
- ✅ Event-driven applications
- ✅ Microservices (Lambda-based)
- ✅ Node.js/TypeScript applications
- ✅ Organizations using CodeArtifact

**Not Ideal For**:
- ❌ .NET applications (use different exemplar)
- ❌ Python applications (use different exemplar)
- ❌ Pure container applications (ECS is secondary)
- ❌ Applications not using npm

---

## Comparison with Other Repositories

### Payroll Engine Backend

| Aspect | Exemplar | Payroll Engine Backend |
|--------|----------|------------------------|
| **Primary Architecture** | Serverless (Lambda) | ECS Fargate |
| **Language** | TypeScript/Node.js | C#/.NET |
| **Package Manager** | npm | NuGet |
| **Build Tool** | SAM CLI | Docker |
| **Deployment** | SAM deploy | Direct ECS update |
| **Container Focus** | Secondary (example) | Primary |

### Employee Data Pipeline

| Aspect | Exemplar | Employee Data Pipeline |
|--------|----------|------------------------|
| **Primary Architecture** | Serverless (Lambda) | Serverless (Lambda) ✅ |
| **Language** | TypeScript/Node.js | TypeScript/Node.js ✅ |
| **Package Manager** | npm | npm ✅ |
| **Build Tool** | SAM CLI | SAM CLI ✅ |
| **Deployment** | SAM deploy | SAM deploy ✅ |
| **Container Focus** | Secondary (example) | None |

**Note**: `payroll-engine-employee-data-pipeline` closely matches the exemplar's primary target architecture (serverless Lambda + npm).

---

## Key Takeaways

1. **Primary Target**: Serverless applications using AWS Lambda, API Gateway, and Node.js/TypeScript
2. **Package Management**: npm with CodeArtifact integration
3. **Build/Deploy**: SAM CLI for serverless deployment
4. **Containers**: Included as examples/demonstrations, not primary focus
5. **Technology Stack**: Node.js/TypeScript ecosystem
6. **Not For**: .NET, Python, Java, or pure container applications

---

## References

- **Repository**: `gp-nova/exp-container-image-exemplar`
- **Workflows**: `.github/workflows/`
- **Template**: `template.yaml`
- **Configuration**: `samconfig.toml`
- **Package Config**: `package.json`
- **Complete Guide**: `docs/EXEMPLAR-COMPLETE-GUIDE.md`

