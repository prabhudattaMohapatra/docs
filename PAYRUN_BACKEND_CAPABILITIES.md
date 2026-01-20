# Payroll Engine Backend - Payrun Capabilities

Complete documentation of payrun capabilities in the backend, excluding company and employee management.

---

## Table of Contents

1. [Payrun Management](#payrun-management)
2. [Payrun Job Operations](#payrun-job-operations)
3. [Payrun Execution & Processing](#payrun-execution--processing)
4. [Payrun Results](#payrun-results)
5. [Payrun Parameters](#payrun-parameters)
6. [Client.Core Dependencies](#clientcore-dependencies)
7. [API Endpoints Summary](#api-endpoints-summary)

---

## Payrun Management

### Base Route
```
/api/tenants/{tenantId}/payruns
```

### 1. Query Payruns
**Endpoint**: `GET /api/tenants/{tenantId}/payruns`

**Description**: Query payruns with OData filtering, sorting, and pagination

**Query Parameters**:
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `select` (string): OData field selection
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top
- `status` (ActiveStatus): Filter by active/inactive status
- `result` (QueryResultType): Result type (items, count, or both)

**Response**: Array of Payrun objects

**Capabilities**:
- ✅ OData filtering and sorting
- ✅ Pagination
- ✅ Field selection
- ✅ Status filtering

---

### 2. Get Payrun
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/{payrunId}`

**Description**: Get a single payrun by ID

**Response**: Payrun object

**Payrun Model**:
```csharp
public class Payrun
{
    public int PayrollId { get; set; }           // Required
    public string Name { get; set; }             // Required, max 128 chars
    public Dictionary<string, string> NameLocalizations { get; set; }
    public string DefaultReason { get; set; }
    public Dictionary<string, string> DefaultReasonLocalizations { get; set; }
    
    // Expression-based configuration
    public string StartExpression { get; set; }              // Payrun start logic
    public string EmployeeAvailableExpression { get; set; }   // Employee availability
    public string EmployeeStartExpression { get; set; }       // Employee start logic
    public string EmployeeEndExpression { get; set; }         // Employee end logic
    public string WageTypeAvailableExpression { get; set; }  // Wage type availability
    public string EndExpression { get; set; }                 // Payrun end logic
    
    // Retro pay support
    public RetroTimeType RetroTimeType { get; set; }
}
```

**Key Features**:
- ✅ Expression-based payrun control
- ✅ Employee availability filtering
- ✅ Wage type availability filtering
- ✅ Retro pay support
- ✅ Localization support

---

### 3. Create Payrun
**Endpoint**: `POST /api/tenants/{tenantId}/payruns`

**Description**: Create a new payrun

**Request Body**: Payrun object

**Validation**:
- ✅ Payroll must exist
- ✅ Unique name within payroll
- ✅ Required fields validation

**Response**: Created Payrun object (201 Created)

---

### 4. Update Payrun
**Endpoint**: `PUT /api/tenants/{tenantId}/payruns/{payrunId}`

**Description**: Update an existing payrun

**Response**: Updated Payrun object (200 OK)

---

### 5. Rebuild Payrun
**Endpoint**: `PUT /api/tenants/{tenantId}/payruns/{payrunId}/rebuild`

**Description**: Rebuild payrun with updated regulations and data

**Response**: 200 OK

**Use Case**: After regulation changes, rebuild payrun to apply updates

---

### 6. Delete Payrun
**Endpoint**: `DELETE /api/tenants/{tenantId}/payruns/{payrunId}`

**Description**: Delete a payrun

**Response**: 204 No Content

---

## Payrun Job Operations

### Base Route
```
/api/tenants/{tenantId}/payruns/jobs
```

### 1. Query Payrun Jobs
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/jobs`

**Description**: Query payrun jobs with OData filtering

**Query Parameters**:
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top
- `result` (QueryResultType): Result type

**Response**: Array of PayrunJob objects

**Example Filters**:
```http
# Filter by status
GET /api/tenants/1/payruns/jobs?$filter=JobStatus eq 'Draft'

# Filter by payrun
GET /api/tenants/1/payruns/jobs?$filter=PayrunId eq 123

# Filter by period
GET /api/tenants/1/payruns/jobs?$filter=PeriodStart ge 2025-01-01
```

---

### 2. Query Employee Payrun Jobs
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/jobs/employees/{employeeId}`

**Description**: Query payrun jobs for a specific employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Query Parameters**: Same as Query Payrun Jobs

**Response**: Array of PayrunJob objects for the employee

**Capabilities**:
- ✅ Employee-specific job history
- ✅ OData filtering
- ✅ Pagination
- ✅ Count queries

---

### 3. Get Payrun Job
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/jobs/{payrunJobId}`

**Description**: Get a specific payrun job by ID

**Response**: PayrunJob object

**PayrunJob Model**:
```csharp
public class PayrunJob
{
    // Identity
    public string Name { get; set; }              // Required, max 128 chars
    public string Owner { get; set; }             // Job owner
    public int PayrunId { get; set; }            // Required
    public int PayrollId { get; set; }           // Required
    public int DivisionId { get; set; }
    public int? ParentJobId { get; set; }        // For retro pay jobs
    
    // User tracking
    public int CreatedUserId { get; set; }        // Required
    public int? ReleasedUserId { get; set; }
    public int? ProcessedUserId { get; set; }
    public int? FinishedUserId { get; set; }
    
    // Status
    public PayrunJobStatus JobStatus { get; set; }
    public PayrunJobResult JobResult { get; set; }
    
    // Retro pay
    public RetroPayMode RetroPayMode { get; set; }
    
    // Period information
    public string CycleName { get; set; }         // Required
    public DateTime CycleStart { get; set; }      // Required
    public DateTime CycleEnd { get; set; }       // Required
    public string PeriodName { get; set; }       // Required
    public DateTime PeriodStart { get; set; }    // Required
    public DateTime PeriodEnd { get; set; }      // Required
    public DateTime EvaluationDate { get; set; } // Required
    
    // Dates
    public DateTime JobStart { get; set; }        // Required
    public DateTime? JobEnd { get; set; }
    public DateTime? Released { get; set; }
    public DateTime? Processed { get; set; }
    public DateTime? Finished { get; set; }
    
    // Reasons
    public string CreatedReason { get; set; }     // Required
    public string ReleasedReason { get; set; }
    public string ProcessedReason { get; set; }
    public string FinishedReason { get; set; }
    
    // Progress
    public int TotalEmployeeCount { get; set; }
    public int ProcessedEmployeeCount { get; set; }
    
    // Metadata
    public string Message { get; set; }
    public string ErrorMessage { get; set; }
    public string Forecast { get; set; }
    public string Culture { get; set; }
    public List<string> Tags { get; set; }
    public Dictionary<string, object> Attributes { get; set; }
    
    // Employees
    public PayrunJobEmployee[] Employees { get; set; }
}
```

**Job Status Flow**:
```
Draft → Released → Processed → Finished
         ↓           ↓
      Cancelled   Cancelled
```

**Job Status Values**:
- `Draft`: Job created but not released
- `Released`: Job released for processing
- `Processed`: Job processing completed
- `Finished`: Job finalized
- `Cancelled`: Job cancelled

---

### 4. Start Payrun Job
**Endpoint**: `POST /api/tenants/{tenantId}/payruns/jobs`

**Description**: Start a new payrun job (creates and processes the job)

**Request Body**: PayrunJobInvocation
```csharp
public class PayrunJobInvocation
{
    public int UserId { get; set; }               // Required
    public int PayrunId { get; set; }            // Required
    public int? DivisionId { get; set; }
    public string Reason { get; set; }           // Required
    public string Forecast { get; set; }
    public string Owner { get; set; }
    public List<string> EmployeeIdentifiers { get; set; }  // Optional: specific employees
    public List<string> Tags { get; set; }
    public Dictionary<string, object> Attributes { get; set; }
    
    // Period configuration
    public DateTime? PeriodStart { get; set; }
    public DateTime? PeriodEnd { get; set; }
    public DateTime? EvaluationDate { get; set; }
    
    // Retro pay
    public RetroPayMode? RetroPayMode { get; set; }
    public DateTime? RetroDate { get; set; }
    public int? ParentJobId { get; set; }
    public List<int> RetroJobs { get; set; }
    
    // Processing options
    public LogLevel LogLevel { get; set; }
    public bool StoreEmptyResults { get; set; }
}
```

**Response**: Created PayrunJob object (201 Created)

**Validation**:
- ✅ User must exist
- ✅ Payrun must exist and be active
- ✅ Payroll must exist
- ✅ No open draft jobs for the same payroll (unless forecast)

**Processing**:
- ✅ Creates payrun job
- ✅ Validates payroll regulations
- ✅ Processes all employees (or specified employees)
- ✅ Executes payrun scripts
- ✅ Calculates payroll results
- ✅ Stores results in database

---

### 5. Get Payrun Job Status
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/jobs/{payrunJobId}/status`

**Description**: Get the current status of a payrun job

**Response**: Status string (e.g., "Draft", "Released", "Processed", "Finished")

---

### 6. Change Payrun Job Status
**Endpoint**: `PUT /api/tenants/{tenantId}/payruns/jobs/{payrunJobId}/status`

**Description**: Change the status of a payrun job

**Query Parameters**:
- `jobStatus` (PayrunJobStatus, required): New status
- `userId` (int, required): User making the change
- `reason` (string): Reason for status change
- `patchMode` (bool): Skip state validation (default: false)

**Status Transitions**:
- ✅ Validated state transitions
- ✅ Prevents invalid transitions
- ✅ Webhook notifications on status changes
- ✅ Patch mode for forced transitions

**Webhook Triggers**:
- `PayrunJobProcess`: When job moves to Processed
- `PayrunJobFinish`: When job moves to Finished

---

### 7. Delete Payrun Job
**Endpoint**: `DELETE /api/tenants/{tenantId}/payruns/jobs/{payrunJobId}`

**Description**: Delete a payrun job and its results

**Response**: 204 No Content

**Note**: Deletes associated payroll results

---

## Payrun Execution & Processing

### PayrunProcessor

The `PayrunProcessor` is the core engine that executes payrun jobs.

**Key Components**:
1. **PayrunContext**: Execution context with payroll, division, calendar, employees
2. **PayrunProcessorRegulation**: Regulation processing and script execution
3. **PayrunProcessorRepositories**: Data access layer
4. **ResultProvider**: Result storage and retrieval

**Processing Flow**:

```
1. Setup Phase
   ├── Load tenant, payrun, payroll
   ├── Load division and calendar
   ├── Setup case value caches (Global, National, Company, Employee)
   ├── Load employees (all or specified)
   └── Validate payroll regulations

2. Job Creation
   ├── Create PayrunJob record
   ├── Set period dates (cycle, period, evaluation)
   ├── Initialize job status (Draft)
   └── Store job in database

3. Employee Processing (for each employee)
   ├── Check employee availability (EmployeeAvailableExpression)
   ├── Execute PayrunEmployeeStart script
   ├── Process wage types
   │   ├── Check wage type availability (WageTypeAvailableExpression)
   │   ├── Execute wage type value scripts
   │   └── Calculate wage type results
   ├── Process collectors
   │   ├── Execute collector start scripts
   │   ├── Apply wage types to collectors
   │   └── Execute collector end scripts
   ├── Execute PayrunEmployeeEnd script
   └── Store employee results

4. Payrun Completion
   ├── Execute PayrunEnd script
   ├── Finalize job status
   ├── Store consolidated results
   └── Trigger webhooks
```

**Script Execution Points**:
- `PayrunStart`: Before processing employees
- `PayrunEmployeeAvailable`: Check if employee should be processed
- `PayrunEmployeeStart`: Before processing employee wage types
- `PayrunWageTypeAvailable`: Check if wage type should be calculated
- `PayrunEmployeeEnd`: After processing employee
- `PayrunEnd`: After all employees processed

**Features**:
- ✅ Expression-based employee filtering
- ✅ Expression-based wage type filtering
- ✅ Script-based customization
- ✅ Retro pay support
- ✅ Forecast support
- ✅ Multi-employee processing
- ✅ Error handling and logging
- ✅ Result storage

---

## Payrun Results

### Base Route
```
/api/tenants/{tenantId}/payrolls/{payrollId}/results
```

### 1. Query Payroll Results
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/results`

**Description**: Query payroll results from payrun jobs

**Query Parameters**:
- `employeeId` (int, optional): Filter by employee
- `divisionId` (int, optional): Filter by division
- `periodStart` (DateTime, optional): Filter by period start
- `periodEnd` (DateTime, optional): Filter by period end
- `resultNames` (string[], optional): Filter by result names
- `forecast` (string, optional): Filter by forecast
- `tags` (string[], optional): Filter by tags
- `jobStatus` (PayrunJobStatus, optional): Filter by job status
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top

**Response**: Array of PayrollResult objects

---

### 2. Get Payroll Result
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/results/{payrollResultId}`

**Description**: Get a specific payroll result

**Response**: PayrollResult object with:
- Wage type results
- Collector results
- Payrun results
- Custom results

---

### 3. Query Payrun Results
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/results/{payrollResultId}/payruns`

**Description**: Query payrun results for a payroll result

**Response**: Array of PayrunResult objects

---

### 4. Query Wage Type Results
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/results/{payrollResultId}/wagetypes`

**Description**: Query wage type results

**Response**: Array of WageTypeResult objects

---

### 5. Query Collector Results
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/results/{payrollResultId}/collectors`

**Description**: Query collector results

**Response**: Array of CollectorResult objects

---

### 6. Consolidated Results
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/consolidated`

**Description**: Get consolidated results across multiple periods

**Query Parameters**:
- `employeeId` (int, required)
- `periodStarts` (DateTime[], required): Array of period start dates
- `divisionId` (int, optional)
- `resultNames` (string[], optional)
- `forecast` (string, optional)
- `tags` (string[], optional)
- `evaluationDate` (DateTime, optional)
- `jobStatus` (PayrunJobStatus, optional)

**Response**: ConsolidatedPayrollResult with:
- Consolidated wage type results
- Consolidated collector results
- Consolidated payrun results

**Use Case**: Year-to-date calculations, multi-period reporting

---

## Payrun Parameters

### Base Route
```
/api/tenants/{tenantId}/payruns/{payrunId}/parameters
```

### 1. Query Payrun Parameters
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/{payrunId}/parameters`

**Description**: Query payrun parameters

**Query Parameters**: Standard OData query parameters

**Response**: Array of PayrunParameter objects

---

### 2. Get Payrun Parameter
**Endpoint**: `GET /api/tenants/{tenantId}/payruns/{payrunId}/parameters/{payrunParameterId}`

**Description**: Get a specific payrun parameter

**Response**: PayrunParameter object

---

### 3. Create Payrun Parameter
**Endpoint**: `POST /api/tenants/{tenantId}/payruns/{payrunId}/parameters`

**Description**: Create a payrun parameter

**Request Body**: PayrunParameter object

**Response**: Created PayrunParameter (201 Created)

---

### 4. Update Payrun Parameter
**Endpoint**: `PUT /api/tenants/{tenantId}/payruns/{payrunId}/parameters/{payrunParameterId}`

**Description**: Update a payrun parameter

**Response**: Updated PayrunParameter (200 OK)

---

### 5. Delete Payrun Parameter
**Endpoint**: `DELETE /api/tenants/{tenantId}/payruns/{payrunId}/parameters/{payrunParameterId}`

**Description**: Delete a payrun parameter

**Response**: 204 No Content

**Use Case**: Configure payrun-specific parameters (e.g., tax rates, thresholds)

---

## Client.Core Dependencies

### Package Structure

**PayrollEngine.Client.Core** provides:
- API client models
- HTTP client wrapper
- Service clients
- Exchange format support
- Query builders

### Key Components

#### 1. PayrollHttpClient
**Purpose**: HTTP client wrapper for backend API

**Features**:
- ✅ Authentication (API key)
- ✅ Request/response serialization
- ✅ Error handling
- ✅ Connection management
- ✅ SSL/TLS support

#### 2. Service Clients
**Services**:
- `TenantService`: Tenant operations
- `PayrunService`: Payrun CRUD operations
- `PayrunJobService`: Payrun job operations
- `PayrollResultService`: Result queries
- `PayrollConsolidatedResultService`: Consolidated results

#### 3. API Endpoints
**Endpoint Builders**:
- `PayrunApiEndpoints`: Payrun URL builders
- `PayrollResultApiEndpoints`: Result URL builders
- `PayrollConsolidatedResultApiEndpoints`: Consolidated result URLs

#### 4. Exchange Format
**Purpose**: Data import/export

**Components**:
- `Exchange`: Root data model
- `ExchangeExport`: Export payroll data
- `ExchangeImport`: Import payroll data
- `ExchangeReader`: Read JSON/zip files
- `ExchangeWriter`: Write JSON/zip files

**Supported Formats**:
- JSON files
- ZIP archives (multiple JSON files)

#### 5. Query Builders
**Purpose**: Build OData query expressions

**Components**:
- `QueryParameters`: Base query parameters
- `Filter`: Filter expressions
- `OrderBy`: Sorting
- `Select`: Field selection

**Example**:
```csharp
var query = new QueryParameters
{
    Filter = new Filter("JobStatus", "eq", "Draft"),
    OrderBy = new OrderBy("Created", OrderDirection.Descending),
    Top = 10
};
```

#### 6. Models
**Purpose**: API data models

**Key Models**:
- `Payrun`: Payrun definition
- `PayrunJob`: Payrun job instance
- `PayrunJobInvocation`: Job start request
- `PayrollResult`: Payroll calculation result
- `WageTypeResult`: Wage type calculation result
- `CollectorResult`: Collector calculation result
- `PayrunResult`: Payrun-specific result

---

## API Endpoints Summary

### Payrun Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tenants/{tenantId}/payruns` | Query payruns |
| GET | `/api/tenants/{tenantId}/payruns/{payrunId}` | Get payrun |
| POST | `/api/tenants/{tenantId}/payruns` | Create payrun |
| PUT | `/api/tenants/{tenantId}/payruns/{payrunId}` | Update payrun |
| PUT | `/api/tenants/{tenantId}/payruns/{payrunId}/rebuild` | Rebuild payrun |
| DELETE | `/api/tenants/{tenantId}/payruns/{payrunId}` | Delete payrun |

### Payrun Job Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tenants/{tenantId}/payruns/jobs` | Query payrun jobs |
| GET | `/api/tenants/{tenantId}/payruns/jobs/employees/{employeeId}` | Query employee jobs |
| GET | `/api/tenants/{tenantId}/payruns/jobs/{payrunJobId}` | Get payrun job |
| POST | `/api/tenants/{tenantId}/payruns/jobs` | Start payrun job |
| GET | `/api/tenants/{tenantId}/payruns/jobs/{payrunJobId}/status` | Get job status |
| PUT | `/api/tenants/{tenantId}/payruns/jobs/{payrunJobId}/status` | Change job status |
| DELETE | `/api/tenants/{tenantId}/payruns/jobs/{payrunJobId}` | Delete payrun job |

### Payrun Parameter Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tenants/{tenantId}/payruns/{payrunId}/parameters` | Query parameters |
| GET | `/api/tenants/{tenantId}/payruns/{payrunId}/parameters/{id}` | Get parameter |
| POST | `/api/tenants/{tenantId}/payruns/{payrunId}/parameters` | Create parameter |
| PUT | `/api/tenants/{tenantId}/payruns/{payrunId}/parameters/{id}` | Update parameter |
| DELETE | `/api/tenants/{tenantId}/payruns/{payrunId}/parameters/{id}` | Delete parameter |

### Payroll Result Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/results` | Query results |
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/results/{id}` | Get result |
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/results/{id}/payruns` | Query payrun results |
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/results/{id}/wagetypes` | Query wage type results |
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/results/{id}/collectors` | Query collector results |
| GET | `/api/tenants/{tenantId}/payrolls/{payrollId}/consolidated` | Get consolidated results |

---

## Key Features Summary

### Payrun Configuration
- ✅ Expression-based control (start, end, employee availability, wage type availability)
- ✅ Retro pay support
- ✅ Forecast support
- ✅ Localization
- ✅ Custom parameters

### Payrun Job Execution
- ✅ Multi-employee processing
- ✅ Script-based customization
- ✅ Status workflow (Draft → Released → Processed → Finished)
- ✅ Progress tracking
- ✅ Error handling
- ✅ Webhook notifications

### Results Management
- ✅ Wage type results
- ✅ Collector results
- ✅ Payrun results
- ✅ Custom results
- ✅ Consolidated results (multi-period)
- ✅ OData querying
- ✅ Filtering by period, employee, division, tags, forecast

### Client Integration
- ✅ HTTP client wrapper
- ✅ Service clients
- ✅ Query builders
- ✅ Exchange format (import/export)
- ✅ Model definitions

---

*Document Generated: 2025-01-05*  
*Based on analysis of: payroll-engine-backend API controllers, services, and PayrollEngine.Client.Core*

