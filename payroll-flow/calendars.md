# Calendars in the .NET Payroll Engine — Detailed Summary

This document summarizes how **calendars** work in the .NET payroll engine. Source of truth: **payroll-engine-backend**, **payroll-engine-core**, and **payroll-engine-client-core** (and related libraries referenced below).

---

## 1. Purpose of Calendars

Calendars define **payroll period and cycle boundaries**, **working days**, and **date rules** (first day of week, first month of year, week-of-year rules). They are **tenant-scoped**, named objects. The engine uses the active calendar (per payrun or per employee) to:

- Resolve **evaluation period** and **evaluation cycle** for a payrun job (via the payroll calculator).
- Compute **payroll periods** (e.g. calendar month, quarter, year) for wage types, case values, and reporting.
- Determine **working days** when `CalendarWeekMode.WorkWeek` is used.
- Support **culture-specific** date handling (e.g. first day of week, month boundaries) together with `CultureInfo`.

---

## 2. Domain Model: Calendar

**Namespace:** `PayrollEngine.Domain.Model`  
**File:** `payroll-engine-backend/Domain/Domain.Model/Calendar.cs`

The **Calendar** entity extends `DomainObjectBase` and implements `INamedObject`, `IDomainAttributeObject`. Main properties:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| **Name** | string | — | Calendar name (unique per tenant). |
| **NameLocalizations** | Dictionary&lt;string, string&gt; | — | Localized names. |
| **CycleTimeUnit** | CalendarTimeUnit | Year | Time unit for the payroll cycle (e.g. year, quarter). |
| **PeriodTimeUnit** | CalendarTimeUnit | CalendarMonth | Time unit for the payroll period (e.g. calendar month, week). |
| **TimeMap** | CalendarTimeMap | Period | Whether periods are mapped by **Period** or **Cycle**. |
| **FirstMonthOfYear** | Month? | January | First month of the calendar year (for non‑calendar-year cycles). |
| **PeriodDayCount** | decimal? | — | Override effective period length in days. |
| **YearWeekRule** | CalendarWeekRule? | — | Rule for first week of year (first day, first full week, first four-day week). |
| **FirstDayOfWeek** | DayOfWeek? | — | Override first day of week. |
| **WeekMode** | CalendarWeekMode | Week | **Week** = all 7 days; **WorkWeek** = only working days per flags below. |
| **WorkMonday** … **WorkSunday** | bool | Mon–Fri true, Sat–Sun false | Which days count as working days when WeekMode = WorkWeek. |
| **Attributes** | Dictionary&lt;string, object&gt; | — | Custom attributes. |

If no calendar name is set (e.g. payrun level), the engine uses a **default calendar** instance (empty name, default property values).

---

## 3. Core Enums and Types (payroll-engine-core)

Defined in **payroll-engine-core** and used by the domain and API.

### 3.1 CalendarTimeUnit

**File:** `payroll-engine-core/Core/CalendarTimeUnit.cs`

Enum used as **year divisor** (see Oracle-style payroll period documentation). Values:

- **Year** (1), **SemiYear** (2), **Quarter** (4), **BiMonth** (6), **CalendarMonth** (12), **LunisolarMonth** (13), **SemiMonth** (24), **BiWeek** (26), **Week** (52).

### 3.2 CalendarTimeMap

**File:** `payroll-engine-core/Core/CalendarTimeMap.cs`

- **Period** (0): Map by period.
- **Cycle** (1): Map by cycle.

### 3.3 CalendarWeekRule

**File:** `payroll-engine-core/Core/CalendarWeekRule.cs`

Rules for the first week of the year (aligns with `System.Globalization.CalendarWeekRule`):

- **FirstDay**: First week starts on the first day of the year.
- **FirstFullWeek**: First week starts on the first occurrence of the designated first day of week on or after the first day of the year.
- **FirstFourDayWeek**: First week is the first week with four or more days before the designated first day of week.

