# Employee Management Capabilities - Payroll Engine

Complete documentation of all employee management capabilities in the current C# Payroll Engine, including Employee APIs and Employee Case APIs.

---

## Table of Contents

1. [Employee API Endpoints](#employee-api-endpoints)
2. [Employee Case Value APIs](#employee-case-value-apis)
3. [Employee Case Change APIs](#employee-case-change-apis)
4. [Employee Case Document APIs](#employee-case-document-apis)
5. [Case Building & Validation](#case-building--validation)
6. [Query Capabilities (OData)](#query-capabilities-odata)
7. [Data Models](#data-models)
8. [Advanced Features](#advanced-features)

---

## Employee API Endpoints

### Base Route
```
/api/tenants/{tenantId}/employees
```

### 1. Query Employees
**Endpoint**: `GET /api/tenants/{tenantId}/employees`

**Description**: Query employees with OData filtering, sorting, and pagination

**Query Parameters**:
- `divisionId` (int, optional): Filter by division
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `select` (string): OData field selection
- `skip` (int64): Number of items to skip (pagination)
- `top` (int64): Number of items to return
- `status` (ActiveStatus): Filter by active/inactive status
- `result` (QueryResultType): Result type (items, count, or both)

**Response**: Array of Employee objects

**Example**:
```http
GET /api/tenants/1/employees?filter=FirstName eq 'John'&orderBy=LastName asc&top=10
```

**Capabilities**:
- ✅ OData filtering (see [Query Capabilities](#query-capabilities-odata))
- ✅ Sorting by any field
- ✅ Pagination (skip/top)
- ✅ Field selection
- ✅ Division filtering
- ✅ Status filtering (active/inactive)
- ✅ Count queries

---

### 2. Get Employee
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}`

**Description**: Get a single employee by ID

**Path Parameters**:
- `tenantId` (int, required): Tenant identifier
- `employeeId` (int, required): Employee identifier

**Response**: Employee object

**Example**:
```http
GET /api/tenants/1/employees/123
```

---

### 3. Create Employee
**Endpoint**: `POST /api/tenants/{tenantId}/employees`

**Description**: Create a new employee

**Request Body**: Employee object

**Validation**:
- ✅ Unique identifier check (returns 400 if duplicate)
- ✅ Required fields: Identifier, FirstName, LastName
- ✅ String length validation (128 chars max)

**Response**: Created Employee object (201 Created)

**Example**:
```json
POST /api/tenants/1/employees
{
  "identifier": "EMP001",
  "firstName": "John",
  "lastName": "Doe",
  "divisions": ["Sales", "Engineering"],
  "culture": "en-US",
  "calendar": "Standard",
  "attributes": {
    "department": "IT",
    "location": "New York"
  }
}
```

**Capabilities**:
- ✅ Create with divisions
- ✅ Set culture and calendar
- ✅ Add custom attributes
- ✅ Automatic validation

---

### 4. Update Employee
**Endpoint**: `PUT /api/tenants/{tenantId}/employees/{employeeId}`

**Description**: Update an existing employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Request Body**: Employee object with updated values

**Response**: Updated Employee object (200 OK)

**Example**:
```json
PUT /api/tenants/1/employees/123
{
  "id": 123,
  "identifier": "EMP001",
  "firstName": "John",
  "lastName": "Smith",  // Updated
  "divisions": ["Sales", "Marketing"],  // Updated
  "attributes": {
    "department": "Sales"  // Updated
  }
}
```

**Capabilities**:
- ✅ Update all fields
- ✅ Update divisions
- ✅ Update attributes
- ✅ Update culture/calendar

---

### 5. Delete Employee
**Endpoint**: `DELETE /api/tenants/{tenantId}/employees/{employeeId}`

**Description**: Delete an employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Response**: 204 No Content (on success)

**Example**:
```http
DELETE /api/tenants/1/employees/123
```

---

### 6. Employee Attributes Management

#### 6.1 Get Employee Attribute
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/attributes/{attributeName}`

**Description**: Get a specific attribute value for an employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `attributeName` (string, required): Attribute name

**Response**: Attribute value as JSON string

**Example**:
```http
GET /api/tenants/1/employees/123/attributes/department
Response: "IT"
```

---

#### 6.2 Set Employee Attribute
**Endpoint**: `POST /api/tenants/{tenantId}/employees/{employeeId}/attributes/{attributeName}`

**Description**: Set or update an attribute value for an employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `attributeName` (string, required)

**Request Body**: Attribute value as JSON string

**Response**: Current attribute value (201 Created or 200 OK)

**Example**:
```json
POST /api/tenants/1/employees/123/attributes/department
"Sales"
```

**Capabilities**:
- ✅ Store any JSON value
- ✅ Overwrite existing attributes
- ✅ Type-safe storage

---

#### 6.3 Delete Employee Attribute
**Endpoint**: `DELETE /api/tenants/{tenantId}/employees/{employeeId}/attributes/{attributeName}`

**Description**: Delete an attribute from an employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `attributeName` (string, required)

**Response**: Boolean indicating success (200 OK)

**Example**:
```http
DELETE /api/tenants/1/employees/123/attributes/department
Response: true
```

---

## Employee Case Value APIs

### Base Route
```
/api/tenants/{tenantId}/employees/{employeeId}/cases
```

### 1. Query Employee Case Values
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases`

**Description**: Query case values for a specific employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Query Parameters** (CaseValueQuery):
- `divisionScope` (DivisionScope): Filter by division scope
- `divisionId` (int, optional): Filter by specific division
- `caseName` (string, optional): Filter by case name
- `caseFieldName` (string, optional): Filter by case field name
- `caseSlot` (string, optional): Filter by case slot
- `start` (DateTime, optional): Filter by start date
- `end` (DateTime, optional): Filter by end date
- `forecast` (string, optional): Filter by forecast name
- `tags` (string[], optional): Filter by tags
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top
- `result` (QueryResultType): Result type

**Response**: Array of CaseValue objects

**Example**:
```http
GET /api/tenants/1/employees/123/cases?caseName=Salary&start=2025-01-01&end=2025-12-31
```

**Capabilities**:
- ✅ Filter by case name, field, slot
- ✅ Date range filtering (start/end)
- ✅ Forecast filtering
- ✅ Tag filtering
- ✅ Division scope filtering
- ✅ OData filtering and sorting
- ✅ Pagination

---

### 2. Get Employee Case Value Slots
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/slots?caseFieldName={caseFieldName}`

**Description**: Get all available slots for a specific case field

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Query Parameters**:
- `caseFieldName` (string, required): Case field name

**Response**: Array of slot names (strings)

**Example**:
```http
GET /api/tenants/1/employees/123/cases/slots?caseFieldName=Salary
Response: ["Monthly", "Annual", "Bonus"]
```

**Use Case**: Discover available slots for a case field before creating case values

---

### 3. Get Employee Case Value
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/{caseValueId}`

**Description**: Get a specific case value by ID

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `caseValueId` (int, required)

**Response**: CaseValue object

**Example**:
```http
GET /api/tenants/1/employees/123/cases/456
```

---

## Employee Case Change APIs

### Base Route
```
/api/tenants/{tenantId}/employees/{employeeId}/cases/changes
```

### 1. Query Employee Case Changes
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/changes`

**Description**: Query case changes (history) for an employee

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Query Parameters** (CaseChangeQuery):
- `divisionId` (int, optional): Filter by division
- `culture` (string, optional): Filter by culture
- `excludeGlobal` (bool, optional): Exclude global cases
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top
- `result` (QueryResultType): Result type

**Response**: Array of CaseChange objects

**Example**:
```http
GET /api/tenants/1/employees/123/cases/changes?orderBy=Created desc&top=10
```

**Capabilities**:
- ✅ Query case change history
- ✅ Filter by division, culture
- ✅ OData filtering and sorting
- ✅ Pagination

---

### 2. Get Employee Case Change
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/changes/{caseChangeId}`

**Description**: Get a specific case change by ID

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `caseChangeId` (int, required)

**Response**: CaseChange object with all case values

**Example**:
```http
GET /api/tenants/1/employees/123/cases/changes/789
```

---

### 3. Query Employee Case Change Values
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/changes/values`

**Description**: Query case values from case changes

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)

**Query Parameters**: Same as CaseChangeQuery

**Response**: Array of CaseChangeCaseValue objects (includes change metadata)

**Example**:
```http
GET /api/tenants/1/employees/123/cases/changes/values?caseName=Salary
```

**Capabilities**:
- ✅ Get case values with change metadata
- ✅ Includes user, reason, created date
- ✅ Includes validation issues

---

### 4. Delete Employee Case Change
**Endpoint**: `DELETE /api/tenants/{tenantId}/employees/{employeeId}/cases/changes/{caseValueId}`

**Description**: Delete a case change (cancels the change)

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `caseValueId` (int, required): Case value ID to cancel

**Response**: 204 No Content

**Example**:
```http
DELETE /api/tenants/1/employees/123/cases/changes/456
```

**Capabilities**:
- ✅ Cancel case changes
- ✅ Maintains audit trail

---

## Employee Case Document APIs

### Base Route
```
/api/tenants/{tenantId}/employees/{employeeId}/cases/{caseValueId}/documents
```

### 1. Query Employee Case Documents
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/{caseValueId}/documents`

**Description**: Query documents attached to a case value

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `caseValueId` (int, required)

**Query Parameters** (Query):
- `filter` (string): OData filter expression
- `orderBy` (string): OData order-by expression
- `skip` (int64): Pagination skip
- `top` (int64): Pagination top
- `result` (QueryResultType): Result type

**Response**: Array of CaseDocument objects

**Example**:
```http
GET /api/tenants/1/employees/123/cases/456/documents
```

**Capabilities**:
- ✅ Query documents by case value
- ✅ OData filtering and sorting
- ✅ Pagination

---

### 2. Get Employee Case Document
**Endpoint**: `GET /api/tenants/{tenantId}/employees/{employeeId}/cases/{caseValueId}/documents/{documentId}`

**Description**: Get a specific document

**Path Parameters**:
- `tenantId` (int, required)
- `employeeId` (int, required)
- `caseValueId` (int, required)
- `documentId` (int, required)

**Response**: CaseDocument object

**Example**:
```http
GET /api/tenants/1/employees/123/cases/456/documents/789
```

---

## Case Building & Validation

### Base Route
```
/api/tenants/{tenantId}/payrolls/{payrollId}/cases
```

### 1. Build Employee Case
**Endpoint**: `POST /api/tenants/{tenantId}/payrolls/{payrollId}/cases/{caseName}/build`

**Description**: Build an employee case with validation and case value setup

**Path Parameters**:
- `tenantId` (int, required)
- `payrollId` (int, required)
- `caseName` (string, required): Case name to build

**Query Parameters** (CaseBuildQuery):
- `employeeId` (int, required): Employee ID (mandatory for employee cases)
- `userId` (int, required): User ID making the change
- `divisionId` (int, optional): Division ID
- `regulationDate` (DateTime, optional): Regulation date
- `evaluationDate` (DateTime, optional): Evaluation date
- `culture` (string, optional): Culture for localization
- `clusterSetName` (string, optional): Cluster set name

**Request Body** (CaseChangeSetup, optional):
```json
{
  "userId": 1,
  "employeeId": 123,
  "divisionId": 1,
  "reason": "Salary adjustment",
  "forecast": "Q1-2025",
  "case": {
    "caseName": "Salary",
    "values": [
      {
        "caseFieldName": "BaseSalary",
        "value": "50000",
        "start": "2025-01-01T00:00:00Z",
        "end": "2025-12-31T23:59:59Z"
      },
      {
        "caseFieldName": "Bonus",
        "value": "5000",
        "start": "2025-01-01T00:00:00Z"
      }
    ]
  }
}
```

**Response**: CaseSet object with:
- Case definition
- Available case fields
- Current case values
- Validation issues (if any)
- Case relations

**Example**:
```json
POST /api/tenants/1/payrolls/1/cases/Salary/build?employeeId=123&userId=1
{
  "case": {
    "caseName": "Salary",
    "values": [
      {
        "caseFieldName": "BaseSalary",
        "value": "50000",
        "start": "2025-01-01T00:00:00Z"
      }
    ]
  }
}
```

**Capabilities**:
- ✅ Build case structure dynamically
- ✅ Validate case values
- ✅ Check case field availability
- ✅ Validate case relations
- ✅ Return validation issues
- ✅ Support case slots
- ✅ Support case relations
- ✅ Forecast support
- ✅ Date range validation

---

### 2. Get Payroll Case Values (Employee)
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/cases/values`

**Description**: Get case values for a payroll, optionally filtered by employee

**Path Parameters**:
- `tenantId` (int, required)
- `payrollId` (int, required)

**Query Parameters**:
- `caseType` (CaseType, required): Must be `Employee` for employee cases
- `employeeId` (int, required): Employee ID (mandatory for employee cases)
- `caseFieldNames` (string[], optional): Filter by case field names
- `regulationDate` (DateTime, optional): Regulation date
- `evaluationDate` (DateTime, optional): Evaluation date
- `start` (DateTime, optional): Start date filter
- `end` (DateTime, optional): End date filter

**Response**: Array of CaseFieldValue objects

**Example**:
```http
GET /api/tenants/1/payrolls/1/cases/values?caseType=Employee&employeeId=123&caseFieldNames=BaseSalary,Bonus
```

**Capabilities**:
- ✅ Get case values for specific employee
- ✅ Filter by case fields
- ✅ Date range filtering
- ✅ Regulation date support

---

### 3. Get Payroll Time Case Values (Employee)
**Endpoint**: `GET /api/tenants/{tenantId}/payrolls/{payrollId}/cases/time`

**Description**: Get case values at a specific point in time

**Path Parameters**:
- `tenantId` (int, required)
- `payrollId` (int, required)

**Query Parameters**:
- `caseType` (CaseType, required): Must be `Employee`
- `employeeId` (int, required): Employee ID
- `caseFieldNames` (string[], optional): Filter by case field names
- `regulationDate` (DateTime, optional): Regulation date
- `evaluationDate` (DateTime, optional): Evaluation date
- `start` (DateTime, optional): Start date
- `end` (DateTime, optional): End date

**Response**: Array of CaseFieldValue objects at specified time

**Example**:
```http
GET /api/tenants/1/payrolls/1/cases/time?caseType=Employee&employeeId=123&evaluationDate=2025-06-15
```

**Capabilities**:
- ✅ Get case values at specific point in time
- ✅ Time-travel queries
- ✅ Historical data access

---

## Query Capabilities (OData)

All query endpoints support **OData v4** query parameters:

### Supported OData Features

#### 1. Filtering (`$filter`)
**Operators**:
- `eq` - Equal
- `ne` - Not equal
- `gt` - Greater than
- `ge` - Greater than or equal
- `lt` - Less than
- `le` - Less than or equal
- `and` - Logical AND
- `or` - Logical OR
- `not` - Logical NOT
- `()` - Grouping

**Functions**:
- `startswith(field, 'value')` - String starts with
- `endswith(field, 'value')` - String ends with
- `contains(field, 'value')` - String contains
- `year(dateField)` - Extract year
- `month(dateField)` - Extract month
- `day(dateField)` - Extract day
- `hour(dateField)` - Extract hour
- `minute(dateField)` - Extract minute
- `date(dateField)` - Extract date part
- `time(dateField)` - Extract time part

**Examples**:
```http
# Filter employees by name
GET /api/tenants/1/employees?$filter=FirstName eq 'John'

# Filter with AND
GET /api/tenants/1/employees?$filter=FirstName eq 'John' and LastName eq 'Doe'

# Filter with functions
GET /api/tenants/1/employees?$filter=startswith(FirstName, 'J')

# Filter case values by date
GET /api/tenants/1/employees/123/cases?$filter=year(Start) eq 2025
```

---

#### 2. Sorting (`$orderby`)
**Syntax**: `$orderby=field1 asc, field2 desc`

**Examples**:
```http
# Sort by last name ascending
GET /api/tenants/1/employees?$orderby=LastName asc

# Sort by multiple fields
GET /api/tenants/1/employees?$orderby=LastName asc, FirstName desc
```

---

#### 3. Pagination
**Parameters**:
- `$skip` (int64): Number of items to skip
- `$top` (int64): Number of items to return

**Examples**:
```http
# Get first 10 employees
GET /api/tenants/1/employees?$top=10

# Get next 10 employees (page 2)
GET /api/tenants/1/employees?$skip=10&$top=10
```

---

#### 4. Field Selection (`$select`)
**Syntax**: `$select=field1,field2,field3`

**Examples**:
```http
# Get only specific fields
GET /api/tenants/1/employees?$select=Identifier,FirstName,LastName
```

**Note**: Field selection works at database level for performance

---

#### 5. Result Type (`result`)
**Values**:
- `Items` (default): Return items only
- `Count`: Return count only
- `ItemsWithCount`: Return both items and count

**Examples**:
```http
# Get count only
GET /api/tenants/1/employees?result=Count

# Get items with count
GET /api/tenants/1/employees?result=ItemsWithCount
```

---

### OData Query Examples

#### Complex Employee Query
```http
GET /api/tenants/1/employees?$filter=
  (FirstName eq 'John' or FirstName eq 'Jane') 
  and startswith(LastName, 'D')
  &$orderby=LastName asc, FirstName desc
  &$top=20
  &$skip=0
  &result=ItemsWithCount
```

#### Case Value Query with Date Range
```http
GET /api/tenants/1/employees/123/cases?$filter=
  caseName eq 'Salary' 
  and Start ge 2025-01-01T00:00:00Z 
  and End le 2025-12-31T23:59:59Z
  &$orderby=Start desc
```

#### Case Change Query
```http
GET /api/tenants/1/employees/123/cases/changes?$filter=
  year(Created) eq 2025 
  and month(Created) eq 6
  &$orderby=Created desc
  &$top=50
```

---

## Data Models

### Employee Model

```csharp
public class Employee : ApiObjectBase
{
    // Required fields
    public string Identifier { get; set; }      // Unique identifier (max 128 chars)
    public string FirstName { get; set; }      // First name (max 128 chars)
    public string LastName { get; set; }       // Last name (max 128 chars)
    
    // Optional fields
    public List<string> Divisions { get; set; } // Division names
    public string Culture { get; set; }         // Culture (RFC 4646)
    public string Calendar { get; set; }         // Calendar name
    
    // Custom attributes (key-value pairs)
    public Dictionary<string, object> Attributes { get; set; }
    
    // Base fields (from ApiObjectBase)
    public int Id { get; set; }
    public int Status { get; set; }             // Active/Inactive
    public DateTime Created { get; set; }
    public DateTime Updated { get; set; }
}
```

**Key Features**:
- ✅ Unique identifier constraint
- ✅ Multiple divisions support
- ✅ Culture and calendar override
- ✅ Custom attributes (flexible key-value storage)
- ✅ Status tracking (active/inactive)
- ✅ Audit fields (created/updated)

---

### CaseValue Model

```csharp
public class CaseValue : ApiObjectBase
{
    // Identity
    public int? DivisionId { get; set; }         // Division (for local scope)
    public int? EmployeeId { get; set; }       // Employee (for employee cases)
    
    // Case information
    public string CaseName { get; set; }        // Case name
    public Dictionary<string, string> CaseNameLocalizations { get; set; }
    public string CaseFieldName { get; set; }   // Case field name (required)
    public Dictionary<string, string> CaseFieldNameLocalizations { get; set; }
    public string CaseSlot { get; set; }       // Case slot (optional)
    public Dictionary<string, string> CaseSlotLocalizations { get; set; }
    
    // Value
    public ValueType ValueType { get; set; }    // String, Integer, Decimal, Boolean, Date, etc.
    public string Value { get; set; }           // Value as JSON string
    public decimal? NumericValue { get; set; } // Numeric value (if applicable)
    public string Culture { get; set; }         // Culture for localization
    
    // Case relation
    public CaseRelationReference CaseRelation { get; set; }
    
    // Time period
    public DateTime? Start { get; set; }         // Start date (UTC)
    public DateTime? End { get; set; }          // End date (UTC)
    public DateTime? CancellationDate { get; set; }
    
    // Metadata
    public string Forecast { get; set; }         // Forecast name
    public List<string> Tags { get; set; }       // Tags
    public Dictionary<string, object> Attributes { get; set; }
    
    // Base fields
    public int Id { get; set; }
    public DateTime Created { get; set; }
    public DateTime Updated { get; set; }
}
```

**Key Features**:
- ✅ Multiple value types (String, Integer, Decimal, Boolean, Date, etc.)
- ✅ Time-period support (start/end dates)
- ✅ Case slots (for multi-value cases)
- ✅ Case relations (links between cases)
- ✅ Localization support
- ✅ Forecast support (future values)
- ✅ Tags for categorization
- ✅ Custom attributes
- ✅ Cancellation support

---

### CaseChange Model

```csharp
public class CaseChange : ApiObjectBase
{
    // Change metadata
    public int UserId { get; set; }             // User who made the change
    public int? EmployeeId { get; set; }        // Employee (for employee cases)
    public int? DivisionId { get; set; }        // Division
    public string Reason { get; set; }         // Change reason
    public string Forecast { get; set; }        // Forecast name
    
    // Validation
    public string ValidationCaseName { get; set; } // Case used for validation
    
    // Cancellation
    public CaseCancellationType CancellationType { get; set; }
    public int? CancellationId { get; set; }    // ID of cancelled change
    public DateTime? CancellationDate { get; set; }
    
    // Case values
    public List<CaseValue> Values { get; set; }      // Case values in this change
    public List<CaseValue> IgnoredValues { get; set; } // Values that were ignored
    
    // Base fields
    public int Id { get; set; }
    public DateTime Created { get; set; }
    public DateTime Updated { get; set; }
}
```

**Key Features**:
- ✅ Multiple case values in one change
- ✅ Validation support
- ✅ Change cancellation
- ✅ Audit trail (user, reason, dates)
- ✅ Forecast support
- ✅ Ignored values tracking

---

### CaseChangeSetup Model

```csharp
public class CaseChangeSetup
{
    // Change metadata
    public int UserId { get; set; }             // Required
    public int? EmployeeId { get; set; }        // Required for employee cases
    public int? DivisionId { get; set; }
    public string Reason { get; set; }
    public string Forecast { get; set; }
    public DateTime? Created { get; set; }     // Override created date
    
    // Case to cancel
    public int? CancellationId { get; set; }
    
    // Case setup
    public CaseSetup Case { get; set; }        // Required - root case
    
    // Validation results
    public CaseValidationIssue[] Issues { get; set; } // Returned after validation
}
```

**Key Features**:
- ✅ Used for case building and validation
- ✅ Returns validation issues
- ✅ Supports cancellation
- ✅ Supports forecast

---

## Advanced Features

### 1. Multi-Division Support
- ✅ Employees can belong to multiple divisions
- ✅ Case values can be scoped to specific divisions
- ✅ Division-specific filtering in queries

### 2. Case Slots
- ✅ Support for multi-value case fields (e.g., multiple salary components)
- ✅ Query available slots for a case field
- ✅ Filter by slot in queries

### 3. Case Relations
- ✅ Link case values to other cases
- ✅ Support for case hierarchies
- ✅ Relation validation

### 4. Time-Period Support
- ✅ Case values have start/end dates
- ✅ Time-travel queries (get values at specific date)
- ✅ Overlapping period handling
- ✅ Cancellation dates

### 5. Forecast Support
- ✅ Store future case values
- ✅ Filter by forecast name
- ✅ Separate from actual values

### 6. Localization
- ✅ Multi-language support for case names, fields, slots
- ✅ Culture-specific values
- ✅ RFC 4646 culture codes

### 7. Validation
- ✅ Case value validation before creation
- ✅ Returns validation issues
- ✅ Prevents invalid data entry
- ✅ Custom validation scripts

### 8. Audit Trail
- ✅ All changes tracked
- ✅ User who made change
- ✅ Change reason
- ✅ Created/updated timestamps
- ✅ Cancellation tracking

### 9. Custom Attributes
- ✅ Flexible key-value storage on employees and case values
- ✅ JSON value support
- ✅ Query by attributes (via OData)

### 10. Tags
- ✅ Categorize case values with tags
- ✅ Filter by tags
- ✅ Multiple tags per value

### 11. Document Management
- ✅ Attach documents to case values
- ✅ Query documents
- ✅ Document metadata

### 12. Status Management
- ✅ Active/Inactive status for employees
- ✅ Filter by status
- ✅ Soft delete support

---

## API Response Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| **200 OK** | Success | GET, PUT operations |
| **201 Created** | Resource created | POST operations |
| **204 No Content** | Success, no content | DELETE operations |
| **400 Bad Request** | Invalid request | Validation errors, missing required fields |
| **404 Not Found** | Resource not found | Invalid IDs, missing resources |
| **422 Unprocessable Entity** | Validation failed | Case validation issues, script errors |

---

## Performance Considerations

### Query Optimization
- ✅ OData queries are optimized at database level
- ✅ Field selection reduces data transfer
- ✅ Pagination prevents large result sets
- ✅ Indexed queries for common filters

### Bulk Operations
- ✅ Support for bulk employee creation (via batch requests)
- ✅ Bulk case value updates (via case changes)
- ✅ Efficient batch processing

### Caching
- ✅ Case field definitions cached
- ✅ Lookup values cached
- ✅ Regulation data cached

---

## Security & Authorization

### Tenant Isolation
- ✅ All endpoints require tenant ID
- ✅ Tenant-level authorization
- ✅ Data isolation between tenants

### Employee Access Control
- ✅ Employee must belong to tenant
- ✅ Division-level access control
- ✅ User-based authorization

---

## Integration Examples

### Create Employee with Case Values
```http
# Step 1: Create employee
POST /api/tenants/1/employees
{
  "identifier": "EMP001",
  "firstName": "John",
  "lastName": "Doe",
  "divisions": ["Sales"]
}

# Step 2: Build case to validate
POST /api/tenants/1/payrolls/1/cases/Salary/build?employeeId=123&userId=1
{
  "userId": 1,
  "employeeId": 123,
  "case": {
    "caseName": "Salary",
    "values": [
      {
        "caseFieldName": "BaseSalary",
        "value": "50000",
        "start": "2025-01-01T00:00:00Z"
      }
    ]
  }
}

# Step 3: Create case change (if validation passes)
POST /api/tenants/1/payrolls/1/cases/Salary/changes?employeeId=123&userId=1
{
  "userId": 1,
  "employeeId": 123,
  "reason": "New employee",
  "case": {
    "caseName": "Salary",
    "values": [...]
  }
}
```

### Query Employee with Case Values
```http
# Get employee
GET /api/tenants/1/employees/123

# Get all case values for employee
GET /api/tenants/1/employees/123/cases

# Get specific case values
GET /api/tenants/1/employees/123/cases?caseName=Salary&start=2025-01-01

# Get case change history
GET /api/tenants/1/employees/123/cases/changes?$orderBy=Created desc
```

---

## Summary

The Payroll Engine provides **comprehensive employee management** with:

✅ **Full CRUD** for employees
✅ **Advanced querying** with OData
✅ **Case value management** with time periods
✅ **Case change tracking** with audit trail
✅ **Document management** for case values
✅ **Case building & validation** before creation
✅ **Multi-division support**
✅ **Localization** support
✅ **Forecast** support for future values
✅ **Custom attributes** for flexible data
✅ **Tags** for categorization
✅ **Case relations** for hierarchies
✅ **Case slots** for multi-value fields

**Total API Endpoints**: ~20+ endpoints for employee and case management

---

*Document Generated: 2025-01-05*
*Based on analysis of: payroll-engine-backend API controllers and models*

