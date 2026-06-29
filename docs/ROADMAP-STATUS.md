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
| 3 — First real integration (ACL) | 2026-06-29 09:17 (-03:00) | 2026-06-29 10:05 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=3); supervisor **reverificou**: `./mvnw verify` **135 tests** verde, 0 Checkstyle, V9–V11. Sourcing + ramo INTEGRATED + webhook ACL (HMAC, DTO externo não cruza p/ domínio). Released **`0.4.0`**. DL-0016…0019 (**DL-0017 Conf. Baixa**). |
| 4 — Cancellation + merchant trap | 2026-06-29 10:17 (-03:00) | 2026-06-29 11:00 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=4); supervisor **reverificou**: `./mvnw verify` **157 tests** verde, 0 Checkstyle, V12–V13. CancellationPolicy + armadilha do merchant (cobranças nunca se anulam) + no-show. Released **`0.5.0`**. DL-0020…0024 (**DL-0024 Rev. Cara**). |
| 5 — Exchange exposure + reports | 2026-06-29 11:17 (-03:00) | 2026-06-29 12:00 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=5); supervisor **reverificou**: `./mvnw verify` **179 tests** verde, 0 Checkstyle, V14–V15. Taxa de mercado + subsídio×drift (`FxPosition`) + relatórios (`LiveExposure`/`PromoFxResult`, alerta de drift 2%). Released **`0.6.0`**. DL-0025…0028. Ciclo Modulith pego pelo gate e corrigido (reconciliation→exchange). |
| 6 — Point-clock crawler | 2026-06-29 12:17 (-03:00) | 2026-06-29 13:00 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=6); supervisor **reverificou**: `./mvnw verify` **206 tests** verde, 0 Checkstyle, V16. Módulo `people` (11º) + crawler com **disjuntor/dead-letter** + ingestão de **AFD/AEJ assinado** no cofre (retenção 5 anos). Released **`0.7.0`**. DL-0029…0033 (**DL-0029 Conf. Baixa + Rev. Cara**: tipo de REP). |
| 7 — Intelligence (DSS) | 2026-06-29 13:17 (-03:00) | _in progress_ | Supervisor loop (8b1087fe): sem 🟡 → próxima ⬜ = Fase 7; marcada 🟡; `RUN-PHASE` (FASE-ALVO=7) delegado a um subagente em background. |

A phase is **Complete** only when every slice's acceptance criteria are tested and
passing, the architecture gates (ArchUnit + Spring Modulith + Spotless/Checkstyle)
are green, docs are updated, and the work is merged to `develop` (and released).

## Phase overview

| Phase | Name | Spec(s) | Status | Notes |
|---|---|---|---|---|
| **0** | Foundation (walking skeleton + Event Storming) | SPEC-0001 | ✅ Complete | Released `0.1.0` (tag). See slice detail below. |
| **1** | Manual commercial core | SPEC-0002…0007 | ✅ Complete | Backend `0.2.0` (82 tests) + Angular screens `0.2.1` (14 tests). End-to-end: 6 contextos com tela (loading/empty/erro). |
| **2** | Minimal compliance | SPEC-0008 (+ Finance seam 0015) | ✅ Complete | Released `0.3.0` (tag). Finance AP/AR seam + period close, Compliance vault + mandatory attachment + **monthly-close veto** + retention. `./mvnw verify` 108 tests (9 Modulith modules). Telas: backend-first (UI follow-up). |
| **3** | First real integration (ACL) | SPEC-0009 | ✅ Complete | Released `0.4.0` (tag). Sourcing + ramo `INTEGRATED` (confia no preço externo, sem recompor) + **webhook ACL de entrada** (HMAC, idempotente, DTO externo só em `infra.integration`). `./mvnw verify` 135 tests (10 Modulith modules). |
| **4** | Cancellation + merchant trap | SPEC-0010 | ✅ Complete | Released `0.5.0` (tag). `CancellationPolicy` (STANDARD/ALL_SALES_FINAL/CUSTOM, janelas, costBearer) + `NoShowPolicy` + **armadilha do merchant** (reembolso ao cliente e cobrança do portal não se anulam). Vive no módulo `booking`. `./mvnw verify` 157 tests. |
| **5** | Exchange exposure + reports | SPEC-0011 | ✅ Complete | Released `0.6.0` (tag). Taxa de mercado + decomposição **subsídio × drift** (`FxPosition`), **posição agregada do livro** (`LiveExposure`) com alerta de drift (2%) e relatório `PromoFxResult`. Estende `exchange`. `./mvnw verify` 179 tests. |
| **6** | Point-clock crawler | SPEC-0012 | ✅ Complete | Released `0.7.0` (tag). `people` (11º módulo) + `PointClockCrawler` (ACL, **disjuntor + retry/dead-letter**, idempotente, não escreve no núcleo) + ingestão de **AFD/AEJ assinado** no cofre Compliance (retenção 5 anos). `./mvnw verify` 206 tests. **Q6 (REP) = Conf. Baixa — confirmar.** |
| **7** | Intelligence (DSS) | SPEC-0013 | 🟡 In progress | Supervisor loop (8b1087fe) started 2026-06-29 13:17 (-03:00); RUN-PHASE delegated to a subagent. OverrideNudge + PromoFxAdvisor (aconselha, nunca comanda). |
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

