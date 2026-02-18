# Migration to Self-Contained Stack Architecture

This document outlines how to migrate from the current **centralized infrastructure** model to the **exemplar's self-contained stack** architecture.

---

## Current Architecture vs. Exemplar Architecture

### Current Architecture (Centralized)

**Structure**:
```
payroll-engine-backend/
├── Dockerfile                    # Container image definition
├── template.yaml                 # Only ImageBuildConfiguration (minimal)
└── Source code                   # Application code only

gp-nova-payroll-engine-infra/
├── template.yaml                 # ALL infrastructure (ECS, RDS, API Gateway, etc.)
├── foundational-resources.yaml   # ECS cluster, ECR repos
└── Deploys: payroll-engine-ecs-application stack
```

**Characteristics**:
- ✅ Infrastructure centralized in one repository
- ✅ Single stack for all services (backend, webapp, console)
- ✅ Shared resources (cluster, VPC, RDS) in one place
- ❌ Backend repo is just source code
- ❌ No infrastructure as code in backend repo
- ❌ Manual parameter passing for deployments

### Exemplar Architecture (Self-Contained)

**Structure**:
```
exp-container-image-exemplar/
├── Dockerfile                    # Container image definition
├── template.yaml                 # FULL infrastructure (task definitions, services, etc.)
├── samconfig.toml                # Environment-specific deployment config
└── Source code                   # Application code + infrastructure
```

**Characteristics**:
- ✅ Each repository is self-contained
- ✅ Own CloudFormation stack per repository
- ✅ Infrastructure co-located with application code
- ✅ Environment-specific configurations
- ✅ Automated deployments via SAM

---

## Key Differences

| Aspect | Current (Centralized) | Exemplar (Self-Contained) |
|--------|----------------------|---------------------------|
| **Infrastructure Location** | `gp-nova-payroll-engine-infra` | `payroll-engine-backend` |
| **Stack Ownership** | One stack for all services | One stack per service |
| **Template Scope** | Full infrastructure | Service-specific infrastructure |
| **Deployment** | Manual parameter passing | SAM deploy with `samconfig.toml` |
| **Shared Resources** | In same stack | Imported via CloudFormation exports |
| **Repository Purpose** | Source code only | Source code + infrastructure |

---

## Migration Feasibility

### ✅ Yes, Migration is Possible

**Benefits**:
- ✅ Better separation of concerns
- ✅ Independent deployments per service
- ✅ Infrastructure versioned with application code
- ✅ Follows exemplar best practices
- ✅ Automated deployments

**Challenges**:
- ⚠️ Requires splitting shared infrastructure
- ⚠️ Need to handle shared resources (cluster, VPC, RDS)
- ⚠️ Migration downtime if not planned carefully
- ⚠️ Need to coordinate with other services (webapp, console)

---

## Migration Strategy

### Option 1: Full Migration (Recommended for Long-Term)

**Move all backend-specific infrastructure to `payroll-engine-backend` repository.**

### Option 2: Hybrid Approach (Recommended for Gradual Migration)

**Keep shared resources in `gp-nova-payroll-engine-infra`, move service-specific resources to backend repo.**

---

## Migration Steps: Full Migration

### Phase 1: Prepare Backend Repository

**Step 1.1: Create Full `template.yaml`**

