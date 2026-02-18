# Swiss Payroll Engine - Complete Wage Calculation Flow Analysis

## Executive Summary

This document provides a comprehensive mapping of how case fields flow through wage types and collectors to calculate employee pay in the Swiss Swissdec payroll system.

---

## Part 1: Wage Type Calculation Logic from Case Fields

### 1.1 Base Salary Calculations

#### **OPTION A: Monthly Salary Employee** ✅ RECOMMENDED FOR YEARLY SALARY

**Case Field Required:**
- `GP.Swissdec.EmployeeStatisticsContractualMonthlyWage` (Decimal)

**Calculation:**
```csharp
// Wage Type 1000: Monthly Salary
// Location: Scripts/WageTypeWageTypes.cs:130-137
monthSalary = Employee.HasSvMonthDays() 
    ? Employee.Statistics.ContractualMonthlyWage.RoundTwentieth() 
    : null
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- `GP.Swissdec.ThirteenthMonthWageBase`
- `GP.Swissdec.QstBase` (Tax base)
- `GP.Swissdec.QstpBase` (Tax base)

**Collector Groups:**
- `GP.Swissdec.AhvAlvAlvzBase` (Pension/Unemployment insurance base)
- `GP.Swissdec.UvgUvgzKtgBvgBase` (Accident/Sickness insurance base)

**Example:**
```json
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
  "value": "5500",
  "start": "2022-01-01T00:00:00Z"
}
```

---

#### **OPTION B: Hourly Wage Employee** 

**Case Fields Required:**
- `GP.Swissdec.EmployeeStatisticsContractualHourlyWage` (Decimal) - Rate per hour
- `GP.Swissdec.EmployeeActivityWorkedHours` (Decimal) - Hours worked in period

**Calculation:**
```csharp
// Wage Type 1005: Hourly Wage
// Location: Scripts/WageTypeWageTypes.cs:141-159
result = EmployeeStatisticsContractualHourlyWage × EmployeeActivityWorkedHours
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- `GP.Swissdec.QstBase`
- `GP.Swissdec.QstpBase`

**Collector Groups:**
- `GP.Swissdec.AhvAlvAlvzBase`
- `GP.Swissdec.UvgUvgzKtgBvgBase`

---

### 1.2 Compensation Wage Types

#### **Holiday Compensation** (Wage Type 1160)

**Case Fields:**
- `GP.Swissdec.EmployeeWageHolidayCompensation` (Decimal percentage)
- Plus: Hourly/Daily wage results

**Calculation:**
```csharp
// Location: Scripts/WageTypeWageTypes.cs:185-197
baseWage = WageType[1005 HourlyWage] + WageType[1006 DailyWage]
wage = baseWage × HolidayCompensation
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- All insurance bases

---

#### **Public Holiday Compensation** (Wage Type 1161)

**Case Fields:**
- `GP.Swissdec.EmployeeWagePublicHolidayCompensation` (Decimal percentage)
- Plus: Hourly/Daily wage results

**Calculation:**
```csharp
// Location: Scripts/WageTypeWageTypes.cs:201-214
baseWage = WageType[1005] + WageType[1006]
wage = baseWage × PublicHolidayCompensation
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- All insurance bases

---

#### **13th Month Salary** (Wage Type 1200)

**Case Fields:**
- `GP.Swissdec.EmployeeStatisticsContractual13thPercent` (Decimal percentage)
- OR: `GP.Swissdec.EmployeeWageMonthlyWage13th` (Decimal amount)

**Calculation Logic:**
```csharp
// Location: Scripts/WageTypeWageTypes.cs:234-278

// Option 1: Direct value
if (EmployeeWageMonthlyWage13th is set) {
    wage = EmployeeWageMonthlyWage13th
}
// Option 2: Calculate from monthly salary
else if (MonthlySalary > 0) {
    consCollector = ThirteenthMonthWageBase (YTD accumulated)
    consWage = Previously paid 13th month wages (YTD)
    wage = consCollector / 12 - consWage
}
// Option 3: Calculate from hourly wage
else if (HourlyWage > 0) {
    supplementaryWages = [various allowances]
    baseWage = HourlyWage + supplementaryWages
    wage = baseWage × Contractual13thPercent
}
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- All insurance bases

---

### 1.3 Employer-Paid Benefits (Add to Gross)

#### **Employer Facultative PF/LOB** (Wage Type 1972)

**Case Field:**
- `GP.Swissdec.EmployeeWageEmployerFacultativePfLob` (Decimal amount)

**Calculation:**
```csharp
// Direct case field value
wage = EmployeeWageEmployerFacultativePfLob
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary` (adds to taxable income)
- Insurance bases

---

### 1.4 Absence/Presence Payments

#### **SUVA Daily Allowance** (Wage Type 2030)

**Case Field:**
- `GP.Swissdec.EmployeePresenceAbsenceSuvaDailyAllowance` (Decimal)

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`

