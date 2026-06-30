-- SPEC-0022 People (HR: colaboradores, jornada, banco de horas): the minimal HR context that
-- consumes the OPERATIONAL point snapshot (already owned by this module since SPEC-0012/V16) and
-- turns it into journey/time-bank and discrepancies. Folha pesada (eSocial/FGTS/13º) is OUT OF
-- SCOPE — buy/integrate (DL-0069/0070).
--
-- All cross-context references are BY VALUE (never an FK — Modulith): snapshot_ref (the operational
-- snapshot consumed, DL-0069), contract_document_id / payslip referenced documents (Compliance,
-- DL-0072) are uuid VALUES, not FKs.
--
--   employees              a collaborator (BR1): unique identifier, admission, contracted journey
--                          (minutes/day), ACTIVE|ON_LEAVE|TERMINATED, optional contract document
--                          (Compliance, by value — retention indeterminate).
--   journeys               the processed journey of a (employee, period): worked vs contracted
--                          minutes and the time-bank balance, idempotent by (employee_id, period)
--                          (DL-0069/0070). snapshot_ref is the operational snapshot consumed (value).
--   journey_discrepancies  an alert for an odd/missing punch or incoherent journal (BR4/DL-0071):
--                          OPEN|RESOLVED, never auto-corrected. Unique per (employee, period, kind)
--                          keeps re-processing idempotent (no duplicate alerts).

CREATE TABLE employees (
    id                   uuid          PRIMARY KEY,
    identifier           varchar(100)  NOT NULL,
    admission_date       date          NOT NULL,
    contracted_minutes   integer       NOT NULL,        -- contracted journey, minutes/day (e.g. 480 = 08:00)
    status               varchar(20)   NOT NULL,        -- ACTIVE | ON_LEAVE | TERMINATED
    contract_document_id uuid,                           -- Compliance document id (VALUE, not an FK)
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    created_by           varchar(100),
    updated_by           varchar(100),
    version              bigint        NOT NULL,
    CONSTRAINT uq_employees_identifier UNIQUE (identifier)   -- BR1 unique identifier (idempotency/409)
);

-- Supports the listing filter by status (GET /api/people/employees?status=).
CREATE INDEX ix_employees_status ON employees (status);

CREATE TABLE journeys (
    id                 uuid          PRIMARY KEY,
    employee_id        uuid          NOT NULL,           -- references employees.id within the SAME module
    period             char(7)       NOT NULL,           -- YYYY-MM
    snapshot_ref       uuid          NOT NULL,           -- operational snapshot consumed (VALUE, DL-0069)
    worked_minutes     integer       NOT NULL,
    contracted_minutes integer       NOT NULL,           -- frozen contracted minutes for the period
    balance_minutes    integer       NOT NULL,           -- worked - contracted; +extras / -faltas (DL-0070)
    processed_at       timestamptz   NOT NULL,
    CONSTRAINT uq_journeys_employee_period UNIQUE (employee_id, period)  -- idempotency (BR2/DL-0069)
);

CREATE TABLE journey_discrepancies (
    id          uuid          PRIMARY KEY,
    employee_id uuid          NOT NULL,
    period      char(7)       NOT NULL,                  -- YYYY-MM
    kind        varchar(30)   NOT NULL,                  -- ODD_PUNCH | MISSING_PUNCH | INCOHERENT_JOURNAL
    status      varchar(20)   NOT NULL,                  -- OPEN | RESOLVED
    detail      varchar(300),
    created_at  timestamptz   NOT NULL,
    resolved_at timestamptz,
    resolved_by varchar(100),
    CONSTRAINT uq_discrepancy_employee_period_kind UNIQUE (employee_id, period, kind)  -- no dup alerts (DL-0071)
);

-- Supports the discrepancy queue filters (GET /api/people/discrepancies?period=&status=).
CREATE INDEX ix_discrepancies_period_status ON journey_discrepancies (period, status);