## Phase 3 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 8a | SPEC-0009 | Sourcing — `SourcedOffer` (oferta externa em texto livre) + API; módulo `sourcing` (10º Modulith); `V9` | ✅ |
| 8b | SPEC-0009/0005 | Quoting **ramo INTEGRATED** — confia no preço externo (`suggested == applied`, sem motor de sugestão, override recusado 409); `V10` | ✅ |
| 8c | SPEC-0009 | **Webhook ACL de entrada** (`/api/integration/quotation-site/inbound`) — assinatura HMAC-SHA256, idempotente, DTO externo **só** em `infra.integration` (regra ArchUnit garante que não cruza p/ domínio); `V11` | ✅ |

**Phase 3 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (135 tests; ArchUnit[7] + 10 Modulith modules + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migrações `V9`/`V10`/`V11` aplicadas e validadas (Postgres real).
- [x] **ACL real:** porta no domínio + adapter em `infra.integration`; ramo `INTEGRATED` ativado sem recompor; idempotência por `externalQuotationId`.
- [x] Merge em `develop`, release `0.4.0` (tag), merge em `main`; DL-0016…0019 registradas (**DL-0017 Confiança Baixa** — decisão de negócio a revisitar).
- [ ] Tela Angular — n/a nesta fase (integração máquina-a-máquina).

## Phase 4 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 9a | SPEC-0010 | `CancellationPolicy` como objeto (tipo + `PenaltyWindow` + `CostBearer` + `NoShowPolicy` + `Charge`); cálculo de multa por janela; admin por `scopeRef`; `V12` | ✅ |
| 9b | SPEC-0010 | Política **congelada na confirmação** (snapshot, BR1); `POST /api/bookings/{id}/cancel` rico → `CancellationResult`; eventos `CancellationCharged`/`MerchantObligationIncurred`; `V13` | ✅ |
| 9c | SPEC-0010 | `NoShowPolicy` + `POST /api/bookings/{id}/no-show` com dispensa por prova; evento `NoShowCharged` | ✅ |

**Phase 4 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (157 tests; ArchUnit + 10 Modulith modules + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migrações `V12`/`V13` aplicadas e validadas (Postgres real).
- [x] **Armadilha do merchant** provada por regressão (unit + e2e): cancelamento `ALL_SALES_FINAL` gera **reembolso ao cliente E cobrança do fornecedor/portal** que **não se anulam**; janelas testadas com relógio controlado.
- [x] Merge em `develop`, release `0.5.0` (tag), merge em `main`; DL-0020…0024 (**DL-0024 Reversibilidade=Cara**: cobranças são fatos distintos, nunca compensados).
- [ ] Tela Angular — backend-first (follow-up).

## Phase 5 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 10a | SPEC-0011 | `MarketRate` (série append-only) + `MarketRateProvider` (porta) + `POST /api/exchange/market-rates`; `V14` | ✅ |
| 10b | SPEC-0011 | `FxPosition` — **subsídio** (intencional) × **drift** (risco), mark-to-market e `realizedDrift`/`totalGap` no settlement; dirigido por Reconciliation (acíclico); `V15` | ✅ |
| 10c | SPEC-0011 | Relatórios read-model — `GET /api/exchange/exposure` (`LiveExposure` + alerta de drift 2%) e `…/reports/promo-fx` (`PromoFxResult`) | ✅ |

**Phase 5 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (179 tests; ArchUnit + 10 Modulith modules + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migrações `V14`/`V15` aplicadas e validadas (Postgres real).
- [x] **subsídio × drift** com números exatos (HALF_UP, relógio/feed controlado) + regressão `totalGap == −fxGainLoss` vs SPEC-0007; exposição agregada testada sobre múltiplas posições.
- [x] Merge em `develop`, release `0.6.0` (tag), merge em `main`; DL-0025…0028 (sem Confiança Baixa / Rev. Cara).
- [ ] Métricas Prometheus — por ora **log de evento de negócio** (sem `MeterRegistry`, padrão das Fases 1–5); follow-up. Tela Angular — backend-first.

## Phase 6 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 11a | SPEC-0012 | Módulo `people` (11º Modulith) — `PointSnapshot` (só operacional, idempotente por `(sourceRef, periodRef)`) + histórico `PointCrawlRun`; `V16` | ✅ |
| 11b | SPEC-0012 | `PointClockCrawler` (ACL em `infra.integration`) — **disjuntor** (CLOSED/OPEN/HALF_OPEN) + retry/**dead-letter**, mock com injeção de falha; 2 regras ArchUnit (DTO externo fora do domínio; crawler não escreve no núcleo) | ✅ |
| 11c | SPEC-0012 | Ingestão de **AFD/AEJ assinado** (`Pkcs7AfdSignatureVerifier`, CAdES/PKCS#7 + checagem de adulteração) → cofre Compliance (`retentionUntil=+5y`); inválido → 400, nada guardado | ✅ |

**Phase 6 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (206 tests; ArchUnit[9] + 11 Modulith modules + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migração `V16` aplicada e validada (Postgres real).
- [x] **Resiliência testada:** disjuntor abre e curto-circuita sem bater no portal; falha persistente → `DEAD_LETTER` + evento, sem snapshot falso; ingestão idempotente; AFD adulterado rejeitado.
- [x] **Não escreve no núcleo** (teste de fronteira) + DTO externo só em `infra.integration`.
- [x] Merge em `develop`, release `0.7.0` (tag), merge em `main`; DL-0029…0033.
- [ ] **DL-0029 (Q6 tipo de REP) — Confiança Baixa + Reversibilidade Cara**: confirmar com o cliente qual REP usa (a captura do AFD muda conforme). Tela Angular / Micrometer — follow-up.

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
