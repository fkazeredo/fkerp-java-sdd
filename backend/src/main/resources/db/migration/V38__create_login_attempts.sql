-- SPEC-0024 (Fase 19c, DL-0125): brute-force protection for the self-hosted Authorization Server's
-- form login. One row per username tracks consecutive failed logins; after a threshold the account
-- is locked until `locked_until`. A successful login clears the row. No password/secret is stored
-- here — only the counter and the lock window (BR4).
CREATE TABLE login_attempts (
    username     varchar(100) PRIMARY KEY,
    failed_count int          NOT NULL DEFAULT 0,
    locked_until timestamptz,
    updated_at   timestamptz  NOT NULL
);
