-- SPEC-0009 BR10 (Fase 19b, DL-0120 — revisa a DL-0017): quarentena de inbound. Um payload de
-- cotação inbound rejeitado na fronteira (ex.: conta desconhecida) é PRESERVADO para replay
-- operacional em vez de descartado — o padrão exception-queue de integrações maduras. O contrato
-- externo (422) não muda; o que muda é que o payload sobrevive. Status é máquina de estado
-- (QUARANTINED -> REPLAYED | DISCARDED) e por isso permanece enum no código (critério Fase 18).
CREATE TABLE inbound_quarantine (
    id                    uuid           PRIMARY KEY,
    external_quotation_id varchar(100)   NOT NULL,
    account_document      varchar(20)    NOT NULL,
    product_text          varchar(500)   NOT NULL,
    price_amount          numeric(18, 2) NOT NULL,
    price_currency        varchar(3)     NOT NULL,
    reason_code           varchar(100)   NOT NULL,
    status                varchar(20)    NOT NULL,
    replayed_quote_id     uuid,
    received_at           timestamptz    NOT NULL,
    resolved_at           timestamptz,
    resolved_by           varchar(100),
    version               bigint         NOT NULL
);

-- One PENDING entry per external id: a re-delivery of the same rejected payload keeps the single
-- pending row (the service checks before insert; the partial unique index is the database teeth).
CREATE UNIQUE INDEX ux_inbound_quarantine_pending
    ON inbound_quarantine (external_quotation_id)
    WHERE status = 'QUARANTINED';

CREATE INDEX ix_inbound_quarantine_status_received
    ON inbound_quarantine (status, received_at DESC);
