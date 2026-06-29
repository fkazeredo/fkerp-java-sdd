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
| 1 — Manual commercial core | 2026-06-29 05:08 (-03:00) | 2026-06-29 05:20 (-03:00) | ⏹️ Preempted by direct owner request (ADR 0015 — versionamento/SemVer) before any business code. Phase reverted to ⬜ Not started; only the roadmap marker had been touched. |
| 1 — Manual commercial core (restart) | 2026-06-29 05:35 (-03:00) | 2026-06-29 06:55 (-03:00) | ✅ Backend green: `./mvnw verify` **82 tests**, 7 Modulith modules, 6 slices (SPEC-0002…0007) merged to `develop`, released **`0.2.0`**. Angular screens deferred to 0.2.x (carried debt). Supervisor loop switched 30m → 1h per owner request. |
| 1 — Manual commercial core (frontend) | 2026-06-29 07:40 (-03:00) | 2026-06-29 08:10 (-03:00) | ✅ Owner-directed: **5 telas Angular** (Accounts/Exchange/Quoting/Booking/Reconciliation) + nav; `npm` lint/test(**14**)/build verdes; released **`0.2.1`**. Fase 1 fechada ponta a ponta. |
| 2 — Minimal compliance | 2026-06-29 08:20 (-03:00) | 2026-06-29 09:05 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=2); supervisor **reverificou**: `./mvnw verify` **108 tests** verde, 0 Checkstyle. Finance seam + Compliance + veto de fechamento; released **`0.3.0`** (tag, main+develop). DL-0012…0015. |
| 3 — First real integration (ACL) | 2026-06-29 09:17 (-03:00) | _in progress_ | Supervisor loop (8b1087fe): sem 🟡 → próxima ⬜ = Fase 3; marcada 🟡; `RUN-PHASE` (FASE-ALVO=3) delegado a um subagente em background. |

A phase is **Complete** only when every slice's acceptance criteria are tested and
passing, the architecture gates (ArchUnit + Spring Modulith + Spotless/Checkstyle)
are green, docs are updated, and the work is merged to `develop` (and released).

## Phase overview

| Phase | Name | Spec(s) | Status | Notes |
|---|---|---|---|---|
| **0** | Foundation (walking skeleton + Event Storming) | SPEC-0001 | ✅ Complete | Released `0.1.0` (tag). See slice detail below. |
| **1** | Manual commercial core | SPEC-0002…0007 | ✅ Complete | Backend `0.2.0` (82 tests) + Angular screens `0.2.1` (14 tests). End-to-end: 6 contextos com tela (loading/empty/erro). |
| **2** | Minimal compliance | SPEC-0008 (+ Finance seam 0015) | ✅ Complete | Released `0.3.0` (tag). Finance AP/AR seam + period close, Compliance vault + mandatory attachment + **monthly-close veto** + retention. `./mvnw verify` 108 tests (9 Modulith modules). Telas: backend-first (UI follow-up). |
| **3** | First real integration (ACL) | SPEC-0009 | 🟡 In progress | Supervisor loop (8b1087fe) started 2026-06-29 09:17 (-03:00); RUN-PHASE delegated to a subagent. Quote site, INTEGRATED branch. |
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

## Phase 1 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 1 | SPEC-0002 | Accounts — conta comercial CNPJ/MEI/CPF (validação/unicidade/status) | ✅ Backend |
| 2 | SPEC-0003 | Exchange — taxa congelada append-only (Open-Host) + histórico | ✅ Backend |
| 3 | SPEC-0004 | Commissioning — comissão de duas pontas + spread (puro) + kernel `Money` | ✅ Backend |
| 4 | SPEC-0005 | Quoting (keystone) — composição + override com proveniência | ✅ Backend |
| 5 | SPEC-0006 | Booking — ciclo de vida + localizador + timeout 72h + eventos | ✅ Backend |
| 6 | SPEC-0007 | Reconciliation — esperado × realizado + ganho/perda cambial | ✅ Backend |
| — | 0002–0007 | **Telas Angular** dos contextos (Accounts/Exchange/Quoting/Booking/Reconciliation + nav) | ✅ `0.2.1` |

**Phase 1 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (82 tests; ArchUnit + 7 Modulith modules + Spotless/Checkstyle).
- [x] Migrações `V2`…`V6` aplicadas (Flyway) e validadas pelos testes de integração (Postgres real).
- [x] APIs REST + OpenAPI dos 6 contextos; erro estável `{code,message,fields}`; i18n pt-BR + fallback.
- [x] Merge em `develop`, release `0.2.0` (tag), merge em `main`.
- [x] **Telas Angular** dos contextos da Fase 1 (loading/empty/erro) — `npm lint`/`test` (14)/`build` verdes; release `0.2.1`.

## Phase 2 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 7a | SPEC-0015 | Finance seam — razão AP/AR + máquina de período (OPEN→CLOSING→CLOSED) com `CloseGuard` (porta) | ✅ |
| 7b | SPEC-0008 | Compliance — cofre `Document` (hash SHA-256 + retenção), anexo obrigatório, `DocumentRequirement`, `FileStorage` (porta + adapter de FS) | ✅ |
| 7c | SPEC-0008/0015 | **Veto de fechamento** ponta a ponta (lançamento sem documento exigido não fecha o mês) + job de retenção | ✅ |
| — | 0008/0015 | Telas Angular de Compliance/Finance | ⬜ Follow-up (backend-first) |

**Phase 2 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (108 tests; ArchUnit + 9 Modulith modules + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migrações `V7` (finance) e `V8` (compliance, + seed de requirements) aplicadas e validadas (Postgres real).
- [x] **Regra de ouro:** lançamento AP/AR sem o documento exigido **veta** o fechamento mensal (regressão e2e verde).
- [x] Merge em `develop`, release `0.3.0` (tag), merge em `main`; DL-0012…0015 registradas.
- [ ] Telas Angular de Compliance/Finance — follow-up (não exigidas para o veto/cofre operarem).

## Open architectural debts carried forward

| Item | Owner phase | Tracked in |
|---|---|---|
| ~~ADR 0014 (initial module set & order) not yet written~~ → **written by owner** | resolved | [ADR 0014](adr/0014-initial-modules-and-slice-order.md), [DL-0005](decision-log/DL-0005-adr-0014-ausente-adiar-fase-1.md) |
| ~~**Telas Angular da Fase 1**~~ → **entregues** em `0.2.1` (5 telas + nav; 14 testes) | resolved | [release-notes/0.2.1.md](release-notes/0.2.1.md) |
| PrimeNG + Tailwind not yet added (telas atuais em CSS puro) | Future (quando a UI exigir) | [DL-0003](decision-log/DL-0003-stack-frontend-fase-0.md) |
| Spring Boot 3.5 → 4.x upgrade | Future (own ADR) | [DL-0002](decision-log/DL-0002-stack-versoes-backend.md) |

## How to update this file

1. When a slice goes green and is merged to `develop`, flip its row to ✅ and tick
   the matching exit-criteria checkboxes.
2. When all slices of a phase are ✅ and the release tag is cut, flip the phase to ✅.
3. Keep the "Open architectural debts" table current — move items out when resolved.
