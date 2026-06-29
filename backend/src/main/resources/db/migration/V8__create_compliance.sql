-- SPEC-0008 Compliance: the document vault, the attach link (by value), and the requirement policy.
-- A document carries its content hash, optional signed format and the legal retention deadline.
-- The attachment links a document to a financial entry by value (entry_id + entry_type) — no
-- cross-module FK to Finance. The requirements are system data, seeded below (DL-0012).
CREATE TABLE documents (
    id                uuid         PRIMARY KEY,
    type              varchar(40)  NOT NULL,
    file_ref          varchar(200) NOT NULL,
    hash              varchar(100) NOT NULL,
    issued_at         date         NOT NULL,
    retention_until   date         NOT NULL,
    signed_format     varchar(20),
    has_personal_data boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    created_by        varchar(100),
    updated_by        varchar(100),
    version           bigint       NOT NULL
);

CREATE TABLE document_attachments (
    id          uuid         PRIMARY KEY,
    document_id uuid         NOT NULL REFERENCES documents (id),
    entry_id    uuid         NOT NULL,                 -- value reference to a Finance ledger entry
    entry_type  varchar(40)  NOT NULL,
    attached_at timestamptz  NOT NULL,
    attached_by varchar(100)
);

-- BR5: attaching is idempotent per (document, entry).
CREATE UNIQUE INDEX ux_document_attachments ON document_attachments (document_id, entry_id);
-- The close-check looks up attachments by entry.
CREATE INDEX ix_document_attachments_entry ON document_attachments (entry_id);

CREATE TABLE document_requirements (
    entry_type             varchar(40) NOT NULL,
    required_document_type varchar(40) NOT NULL,
    phase                  varchar(20) NOT NULL,
    PRIMARY KEY (entry_type, required_document_type, phase)
);

-- Retention-expiring job scans by deadline.
CREATE INDEX ix_documents_retention ON documents (retention_until);

-- Seed of the requirement catalog (DL-0012; redesign table 7.7). AT_REGISTRATION rows block the
-- monthly close; AT_SETTLEMENT rows are required only at settlement (Payout, SPEC-0017).
INSERT INTO document_requirements (entry_type, required_document_type, phase) VALUES
    ('COMMISSION_RECEIVABLE', 'COMMISSION_INVOICE', 'AT_REGISTRATION'),
    ('COMMISSION_PAYABLE',    'COMMISSION_INVOICE', 'AT_REGISTRATION'),
    ('COMMISSION_PAYABLE',    'PAYMENT_PROOF',      'AT_SETTLEMENT'),
    ('UTILITY_EXPENSE',       'UTILITY_BILL',       'AT_REGISTRATION'),
    ('UTILITY_EXPENSE',       'PAYMENT_PROOF',      'AT_SETTLEMENT'),
    ('AUTONOMOUS_SERVICE',    'RPA',                'AT_REGISTRATION'),
    ('SUPPLIER_SETTLEMENT',   'NFE',                'AT_REGISTRATION'),
    ('REFUND',                'REFUND_PROOF',       'AT_REGISTRATION'),
    ('PENALTY',               'VOUCHER',            'AT_REGISTRATION');