**Current** (`payroll-engine-backend/template.yaml`):
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: Payroll Engine Backend - Container Image Build Configuration

Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64
```

**New** (`payroll-engine-backend/template.yaml`):
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: Payroll Engine Backend - Complete Infrastructure Stack

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, test, prod]
    Description: Target environment
  
  DeploymentVersion:
    Type: String
    Default: latest
    Description: Container image version to deploy
  
  RepositoryPrefix:
    Type: AWS::SSM::Parameter::Value<String>
    Default: /account/ecr/main/registry
    Description: ECR registry prefix
  
  # Shared Infrastructure Parameters (from foundational stack)
  VpcId:
    Type: AWS::EC2::VPC::Id
    Description: VPC ID from foundational stack
  
  PrivateSubnetIds:
    Type: List<AWS::EC2::Subnet::Id>
    Description: Private subnet IDs from foundational stack
  
  ClusterName:
    Type: String
    Description: ECS cluster name from foundational stack
  
  DBSecretArn:
    Type: String
    Description: RDS database secret ARN from foundational stack
  
  DBHost:
    Type: String
    Description: RDS database host from foundational stack
  
  BackendTgArn:
    Type: String
    Description: Target group ARN for backend from network stack

Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64

Resources:
  # Log Group
  BackendLogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      LogGroupName: /ecs/payroll-engine-backend
      RetentionInDays: 30

  # Security Group
  BackendSecurityGroup:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupDescription: Security group for backend ECS tasks
      VpcId: !Ref VpcId
      SecurityGroupIngress:
        - IpProtocol: tcp
          FromPort: 8080
          ToPort: 8080
          CidrIp: 10.0.0.0/16  # VPC CIDR

  # IAM Roles
  ECSTaskExecutionRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: ecs-tasks.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
      Policies:
        - PolicyName: AllowECRAccess
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - ecr:GetAuthorizationToken
                  - ecr:BatchCheckLayerAvailability
                  - ecr:GetDownloadUrlForLayer
                  - ecr:BatchGetImage
                Resource: "*"
        - PolicyName: AllowCloudWatchLogs
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - logs:CreateLogGroup
                  - logs:CreateLogStream
                  - logs:PutLogEvents
                Resource: "*"

  BackendTaskRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: ecs-tasks.amazonaws.com
            Action: sts:AssumeRole
      Policies:
        - PolicyName: AllowReadDBSecrets
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action: secretsmanager:GetSecretValue
                Resource: !Ref DBSecretArn

  # Task Definition
  BackendTaskDefinition:
    Type: AWS::ECS::TaskDefinition
    Properties:
      Family: payroll-engine-backend
      RequiresCompatibilities: ["FARGATE"]
      NetworkMode: awsvpc
      Cpu: "1024"
      Memory: "2048"
      ExecutionRoleArn: !Ref ECSTaskExecutionRole
      TaskRoleArn: !Ref BackendTaskRole
      ContainerDefinitions:
        - Name: backend
          Image: !Sub
            - "${RepositoryPrefix}/${ImageNameAndTag}"
            - {
                RepositoryPrefix: !Ref RepositoryPrefix,
                ImageNameAndTag: !Join [ ":", [
                  !FindInMap [ ImageBuildConfiguration, BackendService, imageName ],
                  !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
                ]]
              }
          PortMappings:
            - ContainerPort: 8080
          Environment:
            - Name: DB_SECRET_NAME
              Value: !Ref DBSecretArn
            - Name: DB_HOST
              Value: !Ref DBHost
          LogConfiguration:
            LogDriver: awslogs
            Options:
              awslogs-group: !Ref BackendLogGroup
              awslogs-region: !Ref AWS::Region
              awslogs-stream-prefix: ecs

  # ECS Service
  BackendService:
    Type: AWS::ECS::Service
    Properties:
      ServiceName: payroll-engine-backend
      Cluster: !Ref ClusterName
      TaskDefinition: !Ref BackendTaskDefinition
      LaunchType: FARGATE
      DesiredCount: 1
      NetworkConfiguration:
        AwsvpcConfiguration:
          Subnets: !Ref PrivateSubnetIds
          SecurityGroups: [!Ref BackendSecurityGroup]
      LoadBalancers:
        - TargetGroupArn: !Ref BackendTgArn
          ContainerName: backend
          ContainerPort: 8080
      Tags:
        - Key: owner:team:environment
          Value: !Ref Environment
        - Key: owner:team:domain
          Value: global-payroll
        - Key: owner:team:valuestream
          Value: coreplatform

Outputs:
  BackendServiceName:
    Description: Backend ECS service name
    Value: !Ref BackendService
    Export:
      Name: !Sub "${AWS::StackName}-BackendServiceName"
  
  BackendTaskDefinitionArn:
    Description: Backend task definition ARN
    Value: !Ref BackendTaskDefinition
    Export:
      Name: !Sub "${AWS::StackName}-BackendTaskDefinitionArn"
```

**Step 1.2: Create `samconfig.toml`**

**File**: `payroll-engine-backend/samconfig.toml`