---

#### **Daily Sickness Allowance** (Wage Type 2035)

**Case Field:**
- `GP.Swissdec.EmployeePresenceAbsenceDailySicknessAllowance` (Decimal)

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`

---

#### **IC Daily Allowance** (Wage Type 2000)

**Case Field:**
- `GP.Swissdec.EmployeePresenceAbsenceIcDailyAllowance` (Decimal)

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`

---

#### **Military Service Insurance** (Wage Type 2005)

**Case Field:**
- `GP.Swissdec.EmployeePresenceAbsenceMilitaryServiceInsurance` (Decimal)

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`

---

### 1.5 One-Time Payments

#### **Gratuity** (Wage Type 1201)

**Case Field:**
- `GP.Swissdec.EmployeeOneTimeWageGratuity` (Decimal)

**Calculation:**
```csharp
// Direct value from case field
wage = EmployeeOneTimeWageGratuity
```

**Collectors Fed:**
- `GP.Swissdec.GrossSalary`
- All insurance bases

---

## Part 2: Deduction Calculations (Reduce Net Pay)

### 2.1 AHV/OASI Contribution (Wage Type 5010)

**Case Fields Affecting Calculation:**
- `GP.Swissdec.EmployeeBirthDate` → determines pension status
- `GP.Swissdec.EmployeeAhvInsuranceSpecialCase` → special case flag
- `GP.Swissdec.EmployeeAhvInsuranceInstitutionId` → institution

**Calculation:**
```csharp
// Location: Scripts/WageTypeOasi.cs:22-53

// Check pension status (age-based)
pensionStatus = Employee.GetPensionContributionStatus()
if (pensionStatus is SpecialCase or NonObligatory) {
    return 0  // No contribution
}

ahvBaseCollector = Collector["AhvBase"]  // From wage types
if (ahvBaseCollector == 0) return 0

// Get contribution rate from lookup tables
contribution = Company.GetAhvContribution()
wage = ahvBaseCollector × contribution.EmployeePercent
```

**Source Collector:** `GP.Swissdec.AhvBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions` (negative collector)

---

### 2.2 ALV/UI Contribution (Wage Type 5020)

**Case Fields:**
- Employment relationship determines applicability

**Calculation:**
```csharp
// Location: Scripts/WageTypeUi.cs

alvBase = Collector["AlvBase"]
contribution = Company.GetAlvContribution()
wage = alvBase × contribution.EmployeePercent
```

**Source Collector:** `GP.Swissdec.AlvBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions`

---

### 2.3 UVG/SUVA Contribution (Wage Type 5040)

**Case Fields:**
- `GP.Swissdec.EmployeeUvgInsuranceCode` → determines rate
- `GP.Swissdec.EmployeeSex` → affects percentage

**Calculation:**
```csharp
// Location: Scripts/WageTypeSuva.cs:134-173

uvgCode = Employee.Uvg.Code
contribution = Company.GetContribution(InsuranceName.Uvg, uvgCode)

gender = Employee.GetGender()
percent = contribution.GetAnPercent(gender)

suvaBasis = Collector["UvgBase"]
wage = suvaBasis × percent
```

**Source Collector:** `GP.Swissdec.UvgBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions`

---

### 2.4 UVGZ/SAI Contribution (Wage Type 5041)

**Case Fields:**
- `GP.Swissdec.EmployeeUvgzInsuranceCode` (can have multiple slots)
- `GP.Swissdec.EmployeeSex`

**Calculation:**
```csharp
// Location: Scripts/WageTypeSai.cs:96-144

gender = Employee.GetGender()
total = 0

