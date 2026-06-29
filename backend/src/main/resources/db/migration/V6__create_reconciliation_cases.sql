-- SPEC-0007 Reconciliation: one case per confirmed sale, crossing expected vs realized.
-- booking_id is UNIQUE so opening a case is idempotent (BR1). Realized columns are nullable until
-- recorded; the derived columns (realized_spread, fx_gain_loss, discrepancy) are computed, never typed.
CREATE TABLE reconciliation_cases (
    id                                    uuid PRIMARY KEY,
    booking_id                            uuid          NOT NULL UNIQUE,
    base_amount                           numeric(18, 2) NOT NULL,
    base_currency                         varchar(3)    NOT NULL,
    sale_currency                         varchar(3)    NOT NULL,
    pinned_rate                           numeric(18, 6) NOT NULL,
    base_brl                              numeric(18, 2) NOT NULL,
    expected_supplier_commission_brl      numeric(18, 2) NOT NULL,
    expected_agent_commission_brl         numeric(18, 2) NOT NULL,
    expected_spread_brl                   numeric(18, 2) NOT NULL,
    amount_received_from_agency_brl       numeric(18, 2),
    supplier_settlement_rate              numeric(18, 6),
    supplier_paid_brl                     numeric(18, 2),
    commission_received_from_supplier_brl numeric(18, 2),
    commission_paid_to_agent_brl          numeric(18, 2),
    realized_spread_brl                   numeric(18, 2),
    fx_gain_loss_brl                      numeric(18, 2),
    discrepancy_brl                       numeric(18, 2) NOT NULL,
    status                                varchar(20)   NOT NULL,
    created_at                            timestamptz   NOT NULL,
    updated_at                            timestamptz   NOT NULL,
    created_by                            varchar(100),
    updated_by                            varchar(100),
    version                               bigint        NOT NULL
);

-- Prioritization read-model: cases are listed by discrepancy magnitude.
CREATE INDEX ix_reconciliation_discrepancy ON reconciliation_cases (discrepancy_brl DESC);
