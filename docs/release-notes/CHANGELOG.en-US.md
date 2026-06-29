# Changelog (en-US)

> 🌐 **Language / Idioma:** **English** · the detailed pt-BR notes live one file per version in this
> same folder ([`0.1.0.md`](0.1.0.md) … [`0.12.0.md`](0.12.0.md)).

Consolidated, English-language history of released versions. The per-version pt-BR files remain the
detailed source; this file is the stakeholder-facing en-US mirror. Versioning follows
[ADR 0015](../adr/0015-semantic-versioning-and-release-management.md) (SemVer `MAJOR.MINOR.PATCH`,
`0.y.z` pre-1.0; each delivered phase bumps the MINOR). Newest first.

---

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
