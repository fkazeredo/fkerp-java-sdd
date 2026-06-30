-- SPEC-0023 Platform: the operated infra context — e-CNPJ/credential custody, job governance and the
-- consolidated system audit. Three independent table groups (DL-0073). All cross-context references
-- are BY VALUE (never an FK to another context — Modulith).
--
-- SECURITY (BR1, security.md): the certificate/credential MATERIAL is stored ENCRYPTED-at-rest only
-- (AES-256-GCM envelope, DL-0074); the master key lives OUTSIDE the database (env). Only METADATA is
-- in clear. No table here ever holds a private key or password in plaintext.

-- 1) Certificate custody (BR1/BR5/DL-0074). Metadata in clear; secret material encrypted.
CREATE TABLE platform_certificates (
    id                 uuid          PRIMARY KEY,
    subject            varchar(300)  NOT NULL,            -- e-CNPJ subject DN (metadata)
    holder_document    varchar(20)   NOT NULL,            -- holder CNPJ (metadata; masked in logs)
    fingerprint        varchar(95)   NOT NULL,            -- SHA-256 thumbprint of the DER (hex/colon)
    valid_from         date          NOT NULL,
    valid_until        date          NOT NULL,
    status             varchar(20)   NOT NULL,            -- VALID | EXPIRING | EXPIRED | REVOKED
    encrypted_material bytea         NOT NULL,            -- iv||ciphertext||tag (AES-256-GCM, DL-0074)
    key_alias          varchar(60)   NOT NULL,            -- which master key encrypted it (rotation)
    expiry_signaled_at timestamptz,                       -- idempotency guard for CertificateExpiring
    created_at         timestamptz   NOT NULL,
    created_by         varchar(100),
    version            bigint        NOT NULL,
    CONSTRAINT ux_platform_certificates_fingerprint UNIQUE (fingerprint)
);

CREATE INDEX ix_platform_certificates_status ON platform_certificates (status, valid_until);

-- 2) Job governance (BR2/BR3/DL-0075/DL-0076).
CREATE TABLE scheduled_jobs (
    name         varchar(80)   PRIMARY KEY,               -- stable job name (e.g. point-clock-crawl)
    cron         varchar(120)  NOT NULL,                  -- documented schedule (descriptive)
    enabled      boolean       NOT NULL DEFAULT true,
    owner_module varchar(40)   NOT NULL,                  -- the module that owns the job's LOGIC (BR6)
    last_run_at  timestamptz
);

CREATE TABLE job_runs (
    id              uuid          PRIMARY KEY,
    job_name        varchar(80)   NOT NULL,               -- references scheduled_jobs.name (VALUE)
    started_at      timestamptz   NOT NULL,
    finished_at     timestamptz,
    status          varchar(20)   NOT NULL,               -- RUNNING | SUCCEEDED | FAILED | SKIPPED
    items           int,                                  -- countable outcome (e.g. flags), nullable
    failure_class   varchar(30),                          -- TIMEOUT|UNAVAILABLE|... when FAILED
    correlation_id  varchar(100),
    idempotency_key varchar(120)                          -- (job_name, window) idempotency, DL-0075
);

CREATE INDEX ix_job_runs_job_status ON job_runs (job_name, status);

-- Idempotency per window (BR2): at most one non-SKIPPED run per (job_name, idempotency_key). A second
-- start for the same window finds this row and is recorded SKIPPED instead of running again.
CREATE UNIQUE INDEX ux_job_runs_idempotency
    ON job_runs (job_name, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND status <> 'SKIPPED';

-- Seed the initial catalogue (DL-0076): the jobs already activated today. Their LOGIC stays in the
-- owner module; Platform only governs (idempotency/locking/history).
INSERT INTO scheduled_jobs (name, cron, enabled, owner_module) VALUES
    ('point-clock-crawl',    '0 0 1 * * *',  true, 'people'),
    ('aftersales-sla-sweep', '0 */15 * * * *', true, 'aftersales'),
    ('asset-license-expiry', '0 0 2 * * *',  true, 'assets'),
    ('representation-expiry','0 0 3 * * *',  true, 'portfolio'),
    ('retention-expiry',     '0 0 4 * * *',  true, 'compliance'),
    ('certificate-expiry',   '0 0 5 * * *',  true, 'platform');

-- 3) System audit (BR4/DL-0077): append-only. No update/delete in the application path.
CREATE TABLE system_audit (
    id             uuid          PRIMARY KEY,
    occurred_at    timestamptz   NOT NULL,
    actor          varchar(100),                          -- who (or null for system), masked in logs
    type           varchar(60)   NOT NULL,                -- JOB_RUN_STARTED | JOB_RUN_FINISHED | ...
    detail_json    jsonb,                                 -- metadata ONLY — never secret material (BR1)
    correlation_id varchar(100)
);

CREATE INDEX ix_system_audit_type_time ON system_audit (type, occurred_at DESC);
CREATE INDEX ix_system_audit_actor ON system_audit (actor);
