# Swiss Payroll Calculation Flow - Visual Diagram

## Overall Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      INPUT: CASE FIELDS                          │
│  (Employee data like salary, demographics, insurance codes)      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                  WAGE TYPE CALCULATIONS                          │
│  (Individual payment components: salary, bonuses, allowances)    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    COLLECTORS (Aggregation)                      │
│  • GrossSalary                                                   │
│  • AhvBase, AlvBase, UvgBase, etc. (Insurance bases)            │
│  • QstBase (Tax base)                                           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
┌───────────────────────────┐  ┌──────────────────────────────┐
│  DEDUCTION WAGE TYPES     │  │   GROSS WAGE DISPLAY         │
│  (Insurance contributions  │  │   (Wage Type 5000)          │
│   and taxes)              │  └──────────────────────────────┘
└───────────────┬───────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 EmployeeContributions Collector                  │
│  (All deductions aggregated as negative amount)                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      NET WAGE CALCULATION                        │
│  Net = GrossSalary - EmployeeContributions + Expenses           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PAYMENT (Wage Type 6600)                      │
│  Payment = NetWage - AdvancePayment - PreviousPayments         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Detailed Monthly Salary Flow

```
INPUT CASE FIELDS
═════════════════
┌────────────────────────────────────────────┐
│ GP.Swissdec.EmployeeStatistics             │
│ ├─ ContractualMonthlyWage: 5500.00        │  ◄── FROM YEARLY: 66,000 / 12
│ └─ Contractual13thPercent: 0.0833         │  ◄── 1/12 for 13th month
└──────────────────┬─────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Wage Type 1000      │
        │  Monthly Salary      │
        │  Value: 5500.00      │
        └──────────┬───────────┘
                   │
                   ├───────────────────────────────┐
                   │                               │
                   ▼                               ▼
    ┌──────────────────────┐        ┌─────────────────────────┐
    │  GrossSalary         │        │  ThirteenthMonthWageBase│
    │  Collector           │        │  Collector              │
    │  += 5500.00          │        │  += 5500.00             │
    └──────────┬───────────┘        └─────────────┬───────────┘
               │                                   │
               │                    ┌──────────────┘
               │                    │
               ▼                    ▼
    ┌──────────────────────┐  ┌─────────────────────┐
    │  AhvAlvAlvzBase      │  │  Wage Type 1200     │
    │  Collector Group     │  │  13th Month Salary  │
    │  += 5500.00          │  │  = Base × 0.0833    │
    └──────────┬───────────┘  └──────────┬──────────┘
               │                         │
               │                         └────────┐
               ▼                                  │
    ┌──────────────────────┐                     │
    │  UvgUvgzKtgBvgBase   │                     │
    │  Collector Group     │                     │
    │  += 5500.00          │                     │
    └──────────┬───────────┘                     │
               │                                  │
               ▼                                  │
    ┌──────────────────────┐                     │
    │  QstBase             │                     │
    │  Collector           │                     │
    │  += 5500.00          │                     │
    └──────────────────────┘                     │
                                                  │
                                                  ▼
                                    ┌─────────────────────────┐
                                    │  Back to GrossSalary    │
                                    │  += 13th month amount   │
                                    └─────────────────────────┘
```

---

## Deduction Calculation Flow

```
INSURANCE BASE COLLECTORS                 EMPLOYEE DATA
═════════════════════════                ═════════════

┌──────────────────┐                     ┌──────────────────────┐
│  AhvBase         │                     │ • Birth Date         │
│  = 5500.00       │─────┐               │ • AHV Special Case   │
└──────────────────┘     │               └──────────┬───────────┘
                         │                          │
                         ▼                          │
              ┌──────────────────────┐              │
              │  Wage Type 5010      │◄─────────────┘
              │  AHV/OASI            │
              │  = 5500 × 5.3%       │
              │  = -291.50           │
              └──────────┬───────────┘
                         │
                         │
┌──────────────────┐     │               ┌──────────────────────┐
│  UvgBase         │     │               │ • Sex                │
│  = 5500.00       │─────┼───┐           │ • UVG Insurance Code │
└──────────────────┘     │   │           └──────────┬───────────┘
                         │   │                      │
                         │   ▼                      │
                         │ ┌──────────────────────┐ │
                         │ │  Wage Type 5040      │ │
                         │ │  UVG/SUVA            │◄┘
                         │ │  = 5500 × 1.0%       │
                         │ │  = -55.00            │
                         │ └──────────┬───────────┘
                         │            │
                         │            │
┌──────────────────┐     │            │
│  QstBase         │     │            │           ┌──────────────────────┐
│  = 5500.00       │─────┼────────────┼───┐       │ • Canton             │
└──────────────────┘     │            │   │       │ • QST Tax Code       │
                         │            │   │       │ • Civil Status       │
                         │            │   │       └──────────┬───────────┘
                         │            │   │                  │
                         │            │   ▼                  │
                         │            │ ┌──────────────────────┐
                         │            │ │  Wage Type 5060      │◄┘
                         │            │ │  QST Tax             │
                         │            │ │  = Lookup(canton,    │
                         │            │ │         code, base)  │
                         │            │ │  = -220.00           │
                         │            │ └──────────┬───────────┘
                         │            │            │
                         ▼            ▼            ▼
              ┌────────────────────────────────────────┐
              │  EmployeeContributions Collector       │
              │  = -291.50 (AHV)                      │
              │    -55.00  (UVG)                      │
              │    -220.00 (QST)                      │
              │    ...                                │
              │  = -566.50 TOTAL                      │
              └────────────────────────────────────────┘
```

