-- V1 baseline schema for the Acme Travel ERP.
-- The foundation (SPEC-0001) creates no business tables; those start in SPEC-0002.
-- pgcrypto provides gen_random_uuid() for database-generated UUIDs in later slices.
-- Idempotent on purpose (never edit an applied migration; add new V{n} files instead).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