```toml
version = 0.1

[dev]
[dev.deploy.parameters]
stack_name = "payroll-engine-backend-dev"
region = "us-east-1"
capabilities = "CAPABILITY_NAMED_IAM"
parameter_overrides = [
  "Environment=dev",
  "RepositoryPrefix=/account/ecr/dev/registry",
  "DeploymentVersion=latest",
  "VpcId=vpc-xxxxx",  # From foundational stack
  "PrivateSubnetIds=subnet-xxxxx,subnet-yyyyy",  # From foundational stack
  "ClusterName=payroll-engine-cluster",  # From foundational stack
  "DBSecretArn=arn:aws:secretsmanager:us-east-1:258215414239:secret:payroll-engine/database-credentials-xxxxx",  # From infra stack
  "DBHost=payroll-engine-db.xxxxx.us-east-1.rds.amazonaws.com",  # From infra stack
  "BackendTgArn=arn:aws:elasticloadbalancing:us-east-1:258215414239:targetgroup/backend-tg/xxxxx"  # From network stack
]

[test]
[test.deploy.parameters]
stack_name = "payroll-engine-backend-test"
region = "us-east-1"
capabilities = "CAPABILITY_NAMED_IAM"
parameter_overrides = [
  "Environment=test",
  "RepositoryPrefix=/account/ecr/test/registry",
  "DeploymentVersion=latest",
  # ... same pattern for test environment
]

[prod]
[prod.deploy.parameters]
stack_name = "payroll-engine-backend-prod"
region = "us-east-1"
capabilities = "CAPABILITY_NAMED_IAM"
parameter_overrides = [
  "Environment=prod",
  "RepositoryPrefix=/account/ecr/prod/registry",
  "DeploymentVersion=latest",
  # ... same pattern for prod environment
]
```

**Step 1.3: Update Deploy Workflow**

**Current** (`.github/workflows/deploy.yaml`):
- Direct ECS task definition update via AWS CLI

**New** (`.github/workflows/deploy.yaml`):
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
        default: dev
      version:
        description: Image version to deploy
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

permissions:
  contents: read
  id-token: write

concurrency:
  group: ${{ github.workflow }}-${{ inputs.environment }}
  cancel-in-progress: false

env:
  AWS_REGION: us-east-1

jobs:
  deploy:
    name: Deploy to ${{ inputs.environment || 'dev' }}
    runs-on: ubuntu-latest
    environment: ${{ inputs.environment || 'dev' }}
    permissions:
      contents: read
      id-token: write
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Assume OIDC Role
        uses: gp-nova/devx-action-assume-oidc-role@v2
        with:
          environment: ${{ inputs.environment || 'dev' }}

      - name: Setup AWS SAM CLI
        uses: aws-actions/setup-sam@v2

      - name: Get SSM Parameters
        id: ssm-params
        run: |
          # Get ECR registry prefix
          REPO_PREFIX=$(aws ssm get-parameter \
            --name /account/ecr/${{ inputs.environment || 'dev' }}/registry \
            --region ${{ env.AWS_REGION }} \
            --query 'Parameter.Value' \
            --output text)
          echo "repo-prefix=$REPO_PREFIX" >> $GITHUB_OUTPUT

      - name: Get Shared Infrastructure Values
        id: shared-infra
        run: |
          # Get VPC ID, subnets, cluster name from foundational stack
          # Get DB secret ARN, DB host from infra stack
          # Get target group ARN from network stack
          # Export as outputs for use in sam deploy

      - name: Deploy with SAM
        run: |
          VERSION="${{ inputs.version || 'latest' }}"
          sam deploy \
            --config-env ${{ inputs.environment || 'dev' }} \
            --parameter-overrides \
              DeploymentVersion=$VERSION \
            --no-fail-on-empty-changeset \
            --region ${{ env.AWS_REGION }}

      - name: Verify Deployment
        run: |
          STACK_NAME="payroll-engine-backend-${{ inputs.environment || 'dev' }}"
          aws cloudformation describe-stacks \
            --stack-name $STACK_NAME \
            --region ${{ env.AWS_REGION }} \
            --query 'Stacks[0].{Status:StackStatus,Outputs:Outputs}' \
            --output table
