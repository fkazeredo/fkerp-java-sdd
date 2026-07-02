-- SPEC-0032 (Fase 19h, DL-0130): FX forward contracts — the treasury practice of the travel trade
-- (lock a future exchange rate to hedge the open book). Manual registration (no bank integration);
-- OPEN forwards reduce the UNHEDGED exposure the drift alert watches (DL-0027 revised: the alert
-- now measures only the uncovered book). Status is a state machine (OPEN -> SETTLED | CANCELLED).
CREATE TABLE fx_forward_contracts (
    id             uuid           PRIMARY KEY,
    currency       varchar(3)     NOT NULL,
    notional       numeric(18, 2) NOT NULL,
    contract_rate  numeric(18, 6) NOT NULL,
    trade_date     date           NOT NULL,
    maturity_date  date           NOT NULL,
    counterparty   varchar(100)   NOT NULL,
    status         varchar(20)    NOT NULL,
    settled_rate   numeric(18, 6),
    settled_at     timestamptz,
    cancelled_at   timestamptz,
    created_at     timestamptz    NOT NULL,
    updated_at     timestamptz    NOT NULL,
    created_by     varchar(100),
    resolved_by    varchar(100),
    version        bigint         NOT NULL
);

CREATE INDEX ix_fx_forward_status_currency ON fx_forward_contracts (status, currency);
