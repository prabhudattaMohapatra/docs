# PayrunProcessor Components and Java Implementation Timeline

## PayrunProcessor Architecture Overview

The `PayrunProcessor` is the core engine that executes payroll calculations for employees. It orchestrates the entire payrun lifecycle from initialization to result storage.

---

## Core Components

### 1. **PayrunProcessor** (Main Orchestrator)
**Location**: `Domain/Domain.Application/PayrunProcessor.cs`  
**Purpose**: Main class that orchestrates the entire payrun execution

**Key Responsibilities**:
- Initialize payrun job and context
- Load payroll, division, regulations, employees
- Coordinate employee processing
- Handle retro pay calculations
- Manage job lifecycle (start, process, complete, abort)

**Main Methods**:
- `Process(PayrunJobInvocation)` - Entry point
- `ProcessAllEmployeesAsync()` - Process all employees in payrun
- `ProcessEmployeeAsync()` - Process single employee
- `CalculateEmployeeAsync()` - Calculate payroll for employee
- `SetupEmployeesAsync()` - Load and filter employees
- `GetRetroDateAsync()` - Calculate retro pay date
- `GetCalculatorAsync()` - Get payroll calculator
- `UpdateJobAsync()` - Update job status
- `AbortJobAsync()` - Abort job on error
- `CompleteRetroJobAsync()` - Complete retro pay job

**Dependencies**:
- `PayrunProcessorSettings` - Configuration and repositories
- `PayrunProcessorRepositories` - Data access layer
- `PayrunProcessorRegulation` - Regulation processing
- `PayrunProcessorScripts` - Script execution
- `FunctionHost` - Script compilation and execution
- `ResultProvider` - Result storage

---

### 2. **PayrunProcessorSettings**
**Location**: `Domain/Domain.Application/PayrunProcessorSettings.cs`  
**Purpose**: Configuration and dependency injection container

**Contains**:
- **Repositories** (15+):
  - `IUserRepository`
  - `IDivisionRepository`
  - `IEmployeeRepository`
  - `IGlobalCaseValueRepository`
  - `INationalCaseValueRepository`
  - `ICompanyCaseValueRepository`
  - `IEmployeeCaseValueRepository`
  - `IPayrunRepository`
  - `IPayrunJobRepository`
  - `ILookupSetRepository`
  - `IRegulationRepository`
  - `IRegulationShareRepository`
  - `IPayrollRepository`
  - `IPayrollResultRepository`
  - `IPayrollConsolidatedResultRepository`
  - `IPayrollResultSetRepository`
- **Services**:
  - `ICalendarRepository`
  - `IPayrollCalculatorProvider`
  - `IWebhookDispatchService`
- **Configuration**:
  - `FunctionLogTimeout`
  - `DbContext`

---

### 3. **PayrunProcessorRepositories**
**Location**: `Domain/Domain.Application/PayrunProcessorRepositories.cs`  
**Purpose**: Data access layer for loading domain objects

**Methods**:
- `LoadPayrollAsync(int payrollId)` - Load payroll
- `LoadDerivedRegulationsAsync()` - Load regulations for payroll
- `ValidatePayrollAsync()` - Validate payroll configuration
- `LoadDivisionAsync(int divisionId)` - Load division
- `LoadUserAsync(int userId)` - Load user
- `LoadPayrunJobAsync(int payrunJobId)` - Load payrun job
- `LoadPayrunAsync(int payrunId)` - Load payrun

---

### 4. **PayrunProcessorRegulation**
**Location**: `Domain/Domain.Application/PayrunProcessorRegulation.cs`  
**Purpose**: Handles regulation-specific processing (wage types, collectors)

**Key Methods**:
- `GetDerivedCollectorsAsync()` - Get collectors for payrun
- `GetDerivedWageTypesAsync()` - Get wage types for payrun
- `IsWageTypeAvailable()` - Check if wage type should be calculated
- `CalculateWageTypeValue()` - Calculate wage type value
- `CalculateWageTypeResult()` - Calculate wage type result
- `CalculateCollectorStart()` - Initialize collector
- `CalculateCollectorApply()` - Apply value to collector
- `CalculateCollectorEnd()` - Finalize collector

**Dependencies**:
- `IFunctionHost` - For script execution
- `IResultProvider` - For result storage
- `WageTypeScriptController` - Wage type script execution
- `CollectorScriptController` - Collector script execution

---

### 5. **PayrunProcessorScripts**
**Location**: `Domain/Domain.Application/PayrunProcessorScripts.cs`  
**Purpose**: Executes payrun lifecycle scripts

**Methods**:
- `PayrunStart()` - Execute payrun start script
- `PayrunEnd()` - Execute payrun end script
- `EmployeeStart()` - Execute employee start script
- `EmployeeEnd()` - Execute employee end script

