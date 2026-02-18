# Your HR Fields → Swiss Payroll Engine Mapping

## Field Impact Classification

### 🟢 HIGH IMPACT - Directly Affects Gross Pay (11 fields)

| Your Field | Payroll Engine Case Field | Impact | Notes |
|------------|---------------------------|--------|-------|
| **basic salary** | `EmployeeStatisticsContractualMonthlyWage` | PRIMARY | Main salary component |
| **activity rate** | `EmployeeActivityRatePercent` | HIGH | For part-time (0.5 = 50%, 1.0 = 100%) |
| **13th salary** | `EmployeeStatisticsContractual13thPercent` | HIGH | Usually 0.0833 (1/12) or 0 |
| **Sign on Bonus Gross** | `EmployeeOneTimeWageGratuity` or custom wage field | MEDIUM | One-time payment, adds to gross |
| **Insurance Allowance** | `EmployeeWageEmployerFacultativeHealthInsurance` | MEDIUM | Tax-free or taxable benefit |
| **Home Office Allowance** | `EmployeeWageHomebasedWorkAllowance` | MEDIUM | Adds to gross salary |
| **Transportation Allowance** | `TravelExpensesReimbursement` or `TravelExpenses` | MEDIUM | Could be expense (tax-free) or taxable |
| **Telephone/Data Allowance** | Custom wage type or `EmployeeWage` field | MEDIUM | Usually taxable benefit |
| **Education Allowance** | `EmployeeWageEducationAllowance` or `FurtherTraining` | MEDIUM | Could be expense or benefit |
| **General Allowance** | Custom `EmployeeWage` field | MEDIUM | Adds to gross |
| **"Other" Allowance** | Custom `EmployeeWage` field | MEDIUM | Adds to gross |

---

### 🟡 MEDIUM IMPACT - Affects Deductions & Net Pay (14 fields)

#### Demographics (Affect Insurance & Tax Rates)

| Your Field | Payroll Engine Case Field | Impact | Why It Matters |
|------------|---------------------------|--------|----------------|
| **Date of birth** | `EmployeeBirthDate` | HIGH | Determines pension contribution status, BVG rates |
| **Sex** | `EmployeeSex` | HIGH | Affects UVG, UVGZ, KTG insurance rates |
| **Civil status** | `EmployeeCivilStatus` | HIGH | Affects QST tax withholding significantly |
| **civil status since** | `EmployeeCivilStatusValidFromDate` | LOW | Date tracking, minor impact |
| **Nationality 1** | `EmployeeNationality` | MEDIUM | Could affect special cases, work permits |
| **Nationality 2** | Additional field | LOW | Secondary nationality tracking |

#### Location (Affects Tax Rates)

| Your Field | Payroll Engine Case Field | Impact | Why It Matters |
|------------|---------------------------|--------|----------------|
| **Canton** | `EmployeeAddressCanton` | HIGH | Determines QST tax rates (varies significantly by canton) |
| **canton of work** | `EmployeeWorkplaceCanton` or company setting | MEDIUM | Can differ from residence, affects tax jurisdiction |

#### Family (Affects Tax & Possibly Child Allowances)

| Your Field | Payroll Engine Case Field | Impact | Why It Matters |
|------------|---------------------------|--------|----------------|
| **spouse surname/name** | `EmployeeSpouse` fields | LOW | Informational for married tax calculations |
| **spouse date of birth** | `EmployeeSpouseBirthDate` | LOW | Could affect tax calculations |
| **marriage date** | `EmployeeMarriageDate` | LOW | Date tracking |
| **Canton of work spouse** | Custom field | LOW | Could affect tax calculations |
| **Children info** (4 children) | `EmployeeChild` case with multiple slots | MEDIUM | Affects tax deductions, possibly child allowances |

#### Employment Terms

| Your Field | Payroll Engine Case Field | Impact | Why It Matters |
|------------|---------------------------|--------|----------------|
| **Start date** | `EmployeeEntryDate` | MEDIUM | Determines pay start, prorating for partial months |
| **Pension plan** | BVG insurance codes/settings | MEDIUM | Determines pension contribution rates and thresholds |

---

