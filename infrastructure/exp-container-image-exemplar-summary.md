# Exp Container Image Exemplar - Repository Summary

## Repository Overview

**exp-container-image-exemplar** is a reference/exemplar repository that demonstrates best practices for container image workflows using the `devx-pipeline-modules-containers` GitHub Actions. It serves as a template showing:

- Container image building, linting, scanning, security, publishing, and promotion workflows
- Integration with AWS ECR (Elastic Container Registry) and ECS (Elastic Container Service)
- Serverless API architecture (Lambda + API Gateway)
- Complete CI/CD pipelines with automated deployments

**Key Components:**
- TypeScript/Node.js application with SAM (Serverless Application Model)
- Dockerfiles for container images (BusyBox and Nginx examples)
- CloudFormation/SAM templates for AWS infrastructure
- GitHub Actions workflows for automation

---

## ECR and ECS Infrastructure Integration

### 1. **ECR (Elastic Container Registry) Integration**

#### Container Image Build & Publishing:
- Uses `devx-pipeline-modules-containers/build@v1` to build container images
- Uses `devx-pipeline-modules-containers/publish@v1` to push images to ECR
- Images are tagged with version numbers and environment names

#### ECR Configuration in Infrastructure:
The SAM template references ECR repository prefix from SSM Parameter Store:

```yaml
RepositoryPrefix:
  Type: AWS::SSM::Parameter::Value<String>
  Default: /account/ecr/main/registry
  Description: RepoPrefix for ECR repo
```

#### Image Reference Pattern:
- Images are referenced as: `${RepositoryPrefix}/${ImageNameAndTag}`
- Format: `{ECR_PREFIX}/{imageName}-{environment}-{version}`
- Example: `237156726900.dkr.ecr.us-east-1.amazonaws.com/exp-container-image-exemplar-dev-1.0.0`

#### Base Images from ECR:
- Dockerfiles pull base images from ECR pull-through cache:
  - `237156726900.dkr.ecr.us-east-1.amazonaws.com/dockerhub/library/busybox:1.37`
  - `237156726900.dkr.ecr.us-east-1.amazonaws.com/dockerhub/library/nginx:1.27-alpine`

### 2. **ECS (Elastic Container Service) Integration**

#### ECS Task Definition:
The template includes an ECS Task Definition that uses images from ECR:

```yaml
TestContainerDefinition:
  Type: AWS::ECS::TaskDefinition
  Properties:
    Family: !Sub "${Environment}-test-container"
    Cpu: "256"
    Memory: "512"
    NetworkMode: awsvpc
    RequiresCompatibilities:
      - FARGATE
    ExecutionRoleArn: !GetAtt EcsTaskExecutionRole.Arn
    ContainerDefinitions:
      - Name: busybox-test
        Image: !Sub
          - "${RepositoryPrefix}/${ImageNameAndTag}"
          - {
              RepositoryPrefix: !Ref RepositoryPrefix,
              ImageNameAndTag: !Join [ ":", [ !FindInMap [ ImageBuildConfiguration, BusyBoxService, imageName ], !Join ["-", [ !Ref Environment, !Ref DeploymentVersion ] ] ] ]
            }
        Essential: true
        Command: ["echo", "This is a harmless image reference test."]
        LogConfiguration:
          LogDriver: awslogs
          Options:
            awslogs-group: !Ref TestContainerLogGroup
            awslogs-region: !Ref AWS::Region
            awslogs-stream-prefix: busybox-test
```

#### ECS Task Execution Role with ECR Permissions:
The IAM role allows ECS tasks to pull images from ECR:

```yaml
EcsTaskExecutionRole:
  Type: AWS::IAM::Role
  Properties:
    AssumeRolePolicyDocument:
      Version: '2012-10-17'
      Statement:
        - Effect: Allow
          Principal:
            Service:
              - ecs-tasks.amazonaws.com
          Action:
            - sts:AssumeRole
    PermissionsBoundary: !Sub "arn:${AWS::Partition}:iam::${AWS::AccountId}:policy/gp-boundary"
    ManagedPolicyArns:
      - arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
    Policies:
      - PolicyName: ECRAccessPolicy
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
```

### 3. **CI/CD Pipeline Flow**

#### Build & Publish Workflow:
The main build workflow includes:

```yaml
- name: Build container images
  uses: gp-nova/devx-pipeline-modules-containers/build@v1
  env:
    SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}

- name: Publish container images
  uses: gp-nova/devx-pipeline-modules-containers/publish@v1
  with:
    version: ${{ steps.set-version.outputs.version }}
```

#### Promotion Workflow:
- Images can be promoted across environments using `devx-pipeline-modules-containers/promote@v1`
- Supports moving images from dev → test → prod

### 4. **How It Helps Your Infrastructure**

1. **Automated Image Lifecycle**: Complete automation from build → scan → publish → deploy
2. **Security Scanning**: Snyk integration during build process
3. **Version Management**: Properly tagged images with semantic versions
4. **Multi-Environment Support**: Separate ECR repositories per environment
5. **ECS-Ready**: Task definitions that reference ECR images
6. **IAM Integration**: Proper roles for ECS to access ECR
7. **Logging**: CloudWatch Logs integration for ECS containers

---

## Key Takeaways

This exemplar repository provides a complete template for integrating containerized applications with AWS ECR and ECS infrastructure, including security scanning, version management, and multi-environment deployment patterns. It demonstrates best practices for:

- Container image build and publishing workflows
- ECR integration with proper tagging and versioning
- ECS task definitions that pull from ECR
- IAM roles and permissions for secure ECR access
- CI/CD automation for container deployments

---

## Repository Reference

- **GitHub**: https://github.com/gp-nova/exp-container-image-exemplar
- **Purpose**: Exemplar/template repository for container image workflows
- **Value Stream**: core-platform
- **Domain**: dev-enablement
- **Bounded Context**: training

