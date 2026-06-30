-- SPEC-0024 Identity — GRADUAÇÃO Fase 13 (DL-0107). Authentication moved to the external OIDC IdP
-- (Keycloak — DL-0103/0104): users and passwords now live in the IdP, so the minimal LOCAL user store
-- of the 8k (DL-0080) is retired. The ERP stops custodying password hashes (better security posture).
--
-- The role/permission CATALOGUE (roles, role_permissions) is KEPT — it is the single source of truth
-- of internal authorization (BR5/BR16): the IdP says which roles a user has, the ERP says what each
-- role may do. Only the user tables are dropped.
--
-- Idempotent (DROP TABLE IF EXISTS); never edits the applied V29.

DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS identity_users;
