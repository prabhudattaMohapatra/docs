# Performance Testing Plan - Payroll Engine Backend

## Executive Summary

This document outlines the comprehensive performance testing plan for the Payroll Engine Backend, a .NET Core REST API application that provides payroll processing services. The testing will focus on identifying performance bottlenecks, validating system scalability, and ensuring the application meets production performance requirements under various load conditions.

**Key Focus Areas:**
- Employee Data Ingestion performance
- Pay run execution performance
- Lookup Ingestion performance
- Logging Performance
- Overall API endpoint performance
- Database query performance
- Scripting engine performance
- Report Generation Performance

## Objectives

### Primary Objectives
1. **Identify Performance Bottlenecks**: Discover and document performance issues in critical workflows
2. **Validate Scalability**: Ensure the system can handle expected production loads
3. **Establish Baselines**: Create performance benchmarks for future regression testing
4. **Optimize Resource Usage**: Identify opportunities to improve CPU, memory, and database utilization
5. **Validate SLAs**: Verify that the system meets defined performance service level agreements (For now, Baseline Performance Metrics)

### Secondary Objectives
1. **Stress Testing**: Determine system breaking points and failure modes
2. **Endurance Testing**: Identify memory leaks and performance degradation over time
3. **Spike Testing**: Validate system resilience to sudden traffic increases
4. **Database Performance**: Analyze and optimize database queries and connection pooling

## Scope

### In Scope

#### API Endpoints
- **Employee Management**: Employee CRUD operations
- **Pay Run Operations**: Pay run execution, job status management, result retrieval
- **Lookup Operations**: Lookup values CRUD, bulk ingestion and updation
- **Case Management**: Employee Case Change CRUD operations
- **Regulation Management**: Regulation CRUD, sharing, script management
- **Payroll Operations**: Payroll result queries
- **Reporting**: Report execution
- **Administrative**: Tenant management, user management, audit operations
- **Logging**: Tenant Level Log APIs

#### Key Workflows
1. **Employee Data Ingestion Pipeline**
   - Bulk employee creation/updates

2. **Pay Run Execution**
   - Pay run job execution
   - Payroll result retrieval and consolidation

3. **Lookup Ingestion Pipeline**
   - Lookup Ingestion and updation
   - Lookup value queries

4. **Scripting Engine**
   - Script execution performance

#### Infrastructure Components
- Application server performance
- SQL Server database performance
- Connection pooling
- Memory and CPU utilization
- Network I/O

### Out of Scope
- Frontend application performance
- Third-party service integrations
- Security penetration testing
- Functional testing

## Test Environment

### Current Dev Environment

### Test Data Requirements
- Multiple tenants (e.g., Swiss and India)
- Employees: ~700 per tenant
- Employee Cases: Historical data for 12+ months
- Lookups: Complete lookup sets per tenant
- Regulations: Full regulation sets per tenant

### Environment Isolation
- Dedicated performance testing environment (Separate DB and CloudFormation Stack)
- Network isolation to prevent impact on other systems

## Test Strategy

### Test Types

#### 1. Baseline Testing
**Purpose**: Establish performance benchmarks for individual endpoints and workflows

**Approach**:
- Single user/single request testing
- Measure response times for all critical endpoints
- Document resource utilization under minimal load
- Create performance baseline documentation

**Duration**: 2-3 days

#### 2. Load Testing
**Purpose**: Validate system performance under expected production load

**Approach**:
- Simulate normal production traffic patterns
- Gradual ramp-up to target load
- Sustained load for extended periods (1-2 hours)
- Monitor all performance metrics

**Duration**: 3-4 days

#### 3. Stress Testing
**Purpose**: Identify system breaking points and failure modes

**Approach**:
- Gradually increase load beyond normal capacity
- Identify maximum throughput
- Document failure points and recovery behavior
- Test system behavior under extreme conditions

**Duration**: 2-3 days

#### 4. Spike Testing
**Purpose**: Validate system resilience to sudden traffic increases

**Approach**:
- Sudden load increases (2x, 5x, 10x normal load)
- Rapid ramp-up and ramp-down scenarios
- Test system recovery after spike
- Validate auto-scaling behavior (if applicable)

