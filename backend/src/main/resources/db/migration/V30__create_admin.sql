-- SPEC-0025 Admin (administrative suppliers & contracts): the "administrative desk" — a lean
-- registry of administrative suppliers (utilities, software/service PJ, self-employed) and their
-- contracts, that feeds recurring expense entries into the Finance ledger and references the
-- supporting documents in the Compliance vault. NOT the tourism brands/suppliers (Portfolio).
--
-- All references to OTHER contexts are BY VALUE (never a cross-context FK — Modulith): document_id
-- (Compliance) and finance_entry_id (Finance) are uuid values, not FKs. The supplier_id FKs inside
-- admin_* are INTERNAL to the Admin module (same module) — allowed.

--   admin_suppliers   an administrative supplier (BR1). status ACTIVE|INACTIVE; identifier is the
--                     legal id (CNPJ/CPF) when applicable — possible personal data (masked in logs).
CREATE TABLE admin_suppliers (
    id           uuid          PRIMARY KEY,
    type         varchar(20)   NOT NULL,        -- UTILITY | SOFTWARE | SERVICE | OTHER
    identifier   varchar(40),                    -- CNPJ/CPF (value), nullable
    display_name varchar(200)  NOT NULL,
    status       varchar(20)   NOT NULL,        -- ACTIVE | INACTIVE
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    version      bigint        NOT NULL
);

-- Supports the listing filters by type and/or status (GET /api/admin/suppliers?type=&status=).
CREATE INDEX ix_admin_suppliers_type_status ON admin_suppliers (type, status);

--   admin_contracts   the contract sustaining a recurring administrative cost (BR2): validity
--                     window, recurrence, recurring amount (Money) and the contract document_id
--                     (Compliance, value). expiry_signaled_at makes the expiry alert idempotent
--                     (DL-0087).
CREATE TABLE admin_contracts (
    id                 uuid          PRIMARY KEY,
    supplier_id        uuid          NOT NULL REFERENCES admin_suppliers (id),
    valid_from         date          NOT NULL,
    valid_until        date,                       -- null = open-ended
    recurrence         varchar(20),                -- MONTHLY | YEARLY | OTHER
    amount             numeric(18,2),              -- Money amount (value)
    currency           varchar(3),                 -- Money currency (ISO 4217)
    document_id        uuid,                        -- Compliance contract document id (VALUE, not an FK)
    expiry_signaled_at timestamptz,                 -- DL-0087: expiry alert raised once per contract
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    created_by         varchar(100),
    updated_by         varchar(100),
    version            bigint        NOT NULL
);

-- The contracts-of-a-supplier listing.
CREATE INDEX ix_admin_contracts_supplier ON admin_contracts (supplier_id);

-- Supports the contract-expiry sweep (DL-0087): not-yet-signaled contracts with a due valid_until.
-- Partial index keeps it tight to the candidate set.
CREATE INDEX ix_admin_contracts_expiry
    ON admin_contracts (valid_until)
    WHERE valid_until IS NOT NULL AND expiry_signaled_at IS NULL;

--   admin_expenses    a recorded recurring expense (BR3) that created a PAYABLE entry in the Finance
--                     ledger (finance_entry_id, value). Idempotent per (supplier, period, kind) —
--                     UNIQUE — so re-registering the same expense never double-posts (DL-0086).
CREATE TABLE admin_expenses (
    id               uuid          PRIMARY KEY,
    supplier_id      uuid          NOT NULL REFERENCES admin_suppliers (id),
    period           varchar(7)    NOT NULL,        -- YYYY-MM
    amount           numeric(18,2) NOT NULL,        -- Money amount
    currency         varchar(3)    NOT NULL,        -- Money currency (ISO 4217)
    kind             varchar(30)   NOT NULL,        -- UTILITY | AUTONOMOUS_SERVICE | SERVICE | OTHER
    finance_entry_id uuid          NOT NULL,        -- Finance ledger entry id (VALUE, not an FK)
    created_at       timestamptz   NOT NULL,
    created_by       varchar(100)
);

-- Idempotency of recurring-expense registration (DL-0086): one expense per supplier+period+kind.
CREATE UNIQUE INDEX ux_admin_expenses_supplier_period_kind
    ON admin_expenses (supplier_id, period, kind);

-- DL-0085: extend the Compliance requirement catalog (seed; table 7.7) for the administrative
-- SERVICE expense (software/service PJ) — its NFS-e is mandatory at registration (blocks the close);
-- the payment proof is required only at settlement. UTILITY_EXPENSE/AUTONOMOUS_SERVICE already exist
-- in V8 and are reused; OTHER_EXPENSE deliberately has no mandatory document at registration.
-- This is an ADDITIVE seed — the applied V8 migration is never edited.
INSERT INTO document_requirements (entry_type, required_document_type, phase) VALUES
    ('SERVICE', 'NFSE',          'AT_REGISTRATION'),
    ('SERVICE', 'PAYMENT_PROOF', 'AT_SETTLEMENT');

-- DL-0087/DL-0076: register the administrative contract-expiry alert in the Platform job catalog
-- (its LOGIC lives in AdminService; the Platform only governs idempotency/locking/history). Daily.
INSERT INTO scheduled_jobs (name, cron, enabled, owner_module) VALUES
    ('admin-contract-expiry', '0 0 6 * * *', true, 'admin');
