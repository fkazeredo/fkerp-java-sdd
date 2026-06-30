-- SPEC-0024 Identity: real authentication model + roles/permissions, graduating the SPEC-0001 dev stub.
-- The ERP authenticates in-house and is the Resource Server of its own JWT issuer (DL-0079); the live
-- external OIDC IdP is Phase 13. This module owns the role/permission model and a minimal local user
-- store (DL-0080). Access auditing reuses the Platform system_audit (V28/DL-0083) — no new audit table.
--
-- SECURITY (BR4, security.md): only the BCrypt password HASH is stored — never a plaintext password,
-- token or secret. Cross-context references are BY VALUE (no FK to another context's tables — Modulith).

-- 1) Roles — the single source of truth of internal authorization (BR5).
CREATE TABLE roles (
    name        varchar(40)  PRIMARY KEY,                 -- e.g. ROLE_DIRECTOR (Spring 'ROLE_' prefix)
    description varchar(200)
);

-- 2) Role → permission (named capabilities, closed catalogue — DL-0082).
CREATE TABLE role_permissions (
    role_name  varchar(40) NOT NULL REFERENCES roles (name),
    permission varchar(80) NOT NULL,                       -- e.g. billing:invoice:issue
    PRIMARY KEY (role_name, permission)
);

-- 3) Minimal local user store (DL-0080). Only the BCrypt hash is kept (BR4). Users migrate to the IdP
-- in Phase 13; the table then becomes optional/read-only.
CREATE TABLE identity_users (
    id            uuid         PRIMARY KEY,
    username      varchar(60)  NOT NULL,
    password_hash varchar(100) NOT NULL,                   -- BCrypt hash ONLY — never plaintext (BR4)
    display_name  varchar(120),
    status        varchar(20)  NOT NULL,                   -- ACTIVE | DISABLED
    created_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT ux_identity_users_username UNIQUE (username)
);

-- 4) User → role (M:N; role_name by value within this module's boundary).
CREATE TABLE user_roles (
    user_id   uuid        NOT NULL REFERENCES identity_users (id),
    role_name varchar(40) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role_name)
);

-- Seed the base roles (DL-0082). The set the specs/ROADMAP already assume.
INSERT INTO roles (name, description) VALUES
    ('ROLE_DIRECTOR',     'Diretor — emite diretivas comerciais e governa parâmetros'),
    ('ROLE_FINANCE',      'Financeiro — emite NF de comissão e fecha período'),
    ('ROLE_OPERATIONS',   'Operacional — opera reservas, cotações e pós-venda'),
    ('ROLE_IT',           'TI — dispara jobs/crawler e custodia certificado'),
    ('ROLE_POLICY_ADMIN', 'Curador de políticas — define regras governadas (não diretivas)'),
    ('ROLE_VIEWER',       'Leitor — somente consulta');

-- Seed the named permissions per role (DL-0082). Sensitive capabilities map to the roles the specs cite.
INSERT INTO role_permissions (role_name, permission) VALUES
    ('ROLE_DIRECTOR',     'policy:directive:write'),
    ('ROLE_DIRECTOR',     'policy:rule:write'),
    ('ROLE_DIRECTOR',     'identity:role:read'),
    ('ROLE_DIRECTOR',     'identity:audit:read'),
    ('ROLE_POLICY_ADMIN', 'policy:rule:write'),
    ('ROLE_FINANCE',      'billing:invoice:issue'),
    ('ROLE_FINANCE',      'finance:period:close'),
    ('ROLE_IT',           'platform:job:trigger'),
    ('ROLE_IT',           'platform:certificate:write'),
    ('ROLE_IT',           'identity:role:read'),
    ('ROLE_IT',           'identity:audit:read');

-- NOTE: dev/test seed USERS are inserted programmatically by DevUserSeeder (profiles dev/test only,
-- DL-0081), using the BCrypt PasswordEncoder bean — never a plaintext or hardcoded hash here.
