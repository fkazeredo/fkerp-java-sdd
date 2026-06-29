-- SPEC-0019 Marketing: B2B campaigns governed by LGPD consent, segmentation over existing data,
-- newsletter dispatch via an ACL port, and campaign->booking attribution for the DSS.
--
-- Five tables, all referencing other contexts BY VALUE (never a cross-context FK — Modulith):
--   consents     append-only consent log (BR1/DL-0056): current state = latest row per
--                (subject_type, subject_id, purpose); revoke/re-consent = a NEW immutable row.
--   segments     segment definition with a VALIDATED criteria_json (jsonb, DL-0059): the jsonb is
--                validated in the domain against a closed catalog of allowed fields (minimization).
--   campaigns    a campaign over a segment, with a unique public `code` used for attribution.
--   campaign_sends  per-recipient idempotency of a dispatch (BR4): UNIQUE (campaign_id, recipient_ref).
--   attributions code->booking link (BR5/DL-0057): UNIQUE (campaign_code, booking_id).

-- Consent log (append-only). No version column: rows are write-once (the only mutation is the LGPD
-- erasure anonymizing PII in place — DL-0058). The current state is resolved by the latest row.
CREATE TABLE consents (
    id           uuid          PRIMARY KEY,
    subject_id   varchar(120)  NOT NULL,             -- subject id (VALUE: Accounts/agent id)
    subject_type varchar(20)   NOT NULL,             -- ACCOUNT | AGENT
    purpose      varchar(30)   NOT NULL,             -- NEWSLETTER
    legal_basis  varchar(30)   NOT NULL,             -- CONSENT | LEGITIMATE_INTEREST
    status       varchar(20)   NOT NULL,             -- GRANTED | REVOKED
    source       varchar(200),                       -- free-text origin (audit; anonymized on erasure)
    created_at   timestamptz   NOT NULL,
    created_by   varchar(100)
);

-- Resolve the current state (latest row) and the history cheaply.
CREATE INDEX ix_consents_subject_purpose
    ON consents (subject_type, subject_id, purpose, created_at DESC);

-- Segments: criteria as a validated jsonb (DL-0059). Optimistic locking via version.
CREATE TABLE segments (
    id            uuid          PRIMARY KEY,
    name          varchar(150)  NOT NULL,
    criteria_json jsonb         NOT NULL,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    version       bigint        NOT NULL
);

-- Campaigns: a campaign over a segment (by value), with a unique public attribution code.
CREATE TABLE campaigns (
    id          uuid          PRIMARY KEY,
    segment_id  uuid          NOT NULL,              -- referenced segment (VALUE, not an FK)
    code        varchar(60)   NOT NULL,              -- public attribution code (UTM), unique
    content_ref varchar(300),                        -- pointer to the external creative (not stored here)
    window_from date,
    window_to   date,
    status      varchar(20)   NOT NULL,              -- DRAFT | SENT
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    version     bigint        NOT NULL,
    CONSTRAINT ux_campaigns_code UNIQUE (code)
);

-- Per-recipient idempotency of a dispatch (BR4): a recipient is sent to at most once per campaign.
CREATE TABLE campaign_sends (
    campaign_id   uuid          NOT NULL,
    recipient_ref varchar(120)  NOT NULL,            -- recipient subject id (VALUE)
    sent_at       timestamptz   NOT NULL,
    CONSTRAINT ux_campaign_sends UNIQUE (campaign_id, recipient_ref)
);

CREATE INDEX ix_campaign_sends_campaign ON campaign_sends (campaign_id);

-- Attribution: code->booking link (BR5/DL-0057), idempotent per (code, booking).
CREATE TABLE attributions (
    id            uuid          PRIMARY KEY,
    campaign_code varchar(60)   NOT NULL,            -- the campaign's public code (VALUE)
    booking_id    uuid          NOT NULL,            -- referenced booking (VALUE, not an FK)
    converted     boolean       NOT NULL DEFAULT false, -- confirmed by BookingConfirmed (DL-0057)
    attributed_at timestamptz   NOT NULL,
    converted_at  timestamptz,
    CONSTRAINT ux_attributions UNIQUE (campaign_code, booking_id)
);

CREATE INDEX ix_attributions_code ON attributions (campaign_code);
CREATE INDEX ix_attributions_booking ON attributions (booking_id);
