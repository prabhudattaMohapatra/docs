# Database Performance Testing Plan

## Table of Contents
- Objectives
- Scope
- Test Environment
- Performance Targets
- Test Scenarios
- Tools & Methods
- Metrics to Track
- Test Execution Plan
- Success Criteria

## Objectives

1. **Measure Query Performance**: Measure and document performance metrics for all critical queries (execution times, resource usage, frequency)

2. **Identify Slow Queries**: Identify queries exceeding performance thresholds and analyze root causes

3. **Evaluate Connection Pooling**: Measure connection pool behavior, utilization, and capacity under various load conditions

4. **Evaluate Lock Contention**: Measure and document blocking, deadlock frequency, and lock wait times

5. **Measure Resource Utilization**: Measure CPU, memory, disk I/O, and network utilization under different load patterns

6. **Evaluate Infrastructure**: Evaluate database server configuration, hardware capacity, and infrastructure adequacy for current and projected workloads

## Scope

### In Scope
- **Query Performance Measurement**: Measure execution times, resource consumption, and frequency for all critical queries (employee, pay run, lookup operations)
- **Connection Management Evaluation**: Evaluate connection pooling behavior, utilization patterns, timeout handling, and capacity limits
- **Transaction Performance Evaluation**: Measure and evaluate lock contention, deadlock frequency, blocking patterns, and long-running transaction behavior
- **Database Resource Measurement**: Measure CPU utilization, memory usage (buffer pool, page life expectancy), disk I/O performance, and network latency
- **Infrastructure Evaluation**: Evaluate database server configuration (SQL Server settings), hardware specifications (CPU, RAM, storage), network infrastructure, and overall infrastructure adequacy

### Out of Scope
- Database backup/restore performance
- Database migration
- Replication and Scaling

## Test Environment

**Database**: SQL Server Express (Currently being used on RDS)

**Data Volume**: ~700 concurrent employees, 12+ months history

**Isolation**: Dedicated DB and CloudFormation Stack, isolated from Dev Environment

**Comparison Environment**:
- **Database**: Containerized Database (SQL Server)

**Test Data**:
- At least 2 tenants
- ~700 employees per tenant (India)
- 12+ months of Employee Case Changes
- Complete lookup sets (Swiss)

## Test Scenarios

### Scenario 1: Query Performance Measurement

**Objective**: Measure and document query performance metrics for all critical queries

**Tests**:
- Capture all queries during normal application load
- Measure execution times (P50, P95, P99, max) for all queries
- Measure resource consumption (CPU time, logical/physical reads) per query
- Measure query execution frequency
- Identify queries > 500ms execution time
- Analyze execution plans to understand query behavior:
  - Index usage patterns
  - Table scans vs index seeks
  - Join strategies
  - Parameter sniffing impact
- Test complex OData queries with various filters and measure performance
- Test queries with large result sets (pagination) and measure performance

**Expected Results**:
- Comprehensive query performance metrics (execution times, resource usage, frequency)
- List of slow queries with detailed performance analysis
- Execution plan analysis report
- Query performance baseline documentation

### Scenario 2: Connection Pool Evaluation

**Objective**: Evaluate connection pooling behavior and capacity under various load conditions

**Tests**:
- Baseline: Measure connection usage under normal load (50 concurrent requests)
- Stress: Gradually increase to 100, 150, 200 concurrent connections
- Measure and document:
  - Active connection count at each load level
  - Connection pool utilization percentage
  - Connection wait times
  - Connection timeout error rates
  - Connection acquisition patterns
- Test connection leak detection (long-running test)

**Expected Results**:
- Connection pool utilization metrics at various load levels
- Maximum concurrent connections supported before degradation
- Connection wait time measurements
- Connection pool capacity evaluation
- Connection behavior analysis report

### Scenario 3: Lock Contention & Deadlock Evaluation

**Objective**: Measure and evaluate lock contention, blocking patterns, and deadlock frequency

**Tests**:
- Concurrent pay run executions (10 concurrent)
- Concurrent employee updates (100+ concurrent)
- Concurrent case value updates (200+ concurrent)
- Measure and document:
  - Blocking session counts and duration
  - Deadlock frequency and patterns
  - Lock wait times
  - Lock escalation events
  - Lock types and distribution

**Expected Results**:
- Deadlock frequency measurements and analysis
- Blocking session reports with duration metrics
- Lock contention measurement data
- Lock wait time statistics
- Transaction isolation level impact evaluation

### Scenario 4: Bulk Operation Performance Measurement

**Objective**: Measure performance characteristics of bulk data operations

**Tests**:
- Bulk lookup value operations (5000+ values)
- Measure and document:
  - Transaction duration
  - Throughput (records per second)
  - Lock escalation frequency
  - Transaction log growth rate
  - TempDB usage and contention
  - Resource consumption (CPU, memory, I/O)