### 3.4 CalendarWeekMode

**File:** `payroll-engine-core/Core/CalendarWeekMode.cs`

- **Week** (0): Use all days of the week.
- **WorkWeek** (1): Use only working days from the calendar (WorkMonday … WorkSunday).

### 3.5 CalendarTimeUnitExtensions

**File:** `payroll-engine-core/Core/CalendarTimeUnitExtensions.cs`

- **PeriodCount(periodUnit)**: Number of period units in one cycle unit (e.g. 12 for Year vs CalendarMonth).
- **IsValidTimeUnit(periodUnit)**: Ensures period unit is “larger” than cycle unit and divides evenly (valid cycle/period combination).

---

## 4. Where the Calendar Name Comes From (Resolution Order)

The engine works with a **calendar name** (string). The actual **Calendar** object is loaded from the repository by name when building the payroll calculator.

### 4.1 At payrun (job) level

**File:** `payroll-engine-backend/Domain/Domain.Application/PayrunProcessor.cs` (around lines 101–103)

- `context.CalendarName = context.Division.Calendar ?? Tenant.Calendar`
- So: **Division.Calendar** wins; if missing, **Tenant.Calendar**. Stored in `PayrunContext.CalendarName` and used when creating the payrun-level calculator (e.g. for job period/cycle and first calculator in context).

### 4.2 At employee level (per-employee processing)

**File:** `payroll-engine-backend/Domain/Domain.Application/PayrunProcessor.cs` (around lines 697–705)

- `calendarName = employee.Calendar ?? context.Division.Calendar ?? Tenant.Calendar`
- So: **Employee.Calendar** > **Division.Calendar** > **Tenant.Calendar**. This is used to create the **employee-specific** calculator (and case value provider), so period/cycle and working-day logic can vary per employee.

### 4.3 Summary of “Calendar” reference on entities

- **Tenant**: Optional `Calendar` (string) — default calendar name for the tenant.
- **Division**: Optional `Calendar` (string) — overrides tenant default for that division.
- **Employee**: Optional `Calendar` (string) — overrides division/tenant for that employee.
- **WageType** (client/core / derived): Optional `Calendar` (string) — wage-type-specific calendar; fallback is employee calendar when resolving derived wage types.

---

## 5. How the Calendar Is Used in the Payrun

### 5.1 Loading the Calendar and creating the calculator

- **GetCalculatorAsync** (PayrunProcessor): Takes `calendarName`. If name is null/empty, uses the **default** calendar instance; otherwise loads **Calendar** via `Settings.CalendarRepository.GetByNameAsync(Settings.DbContext, tenantId, calendarName)`. Throws if name is set but calendar not found. Calculators are cached by calendar name for the duration of the process.
- **PayrollCalculatorProvider.CreateCalculator** is called with the resolved **Calendar** object (plus tenantId, userId, culture). The calculator then uses that calendar (and culture) for **GetPayrunPeriod** and **GetPayrunCycle**.

### 5.2 Payrun job creation

**File:** `payroll-engine-backend/Domain/Domain.Model/PayrunJobFactory.cs`

- The payrun job is created with a **payroll calculator** that was built using the payrun-level calendar (division or tenant).
- `evaluationCycle = payrollCalculator.GetPayrunCycle(jobInvocation.PeriodStart)`
- `evaluationPeriod = payrollCalculator.GetPayrunPeriod(jobInvocation.PeriodStart)`
- So **CycleName/CycleStart/CycleEnd** and **PeriodName/PeriodStart/PeriodEnd** on the payrun job come from the calendar (and time units) configured on that calculator.

### 5.3 Per-employee calculator

- When processing each employee, the engine may set a new **CalendarName** from employee > division > tenant, then calls **GetCalculatorAsync** again with that name. The resulting **IPayrollCalculator** is pushed onto the context (and case value provider), so wage type evaluation and case period values use that employee’s calendar.

