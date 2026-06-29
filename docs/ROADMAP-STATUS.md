# Roadmap Status — Phase & Slice Control

> **Purpose.** Single source of truth for *what is done and what is left* in the
> ERP Acme Travel build. Update this file at the end of every slice/phase (before
> merging to `develop`). It mirrors the phases of [ROADMAP.md](ROADMAP.md) and the
> specs in [specs/](specs/). Prose in English by request; code identifiers stay in
> English per project convention.

**Legend:** ✅ Complete (implemented, `./mvnw verify` green, merged) · 🟡 In progress ·
⬜ Not started · ⛔ Blocked.

## Execution log

> Timeline of autonomous execution runs (date/time in America/Sao_Paulo, UTC-03:00).
> Append one row per run; keep newest at the bottom. This is the project's run-control record.

| Phase | Started | Finished | Outcome |
|---|---|---|---|
| 0 — Foundation | 2026-06-29 03:57 (-03:00) | 2026-06-29 04:57 (-03:00) | ✅ Green: `./mvnw verify` 12 tests, `docker compose up` → health UP, frontend 4 tests, released `0.1.0`. |

A phase is **Complete** only when every slice's acceptance criteria are tested and
passing, the architecture gates (ArchUnit + Spring Modulith + Spotless/Checkstyle)
are green, docs are updated, and the work is merged to `develop` (and released).

## Phase overview

| Phase | Name | Spec(s) | Status | Notes |
|---|---|---|---|---|
| **0** | Foundation (walking skeleton + Event Storming) | SPEC-0001 | ✅ Complete | Released `0.1.0` (tag). See slice detail below. |
| **1** | Manual commercial core | SPEC-0002…0007 | ⬜ Not started | Accounts, Exchange, Commissioning, Quoting (keystone), Booking, Reconciliation. Needs ADR 0014. |
| **2** | Minimal compliance | SPEC-0008 (+ Finance seam 0015) | ⬜ Not started | Document vault + mandatory attachment + monthly-close veto + retention. |
| **3** | First real integration (ACL) | SPEC-0009 | ⬜ Not started | Quote site, INTEGRATED branch. |
| **4** | Cancellation + merchant trap | SPEC-0010 | ⬜ Not started | Policy as object + ALL_SALES_FINAL trap + no-show. |
| **5** | Exchange exposure + reports | SPEC-0011 | ⬜ Not started | Subsidy × drift, book position, first FX reports. |
| **6** | Point-clock crawler | SPEC-0012 | ⬜ Not started | Operational snapshot for People + signed AFD/AEJ for Compliance. |
| **7** | Intelligence (DSS) | SPEC-0013 | ⬜ Not started | OverrideNudge + PromoFxAdvisor. |
| **8+** | Support & generic contexts | SPEC-0014…0025 | ⬜ Not started | CommercialPolicy, Finance, Billing, Payout, AfterSales, Marketing, Portfolio, Assets, People, Platform, Identity, Admin. |

## Phase 0 — slice detail

| Slice | Deliverable | Status |
|---|---|---|
| Slice 0 | Modular-monolith skeleton (`com.fksoft`, 3 layers), Postgres via docker-compose, Flyway baseline, `GlobalExceptionHandler`/`ApiErrorResponse`/`HttpErrorMapping`/`PageResponse`, `UserContextProvider` dev stub, i18n, correlation id, `GET /api/system/health` (readiness checks DB), ArchUnit + Spring Modulith green, minimal CI, Angular health screen | ✅ Complete |
| Slice 0 | `docs/event-storming.md` (Portal de Experiências end-to-end sale) | ✅ Complete |

**Phase 0 exit criteria** (from SPEC-0001 Acceptance Criteria):
- [x] `cd backend && ./mvnw verify` green with Docker up (incl. ArchUnit + Modulith).
- [x] `docker-compose up` brings up app + db; `GET /api/system/health` returns `UP`.
- [x] Angular screen shows health OK (and the error state when backend is down) — component tests cover loading/success/error.
- [x] Minimal CI green (backend + frontend build/tests, lint, `flyway validate`) — workflow added; each step run locally.
- [x] `docs/event-storming.md` exists with the Portal de Experiências flow and boundaries.

## Open architectural debts carried forward

| Item | Owner phase | Tracked in |
|---|---|---|
| ~~ADR 0014 (initial module set & order) not yet written~~ → **written by owner** | resolved | [ADR 0014](adr/0014-initial-modules-and-slice-order.md), [DL-0005](decision-log/DL-0005-adr-0014-ausente-adiar-fase-1.md) |
| PrimeNG + Tailwind not yet added (Angular UI libs) | Phase 1 (SPEC-0002 first real screen) | [DL-0003](decision-log/DL-0003-stack-frontend-fase-0.md) |
| Spring Boot 3.5 → 4.x upgrade | Future (own ADR) | [DL-0002](decision-log/DL-0002-stack-versoes-backend.md) |

## How to update this file

1. When a slice goes green and is merged to `develop`, flip its row to ✅ and tick
   the matching exit-criteria checkboxes.
2. When all slices of a phase are ✅ and the release tag is cut, flip the phase to ✅.
3. Keep the "Open architectural debts" table current — move items out when resolved.