**Expected Results**:
- Bulk operation performance metrics (duration, throughput)
- Transaction behavior analysis
- Resource consumption measurements
- Lock escalation patterns
- Bulk operation capacity evaluation

### Scenario 5: Resource Utilization Measurement

**Objective**: Measure database server resource utilization under various load patterns

**Tests**:
- Normal load: Measure baseline resource usage
- Peak load: Measure during stress tests
- Sustained load: 2-4 hour endurance test
- Measure and document:
  - CPU utilization (per core, average, peak)
  - Memory usage (buffer pool hit ratio, page life expectancy, memory grants)
  - Disk I/O (latency, throughput, queue depth, IOPS)
  - Network latency and bandwidth
  - Resource utilization trends over time

**Expected Results**:
- Resource utilization metrics at different load levels
- Resource utilization trends and patterns
- Bottleneck identification and measurement
- Infrastructure capacity evaluation

### Scenario 6: Infrastructure Evaluation

**Objective**: Evaluate database server configuration, hardware specifications, and infrastructure adequacy

**Evaluation Areas**:
- **SQL Server Configuration**:
  - Memory settings (min/max server memory) and current allocation
  - MAXDOP and parallelism settings and their impact
  - Cost threshold for parallelism configuration
  - Database file configuration (autogrowth settings, file placement, sizing)
  - TempDB configuration (file count, sizing, placement)
- **Hardware Specifications**:
  - CPU cores, speed, and architecture
  - RAM capacity and configuration
  - Storage type, configuration (SSD, HDD, RAID level), and performance characteristics
  - Network bandwidth and latency characteristics
- **Index Configuration**:
  - Index fragmentation levels
  - Index usage statistics
  - Missing index opportunities (for documentation, not implementation)

**Expected Results**:
- Infrastructure configuration audit report
- Hardware specifications documentation
- Configuration adequacy evaluation
- Infrastructure capacity assessment for current and projected workloads

## Tools & Methods

> **Infrastructure Context**: AWS RDS SQL Server Express Edition (db.t3.medium: 2 vCPU, 4GB RAM, 20GB storage)

### AWS RDS Native Tools (Infrastructure Evaluation)

#### AWS Performance Insights
- **Purpose**: Built-in RDS monitoring tool for database load metrics, wait events, and top SQL statements
- **Usage**: Enable via RDS Console → Modify → Performance Insights
- **Benefits**: Automatic data collection, visual dashboards, minimal performance impact
- **Best For**: Infrastructure-level performance evaluation, identifying bottlenecks (CPU, I/O, locks), top SQL identification
- **Limitations**: SQL Server Express may have limited features; verify availability

#### AWS CloudWatch Metrics
- **Purpose**: Automatic collection of RDS instance metrics (CPU, memory, storage, I/O)
- **Usage**: Access via CloudWatch Console → Metrics → RDS
- **Benefits**: No performance impact, 15 months retention, easy dashboards and alarms
- **Best For**: Infrastructure resource utilization monitoring
- **Key Metrics**: CPUUtilization, DatabaseConnections, ReadLatency, WriteLatency, FreeableMemory, FreeStorageSpace

#### AWS CloudWatch Logs
- **Purpose**: Automatic collection of SQL Server error logs
- **Usage**: Enable via RDS Console → Modify → Log exports
- **Benefits**: Searchable log data, integration with CloudWatch Insights
- **Best For**: Error and deadlock detection, SQL Server error analysis

### SQL Server Native Tools (Query Performance)

#### SQL Server Query Store
- **Purpose**: Track query performance over time automatically
- **Usage**: Enable on test database, capture query execution statistics
- **Benefits**: Historical performance data, query regression detection, low overhead (< 2% CPU)
- **Best For**: Query performance measurement, identifying slow queries, execution plan analysis
- **Setup**:
  ```sql
  ALTER DATABASE [DatabaseName] SET QUERY_STORE = ON
  (
      OPERATION_MODE = READ_WRITE,
      CLEANUP_POLICY = (STALE_QUERY_THRESHOLD_DAYS = 30),
      DATA_FLUSH_INTERVAL_SECONDS = 900,
      MAX_STORAGE_SIZE_MB = 100,
      INTERVAL_LENGTH_MINUTES = 15
  );
  ```

#### Dynamic Management Views (DMVs)
- **Purpose**: Real-time performance metrics
- **Usage**: Query DMVs directly (no setup required)
- **Benefits**: Zero overhead, real-time data, comprehensive system information
- **Best For**: Real-time performance monitoring, active query analysis, connection pool evaluation, lock and blocking analysis
- **Key DMVs**:
  - `sys.dm_exec_query_stats`: Query performance statistics
  - `sys.dm_exec_requests`: Currently executing queries
  - `sys.dm_os_wait_stats`: Wait statistics
  - `sys.dm_db_index_usage_stats`: Index usage
  - `sys.dm_tran_locks`: Lock information

