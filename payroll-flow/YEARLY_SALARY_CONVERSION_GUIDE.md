# Yearly Salary to Payroll Input Conversion Guide

## Quick Reference: From Yearly Salary to Required Inputs

### Scenario: You have only yearly base salary and basic employee info

**Example Input Data:**
- Employee: John Smith
- Yearly Gross Salary: CHF 72,000
- Canton: Zürich (ZH)
- Gender: Male
- Birth Date: June 15, 1985
- Civil Status: Married
- Start Date: January 1, 2022

---

## Step 1: Calculate Monthly Wage

### Formula
```
Monthly Wage = Yearly Salary ÷ 12 (if no 13th month included)
Monthly Wage = Yearly Salary ÷ 13 (if 13th month included in yearly)
```

### Examples

| Yearly Salary | Calculation | Monthly Wage | 13th Month |
|---------------|-------------|--------------|------------|
| CHF 60,000 | 60,000 ÷ 12 | CHF 5,000 | Separate |
| CHF 65,000 | 65,000 ÷ 13 | CHF 5,000 | Included |
| CHF 72,000 | 72,000 ÷ 12 | CHF 6,000 | Separate |
| CHF 78,000 | 78,000 ÷ 13 | CHF 6,000 | Included |
| CHF 84,000 | 84,000 ÷ 12 | CHF 7,000 | Separate |
| CHF 96,000 | 96,000 ÷ 12 | CHF 8,000 | Separate |

### For Our Example:
```
CHF 72,000 ÷ 12 = CHF 6,000 per month
```

---

## Step 2: Determine 13th Month Salary Setting

### Option A: Separate 13th Month (Most Common)
```json
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
  "value": "6000.00"
},
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
  "value": "0.0833"  
}
```
**Result:** Employee gets CHF 6,000 per month + CHF 500 (6000×0.0833) 13th month accrual

### Option B: 13th Month Included in Yearly
```json
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
  "value": "5538.46"  // 72,000 ÷ 13
},
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
  "value": "0.0833"
}
```

### Option C: No 13th Month
```json
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractualMonthlyWage",
  "value": "6000.00"
},
{
  "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
  "value": "0"
}
```

---

## Step 3: Map Employee Demographics

### From Your Data → Case Fields

| Your Data | Case Field Name | Value Format | Example |
|-----------|-----------------|--------------|---------|
| Gender: Male/Female | `EmployeeSex` | "M" or "F" | "M" |
| Birth Date | `EmployeeBirthDate` | "YYYY-MM-DD" | "1985-06-15" |
| Civil Status | `EmployeeCivilStatus` | See below | "married" |
| Start Date | `EmployeeEntryDate` | "YYYY-MM-DD" | "2022-01-01" |

### Civil Status Values
- `"single"` - Single, never married
- `"married"` - Married
- `"divorced"` - Divorced
- `"widowed"` - Widowed
- `"separated"` - Legally separated
- `"registeredPartnership"` - Registered partnership

---

## Step 4: Map Location Data

### Canton Codes (for Address)

| Canton | Code | German Name |
|--------|------|-------------|
| Aargau | AG | Aargau |
| Appenzell Innerrhoden | AI | Appenzell Innerrhoden |
| Appenzell Ausserrhoden | AR | Appenzell Ausserrhoden |
| Bern | BE | Bern |
| Basel-Landschaft | BL | Basel-Landschaft |
| Basel-Stadt | BS | Basel-Stadt |
| Fribourg | FR | Freiburg |
| Geneva | GE | Genf |
| Glarus | GL | Glarus |
| Graubünden | GR | Graubünden |
| Jura | JU | Jura |
| Lucerne | LU | Luzern |
| Neuchâtel | NE | Neuenburg |
| Nidwalden | NW | Nidwalden |
| Obwalden | OW | Obwalden |
| St. Gallen | SG | St. Gallen |
| Schaffhausen | SH | Schaffhausen |
| Solothurn | SO | Solothurn |
| Schwyz | SZ | Schwyz |
| Thurgau | TG | Thurgau |
| Ticino | TI | Tessin |
| Uri | UR | Uri |
| Vaud | VD | Waadt |
| Valais | VS | Wallis |
| Zug | ZG | Zug |
| **Zürich** | **ZH** | **Zürich** |

---

## Step 5: Set Default Insurance Values

### Standard Insurance Codes

Most companies use standard codes. If you don't have specific codes:

```json
// AHV Insurance (Pension)
{
  "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceInstitutionId",
  "value": "079.000"  // Standard code
},
{
  "caseFieldName": "GP.Swissdec.EmployeeAhvInsuranceSpecialCase",
  "value": "false"  // Normal case (not special)
}

// UVG Insurance (Accident)
{
  "caseFieldName": "GP.Swissdec.EmployeeUvgInsuranceCode",
  "value": "A1"  // Standard shared employee/employer code
}
```