foreach (insurance in Employee.Uvgz.Insurances) {
    contribution = Company.GetContribution(InsuranceName.Uvgz, insurance.Code)
    percent = contribution.GetAnPercent(gender)
    
    slotWageType = GetSlotWageType(5005, insurance.CaseSlot)  // 5005.1, 5005.2, etc.
    result = WageType[slotWageType] × percent
    total += result
}
```

**Source Collector:** `GP.Swissdec.UvgzBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions`

---

### 2.5 KTG/DSA Contribution (Wage Type 5045)

**Case Fields:**
- `GP.Swissdec.EmployeeKtgInsuranceCode` (can have multiple slots)
- `GP.Swissdec.EmployeeSex`

**Calculation:**
```csharp
// Location: Scripts/WageTypeDsa.cs:93-141

gender = Employee.GetGender()
total = 0

foreach (insurance in Employee.Ktg.Insurances) {
    contribution = Company.GetContribution(InsuranceName.Ktg, insurance.Code)
    percent = contribution.GetAnPercent(gender)
    
    slotWageType = GetSlotWageType(5006, insurance.CaseSlot)  // 5006.1, 5006.2, etc.
    result = WageType[slotWageType] × percent
    total += result
}
```

**Source Collector:** `GP.Swissdec.KtgBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions`

---

### 2.6 QST/Withholding Tax (Wage Type 5060)

**Case Fields:**
- `GP.Swissdec.EmployeeQstTaxCode` → tax rate code
- `GP.Swissdec.EmployeeAddressCanton` → canton determines rates
- `GP.Swissdec.EmployeeCivilStatus` → affects tax bracket
- `GP.Swissdec.EmployeeChildCount` → affects deductions

**Calculation:**
```csharp
// Location: Scripts/WageTypeQst.cs:537-588

if (Collector["QstBase"] == 0) return 0
if (!Employee.IsQstObligated()) return 0

qstCode = GetPeriodCaseValue("EmployeeQstTaxCode")
qstStyle = Employee.GetQstCycle()  // Yearly or Monthly

// Lookup tax rate from lookup tables based on:
// - Canton
// - QST code
// - Year/Month defining wage
wage = LookupTaxRate(canton, qstCode, definingWage)
```

**Source Collector:** `GP.Swissdec.QstBase`
**Feeds Into:** `GP.Swissdec.EmployeeContributions`

---

## Part 3: Net Pay Calculation Flow

### 3.1 The Collector Hierarchy

```
WAGE TYPES (Individual payments)
    ↓ [collectors]
COLLECTORS (Aggregated amounts)
    ↓
FINAL CALCULATION
```

### 3.2 Key Collectors

#### **GrossSalary Collector**
- **Fed by:** All wage types that contribute to gross pay
  - Monthly Salary (1000)
  - Hourly Wage (1005)
  - Holiday Compensation (1160)
  - Public Holiday Compensation (1161)
  - 13th Month Wage (1200)
  - Employer Benefits (1972)
  - Daily Allowances (2000-2035)
  - Gratuity (1201)
  - Many others...

#### **EmployeeContributions Collector** (Negative amounts)
- **Fed by:** All deduction wage types
  - AHV/OASI (5010)
  - ALV/UI (5020, 5030)
  - UVG/SUVA (5040)
  - UVGZ/SAI (5041)
  - KTG/DSA (5045)
  - QST Tax (5060)
  - BVG/PF (5050)

#### **Expenses Collector**
- **Fed by:** Expense wage types
  - Travel expenses (6000)
  - Car expenses (6001)
  - Meals expenses (6002)
  - etc.

### 3.3 Net Wage Calculation (Wage Type 6500)

```csharp
// Location: Scripts/WageTypeWageTypes.cs:337-347

netWage = Collector["GrossSalary"] 
        - Collector["EmployeeContributions"]
        + Collector["Expenses"]
        + NetWageRetro
```

### 3.4 Payment Calculation (Wage Type 6600)

```csharp
// Location: Scripts/WageTypeWageTypes.cs:359-369

paymentWage = WageType[6500 NetWage]
            - WageType[6510 AdvancePayment]
            - WageType[6550 PeriodPaidWageRuns]
```

---

## Part 4: Converting Yearly Salary to Input

### 4.1 Minimal Setup for Monthly Salary Employee

**Given:** Yearly Gross Salary = CHF 66,000

**Required Case Fields:**

```json
{
  "caseName": "GP.Swissdec.EmployeeStatistics",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeStatisticsContract",
      "value": "indefiniteSalaryMth",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
      "value": "5500",  // 66000 / 12 = 5500
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
      "value": "0.0833",  // If 13th month is included in yearly, otherwise 0
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}
```

### 4.2 Essential Non-Numeric Fields for Deductions

Even with monthly salary, these fields affect net pay calculations:

```json
// 1. Employee Demographics
{
  "caseName": "GP.Swissdec.Employee",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeSex",
      "value": "M",  // or "F"
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeBirthDate",
      "value": "1985-01-15",  // Determines pension contribution status
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeCivilStatus",
      "value": "married",  // single, married, divorced, widowed
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeEntryDate",
      "value": "2022-01-01",
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}