#### Extended Events
- **Purpose**: Low-overhead query and performance monitoring
- **Usage**: Create sessions to capture slow queries, deadlocks, blocking
- **Benefits**: Minimal performance impact, detailed event data, highly configurable
- **Best For**: Detailed query capture, deadlock graphs, blocking analysis
- **Limitations**: SQL Server Express has limited events available
- **Setup Example**:
  ```sql
  CREATE EVENT SESSION [SlowQueries] ON SERVER
  ADD EVENT sqlserver.rpc_completed(
      ACTION(sqlserver.sql_text)
      WHERE duration > 5000000)
  ADD TARGET package0.ring_buffer
  WITH (MAX_MEMORY = 4 MB);
  ALTER EVENT SESSION [SlowQueries] ON SERVER STATE = START;
  ```

### Additional Tools

- **Execution Plan Analysis**: SSMS execution plan viewer
- **Index Analysis**: `sys.dm_db_missing_index_details`
- **Custom Scripts**: SQL scripts for connection pool testing, load generation

### Monitoring Setup

1. **Enable Query Store**:
   ```sql
   ALTER DATABASE [DatabaseName] SET QUERY_STORE = ON;
   ```

2. **Create Extended Events Session** for slow queries:
   - Capture queries > 500ms
   - Include execution plan
   - Capture blocking information

3. **Set Up AWS Performance Insights**:
   - Enable via RDS Console → Modify → Performance Insights

4. **Set Up CloudWatch Dashboard**:
   - Create custom dashboard in CloudWatch Console
   - Add RDS metrics: CPUUtilization, DatabaseConnections, ReadLatency, WriteLatency, FreeableMemory

## Metrics to Track

### Query Metrics
- **Execution Time**: Average, P95, P99, maximum
- **CPU Time**: CPU time vs elapsed time
- **Logical Reads**: Number of pages read
- **Physical Reads**: Disk I/O operations
- **Execution Count**: Frequency of query execution
- **Plan Cache Hit Ratio**: Percentage of queries using cached plans

### Connection Metrics
- **Active Connections**: Current number of active connections
- **Connection Pool Utilization**: Percentage of pool in use
- **Connection Wait Time**: Time waiting for available connection
- **Connection Timeout Rate**: Percentage of connection timeouts
- **Connection Leaks**: Connections not properly released

### Lock & Blocking Metrics
- **Blocking Sessions**: Number of sessions being blocked
- **Deadlock Count**: Number of deadlocks per time period
- **Lock Wait Time**: Average time waiting for locks
- **Lock Escalation Events**: Number of lock escalations
- **Lock Types**: Breakdown by lock type (shared, exclusive, etc.)

### Resource Metrics
- **CPU Utilization**: Average, peak, per-core utilization
- **Memory Usage**:
  - Buffer pool hit ratio (target > 95%)
  - Page life expectancy (target > 300 seconds)
  - Memory grants pending
- **Disk I/O**:
  - Read/write latency (target < 20ms)
  - Throughput (IOPS)
  - Queue depth
- **Network**: Latency between application and database servers

### Database-Specific Metrics
- **Transaction Log**: Growth rate, space usage
- **TempDB**: Usage, contention, file configuration
- **Index Fragmentation**: Average fragmentation percentage
- **Statistics**: Last update time, staleness

## Test Execution Plan

### Setup:
- [ ] Set up test environment
- [ ] Enable Query Store
- [ ] Configure Extended Events sessions
- [ ] Set up performance monitoring (Performance Insights, CloudWatch)
- [ ] Validate test data availability

### Set Baseline:
- [ ] Run baseline queries (single execution)
- [ ] Capture baseline execution plans
- [ ] Document baseline metrics
- [ ] Identify obvious performance issues

### Query Performance Analysis:
- [ ] Run application load tests (from backend testing)
- [ ] Capture all queries using Query Store/Extended Events
- [ ] Identify slow queries (> 500ms)
- [ ] Analyze execution plans for slow queries
- [ ] Generate missing index recommendations
- [ ] Test complex OData queries
- [ ] Analyze query plan cache effectiveness
- [ ] Document query performance findings

### Connection & Lock Testing:
- [ ] Connection pool stress testing
  - Baseline (50 connections)
  - Increase to 100, 150, 200
  - Monitor connection metrics
- [ ] Document connection pool findings
- [ ] Concurrent transaction testing
  - Concurrent pay runs (10-20)
  - Concurrent employee updates (100+)
  - Concurrent case value updates (200+)
