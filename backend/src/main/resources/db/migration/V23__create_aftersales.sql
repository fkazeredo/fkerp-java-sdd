-- SPEC-0018 AfterSales: the post-sale context. A support_case references a booking by VALUE (never a
-- cross-context FK — Modulith) and owns the lifecycle status machine, the governed SLA deadlines
-- (frozen at open from CommercialPolicy — DL-0052), the SLA breach alert flag (BR4/DL-0053 — an alert
-- that never blocks), the resolution and the linked Payout REFUND id (idempotency guard, BR3/DL-0054)
-- and the cost-to-serve (BR5/DL-0053).
--
-- Cost-to-serve is stored as fixed columns (cost_handling/cost_refund + reopen_count), NOT jsonb: the
-- shape is small and known, so a jsonb dependency would be overengineering (Rule Zero — same posture
-- as V8/V17/V18; the spec's cost_to_serve_json is realized as fixed columns).
CREATE TABLE support_cases (
    id                   uuid          PRIMARY KEY,
    booking_id           varchar(120)  NOT NULL,             -- referenced booking (VALUE, not an FK)
    type                 varchar(30)   NOT NULL,             -- COMPLAINT | CHANGE_REQUEST | CANCELLATION_REQUEST | REFUND_REQUEST | INFO
    status               varchar(20)   NOT NULL,             -- OPEN | IN_PROGRESS | WAITING | RESOLVED | CLOSED
    summary              varchar(500),
    opened_at            timestamptz   NOT NULL,
    first_response_due_at timestamptz  NOT NULL,             -- SLA: first-response deadline (BR1/BR4)
    due_at               timestamptz   NOT NULL,             -- SLA: resolution deadline (BR1/BR4)
    breached             boolean       NOT NULL DEFAULT false,-- SLA breach alert flag (BR4 — never blocks)
    resolved_at          timestamptz,
    resolution           varchar(30),                        -- REFUND_APPROVED | CANCEL_APPROVED | RESOLVED_NO_ACTION | REJECTED
    linked_payout_id     uuid,                               -- the Payout REFUND triggered (BR3, idempotency)
    reopen_count         int           NOT NULL DEFAULT 0,
    cost_handling        numeric(19,2) NOT NULL DEFAULT 0,   -- cost-to-serve: handling effort (BRL)
    cost_refund          numeric(19,2) NOT NULL DEFAULT 0,   -- cost-to-serve: linked refund (BRL)
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    created_by           varchar(100),
    updated_by           varchar(100),
    version              bigint        NOT NULL
);

-- Listing filters (type/status/booking/breached); the breach sweep filters by status + due_at.
CREATE INDEX ix_support_cases_booking ON support_cases (booking_id);
CREATE INDEX ix_support_cases_status_due ON support_cases (status, due_at);

-- Seed: the governed SLA parameters as SYSTEM_DEFAULT in the CommercialPolicy engine (SPEC-0014 V18;
-- DL-0052), at global scope, so resolution is never empty (BR4 of SPEC-0014). Values come from the
-- ROADMAP "governed parameters — recommended defaults": first response 24h, resolution 72h,
-- cancellation/refund 48h. Type NUMBER = hours. Fixed UUIDs keep the seed idempotent across replays.
INSERT INTO parameter_rules (
    id, parameter_key, layer, value_text, value_type, valid_from, valid_until,
    defined_by, justification, created_at, updated_at, version
) VALUES
    ('00000000-0000-0000-0000-0000000000b1', 'AFTERSALES_SLA_FIRST_RESPONSE', 'SYSTEM_DEFAULT', '24', 'NUMBER', DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0),
    ('00000000-0000-0000-0000-0000000000b2', 'AFTERSALES_SLA_RESOLUTION',     'SYSTEM_DEFAULT', '72', 'NUMBER', DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0),
    ('00000000-0000-0000-0000-0000000000b3', 'AFTERSALES_SLA_REFUND',         'SYSTEM_DEFAULT', '48', 'NUMBER', DATE '2026-01-01', NULL, 'system-seed', NULL, now(), now(), 0);