**Duration**: 1-2 days

#### 5. Endurance Testing
**Purpose**: Identify memory leaks and performance degradation over time

**Approach**:
- Sustained load for extended periods (8-24 hours)
- Monitor memory usage trends
- Check for performance degradation
- Validate connection pool stability

**Duration**: 2-3 days (including monitoring)

#### 6. Volume Testing
**Purpose**: Test system with large datasets

**Approach**:
- Test with maximum expected data volumes
- Large batch operations
- Complex queries with large result sets
- Database performance with full datasets

**Duration**: 2-3 days

### Test Execution Approach

**Phased Execution**:
- Phase 1: Baseline and single endpoint testing
- Phase 2: Workflow testing (user journeys)
- Phase 3: Integrated load testing
- Phase 4: Stress and spike testing
- Phase 5: Endurance testing

**Iterative Approach**:
- Execute tests
- Analyze results
- Identify bottlenecks
- Optimize (if time permits)
- Re-test to validate improvements

**Continuous Monitoring**:
- Real-time monitoring during all tests
- Alert on anomalies
- Capture detailed metrics for analysis

## Test Scenarios

### Scenario 1: Employee Data Ingestion Pipeline

#### 1.1 Bulk Employee Creation
- **Endpoint**: `POST /api/tenants/{tenantId}/employees`
- **Load**: ~700 employees in single batch, 10 concurrent batches
- **Metrics**: Response time, throughput, database impact
- **Expected**: Complete within 30 seconds, no errors

#### 1.2 Employee Updates
- **Endpoint**: `PUT /api/tenants/{tenantId}/employees/{employeeId}`
- **Load**: 500 concurrent updates
- **Metrics**: Response time, database lock contention
- **Expected**: P95 < 500ms, no deadlocks

#### 1.3 Employee Case Changes
- **Endpoint**: `POST /api/tenants/{tenantId}/employees/{employeeId}/cases/{caseName}/values`
- **Load**: 1000 case value updates per minute
- **Metrics**: Response time, database write performance
- **Expected**: P95 < 500ms

#### 1.4 Employee Queries
- **Endpoint**: `GET /api/tenants/{tenantId}/employees` (with OData filters)
- **Load**: 100 concurrent queries with various filters
- **Metrics**: Query execution time, database CPU
- **Expected**: P95 < 500ms, efficient query plans

### Scenario 2: Pay Run Execution

#### 2.1 Pay Run Creation
- **Endpoint**: `POST /api/tenants/{tenantId}/payruns{payrunId}/jobs`
- **Load**: 1 Payrun and then 5 concurrent pay run executions
- **Metrics**: Execution time, CPU usage, memory usage, script compilation time
- **Expected**: P95 < 10s

#### 2.2 Pay Run Result Retrieval
- **Endpoint**: `GET /api/tenants/{tenantId}/payruns/{payrunId}/results`
- **Load**: 50 concurrent result queries
- **Metrics**: Query performance, data transfer time
- **Expected**: P95 < 2s

#### 2.3 Consolidated Results
- **Endpoint**: `GET /api/tenants/{tenantId}/payruns/{payrunId}/consolidated`
- **Load**: 10 concurrent consolidated result queries
- **Metrics**: Aggregation performance, database load
- **Expected**: P95 < 2s

### Scenario 3: Lookup Ingestion Pipeline

#### 3.1 Lookup Creation
- **Endpoint**: `POST /api/tenants/{tenantId}/lookups`
- **Load**: 100 lookups with 1000 values each
- **Metrics**: Creation time, database write performance
- **Expected**: Complete within 10 seconds

#### 3.2 Bulk Lookup Value Updates
- **Endpoint**: `POST /api/tenants/{tenantId}/lookups/{lookupId}/values`
- **Load**: 5000 values per lookup, 10 concurrent operations
- **Metrics**: Write performance, transaction handling
- **Expected**: P95 < 2s per operation

#### 3.3 Lookup Queries
- **Endpoint**: `GET /api/tenants/{tenantId}/lookups/{lookupId}/values`
- **Load**: 200 concurrent queries
- **Metrics**: Query performance, caching effectiveness
- **Expected**: P95 < 100ms (with caching)

### Scenario 4: Scripting Engine Performance

