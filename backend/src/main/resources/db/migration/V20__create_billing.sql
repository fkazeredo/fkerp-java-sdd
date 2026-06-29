-- SPEC-0016 Billing: commission invoice (NFS-e de serviço) over the commission + ISS/withholdings.
-- The invoice references its Finance commission entry and its Compliance document BY VALUE (no
-- cross-module FK), so Billing stays independently extractable. The taxable base is the COMMISSION
-- (base_amount), never the gross package — there is deliberately no column for the supplier tariff
-- (BR1, DL-0045). Withholdings are a short, stable list kept as compact text (DL-0045), not jsonb.
CREATE TABLE commission_invoices (
    id                  uuid          PRIMARY KEY,
    commission_entry_id uuid          NOT NULL,        -- value ref to the Finance commission entry
    base_amount         numeric(18,2) NOT NULL,        -- the commission (the taxable base, BR1)
    base_currency       varchar(3)    NOT NULL,
    iss_amount          numeric(18,2),                 -- filled on issue
    withholdings_json   text,                          -- compact "KIND:amount,..." (empty for Simples)
    status              varchar(20)   NOT NULL,        -- RASCUNHO | EMITIDA | CANCELADA
    tax_regime          varchar(30)   NOT NULL,        -- SIMPLES_NACIONAL | LUCRO_PRESUMIDO | LUCRO_REAL
    municipality        varchar(20),                   -- IBGE code of incidence
    service_code        varchar(40),
    number              varchar(40),                   -- municipal NFS-e number (on issue)
    verification_code   varchar(80),                   -- municipal verification code (on issue)
    document_id         uuid,                          -- value ref to the archived Compliance document
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          varchar(100),
    updated_by          varchar(100),
    version             bigint        NOT NULL
);

-- BR4/BR6 idempotency: at most ONE live (non-cancelled) invoice per commission. A cancelled invoice
-- frees the commission for a re-issue (a partial unique index, so cancelled rows do not collide).
CREATE UNIQUE INDEX ux_commission_invoice_live
    ON commission_invoices (commission_entry_id)
    WHERE status <> 'CANCELADA';

-- Seeded municipal ISS rates (DL-0044): the legal ISS band is 2%-5% (LC 116/2003). The default
-- (absent municipality) is the 5% cap, resolved in code; São Paulo capital (3550308) seeded at 2%
-- as an example of a local rate distinct from the cap.
CREATE TABLE municipal_iss_rates (
    municipality varchar(20)  PRIMARY KEY,
    iss_rate     numeric(5,4) NOT NULL
);

INSERT INTO municipal_iss_rates (municipality, iss_rate) VALUES ('3550308', 0.0200);
