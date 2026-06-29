-- SPEC-0005 Quoting: composed MANUAL quotes with frozen provenance, plus the override history.
-- account_id and rate_id are stored as values (no cross-module FK) to preserve future extraction;
-- override_records -> quotes is an in-aggregate FK (same module), which is allowed.
CREATE TABLE quotes (
    id                    uuid PRIMARY KEY,
    account_id            uuid          NOT NULL,
    price_origin          varchar(20)   NOT NULL,
    base_price_amount     numeric(18, 2) NOT NULL,
    base_price_currency   varchar(3)    NOT NULL,
    currency_pair         varchar(10)   NOT NULL,
    fx_rate               numeric(18, 6) NOT NULL,
    rate_id               uuid          NOT NULL,
    base_converted_amount numeric(18, 2) NOT NULL,
    supplier_pct          numeric(7, 6) NOT NULL,
    agent_pct             numeric(7, 6) NOT NULL,
    supplier_commission   numeric(18, 2) NOT NULL,
    agent_commission      numeric(18, 2) NOT NULL,
    spread                numeric(18, 2) NOT NULL,
    spread_negative       boolean       NOT NULL,
    markup_pct            numeric(7, 6) NOT NULL,
    markup_amount         numeric(18, 2) NOT NULL,
    markup_source         varchar(50)   NOT NULL,
    suggested_amount      numeric(18, 2) NOT NULL,
    applied_amount        numeric(18, 2) NOT NULL,
    status                varchar(20)   NOT NULL,
    valid_until           timestamptz,
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,
    created_by            varchar(100),
    updated_by            varchar(100),
    version               bigint        NOT NULL
);

CREATE TABLE override_records (
    id           uuid PRIMARY KEY,
    quote_id     uuid          NOT NULL REFERENCES quotes (id),
    from_amount  numeric(18, 2) NOT NULL,
    to_amount    numeric(18, 2) NOT NULL,
    reason       varchar(500)  NOT NULL,
    performed_by varchar(100)  NOT NULL,
    performed_at timestamptz   NOT NULL
);

CREATE INDEX ix_overrides_quote ON override_records (quote_id);