---

## 6. Repository and Service

### 6.1 ICalendarRepository / CalendarRepository

- **Interface:** `PayrollEngine.Domain.Model.Repository.ICalendarRepository` extends `IChildDomainRepository<Calendar>`.
- **Method:** `Task<Calendar> GetByNameAsync(IDbContext context, int tenantId, string name)` — fetch calendar by tenant and name.
- **Implementation:** `PayrollEngine.Persistence.CalendarRepository` (table `DbSchema.Tables.Calendar`, parent key `CalendarColumn.TenantId`). Persists all Calendar properties (Name, NameLocalizations, CycleTimeUnit, PeriodTimeUnit, TimeMap, FirstMonthOfYear, PeriodDayCount, YearWeekRule, FirstDayOfWeek, WeekMode, Work* flags, Attributes).

### 6.2 CalendarService / ICalendarService

- **CalendarService** (Domain.Application): Wraps **ICalendarRepository**; exposes **GetByNameAsync** for the API and other callers.
- Used by **CalendarController** (API) and by **PayrunProcessor** via **PayrunJobServiceSettings.CalendarRepository** (processor uses repository directly in **GetCalculatorAsync**).

---

## 7. Payroll Periods and Calendar

Period types that take a **Calendar** (and optionally culture) to compute date ranges:

- **CalendarMonthPayrollPeriod** (Domain.Model): Uses **Calendar** and **CultureInfo**; period is the calendar month containing the given moment; uses `Culture.Calendar.GetOffsetDate` for month start/end.
- **BiMonthPayrollPeriod**, **YearPayrollPeriod**, **SemiYearPayrollPeriod**, **QuarterPayrollPeriod**, etc.: Use **Calendar** for **FirstMonthOfYear** and time-unit logic to derive period boundaries.

**CalendarExtensions** (Domain.Model) and **SystemCalendarExtensions** (Domain.Model) provide:

- **GetWeekOfYear(culture, moment)** (Calendar extension): Week-of-year using FirstDayOfWeek (or culture) and week rule.
- **IsWorkDay(moment)** / **IsWorkDay(dayOfWeek)**: Whether a day is a working day (WeekMode or WorkMonday…WorkSunday).
- **GetWeekDays()**, **GetPreviousWorkDays**, **GetNextWorkDays**: Working-day lists.
- **ValidTimeUnits()**: Whether the calendar’s cycle/period combination is valid (via **CalendarTimeUnitExtensions**).
- **GetOffsetDate** (System.Globalization.Calendar): Date offset by months in a given calendar (e.g. Gregorian).

---

## 8. API

### 8.1 Calendar CRUD

- **Base URL:** `GET/POST/PUT/DELETE .../api/tenants/{tenantId}/calendars` (and `.../calendars/{id}`). Implemented by **Api.Controller.CalendarController** and **Backend.Controller.CalendarController**; uses **ICalendarService** and **CalendarMap** (API model ↔ domain).

### 8.2 Calendar period and cycle endpoints

**Api.Controller.CalendarController**:

- **GetCalendarPeriodAsync(tenantId, cultureName, calendarName, periodMoment, offset):** Resolves tenant; resolves calendar by name (fallback to tenant.Calendar, then default calendar instance); builds culture; creates calculator via **PayrollCalculatorProvider.CreateCalculator(calendar, tenantId, culture)**; returns **DatePeriod** for `calculator.GetPayrunPeriod(periodMoment)` with optional offset.
- **GetCalendarCycleAsync(...):** Same resolution; returns **DatePeriod** for `calculator.GetPayrunCycle(cycleMoment)` with optional offset.
- **CalculateCalendarValueAsync(tenantId, value, cultureName, calendarName, evaluationDate, evaluationPeriodDate):** Builds calendar + culture + calculator; builds **CaseValueCalculation** with evaluation period from the calendar; returns **calculator.CalculateCasePeriodValue(calculation)**.