**Script Types**:
- Payrun start/end expressions
- Employee start/end expressions
- Wage type available expressions

---

### 6. **PayrunContext**
**Purpose**: Context object passed through processing pipeline

**Contains**:
- `User` - Current user
- `Payroll` - Payroll configuration
- `Division` - Division configuration
- `PayrunJob` - Current payrun job
- `ParentPayrunJob` - Parent job (for retro pay)
- `RetroPayrunJobs` - List of retro jobs
- `EvaluationDate` - Evaluation date
- `EvaluationPeriod` - Evaluation period
- `RetroDate` - Retro pay date
- `DerivedRegulations` - Regulations for payroll
- `DerivedWageTypes` - Wage types to calculate
- `DerivedCollectors` - Collectors to process
- `GlobalCaseValues` - Global case value cache
- `NationalCaseValues` - National case value cache
- `CompanyCaseValues` - Company case value cache
- `EmployeeCaseValues` - Employee case value cache
- `CaseFieldProvider` - Case field definitions
- `RegulationLookupProvider` - Lookup tables
- `Calculator` - Payroll calculator
- `PayrollCulture` - Culture for calculations
- `CalendarName` - Calendar name
- `Errors` - Processing errors

---

## Processing Flow

### 1. Initialization Phase
```
Process() → Load Payroll → Load Division → Load Regulations → 
Create PayrunJob → Setup Calculator → Load Case Values
```

### 2. Employee Processing Phase
```
ProcessAllEmployeesAsync() → 
  For each employee:
    ProcessEmployeeAsync() →
      EmployeeStart() →
      CalculateEmployeeAsync() →
        Calculate Wage Types →
        Calculate Collectors →
      EmployeeEnd() →
      Store Results
```

### 3. Wage Type Calculation
```
CalculateWageTypeValue() →
  Check Availability →
  Execute ValueExpression →
  Execute Rules.cs functions →
  Store WageTypeResult
```

### 4. Collector Calculation
```
CalculateCollectorStart() →
  CalculateCollectorApply() (for each wage type) →
  CalculateCollectorEnd() →
  Store CollectorResult
```

### 5. Completion Phase
```
PayrunEnd() → 
Update Job Status → 
Store Consolidated Results → 
Dispatch Webhooks
```

---

## Java Implementation Timeline

### Phase 1: Foundation (Weeks 1-3)

#### Week 1: Core Infrastructure
- **PayrunProcessor class structure** (3 days)
  - Main class with dependency injection
  - Settings class (Spring @Configuration)
  - Context class (immutable data holder)
- **Repository interfaces** (2 days)
  - Define all repository interfaces
  - Create Spring Data JPA repositories

**Deliverables**:
- `PayrunProcessor.java` (skeleton)
- `PayrunProcessorSettings.java`
- `PayrunContext.java`
- Repository interfaces

---

#### Week 2: Data Access Layer
- **PayrunProcessorRepositories** (3 days)
  - Implement all load methods
  - Add validation logic
  - Error handling
- **Database integration** (2 days)
  - JPA entity mappings
  - Query methods
  - Transaction management

**Deliverables**:
- `PayrunProcessorRepositories.java`
- Repository implementations
- Database queries

---

#### Week 3: Calculator and Context Setup
- **PayrollCalculator integration** (2 days)
  - Integrate existing calculator
  - Period/cycle calculations
- **Context initialization** (3 days)
  - Load payroll, division, regulations
  - Setup case value caches
  - Initialize providers

**Deliverables**:
- Calculator integration
- Context setup logic
- Case value cache implementation

---

### Phase 2: Core Processing (Weeks 4-7)

#### Week 4: Employee Processing Loop
- **ProcessAllEmployeesAsync** (3 days)
  - Employee loading and filtering
  - Loop structure
  - Error handling per employee
- **ProcessEmployeeAsync** (2 days)
  - Employee processing orchestration
  - Script execution coordination

**Deliverables**:
- `ProcessAllEmployeesAsync()` method
- `ProcessEmployeeAsync()` method
- Employee filtering logic

---

#### Week 5: Script Execution Framework
- **PayrunProcessorScripts** (3 days)
  - Payrun start/end scripts
  - Employee start/end scripts
  - Script controller integration
- **Script runtime integration** (2 days)
  - Connect to Java script execution engine
  - Runtime context setup

**Deliverables**:
- `PayrunProcessorScripts.java`
- Script execution framework
- Runtime integration

---

#### Week 6: Wage Type Calculation
- **PayrunProcessorRegulation - Wage Types** (3 days)
  - `CalculateWageTypeValue()` implementation
  - Availability checking
  - Result storage
