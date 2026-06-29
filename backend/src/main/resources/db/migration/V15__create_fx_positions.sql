-- SPEC-0011 Exchange exposure (slice 10b): the FX position opened when a confirmed sale carries a
-- foreign-currency cost priced at the frozen rate (BR2). It freezes the provenance (pinned_rate,
-- market_at_freeze) and the accrued subsidy at opening (BR3); the realized drift and total gap are
-- filled on settlement (BR5). No cross-module FK: booking_id is a value, unique for idempotent open.
-- Money columns are BRL scale 2; rate columns scale 6 (same conventions as reconciliation_cases).

CREATE TABLE fx_positions (
    id                 uuid          PRIMARY KEY,
    booking_id         uuid          NOT NULL UNIQUE,    -- idempotent open per booking (BR2)
    foreign_amount     numeric(18,2) NOT NULL,           -- supplier cost in the foreign currency
    currency           varchar(3)    NOT NULL,           -- the foreign currency code
    pinned_rate        numeric(18,6) NOT NULL,           -- frozen sell rate (provenance)
    market_at_freeze   numeric(18,6) NOT NULL,           -- market rate at the freeze instant (provenance)
    subsidy_brl        numeric(18,2) NOT NULL,           -- accrual at opening (BR3)
    settlement_rate    numeric(18,6),                    -- null until settled (BR5)
    realized_drift_brl numeric(18,2),                    -- null until settled (BR5)
    total_gap_brl      numeric(18,2),                    -- null until settled (BR5)
    status             varchar(10)   NOT NULL,           -- OPEN | CLOSED
    opened_at          timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL
);

-- The open book is summed for LiveExposure; positions are also grouped by opening period (PromoFxResult).
CREATE INDEX ix_fx_positions_status ON fx_positions (status);
CREATE INDEX ix_fx_positions_opened_at ON fx_positions (opened_at);