- [ ] Monitor blocking and deadlocks
- [ ] Analyze deadlock graphs

### Bulk Operations & Resource Monitoring:
- [ ] Bulk operation testing
  - Bulk lookup values (5000+)
- [ ] Monitor transaction performance
- [ ] Analyze lock escalation
- [ ] Resource utilization monitoring
  - Normal load baseline
  - Peak load monitoring
  - Start 2-hour endurance test
- [ ] Monitor CPU, memory, disk I/O
- [ ] Analyze resource trends

### Analysis & Reporting:
- [ ] Complete endurance test analysis
- [ ] Infrastructure configuration audit
- [ ] Index fragmentation analysis
- [ ] Compile all test results
- [ ] Create performance evaluation report
- [ ] Document all measurements and findings
- [ ] Prioritize findings by impact
- [ ] Create evaluation summary


## Key Deliverables

1. **Database Performance Evaluation Report**
   - Executive summary
   - Query performance measurements and analysis
   - Connection pool evaluation and capacity assessment
   - Lock contention measurements and analysis
   - Resource utilization measurements and trends
   - Infrastructure evaluation (configuration and hardware)
   - Findings and observations

2. **Query Performance Measurement Report**
   - Comprehensive query performance metrics (execution times, resource usage, frequency)
   - List of slow queries with detailed measurements
   - Execution plan analysis
   - Query performance baseline documentation

3. **Infrastructure Evaluation Report**
   - Configuration audit and evaluation
   - Hardware specifications documentation
   - Infrastructure adequacy assessment
   - Capacity evaluation for current and projected workloads

4. **Monitoring Setup Documentation**
   - Query Store configuration
   - Extended Events sessions setup
   - Performance Insights and CloudWatch configuration
   - Measurement procedures

## Appendix: Key SQL Queries

### Find Slow Queries
```sql
SELECT TOP 20
    qs.execution_count,
    qs.total_elapsed_time / 1000 AS total_elapsed_time_ms,
    qs.avg_elapsed_time / 1000 AS avg_elapsed_time_ms,
    qs.max_elapsed_time / 1000 AS max_elapsed_time_ms,
    SUBSTRING(qt.text, (qs.statement_start_offset/2)+1,
        ((CASE qs.statement_end_offset
            WHEN -1 THEN DATALENGTH(qt.text)
            ELSE qs.statement_end_offset
        END - qs.statement_start_offset)/2)+1) AS query_text
FROM sys.dm_exec_query_stats qs
CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) qt
ORDER BY qs.avg_elapsed_time DESC;
```

### Missing Index Recommendations
```sql
SELECT 
    migs.avg_total_user_cost * (migs.avg_user_impact / 100.0) * (migs.user_seeks + migs.user_scans) AS improvement_measure,
    'CREATE INDEX [missing_index_' + CONVERT (varchar, mig.index_group_handle) + '_' + CONVERT (varchar, mid.index_handle) + '_' + LEFT (PARSENAME(mid.statement, 1), 32) + ']'
    + ' ON ' + mid.statement
    + ' (' + ISNULL (mid.equality_columns,'')
    + CASE WHEN mid.equality_columns IS NOT NULL AND mid.inequality_columns IS NOT NULL THEN ',' ELSE '' END
    + ISNULL (mid.inequality_columns, '')
    + ')'
    + ISNULL (' INCLUDE (' + mid.included_columns + ')', '') AS create_index_statement,
    migs.*, mid.database_id, mid.[statement]
FROM sys.dm_db_missing_index_groups mig
INNER JOIN sys.dm_db_missing_index_group_stats migs ON migs.group_handle = mig.index_group_handle
INNER JOIN sys.dm_db_missing_index_details mid ON mig.index_handle = mid.index_handle
WHERE migs.avg_total_user_cost * (migs.avg_user_impact / 100.0) * (migs.user_seeks + migs.user_scans) > 10
ORDER BY migs.avg_total_user_cost * migs.avg_user_impact * (migs.user_seeks + migs.user_scans) DESC;
```

### Current Blocking
```sql
SELECT 
    session_id,
    blocking_session_id,
    wait_type,
    wait_time,
    wait_resource,
    command,
    DB_NAME(database_id) AS database_name,
    text AS query_text
FROM sys.dm_exec_requests
CROSS APPLY sys.dm_exec_sql_text(sql_handle)
WHERE blocking_session_id > 0;
```

### Connection Pool Usage
```sql
SELECT 
    COUNT(*) AS active_connections,
    DB_NAME(database_id) AS database_name,
    login_name,
    program_name
FROM sys.dm_exec_sessions
WHERE is_user_process = 1
GROUP BY database_id, login_name, program_name
ORDER BY active_connections DESC;
```
