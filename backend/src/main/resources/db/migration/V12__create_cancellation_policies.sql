-- SPEC-0010 Cancellation: the administrable SOURCE of the cancellation/no-show policy, per
-- product/supplier scope (scope_ref is a value — no cross-module FK). A booking freezes a snapshot
-- from this source at confirmation (BR1, V13). Windows are stored as a compact text form
-- (hoursBefore:penaltyPct;...) decoded in the domain; no jsonb dependency is needed for a short list.
CREATE TABLE cancellation_policies (
    id                         uuid          PRIMARY KEY,
    scope_ref                  varchar(200)  NOT NULL,
    type                       varchar(20)   NOT NULL,   -- STANDARD | ALL_SALES_FINAL | CUSTOM
    windows_encoded            varchar(2000) NOT NULL,    -- "" when no windows
    refundable                 boolean       NOT NULL,
    cost_bearer                varchar(20)   NOT NULL,    -- AGENCY | ACME | SUPPLIER
    merchant_of_record         boolean       NOT NULL DEFAULT false,  -- BR8/DL-0021 (default affiliate)
    no_show_fee_amount         numeric(18,2),
    no_show_fee_currency       varchar(3),
    waived_if_flight_cancelled boolean       NOT NULL DEFAULT false,
    created_at                 timestamptz   NOT NULL,
    updated_at                 timestamptz   NOT NULL,
    created_by                 varchar(100),
    updated_by                 varchar(100),
    version                    bigint        NOT NULL
);

-- One administered policy per scope (the lookup the snapshot freeze uses).
CREATE UNIQUE INDEX ux_cancellation_policies_scope ON cancellation_policies (scope_ref);