### If You Have Company-Specific Insurance:
```json
// Use your company's insurance provider codes
{
  "caseFieldName": "GP.Swissdec.EmployeeUvgInsuranceInstitutionId",
  "value": "YourInsuranceCompanyName"
},
{
  "caseFieldName": "GP.Swissdec.EmployeeUvgInsuranceCode",
  "value": "YourPlanCode"
}
```

---

## Complete Example JSON

### Scenario: John Smith, CHF 72,000/year, Zürich

```json
{
  "$schema": "../PayrollEngine.Exchange.schema.json",
  "createdObjectDate": "2022-01-01T00:00:00.0Z",
  "tenants": [
    {
      "identifier": "GP.Swissdec",
      "payrolls": [
        {
          "name": "GP.Swissdec",
          "divisionName": "GP.Swissdec",
          "cases": [
            {
              "userIdentifier": "hr@company.ch",
              "employeeIdentifier": "John Smith",
              "divisionName": "GP.Swissdec",
              "case": {
                "caseName": "GP.Swissdec.Employee",
                "values": [
                  {
                    "caseFieldName": "GP.Swissdec.EmployeeFirstName",
                    "value": "John",
                    "start": "2022-01-01T00:00:00Z"
                  },
                  {
                    "caseFieldName": "GP.Swissdec.EmployeeLastName",
                    "value": "Smith",
                    "start": "2022-01-01T00:00:00Z"
                  },
                  {
                    "caseFieldName": "GP.Swissdec.EmployeeNumber",
                    "value": "EMP001",
                    "start": "2022-01-01T00:00:00Z"
                  },
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
                    "caseFieldName": "GP.Swissdec.EmployeeNationality",
                    "value": "CH",
                    "start": "2022-01-01T00:00:00Z"
                  },
                  {
                    "caseFieldName": "GP.Swissdec.EmployeeCivilStatus",
                    "value": "married",
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
              "userIdentifier": "hr@company.ch",
              "employeeIdentifier": "John Smith",
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
                    "value": "6000.00",
                    "start": "2022-01-01T00:00:00Z",
                    "comment": "Calculated from yearly: 72000 / 12"
                  },
                  {
                    "caseFieldName": "GP.Swissdec.EmployeeStatisticsContractual13thPercent",
                    "value": "0.0833",
                    "start": "2022-01-01T00:00:00Z",
                    "comment": "1/12 for 13th month salary"
                  }
                ]
              }
            },
            {
              "userIdentifier": "hr@company.ch",
              "employeeIdentifier": "John Smith",
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
              "userIdentifier": "hr@company.ch",
              "employeeIdentifier": "John Smith",
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
              "userIdentifier": "hr@company.ch",
              "employeeIdentifier": "John Smith",
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
        }
      ]
    }
  ]
}
```

---

## Expected Output

### Based on CHF 72,000/year (CHF 6,000/month)

**Approximate monthly calculation:**

| Item | Amount (CHF) | Calculation |
|------|--------------|-------------|
| **GROSS INCOME** | | |
| Monthly Salary | 6,000.00 | Base |
| 13th Month Accrual | 499.98 | 6000 × 0.0833 |
| **Total Gross** | **6,499.98** | |
| | | |
| **DEDUCTIONS** | | |
| AHV/IV/EO (Pension) | -344.50 | 6500 × 5.3% |
| ALV (Unemployment) | -71.50 | 6500 × 1.1% |
| UVG (Accident) | -65.00 | 6500 × 1.0% |
| BVG (Pension Fund) | ~-260.00 | Age/plan dependent |
| QST (Withholding Tax) | ~-400.00 | Canton ZH, married |
| **Total Deductions** | **-1,141.00** | |
| | | |
| **NET PAY** | **~5,359.00** | Gross - Deductions |

*Note: Exact amounts depend on specific insurance plans and tax tables*

---

## Conversion Formulas Reference

### From Different Yearly Formats

#### 1. **Yearly Gross to Monthly**
```
Monthly = Yearly / 12
```

#### 2. **Yearly Gross including 13th Month to Monthly**
```
Monthly = Yearly / 13
13th Percent = 1/12 = 0.0833
```

#### 3. **Yearly Net to Yearly Gross (Approximate)**
```
Gross ≈ Net / 0.80  // Assuming ~20% total deductions
```
*More accurate: Use specific deduction rates for canton*

#### 4. **Hourly Rate to Yearly**
```
Yearly = Hourly × Hours per Year
Standard: 2080 hours (40hrs/week × 52 weeks)
Example: CHF 35/hr × 2080 = CHF 72,800/year
```

