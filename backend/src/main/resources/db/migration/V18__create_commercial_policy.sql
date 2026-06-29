-- SPEC-0014 CommercialPolicy: the governed-parameter precedence engine. A parameter_rule is one
-- value for a parameter_key at a layer (DIRECTIVE > PROMOTION > CONTRACT > POLICY > SYSTEM_DEFAULT),
-- with a scope matcher (account/product/channel — null = wildcard "any") and effectivity. Resolution
-- picks the active, highest-precedence rule whose scope matches, returning value + provenance
-- (BR2/BR3, DL-0037). Scope dimensions are VALUES copied from the request, never cross-context FKs.
--
-- Matcher by explicit columns (not jsonb): the dimension set is small and fixed — jsonb would be
-- overengineering (Rule Zero, same posture as V8/V17).
CREATE TABLE parameter_rules (
    id                uuid          PRIMARY KEY,
    parameter_key     varchar(60)   NOT NULL,
    layer             varchar(20)   NOT NULL,             -- DIRECTIVE | PROMOTION | CONTRACT | POLICY | SYSTEM_DEFAULT
    scope_account_id  uuid,                               -- matcher dimension (null = any account)
    scope_product_ref varchar(120),                       -- matcher dimension (null = any product)
    scope_channel     varchar(60),                        -- matcher dimension (null = any channel)
    value_text        varchar(200)  NOT NULL,
    value_type        varchar(20)   NOT NULL,             -- NUMBER | PERCENT | MONEY | BOOL
    valid_from        date          NOT NULL,
    valid_until       date,                               -- null = open-ended
    defined_by        varchar(100)  NOT NULL,
    justification     varchar(500),                       -- mandatory for DIRECTIVE (BR5)
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL
);

-- Resolution loads the candidate set per key (BR2); the listing/audit query filters by key+layer.
CREATE INDEX ix_param_rules_key_layer ON parameter_rules (parameter_key, layer);

-- Seed: a SYSTEM_DEFAULT for every parameter_key already in use (BR4, DL-0039), at global scope, so
-- resolution is never empty for these keys. Values come from the ROADMAP "governed parameters —
-- recommended defaults" (markup default 0 = DL-0009; drift alert 2% = DL-0027; reconciliation
-- discrepancy absolute floor R$1,00 = DL-0011). Fixed UUIDs keep the seed idempotent across replays.
INSERT INTO parameter_rules (
    id, parameter_key, layer, value_text, value_type, valid_from, valid_until,
    defined_by, justification, created_at, updated_at, version
) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'MARKUP_PCT',            'SYSTEM_DEFAULT', '0',    'PERCENT', DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0),
    ('00000000-0000-0000-0000-0000000000a2', 'FX_DRIFT_LIMIT',        'SYSTEM_DEFAULT', '0.02', 'PERCENT', DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0),
    ('00000000-0000-0000-0000-0000000000a3', 'RECON_DISCREPANCY_TOL', 'SYSTEM_DEFAULT', '1.00', 'MONEY',   DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0);