#### 4.1 Script Compilation
- **Scenario**: First-time script compilation
- **Load**: 100 unique scripts, sequential compilation
- **Metrics**: Compilation time, memory usage, CPU usage
- **Expected**: < 1s per script, efficient memory usage

#### 4.2 Script Execution
- **Scenario**: Compiled script execution
- **Load**: 1000 script executions per minute
- **Metrics**: Execution time, assembly cache hit rate
- **Expected**: P95 < 100ms (cached), < 500ms (first execution)

#### 4.3 Assembly Caching
- **Scenario**: Verify assembly cache effectiveness
- **Load**: Repeated script executions
- **Metrics**: Cache hit rate, memory usage
- **Expected**: > 90% cache hit rate after warm-up

## Tools & Infrastructure

> **Infrastructure Context**: AWS ECS Fargate (.NET Core 9.0), API Gateway → NLB → ECS, RDS SQL Server Express
> 
> **Key Considerations**:
> - API Gateway in front (adds latency, has rate limits/throttling)
> - Private VPC (may require VPN or EC2 instance for testing)
> - Linux containers (no Windows tools)
> - No SSH access (use ECS Exec for container access)
> - CloudWatch already integrated

### Load Testing Tools

#### Primary Tool: k6 (Recommended)
- **Rationale**: Modern, developer-friendly, good performance, JavaScript-based scripting
- **Features**:
  - HTTP/1.1 and HTTP/2 support
  - Real-time metrics
  - Cloud execution option
  - Good integration with CI/CD
- **Scripting**: JavaScript/TypeScript
- **Metrics**: Built-in performance metrics, custom metrics support
- **Infrastructure Considerations**:
  - Can test via API Gateway endpoint (handles HTTPS)
  - For PRIVATE API Gateway: May need VPN or EC2 instance in VPC
  - Test both paths: API Gateway (real-world) and direct NLB (backend-only)
  - Account for API Gateway throttling (default 10,000 req/sec)

#### Alternative Tools
- **JMeter**: Mature, GUI-based, extensive plugin ecosystem
- **Artillery**: Node.js based, good for API testing
- **NBomber**: .NET native, good for .NET teams
- **Locust**: Python-based, code-based test definitions, good for distributed load

### Monitoring Tools

#### AWS CloudWatch Container Insights (Primary for Infrastructure)
- **Purpose**: Automatic ECS metrics collection for containers
- **Features**: CPU, memory, network metrics per container/task
- **Setup**: Enable in ECS cluster settings
- **Benefits**: No code changes, automatic collection, per-container visibility
- **Best For**: Infrastructure metrics (CPU, memory, network per task)

#### AWS X-Ray (Primary for Distributed Tracing)
- **Purpose**: End-to-end request tracing across API Gateway → NLB → ECS → RDS
- **Features**: Request latency breakdown, service map, error analysis
- **Setup**: Add X-Ray SDK to .NET Core application
- **Benefits**: Understand latency across all components, identify bottlenecks
- **Best For**: Distributed tracing, latency analysis, service dependency mapping

#### AWS CloudWatch Metrics (Already Available)
- **ECS Metrics**: CPUUtilization, MemoryUtilization per service
- **API Gateway Metrics**: Count, Latency, 4XXError, 5XXError, IntegrationLatency
- **NLB Metrics**: ActiveFlowCount, ProcessedBytes, HealthyHostCount, TargetResponseTime
- **RDS Metrics**: CPUUtilization, DatabaseConnections, ReadLatency, WriteLatency
- **Benefits**: Real-time monitoring, dashboards, alarms
- **Best For**: Real-time monitoring, alerting, historical trends

#### CloudWatch Logs Insights (Already Configured)
- **Purpose**: Query and analyze application logs
- **Setup**: Already configured (`/ecs/payroll-engine-backend`)
- **Features**: Query Serilog logs, analyze errors, performance patterns
- **Best For**: Log analysis, error tracking, custom metrics from logs

#### Infrastructure Monitoring
- **CloudWatch Container Insights**: Automatic ECS container metrics
- **CloudWatch ServiceLens**: Combines X-Ray traces with CloudWatch metrics (optional)
- **Database Monitoring**: Query Store, Extended Events (see Database Performance Testing Plan)
- **Application Logs**: Serilog logs via CloudWatch Logs Insights