### 🔴 NO IMPACT - Administrative/Informational Only (18 fields)

These fields don't affect pay calculations but are needed for records:

| Your Field | Purpose | Notes |
|------------|---------|-------|
| GP PR ID | Employee identifier | Internal system ID |
| Trace ID | Tracking/audit | System tracking |
| Reference Id | External reference | Could be employee number |
| SURNAME | Employee name | For display/reports |
| NAME | Employee name | For display/reports |
| Address1 | Mailing address | For correspondence |
| Address2 | Mailing address | For correspondence |
| Postcode | Mailing address | For correspondence |
| City | Mailing address | For correspondence |
| Country | Address country | Informational (unless not CH) |
| E-mail for sending payslip | Communication | Payslip delivery |
| Place of birth | Personal data | Informational |
| Country of birth | Personal data | Informational |
| AVS number | Social security ID | Identification only |
| Bank name | Payment routing | For bank transfer |
| IBAN 1 | Payment routing | For bank transfer |
| Other Allowance NOTES | Documentation | Explains custom allowance |
| Additional Comments | Documentation | General notes |
| Team Email | Communication | Team contact |

---

## Mapping Priority Levels

### 🔥 CRITICAL - Must Map (Core Pay Calculation)

1. **basic salary** → `ContractualMonthlyWage`
2. **Date of birth** → `EmployeeBirthDate`
3. **Sex** → `EmployeeSex`
4. **Civil status** → `EmployeeCivilStatus`
5. **Canton** → `EmployeeAddressCanton`
6. **Start date** → `EmployeeEntryDate`

### ⚡ HIGH PRIORITY - Significantly Affects Pay

7. **activity rate** → `EmployeeActivityRatePercent`
8. **13th salary** → `EmployeeStatisticsContractual13thPercent`
9. **canton of work** → Workplace or company setting
10. **Pension plan** → Insurance configuration
11. **Children** (all 4) → `EmployeeChild` cases

### 📊 MEDIUM PRIORITY - Adds to Gross/Benefits

12. **Sign on Bonus Gross** → Custom wage type
13. **Insurance Allowance** → `EmployerFacultativeHealthInsurance`
14. **Home Office Allowance** → `HomebasedWorkAllowance`
15. **Transportation Allowance** → Expense or wage type
16. **Telephone/Data Allowance** → Custom wage type
17. **Education Allowance** → Custom wage type
18. **General Allowance** → Custom wage type
19. **"Other" Allowance** → Custom wage type

---

## Detailed Mapping Guide

### 1. Basic Salary Setup

```json
{
  "caseName": "GP.Swissdec.EmployeeStatistics",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
      "value": "[YOUR: basic salary]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
      "value": "[YOUR: 13th salary]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Usually 0.0833 for 1/12, or 0 if included in basic"
    }
  ]
}
```

### 2. Demographics for Deductions

```json
{
  "caseName": "GP.Swissdec.Employee",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeFirstName",
      "value": "[YOUR: NAME]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeLastName",
      "value": "[YOUR: SURNAME]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeNumber",
      "value": "[YOUR: Reference Id or GP PR ID]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeSex",
      "value": "[YOUR: Sex]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Convert to 'M' or 'F'"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeBirthDate",
      "value": "[YOUR: Date of birth]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Format: YYYY-MM-DD"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeNationality",
      "value": "[YOUR: Nationality 1]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "ISO country code: CH, DE, FR, IT, etc."
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeCivilStatus",
      "value": "[YOUR: Civil status]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Convert to: single, married, divorced, widowed"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeEntryDate",
      "value": "[YOUR: Start date]",
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}
```

### 3. Part-Time Employee

```json
{
  "caseName": "GP.Swissdec.EmployeeEmployment",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeActivityRatePercent",
      "value": "[YOUR: activity rate]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "0.5 = 50%, 0.8 = 80%, 1.0 = 100%"
    }
  ]
}
```

### 4. Address (for Tax Canton)

