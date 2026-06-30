-- SPEC-0020 Portfolio (representation): the brands the Acme represents, the representation contracts
-- (validity + Compliance document, with an expiry alert) and the goals per brand with the
-- realized-vs-goal projection over sales events.
--
-- All tables reference other contexts BY VALUE (never a cross-context FK — Modulith): brand_ref is a
-- string value; document_id, booking_id and source_ref are values, not FKs.
--
--   represented_brands       a represented brand/supplier (BR1), unique brand_ref, ACTIVE/INACTIVE.
--   representation_contracts  the right to sell a brand (BR2): validity, Compliance document_id
--                             (value), reference terms (jsonb), and expiring_signaled_at (DL-0063,
--                             idempotency of the expiry alert).
--   brand_goals              VOLUME/REVENUE target per (brand, period); UNIQUE (brand, period, metric).
--   brand_sale_attributions  intake sale->brand (DL-0062): UNIQUE (booking_id) — links a booking to a
--                            represented brand so the projection can group by brand.
--   brand_realized           idempotent projection of sales events (DL-0062): UNIQUE (metric,
--                            source_ref) so a re-delivered BookingConfirmed/SpreadRealized never
--                            double-counts.

-- Represented brands (BR1). Optimistic locking via version.
CREATE TABLE represented_brands (
    id           uuid          PRIMARY KEY,
    brand_ref    varchar(120)  NOT NULL,            -- brand/supplier identifier (VALUE), unique
    display_name varchar(200)  NOT NULL,
    status       varchar(20)   NOT NULL,            -- ACTIVE | INACTIVE
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    version      bigint        NOT NULL,
    CONSTRAINT ux_represented_brands_ref UNIQUE (brand_ref)
);

-- Representation contracts (BR2). document_id is a VALUE pointing at the Compliance vault, not an FK.
CREATE TABLE representation_contracts (
    id                   uuid          PRIMARY KEY,
    brand_ref            varchar(120)  NOT NULL,    -- covered brand (VALUE)
    valid_from           date          NOT NULL,
    valid_until          date,                       -- null = open-ended
    document_id          uuid,                       -- Compliance document id (VALUE)
    terms_json           jsonb,                      -- reference terms (not prices — BR6)
    expiring_signaled_at timestamptz,                -- DL-0063: alert raised once per contract
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    created_by           varchar(100),
    updated_by           varchar(100),
    version              bigint        NOT NULL
);

CREATE INDEX ix_representation_contracts_brand ON representation_contracts (brand_ref);
-- Supports the expiry sweep (DL-0063): not-yet-signaled contracts with a due validUntil.
CREATE INDEX ix_representation_contracts_expiry
    ON representation_contracts (valid_until)
    WHERE expiring_signaled_at IS NULL AND valid_until IS NOT NULL;

-- Brand goals (BR3): VOLUME (target_count) or REVENUE (target_amount, BRL). Unique per metric.
CREATE TABLE brand_goals (
    id            uuid          PRIMARY KEY,
    brand_ref     varchar(120)  NOT NULL,           -- targeted brand (VALUE)
    period        varchar(7)    NOT NULL,           -- YYYY or YYYY-MM
    metric        varchar(20)   NOT NULL,           -- VOLUME | REVENUE
    target_amount numeric(18,2),                     -- REVENUE target (BRL)
    target_count  integer,                           -- VOLUME target (count)
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    version       bigint        NOT NULL,
    CONSTRAINT ux_brand_goals UNIQUE (brand_ref, period, metric)
);

-- Sale->brand attribution intake (DL-0062): a booking is linked to a brand at most once.
CREATE TABLE brand_sale_attributions (
    id            uuid          PRIMARY KEY,
    booking_id    uuid          NOT NULL,           -- referenced booking (VALUE, not an FK)
    brand_ref     varchar(120)  NOT NULL,           -- attributed brand (VALUE)
    attributed_at timestamptz   NOT NULL,
    CONSTRAINT ux_brand_sale_attributions UNIQUE (booking_id)
);

CREATE INDEX ix_brand_sale_attributions_brand ON brand_sale_attributions (brand_ref);

-- Realized projection over sales events (DL-0062). Idempotent per (metric, source_ref): the same
-- BookingConfirmed (VOLUME, source_ref = bookingId) or SpreadRealized (REVENUE, source_ref = caseId)
-- contributes once. amount is BRL for REVENUE; count_inc is the VOLUME increment.
CREATE TABLE brand_realized (
    id          uuid          PRIMARY KEY,
    brand_ref   varchar(120)  NOT NULL,             -- the brand this contribution belongs to (VALUE)
    metric      varchar(20)   NOT NULL,             -- VOLUME | REVENUE
    source_ref  varchar(120)  NOT NULL,             -- the originating event key (booking/case id, VALUE)
    amount      numeric(18,2),                        -- REVENUE contribution (BRL)
    count_inc   integer,                              -- VOLUME contribution (count)
    occurred_at timestamptz   NOT NULL,
    CONSTRAINT ux_brand_realized UNIQUE (metric, source_ref)
);

CREATE INDEX ix_brand_realized_brand_metric ON brand_realized (brand_ref, metric);