---

## 9. Exchange / Client (payroll-engine-client-core)

- **TenantApiEndpoints:** `CalendarsUrl(tenantId)`, `CalendarUrl(tenantId, calendarId)` for REST calls.
- **Exchange import:** **SetupCalendarAsync** in **ExchangeImport** upserts calendars via `UpsertObjectAsync(..., CalendarsUrl(tenant.Id), ...)` so regulation/tenant payloads can define calendars.
- **WageType** (client model): Optional **Calendar** (string); documented as “wage type calendar (fallback: employee calendar).”

---

## 10. Database

- **Table:** `Calendar` (DbSchema.Tables.Calendar).
- **Parent:** Tenant (CalendarColumn.TenantId).
- **Columns** (from CalendarRepository and CalendarColumn): TenantId, Name, NameLocalizations, CycleTimeUnit, PeriodTimeUnit, TimeMap, FirstMonthOfYear, PeriodDayCount, YearWeekRule, FirstDayOfWeek, WeekMode, WorkMonday through WorkSunday, Attributes, plus base fields (Id, etc.).

---

## 11. Summary Diagram (Flow)

```
Tenant.Calendar / Division.Calendar / Employee.Calendar (name)
        ↓
PayrunProcessor: context.CalendarName = Division.Calendar ?? Tenant.Calendar
        ↓ (per employee)
context.CalendarName = Employee.Calendar ?? Division.Calendar ?? Tenant.Calendar
        ↓
GetCalculatorAsync(tenantId, userId, culture, calendarName)
        ↓
CalendarRepository.GetByNameAsync(context, tenantId, calendarName)  →  Calendar
        ↓ (if null and name set → exception; if name null → default Calendar)
PayrollCalculatorProvider.CreateCalculator(tenantId, userId, culture, calendar)
        ↓
IPayrollCalculator (GetPayrunPeriod, GetPayrunCycle, CalculateCasePeriodValue, …)
        ↓
PayrunJobFactory: evaluationCycle = calculator.GetPayrunCycle(...), evaluationPeriod = calculator.GetPayrunPeriod(...)
        ↓
PayrunJob.CycleName/Start/End, PeriodName/Start/End; per-employee case/period logic
```

---

## 12. References (source locations)

| Topic | Repository / path |
|-------|-------------------|
| Calendar domain model | payroll-engine-backend: Domain/Domain.Model/Calendar.cs |
| ICalendarRepository, CalendarRepository | Domain/Domain.Model/Repository/ICalendarRepository.cs, Persistence/Persistence/CalendarRepository.cs |
| CalendarService | Domain/Domain.Application/CalendarService.cs |
| CalendarExtensions, SystemCalendarExtensions | Domain/Domain.Model/CalendarExtensions.cs, SystemCalendarExtensions.cs |
| CalendarMonthPayrollPeriod, other period types | Domain/Domain.Model/*PayrollPeriod.cs |
| CalendarTimeUnit, CalendarTimeMap, CalendarWeekRule, CalendarWeekMode, CalendarTimeUnitExtensions | payroll-engine-core: Core/*.cs |
| PayrunProcessor calendar resolution and GetCalculatorAsync | Domain/Domain.Application/PayrunProcessor.cs |
| PayrunJobFactory | Domain/Domain.Model/PayrunJobFactory.cs |
| CalendarController (API) | Api/Api.Controller/CalendarController.cs |
| PayrunJobServiceSettings (CalendarRepository) | Domain/Domain.Application/Service/PayrunJobServiceSettings.cs |
| Exchange / client calendars | payroll-engine-client-core: Client.Core/Exchange/ExchangeImport.cs, TenantApiEndpoints.cs, Model/WageType.cs, IWageType.cs |
| DbSchema | Persistence/Persistence/DbSchema/CalendarColumn.cs, Tables.cs |
