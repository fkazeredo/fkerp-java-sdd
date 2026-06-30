-- SPEC-0021 Assets (internal patrimony): the Acme's own equipment, software licenses and other
-- goods, with a basic lifecycle (ACTIVE/RETIRED) and the value links the rest of the system needs.
--
-- All references to other contexts are BY VALUE (never a cross-context FK — Modulith): document_id
-- (Compliance) and finance_entry_id (Finance) are uuid values, not FKs; supplier_ref is a string
-- value. There is NO depreciation/maintenance/resale-stock here (DL-0065).
--
--   assets   a piece of internal patrimony (BR1). A SOFTWARE_LICENSE must carry expires_at (enforced
--            in the domain). Retirement audit (retired_at/by, retirement_reason) is inline (BR4 /
--            DL-0068); expiry_signaled_at makes the license-expiry alert idempotent (DL-0066).

CREATE TABLE assets (
    id                uuid           PRIMARY KEY,
    type              varchar(30)    NOT NULL,        -- EQUIPMENT | SOFTWARE_LICENSE | OTHER
    identifier        varchar(200)   NOT NULL,        -- identification/description
    status            varchar(20)    NOT NULL,        -- ACTIVE | RETIRED
    acquisition_date  date           NOT NULL,
    acquisition_cost  numeric(18,2)  NOT NULL,        -- Money amount
    currency          varchar(3)     NOT NULL,        -- Money currency (ISO 4217)
    expires_at        date,                            -- license expiry (required for SOFTWARE_LICENSE)
    supplier_ref      varchar(200),                    -- supplier reference (VALUE)
    document_id       uuid,                            -- Compliance document id (VALUE, not an FK)
    finance_entry_id  uuid,                            -- Finance cost ledger entry id (VALUE, not an FK)
    expiry_signaled_at timestamptz,                    -- DL-0066: expiry alert raised once per license
    retired_at        timestamptz,                     -- BR4 retirement audit
    retired_by        varchar(100),
    retirement_reason varchar(500),
    created_at        timestamptz    NOT NULL,
    updated_at        timestamptz    NOT NULL,
    created_by        varchar(100),
    updated_by        varchar(100),
    version           bigint         NOT NULL
);

-- Supports the listing filters by type and/or status (SPEC-0021 GET /api/assets?type=&status=).
CREATE INDEX ix_assets_type_status ON assets (type, status);

-- Supports the license-expiry sweep (DL-0066): not-yet-signaled, active licenses with a due
-- expires_at. Partial index keeps it tight to the candidate set.
CREATE INDEX ix_assets_license_expiry
    ON assets (expires_at)
    WHERE type = 'SOFTWARE_LICENSE' AND status = 'ACTIVE' AND expiry_signaled_at IS NULL
          AND expires_at IS NOT NULL;