### Database Profiling
- **Query Store**: Query performance history (recommended)
- **Extended Events**: Low-overhead event tracking (recommended)
- **RDS Performance Insights**: If available for SQL Server Express
- **Execution Plans**: Analyze query optimization
- **Note**: SQL Server Profiler not recommended (high overhead) - use Extended Events instead

### Code Profiling Tools

#### .NET Diagnostic Tools (Linux-Compatible)
- **dotnet-counters**: Real-time performance counter collection
  - **Usage**: Monitor CPU, memory, GC, HTTP requests
  - **Access**: Via ECS Exec or run in container
  - **Best For**: Real-time performance metrics
  
- **dotnet-trace**: Collect profiling data
  - **Usage**: Collect trace data for performance analysis
  - **Access**: Via ECS Exec
  - **Best For**: CPU profiling, method-level performance analysis
  
- **dotnet-dump**: Collect and analyze crash dumps
  - **Usage**: Collect memory dumps for analysis
  - **Access**: Via ECS Exec
  - **Best For**: Memory leak analysis, crash investigation

#### ECS Exec (Container Access Method)
- **Purpose**: Execute commands in running Fargate containers
- **Usage**: `aws ecs execute-command` to access running container
- **Benefits**: Run diagnostic tools, inspect running processes
- **Best For**: Running dotnet diagnostic tools, container inspection

**Note**: Windows-focused tools (dotTrace, PerfView, Visual Studio Diagnostic Tools) are not suitable for Linux containers in ECS Fargate.

### Test Data Management
- **Data Generation**: Custom scripts or tools to generate test data
- **Data Refresh**: Automated database restore/refresh procedures
- **Data Sanitization**: Ensure no sensitive production data

### Infrastructure Setup
- **Load Generators**: 
  - Local machine (for public API Gateway)
  - EC2 instance in VPC (for private API Gateway testing)
  - Multiple machines or cloud instances for distributed load
- **Network Configuration**: 
  - Ensure sufficient bandwidth and low latency
  - Consider API Gateway throttling limits
  - Test both API Gateway and direct NLB paths
- **Monitoring Infrastructure**: 
  - CloudWatch dashboards (centralized)
  - X-Ray service map
  - CloudWatch Container Insights

## Metrics & KPIs

### Application Metrics

#### Response Time Metrics
- **P50 (Median)**: 50th percentile response time
- **P95**: 95th percentile response time
- **P99**: 99th percentile response time
- **P99.9**: 99.9th percentile response time
- **Min/Max**: Minimum and maximum response times
- **Average**: Mean response time

#### Throughput Metrics
- **Requests Per Second (RPS)**: Total requests processed per second
- **Transactions Per Second (TPS)**: Business transactions per second
- **Bytes Per Second**: Data transfer rate
- **Concurrent Users**: Number of simultaneous users/requests

#### Error Metrics
- **Error Rate**: Percentage of failed requests
- **Error Types**: Breakdown by error type (4xx, 5xx, timeouts)
- **Error Distribution**: Error rate over time
- **Retry Rate**: Percentage of requests requiring retries

#### Business Metrics
- **Pay Run Completion Rate**: Successful pay run executions
- **Data Ingestion Rate**: Records processed per second
- **Script Compilation Success Rate**: Successful script compilations
- **Cache Hit Rate**: Assembly and data cache effectiveness

### Infrastructure Metrics

#### Server Metrics
- **CPU Usage**: Average, peak, per-core utilization
- **Memory Usage**: Used, available, peak memory
- **Disk I/O**: Read/write operations, throughput, latency
- **Network I/O**: Bandwidth usage, packet loss, latency

#### Database Metrics
- **Query Execution Time**: Average, P95, P99 query times
- **Database CPU**: CPU utilization
- **Database Memory**: Memory usage, buffer pool hit rate
- **Connection Pool**: Active connections, wait time, pool utilization
- **Lock Contention**: Lock waits, deadlocks, blocking
- **Transaction Log**: Log growth, backup frequency
- **Index Usage**: Index hit rate, missing indexes

