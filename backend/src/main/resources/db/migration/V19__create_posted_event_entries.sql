-- SPEC-0015 Finance (full, DL-0041): idempotency ledger for event-driven AP/AR posting.
-- When a Booking charge event (CancellationCharged / NoShowCharged / MerchantObligationIncurred) is
-- consumed and posted as a LedgerEntry, a row is written here in the SAME transaction. The UNIQUE
-- (source_ref, charge_kind) is the business idempotency key: a re-delivered event finds the row (or
-- violates the constraint on a race) and is a no-op, so the same fact never double-posts. source_ref
-- is a value reference (the booking id, as text) — no cross-module FK.
CREATE TABLE posted_event_entries (
    id          uuid         PRIMARY KEY,
    source_ref  varchar(100) NOT NULL,
    charge_kind varchar(40)  NOT NULL,
    entry_id    uuid         NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT uq_posted_event_entries UNIQUE (source_ref, charge_kind)
);
