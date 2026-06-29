-- SPEC-0013 Intelligence (DSS): the read-models the module projects from consumed events. Everything
-- here is a projection/read-model fed by events (persistence.md) — the module ADVISES, never commands,
-- and writes only its own tables (BR2). No cross-module FK: subject_ref / account_id / booking_id are
-- VALUES copied from events, never foreign keys into other contexts.

-- The prescriptive Insight (BR1): evidence (numbers + provenance), recommendation (verdict + action +
-- gain/risk) and the guardrail crossed (if any), plus the human-decision status (BR4). Evidence and
-- recommendation are fixed structured columns (not jsonb): the shape is small and known, so a jsonb
-- dependency would be overengineering (Rule Zero, same posture as the booking penalty-windows codec).
CREATE TABLE insights (
    id                     uuid          PRIMARY KEY,
    type                   varchar(30)   NOT NULL,            -- PROMO_FX_ADVISOR | OVERRIDE_NUDGE
    subject_kind           varchar(20)   NOT NULL,            -- AGENCY | ROUTE | PRODUCT | SUPPLIER
    subject_ref            varchar(120)  NOT NULL,            -- the subject value (agency id in v1)

    -- evidence (numbers + provenance)
    accrued_subsidy_brl    numeric(18,2) NOT NULL,
    realized_gap_brl       numeric(18,2) NOT NULL,
    volume_attracted       bigint        NOT NULL DEFAULT 0,
    evidence_sources       varchar(400)  NOT NULL DEFAULT '', -- comma-separated event types (provenance)

    -- recommendation (action + estimated gain/risk)
    verdict                varchar(20)   NOT NULL,            -- CONVERTE | QUEIMA_MARGEM
    recommendation_action  varchar(500)  NOT NULL,
    estimated_gain_brl     numeric(18,2),
    estimated_risk_brl     numeric(18,2),

    -- guardrail (alert, never blocks; null when none crossed)
    guardrail_description  varchar(120),
    guardrail_threshold_brl numeric(18,2),

    status                 varchar(20)   NOT NULL,            -- NEW | ACCEPTED | REJECTED | DISMISSED
    generated_at           timestamptz   NOT NULL,
    decided_by             varchar(100),
    decided_at             timestamptz,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL,
    -- one current insight per (type, subject): the projection is upserted on each event (DL-0036).
    CONSTRAINT uq_insights_type_subject UNIQUE (type, subject_kind, subject_ref)
);

CREATE INDEX ix_insights_type_status ON insights (type, status);
CREATE INDEX ix_insights_estimated_gain ON insights (estimated_gain_brl DESC);

-- Per-booking correlation learned PURELY from events (DL-0034): maps a booking to its agency
-- (from BookingConfirmed) and buffers the FX facts keyed by booking_id (RateSubsidyAccrued,
-- FxPositionClosed) until the agency is known, so out-of-order events lose nothing and invent nothing.
CREATE TABLE intelligence_booking_attribution (
    booking_id          uuid          PRIMARY KEY,            -- a value from the events (no FK)
    account_id          uuid,                                 -- learned from BookingConfirmed (null until then)
    -- running buffered totals observed from events
    pending_subsidy_brl numeric(18,2) NOT NULL DEFAULT 0,
    pending_gap_brl     numeric(18,2) NOT NULL DEFAULT 0,
    position_closed     boolean       NOT NULL DEFAULT false,
    -- what has already been rolled into the agency accrual (incremental application, no double-count)
    applied_subsidy_brl numeric(18,2) NOT NULL DEFAULT 0,
    applied_gap_brl     numeric(18,2) NOT NULL DEFAULT 0,
    volume_counted      boolean       NOT NULL DEFAULT false,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL
);

-- Per-agency running totals the PromoFxAdvisor reads (recomputable projection).
CREATE TABLE intelligence_agency_fx_accrual (
    account_id          uuid          PRIMARY KEY,            -- a value (no cross-module FK)
    accrued_subsidy_brl numeric(18,2) NOT NULL DEFAULT 0,
    realized_gap_brl    numeric(18,2) NOT NULL DEFAULT 0,
    volume_attracted    bigint        NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL
);