```

### Phase 2: Extract Shared Infrastructure

**Step 2.1: Identify Shared Resources**

**Keep in `gp-nova-payroll-engine-infra`**:
- ECS Cluster (shared by all services)
- VPC and Networking (shared)
- RDS Database (shared)
- API Gateway (shared)
- Network Load Balancer (shared)
- Target Groups (shared)

**Move to `payroll-engine-backend`**:
- Backend Task Definition
- Backend ECS Service
- Backend Log Group
- Backend Security Group
- Backend IAM Roles

**Step 2.2: Export Shared Resources**

**Update** `gp-nova-payroll-engine-infra/template.yaml` to export values:

```yaml
Outputs:
  ClusterName:
    Description: ECS cluster name
    Value: !Ref ECSCluster
    Export:
      Name: !Sub "${AWS::StackName}-ClusterName"
  
  VpcId:
    Description: VPC ID
    Value: !Ref VpcId
    Export:
      Name: !Sub "${AWS::StackName}-VpcId"
  
  PrivateSubnetIds:
    Description: Private subnet IDs
    Value: !Join [",", !Ref PrivateSubnetIds]
    Export:
      Name: !Sub "${AWS::StackName}-PrivateSubnetIds"
  
  DBSecretArn:
    Description: Database secret ARN
    Value: !Ref DBSecret
    Export:
      Name: !Sub "${AWS::StackName}-DBSecretArn"
  
  DBHost:
    Description: Database host
    Value: !GetAtt PayrollEngineDB.Endpoint.Address
    Export:
      Name: !Sub "${AWS::StackName}-DBHost"
```

### Phase 3: Update Deployment Process

**Step 3.1: Update Build Workflow**

**Current**: Builds and publishes images

**New**: Same, but also prepares for SAM deployment

**Step 3.2: Update Deploy Workflow**

**Current**: Direct ECS update via AWS CLI

**New**: SAM deploy with CloudFormation stack update

### Phase 4: Migration Execution

**Step 4.1: Create New Stack**

1. Deploy new stack from `payroll-engine-backend`:
   ```bash
   sam deploy --config-env dev
   ```
2. This creates: `payroll-engine-backend-dev` stack
3. New ECS service: `payroll-engine-backend` (in new stack)

**Step 4.2: Migrate Service**

1. **Option A: Blue-Green Deployment**
   - New service runs alongside old service
   - Switch traffic gradually
   - Decommission old service

2. **Option B: Direct Migration**
   - Update old service to use new task definition
   - Migrate tasks to new service
   - Remove old service from infra stack

**Step 4.3: Cleanup**

1. Remove backend resources from `gp-nova-payroll-engine-infra/template.yaml`
2. Update infra stack (removes old backend service)
3. Verify new stack is working

---

## Migration Steps: Hybrid Approach (Recommended)

### Keep Shared Resources Centralized

**Structure**:
```
gp-nova-payroll-engine-infra/
├── foundational-resources.yaml   # ECS cluster, ECR repos
├── network-stack.yaml            # VPC, subnets, NLB, target groups
├── database-stack.yaml            # RDS database
└── api-gateway-stack.yaml        # API Gateway

payroll-engine-backend/
├── template.yaml                  # Backend-specific: task definition, service
├── samconfig.toml                # References shared resources
└── Source code
```

### Hybrid Template Structure

**`payroll-engine-backend/template.yaml`**:
```yaml
Parameters:
  Environment:
    Type: String
  DeploymentVersion:
    Type: String
  RepositoryPrefix:
    Type: AWS::SSM::Parameter::Value<String>
  
  # Import from foundational stack
  ClusterName:
    Type: String
    Description: ECS cluster name (from foundational stack)
  
  # Import from network stack
  VpcId:
    Type: AWS::EC2::VPC::Id
    Description: VPC ID (from network stack)
  
  PrivateSubnetIds:
    Type: List<AWS::EC2::Subnet::Id>
    Description: Private subnet IDs (from network stack)
  
  BackendTgArn:
    Type: String
    Description: Target group ARN (from network stack)
  
  # Import from database stack
  DBSecretArn:
    Type: String
    Description: Database secret ARN (from database stack)
  
  DBHost:
    Type: String
    Description: Database host (from database stack)

Resources:
  # Only backend-specific resources
  BackendLogGroup: ...
  BackendSecurityGroup: ...
  BackendTaskDefinition: ...
  BackendService: ...
