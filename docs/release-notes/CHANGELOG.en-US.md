# Changelog (en-US)

> 🌐 **Language / Idioma:** **English** · the detailed pt-BR notes live one file per version in this
> same folder ([`0.1.0.md`](0.1.0.md) … [`0.12.0.md`](0.12.0.md)).

Consolidated, English-language history of released versions. The per-version pt-BR files remain the
detailed source; this file is the stakeholder-facing en-US mirror. Versioning follows
[ADR 0015](../adr/0015-semantic-versioning-and-release-management.md) (SemVer `MAJOR.MINOR.PATCH`,
`0.y.z` pre-1.0; each delivered phase bumps the MINOR). Newest first.

---

## 0.16.0 — Phase 8h · Assets (SPEC-0021)

`assets` module (18th): the **internal-patrimony** context — the Acme's own equipment, software licenses
and other goods. A deliberately **lean** registry that ties an asset's cost (Finance) and document
(Compliance) together and alerts on expiring licenses — **not** a full asset-management system (no
depreciation, maintenance or resale stock; buy one if full management is needed — DL-0065). An `Asset`
has a type (EQUIPMENT | SOFTWARE_LICENSE | OTHER), an identifier, an ACTIVE/RETIRED status, the
acquisition date and cost (Money); a SOFTWARE_LICENSE **requires an `expiresAt`** (BR1, else a 400). The
acquisition document (Compliance) and cost ledger entry (Finance) are referenced **by value, never an
FK** (BR2). Retirement is **audited (who/when/reason) and terminal** — retiring twice is a 409,
preserving the first audit (BR4/DL-0068). Assets is **a leaf producer** (DL-0067): it publishes
`AssetRegistered` and `AssetLicenseExpiring` in-process but wires no Finance/Intelligence consumers
(posting a patrimony cost is a business rule the spec does not define). A controlled-clock job flags
active licenses within the 30-day horizon and publishes `AssetLicenseExpiring` **once per license**
(idempotent, an alert that never blocks — DL-0066); `GET /api/assets?expiringWithinDays=N` is the ad-hoc
listing. Assets is **patrimony, not a product** — it never prices a sale (BR5). V26 migration.
DL-0064…0068 (none is Low-confidence + Costly: Q2 was settled by the architect's recommendation —
two contexts). `./mvnw verify` green: 388 tests, ArchUnit 14, Modulith acyclic (18th module), 0
Checkstyle.

## 0.15.0 — Phase 8g · Portfolio (SPEC-0020)

`portfolio` module (17th): the **representation** context — what the Acme represents commercially (it is
a GSA): the **brands/suppliers**, the **representation contracts** that grant the right to sell, and the
**goals per brand** with realized-vs-goal tracking. A brand has a unique `brandRef` (a duplicate is a
translated 409, never a raw constraint) and an ACTIVE/INACTIVE status; a contract holds the validity
window, the Compliance document referenced by value (never an FK) and reference terms (jsonb, not
prices). Selling a brand **without an in-force contract only alerts**, it never blocks (DL-0061); an
expiring contract is signalled **once per contract** by a controlled-clock job that publishes
`RepresentationExpiring` (DL-0063). Goals are VOLUME or REVENUE (BRL), unique per (brand, period,
metric); the **realized is a read-model projection over sales events** — `BookingConfirmed` (VOLUME) and
`SpreadRealized` (REVENUE) matched to a brand by a Portfolio-owned sale-attribution intake, **without
changing the sale event**, idempotent per event (DL-0062, Low-confidence: which field identifies the
brand on a sale is a business decision). Portfolio **never prices, computes commission, nor commands the
sale** — a new ArchUnit rule gives BR6 teeth. V25 migration. DL-0060…0063.

## 0.14.0 — Phase 8f · Marketing (SPEC-0019)

`marketing` module (16th): B2B marketing with **LGPD consent as a first-class citizen**. Consent is an
append-only log (current state = the latest row per subject+purpose; revoke/re-consent append rows,
single opt-in in v1). A campaign sends **only** to subjects with a GRANTED consent — the rest are
suppressed and counted — through a `NewsletterSender` ACL (traceable mock; the provider DTO never
crosses into the domain), idempotently per recipient. Segments use validated criteria over existing
data (closed catalog, minimization). Attribution links a campaign code to a booking and, on
`BookingConfirmed`, publishes `CampaignConverted` for the DSS (`BookingConfirmed` is unchanged). LGPD
erasure removes marketing PII while preserving an anonymized revocation tombstone (so the subject is
never silently re-included), keeping attributions and other legal bases intact. Not a CRM — the
consent/attribution layer (full CRM = buy). V24 migration. DL-0055…0059 (DL-0058 is Low-confidence /
Costly-to-reverse: the LGPD erasure scope is a DPO decision and the purge is destructive).