#### 5. **Hourly Rate to Monthly**
```
Monthly = Yearly / 12
       = (Hourly × 2080) / 12
       = Hourly × 173.33
Example: CHF 35/hr × 173.33 = CHF 6,066.55/month
```

---

## Common Deduction Rates (2022-2023)

### Percentage-Based Deductions

| Insurance | Employee % | Employer % | Total % | Base |
|-----------|-----------|-----------|---------|------|
| AHV/IV/EO | 5.3% | 5.3% | 10.6% | Full salary |
| ALV | 1.1% | 1.1% | 2.2% | Up to CHF 148,200 |
| ALVZ | 0.5% | 0.5% | 1.0% | Above CHF 148,200 |
| UVG (Accident) | 1.0%* | Varies | Varies | Full salary |
| BVG (Pension) | Varies | Varies | Varies | Coordinated salary |

*Rates vary by company and insurance provider

### QST (Withholding Tax) Rates
Highly variable by:
- Canton (ZH, BE, GE have different rates)
- Civil status (single, married)
- Children (each child reduces tax)
- Salary level (progressive)

**Example Zürich (ZH) 2022:**
- Single, CHF 72,000/year: ~12-14%
- Married, CHF 72,000/year: ~6-8%

---

## Validation Checklist

Before submitting your converted data:

- [ ] Monthly wage = Yearly / 12 (or 13)
- [ ] 13th month percent = 0.0833 (or 0 if not applicable)
- [ ] Birth date format: YYYY-MM-DD
- [ ] Entry date format: YYYY-MM-DD
- [ ] Canton code is 2 uppercase letters (ZH, BE, etc.)
- [ ] Sex is "M" or "F"
- [ ] Civil status is valid value
- [ ] Insurance codes are set
- [ ] All dates have "start" field
- [ ] All decimal values use decimal point (not comma)

---

## Troubleshooting

### Issue: Net pay seems too low
- Check if 13th month is being calculated correctly
- Verify canton tax rates
- Confirm insurance codes are correct

### Issue: No 13th month salary calculated
- Ensure `Contractual13thPercent` is set to `0.0833`
- Check that monthly salary is feeding `ThirteenthMonthWageBase` collector

### Issue: Tax withholding not calculated
- Verify `EmployeeAddressCanton` is set
- Check if employee has QST tax code assigned
- Confirm canton has QST lookup tables configured

### Issue: Insurance deductions are zero
- Verify insurance codes are set correctly
- Check that `EmployeeAhvInsuranceSpecialCase` is `false`
- Ensure birth date is set (determines pension eligibility)
- Confirm sex is set (affects some insurance rates)

---

## Quick Conversion Calculator

### Python Script Example

```python
def yearly_to_monthly_input(
    yearly_salary: float,
    include_13th: bool = True,
    civil_status: str = "single",
    canton: str = "ZH",
    sex: str = "M",
    birth_date: str = "1985-06-15"
):
    """
    Convert yearly salary to payroll engine inputs
    """
    if include_13th:
        monthly_wage = round(yearly_salary / 12, 2)
        thirteenth_percent = 0.0833
    else:
        monthly_wage = round(yearly_salary / 12, 2)
        thirteenth_percent = 0.0
    
    return {
        "monthly_wage": monthly_wage,
        "thirteenth_percent": thirteenth_percent,
        "civil_status": civil_status,
        "canton": canton,
        "sex": sex,
        "birth_date": birth_date,
        "expected_gross": round(monthly_wage * (1 + thirteenth_percent), 2),
        "expected_net": round(monthly_wage * (1 + thirteenth_percent) * 0.82, 2)  # Approx
    }

# Example usage
result = yearly_to_monthly_input(72000, include_13th=True)
print(f"Monthly Wage: CHF {result['monthly_wage']}")
print(f"Expected Gross: CHF {result['expected_gross']}")
print(f"Expected Net: CHF {result['expected_net']}")
```

---

## Summary

**Minimum inputs from yearly salary:**

1. **One numeric calculation:**
   - Monthly Wage = Yearly Salary / 12

2. **Six basic fields:**
   - Sex
   - Birth Date
   - Civil Status
   - Canton
   - AHV Special Case flag (usually "false")
   - UVG Insurance Code (usually "A1")

3. **Result:**
   - Complete payroll calculation with all deductions and net pay

**Files to modify:**
- Your employee JSON file (like `EmployeeTF01.json`)

**Cases to update:**
- `GP.Swissdec.Employee` - Demographics
- `GP.Swissdec.EmployeeStatistics` - Salary
- `GP.Swissdec.EmployeeAddress` - Canton
- `GP.Swissdec.EmployeeAhvInsurance` - Pension
- `GP.Swissdec.EmployeeUvgInsurance` - Accident

