-- SPEC-0009 Sourcing/Integration: idempotency of the inbound webhook (BR4). The external quotation
-- id is the primary key, so a re-delivery of the same id resolves to the same quote instead of
-- creating a duplicate. quote_id and account_id are plain values (no cross-module FK).
CREATE TABLE inbound_quotations (
    external_quotation_id varchar(100) PRIMARY KEY,
    quote_id              uuid         NOT NULL,
    account_id            uuid         NOT NULL,
    received_at           timestamptz  NOT NULL
);
