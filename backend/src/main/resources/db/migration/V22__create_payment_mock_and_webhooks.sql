-- SPEC-0017 / ADR 0006 / DL-0048: the traceable mock payment gateway with an asynchronous webhook.
-- mock_payout_jobs holds the requested payments the mock will later confirm/fail by POSTing a signed
-- webhook (the async leg). processed_payout_webhooks is the idempotency ledger of the inbound webhook
-- handler: a re-delivered callback for the same (payout, installment, provider_ref) is a no-op
-- (state-check + this UNIQUE), so a duplicate webhook never double-confirms or double-pays (BR3).
CREATE TABLE mock_payout_jobs (
    id             uuid          PRIMARY KEY,
    payout_id      uuid          NOT NULL,        -- value ref to the payout (no FK across the boundary)
    installment_seq int          NOT NULL,
    provider_ref   varchar(100)  NOT NULL,        -- the mock provider's payment reference
    outcome        varchar(20)   NOT NULL,        -- SUCCEEDED | FAILED (the chosen async outcome)
    deliver_after  timestamptz   NOT NULL,        -- when the webhook may be delivered (the async delay)
    delivered      boolean       NOT NULL DEFAULT false,
    created_at     timestamptz   NOT NULL,
    CONSTRAINT ux_mock_payout_job_provider_ref UNIQUE (provider_ref)
);

CREATE INDEX ix_mock_payout_job_undelivered ON mock_payout_jobs (delivered, deliver_after);

CREATE TABLE processed_payout_webhooks (
    id              uuid          PRIMARY KEY,
    payout_id       uuid          NOT NULL,
    installment_seq int           NOT NULL,
    provider_ref    varchar(100)  NOT NULL,
    outcome         varchar(20)   NOT NULL,
    processed_at    timestamptz   NOT NULL,
    CONSTRAINT ux_processed_payout_webhook UNIQUE (payout_id, installment_seq, provider_ref)
);
