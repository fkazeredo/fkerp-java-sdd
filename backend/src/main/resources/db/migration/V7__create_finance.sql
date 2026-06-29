-- SPEC-0015 Finance: minimal AP/AR ledger + monthly accounting period.
-- The ledger entry references its counterparty and (later) its document by value (no cross-module FK),
-- so Finance and the other contexts stay independently extractable. Amounts keep their original
-- currency (DL-0013); the period is the YYYY-MM monthly-close unit.
CREATE TABLE accounting_periods (
    period    varchar(7)  PRIMARY KEY,           -- YYYY-MM
    status    varchar(20) NOT NULL,
    closed_at timestamptz,
    closed_by varchar(100),
    version   bigint      NOT NULL
);

CREATE TABLE ledger_entries (
    id           uuid          PRIMARY KEY,
    direction    varchar(20)   NOT NULL,
    party_id     varchar(100)  NOT NULL,
    party_type   varchar(20)   NOT NULL,
    amount       numeric(18,2) NOT NULL,
    currency     varchar(3)    NOT NULL,
    entry_type   varchar(40)   NOT NULL,
    period       varchar(7)    NOT NULL,
    status       varchar(20)   NOT NULL,
    document_ref uuid,                            -- value reference to a Compliance document
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    version      bigint        NOT NULL
);

-- The close-check and the AP/AR period totals read by (period, status).
CREATE INDEX ix_ledger_period_status ON ledger_entries (period, status);