```json
{
  "caseName": "GP.Swissdec.EmployeeAddress",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressCountry",
      "value": "[YOUR: Country]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Usually 'CHE' for Switzerland"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressStreet",
      "value": "[YOUR: Address1 + Address2]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressZipCode",
      "value": "[YOUR: Postcode]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressCity",
      "value": "[YOUR: City]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeAddressCanton",
      "value": "[YOUR: Canton]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "CRITICAL: ZH, BE, GE, etc. - determines tax rates!"
    }
  ]
}
```

### 5. Children (for Tax Deductions)

```json
// If you have children data
{
  "caseName": "GP.Swissdec.EmployeeChild",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeChildCount",
      "value": "[COUNT of children]",
      "start": "2022-01-01T00:00:00Z",
      "comment": "Count children 1-4 that have data"
    }
  ]
}

// For each child:
{
  "caseName": "GP.Swissdec.EmployeeChildInfo",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeChildFirstName",
      "caseSlot": "1",
      "value": "[YOUR: Name child 1]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeChildLastName",
      "caseSlot": "1",
      "value": "[YOUR: Surname child 1]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeChildBirthDate",
      "caseSlot": "1",
      "value": "[YOUR: date of birth child 1]",
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}
// Repeat for children 2, 3, 4 with caseSlot: "2", "3", "4"
```

### 6. Allowances (Add to Gross Salary)

```json
{
  "caseName": "GP.Swissdec.EmployeeWage",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeWageHomebasedWorkAllowance",
      "value": "[YOUR: Home Office Allowance]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeWageEmployerFacultativeHealthInsurance",
      "value": "[YOUR: Insurance Allowance]",
      "start": "2022-01-01T00:00:00Z"
    },
    {
      "caseFieldName": "GP.Swissdec.EmployeeWageTravelExpensesReimbursement",
      "value": "[YOUR: Transportation Allowance]",
      "start": "2022-01-01T00:00:00Z"
    }
  ]
}

// For custom allowances (Telephone, Education, General, Other):
// You may need to create custom wage types or use generic allowance fields
```

### 7. One-Time Payments

```json
{
  "caseName": "GP.Swissdec.EmployeeOne-timeWage",
  "values": [
    {
      "caseFieldName": "GP.Swissdec.EmployeeOneTimeWageGratuity",
      "value": "[YOUR: Sign on Bonus Gross]",
      "start": "[Date to pay bonus]"
    }
  ]
}
```

---

## Conversion Rules

### Your Field → Payroll Format

| Your Format | Payroll Format | Conversion Rule |
|-------------|----------------|-----------------|
| Sex: "Male"/"Female" | "M"/"F" | M=Male, F=Female |
| Civil status: text | "single", "married", etc. | Map to valid values |
| Date: Any format | "YYYY-MM-DD" | Convert to ISO date |
| Canton: Full name | "ZH", "BE", etc. | Use 2-letter code |
| Activity rate: 80% | 0.8 | Convert percentage to decimal |
| 13th salary: "Yes"/"No" | 0.0833 or 0 | Yes=0.0833, No=0 |
| Allowances: Monthly | Monthly amount | Use as-is if monthly |
| Allowances: Yearly | Monthly amount | Divide by 12 |

---

## Impact Summary by Category

### Gross Pay Components (What Goes In)
```
Basic Salary           ← basic salary
+ 13th Month Salary    ← 13th salary × basic salary
+ Sign-on Bonus        ← Sign on Bonus Gross
+ Home Office Allow    ← Home Office Allowance
+ Insurance Allow      ← Insurance Allowance
+ Transportation       ← Transportation Allowance
+ Telephone            ← Telephone/Data Allowance
+ Education            ← Education Allowance
+ General              ← General Allowance
+ Other                ← "Other" Allowance
─────────────────────
= GROSS SALARY
```

### Deduction Factors (What Affects Deductions)
```
AHV/IV/EO:  Affected by → Date of birth (age), basic salary
ALV:        Affected by → basic salary
UVG:        Affected by → basic salary, Sex
UVGZ:       Affected by → basic salary, Sex
KTG:        Affected by → basic salary, Sex
BVG:        Affected by → Date of birth (age), basic salary, Pension plan
QST Tax:    Affected by → Canton, Civil status, Children, basic salary
```

