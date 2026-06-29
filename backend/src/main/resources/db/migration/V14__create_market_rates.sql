-- SPEC-0011 Exchange exposure (slice 1): the market rate as an append-only time series per currency
-- pair (BR1). "Market now" is the most recent observation with observed_at <= now. Source is FEED
-- (external provider via the MarketRateProvider port, future) or MANUAL (contingency registration).
-- Rates use scale 6 like the pinned sell rate; no cross-module FK (the pair is a value).

CREATE TABLE market_rates (
    id            uuid          PRIMARY KEY,
    currency_pair varchar(7)    NOT NULL,          -- canonical BASE/QUOTE, e.g. USD/BRL
    rate          numeric(18,6) NOT NULL,          -- > 0, enforced in the domain
    observed_at   timestamptz   NOT NULL,          -- when the market showed this rate
    source        varchar(10)   NOT NULL,          -- FEED | MANUAL
    created_at    timestamptz   NOT NULL,
    created_by    varchar(100)
);

-- "Market now" query: most recent observation for a pair not in the future.
CREATE INDEX ix_market_rates_pair_observed ON market_rates (currency_pair, observed_at DESC);