```

**Benefits**:
- ✅ Service-specific infrastructure in service repo
- ✅ Shared resources remain centralized
- ✅ Easier migration (less to move)
- ✅ Better separation than current, but not fully decoupled

---

## Comparison: Migration Approaches

| Aspect | Full Migration | Hybrid Approach |
|--------|----------------|-----------------|
| **Infrastructure Location** | All in backend repo | Service-specific in backend, shared in infra |
| **Stack Independence** | Fully independent | Partially independent |
| **Migration Complexity** | High | Medium |
| **Maintenance** | Each service manages own infra | Shared resources centralized |
| **Deployment Speed** | Fast (service-specific) | Medium (depends on shared) |
| **Risk** | High (more to migrate) | Lower (gradual) |

---

## Recommended Migration Path

### Phase 1: Hybrid Approach (Start Here)

1. **Move service-specific resources** to backend repo:
   - Task definition
   - ECS service
   - Log group
   - Security group
   - IAM roles

2. **Keep shared resources** in infra repo:
   - ECS cluster
   - VPC/Networking
   - RDS database
   - API Gateway
   - Load balancer

3. **Benefits**:
   - ✅ Easier migration
   - ✅ Lower risk
   - ✅ Better than current (infrastructure as code in service repo)
   - ✅ Can evolve to full migration later

### Phase 2: Full Migration (Optional, Later)

If needed, move shared resources to individual service repos or create separate foundational stacks.

---

## Migration Checklist

### Pre-Migration

- [ ] Review current infrastructure in `gp-nova-payroll-engine-infra`
- [ ] Identify backend-specific vs. shared resources
- [ ] Document current stack outputs and exports
- [ ] Create backup/rollback plan
- [ ] Coordinate with team (webapp, console may need similar changes)

### Migration

- [ ] Create full `template.yaml` in backend repo
- [ ] Create `samconfig.toml` with environment configs
- [ ] Update deploy workflow to use SAM
- [ ] Test deployment in dev environment
- [ ] Verify service works with new stack
- [ ] Migrate test environment
- [ ] Migrate prod environment

### Post-Migration

- [ ] Remove backend resources from infra stack
- [ ] Update infra stack (removes old backend service)
- [ ] Verify all environments working
- [ ] Update documentation
- [ ] Train team on new deployment process

---

## Key Considerations

### 1. Shared Resources

**Challenge**: ECS cluster, VPC, RDS are shared by multiple services.

**Solution**: 
- Use CloudFormation exports/imports
- Pass as parameters to service stacks
- Or keep in foundational stack (hybrid approach)

### 2. Service Dependencies

**Challenge**: Backend, webapp, console may depend on each other.

**Solution**:
- Use CloudFormation exports for service endpoints
- Or use service discovery
- Or keep API Gateway in shared stack

### 3. Database Access

**Challenge**: Database is shared but needs to be accessible.

**Solution**:
- Export DB secret ARN and host from infra stack
- Import in backend stack as parameters
- IAM roles allow access to secret

### 4. Load Balancer Integration

**Challenge**: Target groups are in network stack.

**Solution**:
- Export target group ARN from network stack
- Import in backend stack as parameter
- Reference in ECS service

---

## Example: Hybrid Migration Template

**Complete `template.yaml` for Backend (Hybrid Approach)**:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: Payroll Engine Backend - Service Infrastructure

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, test, prod]
  
  DeploymentVersion:
    Type: String
    Default: latest
  
  RepositoryPrefix:
    Type: AWS::SSM::Parameter::Value<String>
    Default: /account/ecr/main/registry
  
  # Shared Infrastructure (from other stacks)
  ClusterName:
    Type: String
    Description: ECS cluster name from foundational stack
  
  VpcId:
    Type: AWS::EC2::VPC::Id
    Description: VPC ID from network stack
  
  PrivateSubnetIds:
    Type: List<AWS::EC2::Subnet::Id>
    Description: Private subnet IDs from network stack
  
  BackendTgArn:
    Type: String
    Description: Target group ARN from network stack
  
  DBSecretArn:
    Type: String
    Description: Database secret ARN from database stack
  
  DBHost:
    Type: String
    Description: Database host from database stack

Mappings:
  ImageBuildConfiguration:
    BackendService:
      buildContext: .
      dockerfile: Dockerfile
      imageName: payroll-engine-backend
      platform: linux/amd64

Resources:
  # Backend-specific resources only
  BackendLogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      LogGroupName: /ecs/payroll-engine-backend
      RetentionInDays: 30

  BackendSecurityGroup:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupDescription: Security group for backend ECS tasks
      VpcId: !Ref VpcId
      SecurityGroupIngress:
        - IpProtocol: tcp
          FromPort: 8080
          ToPort: 8080
          CidrIp: 10.0.0.0/16

  ECSTaskExecutionRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: ecs-tasks.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
      Policies:
        - PolicyName: AllowECRAccess
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - ecr:GetAuthorizationToken
                  - ecr:BatchCheckLayerAvailability
                  - ecr:GetDownloadUrlForLayer
                  - ecr:BatchGetImage
                Resource: "*"
        - PolicyName: AllowCloudWatchLogs
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - logs:CreateLogGroup
                  - logs:CreateLogStream
                  - logs:PutLogEvents
                Resource: "*"

  BackendTaskRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: ecs-tasks.amazonaws.com
            Action: sts:AssumeRole
      Policies:
        - PolicyName: AllowReadDBSecrets
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action: secretsmanager:GetSecretValue
                Resource: !Ref DBSecretArn

  BackendTaskDefinition:
    Type: AWS::ECS::TaskDefinition
    Properties:
      Family: payroll-engine-backend
      RequiresCompatibilities: ["FARGATE"]
      NetworkMode: awsvpc
      Cpu: "1024"
      Memory: "2048"
      ExecutionRoleArn: !Ref ECSTaskExecutionRole
      TaskRoleArn: !Ref BackendTaskRole
      ContainerDefinitions:
        - Name: backend
          Image: !Sub
            - "${RepositoryPrefix}/${ImageNameAndTag}"
            - {
                RepositoryPrefix: !Ref RepositoryPrefix,
                ImageNameAndTag: !Join [ ":", [
                  !FindInMap [ ImageBuildConfiguration, BackendService, imageName ],
                  !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ]
                ]]
              }
          PortMappings:
            - ContainerPort: 8080
          Environment:
            - Name: DB_SECRET_NAME
              Value: !Ref DBSecretArn
            - Name: DB_HOST
              Value: !Ref DBHost
          LogConfiguration:
            LogDriver: awslogs
            Options:
              awslogs-group: !Ref BackendLogGroup
              awslogs-region: !Ref AWS::Region
              awslogs-stream-prefix: ecs

  BackendService:
    Type: AWS::ECS::Service
    Properties:
      ServiceName: payroll-engine-backend
      Cluster: !Ref ClusterName
      TaskDefinition: !Ref BackendTaskDefinition
      LaunchType: FARGATE
      DesiredCount: 1
      NetworkConfiguration:
        AwsvpcConfiguration:
          Subnets: !Ref PrivateSubnetIds
          SecurityGroups: [!Ref BackendSecurityGroup]
      LoadBalancers:
        - TargetGroupArn: !Ref BackendTgArn
          ContainerName: backend
          ContainerPort: 8080
      Tags:
        - Key: owner:team:environment
          Value: !Ref Environment
        - Key: owner:team:domain
          Value: global-payroll
        - Key: owner:team:valuestream
          Value: coreplatform

Outputs:
  BackendServiceName:
    Description: Backend ECS service name
    Value: !Ref BackendService
  
  BackendTaskDefinitionArn:
    Description: Backend task definition ARN
    Value: !Ref BackendTaskDefinition
```