### Part-Time Adjustment
```
If activity rate < 1.0:
  All calculations × activity rate
  
Example: 80% employee
  Basic: CHF 6,000 × 0.8 = CHF 4,800
  All deductions also reduced proportionally
```

---

## Fields NOT in Your List (But May Be Needed)

These payroll engine fields don't appear in your HR data:

| Missing Field | Payroll Field | Default/Solution |
|--------------|---------------|------------------|
| AHV/AVS Special Case | `EmployeeAhvInsuranceSpecialCase` | Default: `false` |
| UVG Insurance Code | `EmployeeUvgInsuranceCode` | Default: `"A1"` |
| AHV Institution ID | `EmployeeAhvInsuranceInstitutionId` | Default: `"079.000"` |
| Weekly Hours | `EmployeeWeeklyHours` | Calculate: 40 hrs × activity rate |
| Contract Type | `EmployeeStatisticsContract` | Default: `"indefiniteSalaryMth"` |

**Recommendation:** Use default values for these unless you have company-specific insurance codes.

---

## Example: Complete Mapping

**Your Data:**
```
NAME: John
SURNAME: Smith
basic salary: 6000
activity rate: 1.0
13th salary: 0.0833
Date of birth: 1985-06-15
Sex: Male
Civil status: Married
Canton: ZH
Start date: 2022-01-01
Home Office Allowance: 100
Insurance Allowance: 50
Sign on Bonus Gross: 5000
Surname child 1: Smith
Name child 1: Emma
date of birth child 1: 2015-03-20
```

**Expected Monthly Pay:**
```
Basic Salary:           CHF 6,000.00
13th Month Accrual:     CHF   499.98  (6000 × 0.0833)
Home Office Allow:      CHF   100.00
Insurance Allow:        CHF    50.00
───────────────────────────────────
GROSS SALARY:           CHF 6,649.98

DEDUCTIONS:
AHV (5.3%):            -CHF   352.45
ALV (1.1%):            -CHF    73.15
UVG (1.0%):            -CHF    66.50
BVG (varies):          -CHF   270.00
QST (married, ZH):     -CHF   380.00  (reduced due to child)
───────────────────────────────────
NET SALARY:             CHF 5,507.88
```

**Plus One-Time (in sign-on month):**
```
Sign-on Bonus:         +CHF 5,000.00
───────────────────────────────────
TOTAL PAYMENT:          CHF 10,507.88
```

---

## Validation Checklist

Before importing, verify:

- [ ] **basic salary** is monthly amount (not yearly)
- [ ] **activity rate** is decimal (0.8, not 80)
- [ ] **13th salary** is either 0.0833 or 0
- [ ] **Date of birth** is YYYY-MM-DD format
- [ ] **Sex** is "M" or "F"
- [ ] **Civil status** is valid value (single/married/divorced/widowed)
- [ ] **Canton** is 2-letter code (ZH, BE, GE, etc.)
- [ ] **Start date** is YYYY-MM-DD format
- [ ] All allowances are monthly amounts (not yearly)
- [ ] Children dates of birth are provided if they exist
- [ ] **Pension plan** maps to valid insurance configuration

---

## Priority Mapping Order

**Phase 1: Minimum Viable (Core Pay)** ✅
1. basic salary
2. Start date
3. Date of birth
4. Sex
5. Canton
6. Civil status

**Phase 2: Accurate Pay (Adjustments)** ✅
7. activity rate
8. 13th salary
9. Children (all 4 if applicable)
10. Pension plan

**Phase 3: Complete Pay (All Components)** ✅
11. All allowances
12. Sign-on bonus
13. Spouse information
14. Canton of work

---

## Summary Table

| Category | Fields That Affect Pay | Fields That Don't |
|----------|----------------------|-------------------|
| **Count** | **25 fields** | **18 fields** |
| **% of Total** | **58%** | **42%** |

**Key Insight:** More than half your fields affect pay calculations!

**Most Critical 6 Fields:**
1. basic salary (primary wage)
2. Date of birth (pension/insurance)
3. Sex (insurance rates)
4. Canton (tax rates)
5. Civil status (tax rates)
6. Start date (timing)

These 6 fields alone enable basic payroll calculation. The other 19 pay-affecting fields refine and adjust the calculation.

