-- SPEC-0009 (DL-0018): activate the dormant INTEGRATED branch of the Quote (SPEC-0005 redesign 7.6).
-- An INTEGRATED quote trusts a closed external price and is NOT recomposed: it has no FX rate, no
-- two-sided commission and no markup. So the MANUAL-only composition columns become nullable, and the
-- MANUAL integrity ("compose freezes the whole provenance") moves to the domain factory Quote.compose
-- (which still fills all of them). A new source_offer_id links the quote to its SourcedOffer (the
-- provenance), as a value (no cross-module FK).
ALTER TABLE quotes ADD COLUMN source_offer_id uuid;

ALTER TABLE quotes ALTER COLUMN currency_pair         DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN fx_rate               DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN rate_id               DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN base_converted_amount DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN supplier_pct          DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN agent_pct             DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN supplier_commission   DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN agent_commission      DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN spread                DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN spread_negative       DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN markup_pct            DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN markup_amount         DROP NOT NULL;
ALTER TABLE quotes ALTER COLUMN markup_source         DROP NOT NULL;