// 2. Address (for tax canton)
{
  "caseName": "GP.Swissdec.EmployeeAddress",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressCanton",
      "value": "ZH",  // Zürich, BE (Bern), LU (Lucerne), etc.
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}

// 3. Insurance Codes
{
  "caseName": "GP.Swissdec.EmployeeAhvInsurance",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceInstitutionId",
      "value": "079.000",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceSpecialCase",
      "value": "false",
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}

{
  "caseName": "GP.Swissdec.EmployeeUvgInsurance",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeUvgInsuranceCode",
      "value": "A1",  // Company-specific insurance code
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}
```

### 4.3 Simplified Input Template

For a basic monthly salary employee with minimal configuration:

```json
{
  "$schema": "../PayrollEngine.Exchange.schema.json",
  "createdObjectDate": "2022-01-01T00:00:00.0Z",
  "tenants": [{
    "identifier": "GP.Swissdec",
    "payrolls": [{
      "name": "GP.Swissdec",
      "divisionName": "GP.Swissdec",
      "cases": [
        {
          "userIdentifier": "user@company.ch",
          "employeeIdentifier": "Employee Name",
          "divisionName": "GP.Swissdec",
          "case": {
            "caseName": "GP.Swissdec.Employee",
            "values": [
              {
                "caseFieldName": "GP.Swissdec.EmployeeSex",
                "value": "M",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeBirthDate",
                "value": "1985-06-15",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeCivilStatus",
                "value": "single",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeEntryDate",
                "value": "2022-01-01",
                "start": "2022-01-01T00:00:00Z"
              }
            ]
          }
        },
        {
          "userIdentifier": "user@company.ch",
          "employeeIdentifier": "Employee Name",
          "divisionName": "GP.Swissdec",
          "case": {
            "caseName": "GP.Swissdec.EmployeeStatistics",
            "values": [
              {
                "caseFieldName": "GP.Swissdec.EmployeeStatisticsContract",
                "value": "indefiniteSalaryMth",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
                "value": "5500.00",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
                "value": "0.0833",
                "start": "2022-01-01T00:00:00Z"
              }
            ]
          }
        },
        {
          "userIdentifier": "user@company.ch",
          "employeeIdentifier": "Employee Name",
          "divisionName": "GP.Swissdec",
          "case": {
            "caseName": "GP.Swissdec.EmployeeAddress",
            "values": [
              {
                "caseFieldName": "GP.Swissdec.EmployeeAddressCanton",
                "value": "ZH",
                "start": "2022-01-01T00:00:00Z"
              }
            ]
          }
        },
        {
          "userIdentifier": "user@company.ch",
          "employeeIdentifier": "Employee Name",
          "divisionName": "GP.Swissdec",
          "case": {
            "caseName": "GP.Swissdec.EmployeeAhvInsurance",
            "values": [
              {
                "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceInstitutionId",
                "value": "079.000",
                "start": "2022-01-01T00:00:00Z"
              },
              {
                "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceSpecialCase",
                "value": "false",
                "start": "2022-01-01T00:00:00Z"
              }
            ]
          }
        },
        {
          "userIdentifier": "user@company.ch",
          "employeeIdentifier": "Employee Name",
          "divisionName": "GP.Swissdec",
          "case": {
            "caseName": "GP.Swissdec.EmployeeUvgInsurance",
            "values": [
              {
                "caseFieldName": "GP.Swissdec.EmployeeUvgInsuranceCode",
                "value": "A1",
                "start": "2022-01-01T00:00:00Z"
              }
            ]
          }
        }
      ]
    }]
  }]
}
```

---

## Part 5: Complete Wage Type to Collector Mapping

### 5.1 Wage Types That Feed GrossSalary

| Wage Type | Number | Case Field(s) | Calculation |
|-----------|--------|---------------|-------------|
| Monthly Salary | 1000 | ContractualMonthlyWage | Direct value |
| Wage Correction | 1001 | WageCorrection | Direct value |
| Hourly Wage | 1005 | ContractualHourlyWage × WorkedHours | Multiplication |
| Daily Wage | 1006 | ContractualHourlyWage × WorkedLessons | Multiplication |
| Weekly Wage | 1007 | WeeklyWage | Direct value |
| Remuneration | 1010 | Remuneration | Direct value |
| Holiday Compensation | 1160 | HolidayCompensation × (HourlyWage + DailyWage) | Percentage |
| Public Holiday Comp | 1161 | PublicHolidayCompensation × (HourlyWage + DailyWage) | Percentage |
| 13th Month Salary | 1200 | See section 1.2 | Complex |
| Employer PF/LOB | 1972 | EmployerFacultativePfLob | Direct value |
| IC Daily Allowance | 2000 | IcDailyAllowance | Direct value |
| Military Service Ins | 2005 | MilitaryServiceInsurance | Direct value |
| SUVA Daily Allowance | 2030 | SuvaDailyAllowance | Direct value |
| Daily Sickness Allow | 2035 | DailySicknessAllowance | Direct value |
| Gratuity | 1201 | Gratuity | Direct value |

### 5.2 Insurance Base Collectors

| Collector | Fed By Wage Types | Used For |
|-----------|-------------------|----------|
| AhvBase | 1000, 1005, 1160, 1161, 1200, etc. | AHV/IV/EO contributions |
| AlvBase | Same as AhvBase | ALV/ALVZ contributions |
| UvgBase | Same as AhvBase | UVG (accident insurance) |
| UvgzBase | Same as AhvBase | UVGZ (supplemental accident) |
| KtgBase | Same as AhvBase | KTG (daily sickness insurance) |
| BvgBase | Same as AhvBase | BVG (pension fund) |
| QstBase | Most wage types | Withholding tax |

### 5.3 Deduction Wage Types

| Wage Type | Number | Source Collector | Calculation Depends On |
|-----------|--------|------------------|------------------------|
| OASI (AHV) | 5010 | AhvBase | Birth date, special case flag |
| UI (ALV) | 5020 | AlvBase | None (standard rate) |
| SUI (ALVZ) | 5030 | AlvBase | Salary threshold |
| SUVA (UVG) | 5040 | UvgBase | Insurance code, gender |
| SAI (UVGZ) | 5041 | UvgzBase | Insurance code(s), gender |
| DSA (KTG) | 5045 | KtgBase | Insurance code(s), gender |
| PF/LOB (BVG) | 5050 | BvgBase | Age, insurance plan |
| QST Tax | 5060 | QstBase | Canton, tax code, civil status |

---

## Part 6: Conversion Formula Reference

### From Yearly Salary to Monthly

```
Monthly Wage = Yearly Salary / 12

