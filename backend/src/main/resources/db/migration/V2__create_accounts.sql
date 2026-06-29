-- SPEC-0002 Accounts: commercial account (agency or agent), the entry point of every operation.
-- Holds commercial/legal identity only; no monetary computation (BR6).
CREATE TABLE accounts (
    id              uuid PRIMARY KEY,
    legal_type      varchar(20)  NOT NULL,
    document_number varchar(20)  NOT NULL,
    display_name    varchar(200) NOT NULL,
    status          varchar(20)  NOT NULL,
    cadastur        varchar(50),
    iata            varchar(50),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      varchar(100),
    updated_by      varchar(100),
    version         bigint       NOT NULL
);

-- BR3: (legalType, documentNumber) is unique. The unique index is the authoritative duplicate guard,
-- translated to account.document.duplicate (a raw DB exception never leaks).
CREATE UNIQUE INDEX ux_accounts_document ON accounts (legal_type, document_number);