#### Application-Specific Metrics
- **Script Compilation Time**: Time to compile C# scripts
- **Assembly Cache Hit Rate**: Percentage of cached assemblies used
- **Database Connection Wait Time**: Time waiting for database connections
- **Transaction Duration**: Long-running transaction times
- **OData Query Complexity**: Query execution time by complexity

### Key Performance Indicators (KPIs)
1. **System Responsiveness**: P95 response time < SLA targets
2. **System Throughput**: RPS meets or exceeds targets
3. **System Stability**: Error rate < 0.1% under normal load
4. **Resource Efficiency**: CPU/Memory usage within targets
5. **Database Performance**: Query times within targets, no deadlocks
6. **Scalability**: Linear performance scaling with load increase

## Test Execution Plan

### Week 1: Setup & Baseline Testing

#### Day 1-2: Environment Setup
- [ ] Provision test environment
- [ ] Restore production database snapshot (sanitized)
- [ ] Configure application settings for performance testing
- [ ] Set up monitoring tools and dashboards
- [ ] Install and configure load testing tools
- [ ] Validate test environment connectivity
- [ ] Create test data generation scripts

#### Day 3-5: Baseline Testing
- [ ] Execute baseline tests for all critical endpoints
- [ ] Document baseline performance metrics
- [ ] Identify obvious performance issues
- [ ] Create performance baseline report

### Week 2: Load & Stress Testing

#### Day 1-2: Load Testing - Employee Operations
- [ ] Employee Data Ingestion Pipeline load tests
- [ ] Employee CRUD operation load tests
- [ ] Employee case value operation tests
- [ ] Analyze results and document findings

#### Day 3-4: Load Testing - Pay Run Operations
- [ ] Pay run creation and execution load tests
- [ ] Concurrent pay run execution tests
- [ ] Pay run result retrieval tests
- [ ] Scripting engine performance tests
- [ ] Analyze results and document findings

#### Day 5: Load Testing - Lookup Operations
- [ ] Lookup Ingestion Pipeline load tests
- [ ] Lookup query performance tests
- [ ] Bulk lookup operation tests
- [ ] Analyze results and document findings

### Week 3: Advanced Testing & Analysis

#### Day 1-2: Stress & Spike Testing
- [ ] Stress tests (gradual load increase)
- [ ] Spike tests (sudden load increases)
- [ ] Identify breaking points
- [ ] Document failure modes and recovery

#### Day 3: Endurance Testing
- [ ] Start 8-24 hour endurance test
- [ ] Monitor for memory leaks
- [ ] Check for performance degradation
- [ ] Validate connection pool stability

#### Day 4-5: Analysis & Optimization
- [ ] Analyze all test results
- [ ] Identify performance bottlenecks
- [ ] Create optimization recommendations
- [ ] Document findings and create final report

### Continuous Activities
- Daily test execution reviews
- Real-time monitoring and alerting
- Issue tracking and resolution
- Documentation updates

## Reporting & Documentation

### Test Reports

#### Daily Progress Reports
- Test execution status
- Issues encountered
- Preliminary findings
- Next day plan

#### Weekly Summary Reports
- Completed test scenarios
- Key findings and metrics
- Performance trends
- Risk updates

#### Final Performance Test Report
- **Executive Summary**: High-level findings and recommendations
- **Test Overview**: Scope, approach, environment
- **Test Results**: Detailed results for all scenarios
- **Performance Baselines**: Documented baseline metrics
- **Bottleneck Analysis**: Identified issues and root causes
- **Recommendations**: Optimization and improvement suggestions
- **Appendices**: Detailed metrics, charts, test scripts

### Documentation Deliverables
1. **Performance Test Plan** (this document)
2. **Test Scripts**: All load testing scripts and configurations
3. **Performance Baselines**: Baseline metrics for all endpoints
4. **Performance Test Report**: Comprehensive test results and analysis
5. **Optimization Recommendations**: Detailed improvement suggestions
6. **Performance Monitoring Guide**: How to monitor performance in production
7. **Regression Test Suite**: Automated tests for performance regression

### Metrics Dashboard
- Real-time performance metrics during testing
- Historical performance trends
- Comparison charts (before/after optimization)
- Alert configurations