---

## Summary

### Your Understanding is Correct

✅ **Yes**, the exemplar expects each repository to be a **self-contained CloudFormation stack** with:
- Full `template.yaml` (not just `ImageBuildConfiguration`)
- `samconfig.toml` for environment-specific configs
- Infrastructure co-located with application code

### Migration is Possible

✅ **Yes**, you can migrate from current architecture to exemplar architecture.

### Recommended Approach

**Start with Hybrid Approach**:
1. Move service-specific infrastructure to backend repo
2. Keep shared resources in infra repo
3. Use CloudFormation imports/exports for shared resources
4. Evolve to full migration later if needed

### Benefits

- ✅ Infrastructure as code in service repository
- ✅ Independent deployments per service
- ✅ Follows exemplar best practices
- ✅ Better version control (infra with code)
- ✅ Automated deployments via SAM

### Next Steps

1. Review this migration guide
2. Decide: Full migration or Hybrid approach
3. Create full `template.yaml` in backend repo
4. Create `samconfig.toml`
5. Update deploy workflow
6. Test in dev environment
7. Migrate gradually

---

## References

- **Exemplar Guide**: `docs/EXEMPLAR-COMPLETE-GUIDE.md`
- **Container Guide**: `docs/EXEMPLAR-CONTAINER-GUIDE.md`
- **Current Infrastructure**: `gp-nova-payroll-engine-infra/template.yaml`
- **Current Backend Template**: `payroll-engine-backend/template.yaml`

