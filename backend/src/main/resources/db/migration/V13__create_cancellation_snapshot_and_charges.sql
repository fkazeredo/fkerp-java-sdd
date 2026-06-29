-- SPEC-0010 Cancellation: the policy frozen onto a booking at confirmation (BR1) and the resulting
-- charges (BR5). The snapshot decouples a confirmed booking from later policy edits; the charges are
-- distinct facts that never net out (BR11/DL-0024 — the merchant trap). No cross-module FK: ids are
-- values.

-- Product/supplier scope reference used to resolve the cancellation policy (nullable: no scope ⇒
-- safe default policy). Additive to the existing bookings table.
ALTER TABLE bookings ADD COLUMN scope_ref varchar(200);

CREATE TABLE booking_cancellation_snapshots (
    booking_id                 uuid          PRIMARY KEY,
    type                       varchar(20)   NOT NULL,
    windows_encoded            varchar(2000) NOT NULL,
    refundable                 boolean       NOT NULL,
    cost_bearer                varchar(20)   NOT NULL,
    merchant_of_record         boolean       NOT NULL DEFAULT false,
    no_show_fee_amount         numeric(18,2),
    no_show_fee_currency       varchar(3),
    waived_if_flight_cancelled boolean       NOT NULL DEFAULT false,
    sale_amount                numeric(18,2) NOT NULL,   -- customer-paid reference (penalty/refund base)
    sale_currency              varchar(3)    NOT NULL,
    supplier_amount            numeric(18,2) NOT NULL,   -- supplier cost reference (ALL_SALES_FINAL)
    supplier_currency          varchar(3)    NOT NULL,
    frozen_at                  timestamptz   NOT NULL
);

CREATE TABLE cancellation_charges (
    id          uuid          PRIMARY KEY,
    booking_id  uuid          NOT NULL,                  -- value (no cross-module FK)
    kind        varchar(20)   NOT NULL,                  -- PENALTY | SUPPLIER | CUSTOMER_REFUND | NO_SHOW
    amount      numeric(18,2) NOT NULL,
    currency    varchar(3)    NOT NULL,
    cost_bearer varchar(20)   NOT NULL,                  -- AGENCY | ACME | SUPPLIER
    created_at  timestamptz   NOT NULL,
    created_by  varchar(100)
);

-- Charges are read back per booking (cancel response + audit).
CREATE INDEX ix_cancellation_charges_booking ON cancellation_charges (booking_id);