Examples:
- CHF 60,000/year → CHF 5,000/month
- CHF 66,000/year → CHF 5,500/month
- CHF 72,000/year → CHF 6,000/month
- CHF 84,000/year → CHF 7,000/month
```

### 13th Month Salary Considerations

**If yearly salary INCLUDES 13th month:**
```
Monthly Wage = Yearly Salary / 13
13th Percent = 1/12 = 0.0833
```

**If yearly salary EXCLUDES 13th month:**
```
Monthly Wage = Yearly Salary / 12
13th Percent = 0 (or set 13th month directly later)
```

### From Hourly Rate

If you only have yearly salary but need hourly:
```
Standard Swiss Full-Time: 2080 hours/year (40 hrs/week × 52 weeks)
Hourly Rate = Yearly Salary / 2080

Example:
CHF 60,000/year → CHF 28.85/hour
```

---

## Summary

**For monthly salary employees (RECOMMENDED):**

1. **Minimum numeric input:** `ContractualMonthlyWage` = Yearly Salary / 12
2. **Essential non-numeric inputs:**
   - Sex, Birth Date, Civil Status
   - Address Canton
   - Insurance codes (AHV, UVG)
3. **Calculation flow:**
   - Monthly Wage → GrossSalary collector
   - GrossSalary → Insurance base collectors
   - Insurance bases × rates → Deductions
   - Net Pay = Gross - Deductions + Expenses

**The system automatically calculates:**
- All insurance contributions based on demographic data
- Tax withholding based on canton and civil status
- Employer contributions based on insurance codes
- Net payment after all deductions

**Files containing calculation logic:**
- `/Scripts/WageTypeWageTypes.cs` - Base wage calculations
- `/Scripts/WageType*.cs` - Specific deduction calculations
- `/Regulation/WageTypes.json` - Wage type to collector mappings
- `/Regulation/Collectors.json` - Collector definitions

