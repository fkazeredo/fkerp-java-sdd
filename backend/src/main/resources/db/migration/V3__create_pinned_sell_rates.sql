-- SPEC-0003 Exchange: append-only history of pinned sell rates (the "frozen rate").
-- Rows are never updated or deleted (BR2). CHECK (rate > 0) reinforces BR4 at the database.
CREATE TABLE pinned_sell_rates (
    id             uuid PRIMARY KEY,
    currency_pair  varchar(10)   NOT NULL,
    rate           numeric(18, 6) NOT NULL CHECK (rate > 0),
    effective_from timestamptz   NOT NULL,
    set_by         varchar(100)  NOT NULL,
    note           varchar(500),
    created_at     timestamptz   NOT NULL
);

-- Serves the prevailing rate (greatest effective_from <= now) and the history, both per pair.
CREATE INDEX ix_rates_pair_effective ON pinned_sell_rates (currency_pair, effective_from DESC);
