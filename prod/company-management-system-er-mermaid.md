erDiagram

    COMPANY {
        UUID company_id PK
        VARCHAR company_name
        VARCHAR legal_entity_name
        VARCHAR tax_id
        VARCHAR registration_number
        VARCHAR address_line_1
        VARCHAR address_line_2
        VARCHAR city
        VARCHAR state_province
        VARCHAR postal_code
        CHAR country_code
        VARCHAR kyb_status
        VARCHAR status
        DATE payroll_start_date
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    PAY_GROUP {
        UUID pay_group_id PK
        UUID company_id FK
        VARCHAR pay_group_name
        CHAR currency_code
        VARCHAR payroll_status
        DATE effective_from
        DATE effective_to
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    PAY_SCHEDULE {
        UUID pay_schedule_id PK
        UUID pay_group_id FK
        VARCHAR payroll_schedule_name
        VARCHAR payroll_calendar
        VARCHAR pay_period
        SMALLINT first_month_of_year
        DECIMAL pay_period_day_count
        VARCHAR year_week_rule
        SMALLINT first_day_of_week
        VARCHAR week_mode
        BOOLEAN work_monday
        BOOLEAN work_tuesday
        BOOLEAN work_wednesday
        BOOLEAN work_thursday
        BOOLEAN work_friday
        BOOLEAN work_saturday
        BOOLEAN work_sunday
        VARCHAR payroll_schedule_status
        DATE effective_from
        DATE effective_to
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    COMPANY_BANK_ACCOUNT {
        UUID company_bank_account_id PK
        UUID company_id FK
        VARCHAR account_name
        VARCHAR bank_name
        VARCHAR account_number
        VARCHAR routing_number
        VARCHAR swift_code
        VARCHAR iban
        CHAR currency
        VARCHAR account_type
        BOOLEAN is_primary
        VARCHAR bank_account_status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    BENEFIT {
        UUID benefit_id PK
        UUID company_id FK
        VARCHAR benefit_name
        VARCHAR benefit_type
        VARCHAR provider_name
        DATE effective_from
        DATE effective_to
        VARCHAR employer_contribution_type
        DECIMAL employer_contribution
        VARCHAR benefit_frequency
        VARCHAR benefit_status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    LEAVE_POLICY {
        UUID leave_policy_id PK
        UUID company_id FK
        VARCHAR policy_name
        VARCHAR accrual_frequency
        BOOLEAN carry_over_allowed
        DECIMAL max_carry_over_days
        VARCHAR leave_type
        DECIMAL annual_entitlement_days
        DECIMAL probation_entitlement_days
        SMALLINT min_service_months
        VARCHAR status
        DATE effective_from
        DATE effective_to
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    HOLIDAY_CALENDAR {
        UUID holiday_calendar_id PK
        UUID company_id FK
        VARCHAR holiday_calendar_name
        SMALLINT year
        VARCHAR status
        DATE effective_from
        DATE effective_to
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    HOLIDAY_CALENDAR_ENTRY {
        UUID holiday_entry_id PK
        UUID holiday_calendar_id FK
        DATE holiday_date
        VARCHAR holiday_name
        BOOLEAN is_half_day
        BOOLEAN is_optional
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    GENERAL_COMPANY_POLICY {
        UUID general_company_policy_id PK
        UUID company_id FK
        VARCHAR policy_name
        VARCHAR policy_type
        TEXT description
        DATE effective_from
        DATE effective_to
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    OVERTIME_POLICY {
        UUID overtime_policy_id PK
        UUID company_id FK
        VARCHAR policy_name
        VARCHAR policy_code
        CHAR country_code
        DECIMAL standard_hours_per_week
        DATE effective_from
        DATE effective_to
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    OVERTIME_POLICY_RULE {
        UUID overtime_rule_id PK
        UUID overtime_policy_id FK
        VARCHAR apply_to
        SMALLINT tier_order
        DECIMAL max_hours_in_tier
        DECIMAL multiplier
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    %% Relationship verbs: "has" = ownership/aggregation; "contains" = header-detail / list-item
    %% ── COMPANY → children ──
    COMPANY ||--o{ PAY_GROUP : "has"
    COMPANY ||--o{ COMPANY_BANK_ACCOUNT : "has"
    COMPANY ||--o{ BENEFIT : "has"
    COMPANY ||--o{ LEAVE_POLICY : "has"
    COMPANY ||--o{ HOLIDAY_CALENDAR : "has"
    COMPANY ||--o{ GENERAL_COMPANY_POLICY : "has"
    COMPANY ||--o{ OVERTIME_POLICY : "has"

    %% ── Pay hierarchy & header-detail ──
    PAY_GROUP ||--o{ PAY_SCHEDULE : "has"
    OVERTIME_POLICY ||--o{ OVERTIME_POLICY_RULE : "contains"
    HOLIDAY_CALENDAR ||--o{ HOLIDAY_CALENDAR_ENTRY : "contains"

