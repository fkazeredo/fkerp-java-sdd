-- SPEC-0006 Booking: operational commitment with an explicit lifecycle and a locator.
-- quote_id and account_id are values (no cross-module FK) to preserve future extraction.
CREATE TABLE bookings (
    id             uuid PRIMARY KEY,
    quote_id       uuid         NOT NULL,
    account_id     uuid         NOT NULL,
    status         varchar(20)  NOT NULL,
    locator_origin varchar(20)  NOT NULL,
    locator_code   varchar(100) NOT NULL,
    pending_since  timestamptz,
    confirmed_at   timestamptz,
    cancel_reason  varchar(200),
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    created_by     varchar(100),
    updated_by     varchar(100),
    version        bigint       NOT NULL
);

-- BR3: a locator is unique per (origin, code).
CREATE UNIQUE INDEX ux_bookings_locator ON bookings (locator_origin, locator_code);
