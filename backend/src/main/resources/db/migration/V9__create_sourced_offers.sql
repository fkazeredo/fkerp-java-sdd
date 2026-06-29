-- SPEC-0009 Sourcing: the provenance of an offer — free-text product, base price, origin and
-- integration level, plus an optional external reference. Free text is a valid offer (BR1); no
-- structured catalog is required. external_ref is a plain value (e.g. an external quotation id);
-- there is no cross-module FK here.
CREATE TABLE sourced_offers (
    id                uuid          PRIMARY KEY,
    product_text      varchar(500)  NOT NULL,
    base_amount       numeric(18, 2) NOT NULL,
    base_currency     varchar(3)    NOT NULL,
    origin            varchar(30)   NOT NULL,
    integration_level varchar(20)   NOT NULL,
    external_ref      varchar(100),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    created_by        varchar(100),
    updated_by        varchar(100),
    version           bigint        NOT NULL
);