## 0.13.0 — Phase 8e · AfterSales (SPEC-0018)

`aftersales` module (15th): the post-sale context — support cases with a lifecycle state machine and
**governed SLA deadlines** (resolved from CommercialPolicy: 24h first response / 72h resolution / 48h
cancellation-refund; an SLA breach is a non-blocking alert). Resolving a case orchestrates the owners:
a refund is forwarded to Payout (idempotently, never cancelling the supplier obligation — the merchant
trap holds) and a cancellation to Booking; it accrues the cost-to-serve the DSS uses. DL-0052…0054.

## 0.12.0 — Phase 8d · Payout (SPEC-0017)

Supplier payout / settlement / refund with cents-exact installments. Payment ACL via an idempotent
webhook (ADR 0006); `SupplierSettled` posts to Finance exactly once; payment receipt produced. The
"merchant trap" is preserved (charges never net themselves out). DL-0048…0051.

## 0.11.0 — Phase 8c · Billing (SPEC-0016)

`billing` module (13th): commission NFS-e (base = the commission, not the gross) + ISS by tax regime
(swappable strategy) + NFS-e ACL (mock); posted to Finance via event; the file in the vault satisfies
the requirement. DL-0044…0047.

## 0.10.0 — Phase 8b · Finance (full) (SPEC-0015)

Automatic AP/AR entries per event (idempotent) + per-currency trial balance. Buy-vs-build reaffirmed
(cash book, not a full GL). The close-veto regression stays green. DL-0041…0043.

## 0.9.0 — Phase 8a · CommercialPolicy (SPEC-0014)

Precedence engine (Directive > Promotion > Contract > Policy > Default) + the markup stub graduated
without breaking Quoting (the `MarkupProvider` contract intact; `source` = the winning level).
DL-0037…0040.

## 0.8.0 — Phase 7 · Intelligence / DSS (SPEC-0013)

`intelligence` module (12th) + `PromoFxAdvisor` + `OverrideNudge` behind a flag; "advises, never
commands" enforced by an ArchUnit rule + a teeth test; LLM `InsightNarrator` port (stub). DL-0034…0036.

## 0.7.0 — Phase 6 · Point-clock crawler (SPEC-0012)

`people` module (11th) + crawler with circuit-breaker / dead-letter + ingestion of the signed AFD/AEJ
into the vault (5-year retention). DL-0029…0033.

## 0.6.0 — Phase 5 · Exchange exposure + reports (SPEC-0011)

Market rate + subsidy × drift (`FxPosition`) + reports (`LiveExposure` / `PromoFxResult`, 2% drift
alert). DL-0025…0028. A Modulith cycle was caught by the gate and fixed (reconciliation → exchange).

## 0.5.0 — Phase 4 · Cancellation + merchant trap (SPEC-0010)

`CancellationPolicy` + the merchant trap (charges never net out) + no-show. DL-0020…0024.

## 0.4.0 — Phase 3 · First real integration / ACL (SPEC-0009)

Sourcing + the `INTEGRATED` branch (trusts the external price, no recompose) + an inbound webhook ACL
(HMAC, idempotent, external DTO confined to `infra.integration`). DL-0016…0019.

## 0.3.0 — Phase 2 · Minimal compliance (SPEC-0008 + Finance seam SPEC-0015)

Finance AP/AR seam + period close + Compliance vault + mandatory attachment + the monthly-close veto +
retention. DL-0012…0015.

## 0.2.1 — Phase 1 · Commercial core (front-end)

5 Angular screens (Accounts / Exchange / Quoting / Booking / Reconciliation) + navigation; lint / test
/ build green. Phase 1 closed end-to-end.

## 0.2.0 — Phase 1 · Manual commercial core (SPEC-0002…0007)

7 Modulith modules, 6 slices: Accounts, Exchange, Commissioning, Quoting, Booking, Reconciliation, on
the shared `Money` kernel.

## 0.1.0 — Phase 0 · Foundation (SPEC-0001)

Walking skeleton: boots, connects to Postgres, `/api/system/health`, minimal Angular screen, ArchUnit +
Spring Modulith + CI green. Event Storming captured.