- **Wage type script execution** (2 days)
  - Integration with script engine
  - Rules.java function execution

**Deliverables**:
- Wage type calculation logic
- Script execution for wage types
- Result storage

---

#### Week 7: Collector Calculation
- **Collector processing** (3 days)
  - `CalculateCollectorStart()`
  - `CalculateCollectorApply()`
  - `CalculateCollectorEnd()`
- **Collector script execution** (2 days)
  - Collector script integration
  - Result aggregation

**Deliverables**:
- Collector calculation logic
- Collector script execution
- Result aggregation

---

### Phase 3: Advanced Features (Weeks 8-10)

#### Week 8: Retro Pay Support
- **Retro pay detection** (2 days)
  - Retro date calculation
  - Retro job creation
- **Retro pay processing** (3 days)
  - Retro job execution
  - Result comparison
  - Retro job completion

**Deliverables**:
- Retro pay logic
- Retro job management
- Result comparison

---

#### Week 9: Result Management
- **Result storage** (3 days)
  - PayrollResultSet creation
  - WageTypeResult storage
  - CollectorResult storage
- **Result consolidation** (2 days)
  - Consolidated result calculation
  - Multi-period aggregation

**Deliverables**:
- Result storage logic
- Consolidated result calculation
- Database persistence

---

#### Week 10: Error Handling and Job Management
- **Job lifecycle** (2 days)
  - Job status management
  - Job updates
  - Job completion/abortion
- **Error handling** (3 days)
  - Error collection
  - Error reporting
  - Partial failure handling

**Deliverables**:
- Job lifecycle management
- Error handling framework
- Status tracking

---

### Phase 4: Integration and Testing (Weeks 11-12)

#### Week 11: Integration
- **Webhook integration** (2 days)
  - Webhook dispatch service
  - Event notifications
- **API integration** (3 days)
  - REST API endpoints
  - Controller integration
  - Request/response handling

**Deliverables**:
- Webhook service
- API endpoints
- Controller integration

---

#### Week 12: Testing and Optimization
- **Unit testing** (2 days)
  - Component tests
  - Mock repositories
- **Integration testing** (2 days)
  - End-to-end tests
  - Performance testing
- **Optimization** (1 day)
  - Performance tuning
  - Memory optimization

**Deliverables**:
- Test suite
- Performance benchmarks
- Documentation

---

## Total Timeline Summary

| Phase | Duration | Focus |
|-------|----------|-------|
| **Phase 1: Foundation** | 3 weeks | Infrastructure, repositories, context setup |
| **Phase 2: Core Processing** | 4 weeks | Employee processing, scripts, wage types, collectors |
| **Phase 3: Advanced Features** | 3 weeks | Retro pay, results, error handling |
| **Phase 4: Integration** | 2 weeks | Webhooks, API, testing |
| **Total** | **12 weeks** | Complete PayrunProcessor implementation |

---

## Critical Dependencies

### Must Build First:
1. **Script Execution Engine** (6-8 weeks)
   - Java script compiler (Janino)
   - Class loading system
   - Function host
   - Runtime interfaces

2. **Payroll Calculator** (2-3 weeks)
   - Period calculations
   - Cycle calculations
   - Case period values

3. **Case Value Provider** (3-4 weeks)
   - Case value caching
   - Case field provider
   - Lookup provider

### Can Build in Parallel:
- Repository implementations
- Result storage
- Webhook service

---

## Risk Factors

### High Risk:
1. **Script Execution** - Complex integration with Rules.java
2. **Retro Pay** - Complex logic for detecting and processing retro pay
3. **Performance** - Processing thousands of employees efficiently

### Medium Risk:
1. **Case Value Caching** - Memory management for large datasets
2. **Result Storage** - Efficient storage of calculation results
3. **Error Handling** - Graceful degradation on partial failures

### Mitigation:
- Build script execution engine first
- Implement performance testing early
- Use caching strategies for case values
- Implement comprehensive error handling

---

## Team Size Recommendations

### Minimum Team (MVP):
- **1 Senior Java Developer** (full-time)
- **1 Mid-level Developer** (full-time)
- **Timeline**: 12-14 weeks

### Recommended Team (Full Implementation):
- **1 Senior Java Developer** (lead)
- **2 Mid-level Developers**
- **1 QA Engineer** (part-time)
- **Timeline**: 10-12 weeks

---

## Key Java Technologies

- **Spring Boot** - Framework and dependency injection
- **Spring Data JPA** - Repository pattern
- **Janino** - Runtime Java compilation
- **BigDecimal** - Financial calculations
- **CompletableFuture** - Async processing
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework

---

*Document Generated: 2025-01-12*  
*PayrunProcessor Components and Java Implementation Timeline*