---

## Net Pay Calculation

```
                    COLLECTORS
                  ═══════════════

┌────────────────────────────────────────────┐
│  GrossSalary                               │
│  = 5500.00 (Monthly Salary)               │
│  + 458.33  (13th Month: 5500×0.0833)      │
│  + 682.00  (Employer PF/LOB)              │
│  + 0.00    (Other wage types)             │
│  ────────────────────────────────────────  │
│  = 6640.33 TOTAL                          │
└──────────────────┬─────────────────────────┘
                   │
                   │
┌──────────────────┴─────────────────────────┐
│  EmployeeContributions (NEGATIVE)          │
│  = -351.74 (AHV/IV/EO: 6640 × 5.3%)      │
│  + -73.04  (ALV: 6640 × 1.1%)            │
│  + -66.40  (UVG: 6640 × 1.0%)            │
│  + -199.21 (BVG/PF)                      │
│  + -265.61 (QST: varies by canton)       │
│  ────────────────────────────────────────  │
│  = -956.00 TOTAL                          │
└──────────────────┬─────────────────────────┘
                   │
                   │
┌──────────────────┴─────────────────────────┐
│  Expenses (POSITIVE)                       │
│  = 0.00 (in this example)                 │
└──────────────────┬─────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Wage Type 6500      │
        │  Net Wage            │
        │                      │
        │  = 6640.33           │
        │  - 956.00            │
        │  + 0.00              │
        │  ────────────────    │
        │  = 5684.33           │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Wage Type 6600      │
        │  Payment             │
        │                      │
        │  = 5684.33           │
        │  - 0.00 (Advance)    │
        │  - 0.00 (Previous)   │
        │  ────────────────    │
        │  = 5684.33 TO PAY    │
        └──────────────────────┘
```

---

## Minimal Input to Output Map

```
┌──────────────────────────────────────────────────────────────────────┐
│                        MINIMAL INPUTS                                 │
└──────────────────────────────────────────────────────────────────────┘

NUMERIC (Pay-Affecting)                    NON-NUMERIC (Deduction-Affecting)
═══════════════════════                    ═════════════════════════════════

ContractualMonthlyWage: 5500.00  ────┐     EmployeeSex: "M"  ────────┐
                                     │                                │
Contractual13thPercent: 0.0833  ─────┤     EmployeeBirthDate:       │
                                     │     "1985-06-15"  ────────────┤
                                     │                                │
                                     │     EmployeeCivilStatus:       │
                                     │     "married"  ────────────────┤
                                     │                                │
                                     │     EmployeeAddressCanton:     │
                                     │     "ZH"  ──────────────────────┤
                                     │                                │
                                     │     AhvInsuranceSpecialCase:   │
                                     │     false  ──────────────────────┤
                                     │                                │
                                     │     UvgInsuranceCode: "A1"  ───┤
                                     │                                │
                                     ▼                                ▼
                    ┌────────────────────────────────────────────────────┐
                    │         PAYROLL ENGINE CALCULATION                 │
                    │                                                    │
                    │  • Wage Type Calculations                          │
                    │  • Collector Aggregation                           │
                    │  • Rate Lookups (based on codes/canton)           │
                    │  • Deduction Calculations                          │
                    │  • Net Pay Calculation                             │
                    └────────────────────┬───────────────────────────────┘
                                         │
                                         ▼
                    ┌────────────────────────────────────────────────────┐
                    │                  OUTPUTS                           │
                    ├────────────────────────────────────────────────────┤
                    │  Gross Salary:           6640.33                   │
                    │  AHV/IV/EO:              -351.74                   │
                    │  ALV:                     -73.04                   │
                    │  UVG:                     -66.40                   │
                    │  BVG:                    -199.21                   │
                    │  QST:                    -265.61                   │
                    │  ──────────────────────────────                    │
                    │  Net Wage:              5684.33                    │
                    │  Payment:               5684.33                    │
                    └────────────────────────────────────────────────────┘
```

---

## Key Takeaways

### 1. Two-Level Aggregation
```
Case Fields → Wage Types → Collectors → Final Calculation
```

### 2. Gross vs Net
- **Gross Salary Collector** = Sum of all income wage types
- **Employee Contributions** = Sum of all deduction wage types (negative)
- **Net Wage** = Gross - Contributions + Expenses

### 3. Insurance Contribution Pattern
```
For each insurance type:
  Base Amount (from collector) × Rate (from lookup) = Contribution
  
  Rate depends on:
  - Insurance code (from case field)
  - Gender (from case field)
  - Age/Birth date (from case field)
  - Canton (for some types)
```

### 4. Yearly to Monthly Conversion
```
Simple Division:
  Monthly Wage = Yearly Salary / 12
  
With 13th Month:
  Monthly Wage = Yearly Salary / 13
  13th Percent = 1/12 = 0.0833
```

### 5. Minimum Required Inputs

**For basic monthly salary calculation:**
- ✅ 1 numeric field: `ContractualMonthlyWage`
- ✅ 6 demographic/insurance fields:
  - Sex
  - Birth Date
  - Civil Status
  - Canton
  - AHV Special Case flag
  - UVG Insurance Code

**Result:** Complete payroll calculation with:
- ✓ Gross pay
- ✓ All insurance deductions
- ✓ Tax withholding
- ✓ Net pay
- ✓ Payment amount

