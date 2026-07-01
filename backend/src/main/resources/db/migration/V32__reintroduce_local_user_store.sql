-- SPEC-0024 Identity — RE-GRADUAÇÃO Fase 17 (ADR-0018 / DL-0112). Phase 13 delegated authentication to
-- an external Keycloak and DROPPED the local user store (V31). Phase 17 removes Keycloak and serves OIDC
-- from an EMBEDDED Spring Authorization Server (DL-0110); the AS authenticates users against a LOCAL
-- store again, so this migration RE-CREATES the two user tables the V31 dropped.
--
-- The role/permission CATALOGUE (roles, role_permissions) was KEPT by V31 and is untouched here — it is
-- the single source of truth of internal authorization (BR5/BR16): the AS says which roles a user has,
-- the ERP says what each role may do.
--
-- SECURITY (BR4, security.md): only the BCrypt password HASH is stored — never a plaintext password,
-- token or secret. Cross-context references are BY VALUE (no FK to another context's tables — Modulith).
--
-- Idempotent (CREATE TABLE IF NOT EXISTS / DROP ... IF EXISTS on the FK); never edits the applied V29/V31.

-- 1) Local user store (DL-0112). Only the BCrypt hash is kept (BR4). Dev/E2E seed users are inserted
--    programmatically by DevUserSeeder (profiles dev/e2e only) using the BCryptPasswordEncoder bean —
--    never a plaintext or hardcoded hash here.
CREATE TABLE IF NOT EXISTS identity_users (
    id            uuid         PRIMARY KEY,
    username      varchar(60)  NOT NULL,
    password_hash varchar(100) NOT NULL,                   -- BCrypt hash ONLY — never plaintext (BR4)
    display_name  varchar(120),
    status        varchar(20)  NOT NULL,                   -- ACTIVE | DISABLED
    created_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL
);

-- Unique username (guard against a partially-applied prior state).
CREATE UNIQUE INDEX IF NOT EXISTS ux_identity_users_username ON identity_users (username);

-- 2) User → role (M:N; role_name by value within this module's boundary). role_name references the
--    kept catalogue (roles), which V31 preserved.
CREATE TABLE IF NOT EXISTS user_roles (
    user_id   uuid        NOT NULL REFERENCES identity_users (id),
    role_name varchar(40) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role_name)
);
