-- SPEC-0017 Payout: the financial outflows of the operation — agent commission repass, supplier
-- settlement (foreign currency, at the real settlement rate, with the BRL baixa) and customer refund,
-- with installments and a receipt. The payout references other contexts BY VALUE (booking_id,
-- origin_ref, payee_id) — no cross-module FK — so Payout stays independently extractable (Modulith).
-- Money is numeric(18,2); the settlement rate is numeric(18,6) (scale 6, > 0 when foreign — BR1).
CREATE TABLE payouts (
    id              uuid          PRIMARY KEY,
    kind            varchar(30)   NOT NULL,          -- AGENT_COMMISSION | SUPPLIER_SETTLEMENT | REFUND
    payee_id        varchar(100)  NOT NULL,          -- value ref to the payee (no FK)
    payee_type      varchar(20)   NOT NULL,          -- AGENT | SUPPLIER | CUSTOMER
    booking_id      varchar(100),                    -- value ref to the related booking
    origin_ref      varchar(200),                    -- value ref to the origin obligation (REFUND, BR7)
    amount          numeric(18,2) NOT NULL,          -- amount in its original currency
    currency        varchar(3)    NOT NULL,
    settlement_rate numeric(18,6),                   -- BRL settlement rate (scale 6, > 0) when foreign
    settled_brl     numeric(18,2),                   -- the BRL baixa = amount × settlement_rate
    status          varchar(20)   NOT NULL,          -- PENDING | EXECUTING | EXECUTED | FAILED
    proof_document_id uuid,                          -- value ref to the receipt (single payout)
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      varchar(100),
    updated_by      varchar(100),
    version         bigint        NOT NULL,
    CONSTRAINT ck_payout_settlement_rate_positive
        CHECK (settlement_rate IS NULL OR settlement_rate > 0)  -- BR1: rate > 0 when present
);

-- BR6/DL-0050: a payout has N installments (an implicit single one when à vista); each executes and is
-- receipted individually; the payout is EXECUTED only when all installments are EXECUTED. The sum of
-- the installments equals the total exactly (cent distribution, validated in the domain).
CREATE TABLE payout_installments (
    id                uuid          PRIMARY KEY,
    payout_id         uuid          NOT NULL REFERENCES payouts(id),
    seq               int           NOT NULL,
    due_date          date          NOT NULL,
    amount            numeric(18,2) NOT NULL,
    currency          varchar(3)    NOT NULL,
    status            varchar(20)   NOT NULL,        -- PENDING | EXECUTING | EXECUTED | FAILED
    executed_at       timestamptz,
    proof_document_id uuid,                          -- value ref to the installment receipt
    CONSTRAINT ux_payout_installment_seq UNIQUE (payout_id, seq)
);

CREATE INDEX ix_payout_status ON payouts (status);
CREATE INDEX ix_payout_kind ON payouts (kind);
CREATE INDEX ix_payout_installment_payout ON payout_installments (payout_id);
