-- SPEC-0012 Point-clock crawler (slice 11a): the OPERATIONAL side of point integration, owned by the
-- People module (DL-0030). point_snapshots is the operational mirror collected from the vendor portal
-- (NOT the legal artifact — operational_only is always true, BR3); the AFD/AEJ signed file has NO table
-- here, it is a Document in the Compliance vault (SPEC-0008). point_crawl_runs is the job history (BR7),
-- including the dead-letter failure state (DL-0031). No cross-module FK: source_ref/period_ref are values.
-- Idempotency by (source_ref, period_ref) — re-collecting a period does not duplicate (BR5).

CREATE TABLE point_snapshots (
    id               uuid         PRIMARY KEY,
    source_ref       varchar(80)  NOT NULL,           -- the REP/branch reference (e.g. REP-FILIAL-SP)
    period_ref       varchar(7)   NOT NULL,           -- YYYY-MM, the collected period
    operational_only boolean      NOT NULL DEFAULT true, -- always true: never a legal document (BR3)
    payload_ref      varchar(120) NOT NULL,           -- opaque ref to the captured mirror (via FileStorage)
    marks            integer      NOT NULL DEFAULT 0,  -- number of punches in the mirror (operational)
    collected_at     timestamptz  NOT NULL,
    created_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL,
    CONSTRAINT uq_point_snapshots_source_period UNIQUE (source_ref, period_ref) -- idempotency (BR5)
);

-- Execution history of the crawl job (BR7): one row per run, with attempts and the failure state.
CREATE TABLE point_crawl_runs (
    id             uuid         PRIMARY KEY,
    source_ref     varchar(80)  NOT NULL,
    period_ref     varchar(7),                         -- null when the run failed before resolving it
    started_at     timestamptz  NOT NULL,
    finished_at    timestamptz,
    status         varchar(20)  NOT NULL,              -- RUNNING | SUCCEEDED | RETRY_SCHEDULED | DEAD_LETTER
    attempts       integer      NOT NULL DEFAULT 1,
    items          integer,                            -- collected marks on success
    failures       integer,
    failure_class  varchar(30),                        -- TIMEOUT | UNAVAILABLE | AUTHENTICATION_FAILED | ...
    correlation_id varchar(64)
);

CREATE INDEX ix_point_crawl_runs_status ON point_crawl_runs (status);
CREATE INDEX ix_point_crawl_runs_source_ref ON point_crawl_runs (source_ref);
CREATE INDEX ix_point_crawl_runs_started_at ON point_crawl_runs (started_at);
