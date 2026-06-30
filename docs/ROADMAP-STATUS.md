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
| 7 — Intelligence (DSS) | 2026-06-29 13:17 (-03:00) | 2026-06-29 14:05 (-03:00) | ✅ Subagente executou `RUN-PHASE` (FASE-ALVO=7); supervisor **reverificou**: `./mvnw verify` **219 tests** (BUILD SUCCESS, 0 Checkstyle), V17. Módulo `intelligence` (12º) + `PromoFxAdvisor` + `OverrideNudge` atrás de flag; **"aconselha, nunca comanda"** com regra ArchUnit + teeth test; port LLM `InsightNarrator` (stub). Released **`0.8.0`**. DL-0034…0036. |
| 8a — CommercialPolicy | 2026-06-29 14:17 (-03:00) | 2026-06-29 14:45 (-03:00) | ✅ Subagente (só SPEC-0014); supervisor **reverificou**: `./mvnw verify` BUILD SUCCESS (**239 tests**), 0 Checkstyle, V18. Motor de precedência (Diretiva>Promoção>Contrato>Política>Padrão) + **markup stub graduado sem quebrar Quoting** (contrato `MarkupProvider` intacto; `source`=nível vencedor). Released **`0.9.0`**. DL-0037…0040. |
| 8b — Finance (pleno) | 2026-06-29 15:17 (-03:00) | 2026-06-29 15:55 (-03:00) | ✅ Subagente (só SPEC-0015); supervisor **reverificou**: `./mvnw verify` **245 tests** verde, 0 Checkstyle, V19, **close-veto regressão verde**. Lançamento AP/AR automático por evento (idempotente) + balancete por moeda; comprar-vs-construir reafirmado (livro-caixa, não GL pleno). Released **`0.10.0`**. DL-0041…0043. |
| 8c — Billing | 2026-06-29 16:17 (-03:00) | 2026-06-29 17:05 (-03:00) | ✅ Subagente (só SPEC-0016); supervisor **reverificou**: `./mvnw verify` **265 tests** verde, 0 Checkstyle, V20. Módulo `billing` (13º) — NFS-e de comissão (base = comissão, não bruto) + ISS por regime (estratégia trocável) + ACL NFS-e (mock) + posta no Finance via evento; arquivo no cofre satisfaz o requirement (regra de ouro verde). Released **`0.11.0`**. DL-0044…0047 (**DL-0044 Conf. Baixa + Rev. Cara**: regime tributário). |
| 8d — Payout | 2026-06-29 17:17 (-03:00) | 2026-06-29 18:15 (-03:00) | ✅ Subagente (só SPEC-0017) **interrompido por rate-limit/reinício transitório** no meio do 8d-3 (8d-1/8d-2 mergeados local, sem push); o supervisor **inspecionou e RETOMOU o subagente** (SendMessage); o subagente retomado terminou 8d-3, cortou `0.12.0` e **pushou** (develop/main/tag). Supervisor **reverificou**: `./mvnw verify` **292 tests** verde, 0 Checkstyle, origin em dia. Payout (repasse/reembolso/parcelamento centavos-exatos) + ACL de pagamento (webhook idempotente, ADR 0006) + `SupplierSettled`→Finance (uma vez) + comprovante; armadilha do merchant preservada. DL-0048…0051 (**DL-0048 Conf. Baixa**; **DL-0049 Conf. Baixa + Rev. Cara**). Nota: o subagente editou o ROADMAP-STATUS contra a instrução; conteúdo conferido e reconciliado pelo supervisor. |
| 8e — AfterSales | 2026-06-29 18:17 (-03:00) | 2026-06-29 19:05 (-03:00) | ✅ Subagente (só SPEC-0018), 3 slices; sobreviveu a uma **colisão de árvore de trabalho** com a sessão paralela da Fase 15 (docs) finalizando num **worktree isolado**. Supervisor **reverificou na develop mergeada**: `git status` limpo, `develop`=`origin/develop` (`0f3807b`), tag `0.13.0`, `./mvnw verify` **319 tests** BUILD SUCCESS, 0 Checkstyle. Módulo `aftersales` (15º) — chamado + máquina de estados + **SLA via CommercialPolicy** (24/72/48h, breach por relógio controlado, alerta não bloqueia) + **reembolso→Payout uma vez** (armadilha do merchant intacta) + cancelamento→Booking + custo de servir. V23. Released **`0.13.0`**. DL-0052…0054. Nota: o subagente reescreveu esta linha durante o build (contra a instrução); conteúdo conferido e reconciliado pelo supervisor. |
| 15 — Documentação bilíngue | 2026-06-29 18:40 (-03:00) | 2026-06-29 18:55 (-03:00) | ✅ Por decisão do dono ("finish Phase 15 now, then resume") o supervisor concluiu a Fase 15 (chore de docs, **sem bump de versão** — ADR 0015). Cobertura bilíngue estendida do manual para **README** (`README.en-US.md` + seletor de idioma) e **changelog consolidado en-US** (`docs/release-notes/CHANGELOG.en-US.md`); regra codificada no `CLAUDE.md` + `_TEMPLATE.md` (go-forward); relatórios técnicos seguem só pt-BR (Regra Zero). Docs-only: sem código/migração/teste tocados; merge em develop. Desbloqueia o pipeline (restava só 8e 🟡). |
| 8j — Platform (contexto) | 2026-06-30 04:02 (-03:00) | 2026-06-30 04:58 (-03:00) | ✅ Subagente (worktree isolado) executou `RUN-PHASE` (FASE-ALVO=8j, SPEC-0023), 3 fatias. Gitflow seguro p/ worktree: publicou via `push origin feature/8j-integration:develop` (fast-forward `46b6db4`→`334a260`) + merge `--no-ff` em `main` (`0f69ccf`, árvore idêntica à develop — diff vazio) + tag `0.18.0`; não tocou no ROADMAP-STATUS. Supervisor **reverificou na develop mergeada**: `git ls-remote` confere os 3 hashes, `./mvnw verify` **434 tests** BUILD SUCCESS (ArchUnit 15, 20 Modulith, Spotless/Checkstyle 0). Módulo `platform` (20º) + V28 — custódia e-CNPJ cifrada (AES-256-GCM, só metadados na API) + governança de jobs (advisory lock, idempotente, falha nunca vira sucesso) + auditoria append-only. Released **`0.18.0`**. DL-0073…0078 (**DL-0074 Conf. Baixa + Rev. Cara**: cripto-at-rest do certificado — KMS×HSM×secret manager, A1×A3 é do dono). |
| 8i — People (jornada) | 2026-06-30 03:01 (-03:00) | 2026-06-30 04:01 (-03:00) | ✅ Subagente (worktree isolado) executou `RUN-PHASE` (FASE-ALVO=8i, SPEC-0022), 3 fatias. Gitflow seguro p/ worktree: publicou via `push origin feature/8i-integration:develop` (fast-forward `30d0825`→`6ad378b`) + merge `--no-ff` em `main` (`e224b1d`, árvore idêntica à develop — diff vazio) + tag `0.17.0` (anotada `93ad499`→`6ad378b`); não tocou no ROADMAP-STATUS. Supervisor **reverificou na develop mergeada**: `git ls-remote` confere os 3 hashes, `./mvnw verify` **411 tests** BUILD SUCCESS (ArchUnit 14, 19 Modulith, Spotless/Checkstyle 0). `people` estendido (jornada/banco/divergência/holerite, módulo-folha) + V27, consome o snapshot da Fase 6 por valor. Released **`0.17.0`**. DL-0069…0072 (**DL-0070 Conf. Baixa**: política de banco de horas — RH/jurídico). Durante o build o supervisor confirmou liveness do builder (processo java + worktree `locked`) antes da conclusão. |
| 8h — Assets | 2026-06-30 02:24 (-03:00) | 2026-06-30 03:00 (-03:00) | ✅ Subagente (worktree isolado) executou `RUN-PHASE` (FASE-ALVO=8h, SPEC-0021), 2 fatias. Gitflow seguro p/ worktree: publicou via `push origin feature/8h-integration:develop` (fast-forward `e3b208b`→`0914d5e`) + merge `--no-ff` em `main` (`8f1d67b`, árvore idêntica à develop — diff vazio) + tag `0.16.0`; não tocou no ROADMAP-STATUS. Supervisor **reverificou na develop mergeada**: `git ls-remote` confere os 3 hashes, `./mvnw verify` **388 tests** BUILD SUCCESS (ArchUnit 14, 18 Modulith, Spotless/Checkstyle 0). Módulo `assets` (18º, leaf) + V26. Released **`0.16.0`**. DL-0064…0068 (nenhuma Conf. Baixa/Rev. Cara — Q2 resolvida por recomendação do arquiteto). Nota: worktree principal ficou em `feature/8h-integration`; supervisor devolveu para `develop` (fast-forward) e limpou a branch. |
| 8g — Portfolio | 2026-06-30 01:34 (-03:00) | 2026-06-30 02:22 (-03:00) | ✅ Subagente (worktree isolado) executou `RUN-PHASE` (FASE-ALVO=8g, SPEC-0020), 2 fatias. Gitflow seguro p/ worktree: publicou via `push origin feature/8g-integration:develop` (fast-forward `3296d67`→`d38722a`) + merge em `main` (`ed8d270`, árvore idêntica à develop — diff vazio confirmado) + tag `0.15.0`; não tocou no ROADMAP-STATUS. Supervisor **reverificou na develop mergeada**: `git ls-remote` confere os 3 hashes, `./mvnw verify` **374 tests** BUILD SUCCESS (ArchUnit 14, 17 Modulith, Spotless/Checkstyle 0). Módulo `portfolio` (17º) + V25. Released **`0.15.0`**. DL-0060…0063 (**DL-0062 Conf. Baixa**: qual campo identifica a marca na venda). Nota: o worktree principal ficou na branch `feature/8g-integration`; supervisor devolveu para `develop` (fast-forward, sem perda) e limpou as branches. |
| 8f — Marketing (restart) | 2026-06-29 22:51 (-03:00) | 2026-06-30 00:04 (-03:00) | ✅ Subagente (worktree isolado `agent-ac67fa52`) executou `RUN-PHASE` (FASE-ALVO=8f), 3 fatias. Aprendeu com a falha anterior: **gitflow seguro p/ worktree** — publicou via `push origin feature/8f-integration:develop` (fast-forward limpo de `761c159`→`2a962ab`, **sem develop fantasma**) + merge em `main` (`4f66bbe`) + tag `0.14.0`; **não tocou no ROADMAP-STATUS** (do supervisor). Supervisor **reverificou na develop mergeada**: `git ls-remote` confere os 3 hashes, fast-forward limpo, `./mvnw verify` **342 tests** BUILD SUCCESS (ArchUnit 13, 16 Modulith, Spotless/Checkstyle 0). Módulo `marketing` (16º) + V24. Released **`0.14.0`**. DL-0055…0059 (**DL-0058 Conf. Baixa + Rev. Cara**: escopo do apagamento LGPD). Nota: alguns Writes do builder vazaram p/ o worktree principal; idênticos aos commitados, preservados em backup e removidos — working tree limpo. |
| 8f — Marketing | 2026-06-29 21:31 (-03:00) | 2026-06-29 22:10 (-03:00) | ⛔ **Abortada e limpa.** O builder do loop 05fbf6c1 trabalhou num worktree que **dessincronizou** (merges 8f-1/8f-2 num "develop" fantasma que nunca chegou ao `origin`; 8f-3 atribuição incompleta). Nada mergeado em develop, **sem release `0.14.0`**. Por ordem do dono ("ver se foi realmente implementado; corrigir worktrees dessincronizadas"), o supervisor: removeu o worktree travado (`agent-a834d1c4498538fe1`) + o de `Temp/erp-dev`, apagou as branches órfãs (`feature/8f-1-consent`, `8f-2-segment-campaign`, `8f-3-attribution`, `worktree-agent-…`) e a `feature/phase-15-bilingual-docs` já mergeada, descartou docs WIP não commitadas (DL-0055…0059, plano, spec 0019 revertida) e **resetou 8f → ⬜**. Base reverificada estável em **`0.13.0`**. 8f será reconstruída do zero pelo próximo loop. |

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
| **7** | Intelligence (DSS) | SPEC-0013 | ✅ Complete | Released `0.8.0` (tag). Módulo `intelligence` (12º) — `PromoFxAdvisor` (determinístico) + `OverrideNudge` (atrás de feature flag até o dono dar as faixas, Q4), read-model que escuta eventos e **aconselha, nunca comanda** (regra ArchUnit + teeth test; port LLM `InsightNarrator` com default rule-based). `./mvnw verify` 219 tests. |
| **8a** | CommercialPolicy (parâmetros governados) | SPEC-0014 | ✅ Complete | Released `0.9.0` (tag). Motor de precedência (Diretiva>Promoção>Contrato>Política>Padrão) + parâmetros governados editáveis/auditados; **gradua o stub de markup** (contrato `MarkupProvider` intacto, Quoting verde). `./mvnw verify` 239 tests. |
| **8b** | Finance (pleno) | SPEC-0015 | ✅ Complete | Released `0.10.0` (tag). Lançamento AP/AR **automático por evento** (idempotente, consome charges do Booking/SPEC-0010) + **balancete por moeda**, sobre o seam da Fase 2 sem quebrar o veto de fechamento. Genérico: **livro-caixa, não GL pleno** (comprar vs. construir, DL-0042). `./mvnw verify` 245 tests. |
| **8c** | Billing | SPEC-0016 | ✅ Complete | Released `0.11.0` (tag). Módulo `billing` (13º) — NFS-e de comissão (**base tributável = a comissão**, não o bruto) + ISS por regime (estratégia trocável, default Simples) + **ACL NFS-e** (mock, DTO externo não cruza); posta ISS no Finance via evento e arquiva a nota no cofre Compliance. `./mvnw verify` 265 tests. **Q7 (regime) = Conf. Baixa — levar ao contador.** |
| **8d** | Payout | SPEC-0017 | ✅ Complete | Released `0.12.0` (tag). Módulo `payout` (14º) — repasse/liquidação/**reembolso**/parcelamento (centavos exatos) + **ACL de pagamento** (webhook assíncrono idempotente, ADR 0006) + `SupplierSettled`→Finance (posta uma vez) + comprovante no cofre; **reembolso não cancela a obrigação do fornecedor** (armadilha do merchant). `./mvnw verify` 292 tests. **DL-0048/0049 = Conf. Baixa (provedor de pagamento + fluxo FX/remessa) — confirmar.** |
| **8e** | AfterSales | SPEC-0018 | ✅ Complete | Released `0.13.0` (tag). Módulo `aftersales` (15º) — chamado + máquina de estados + **SLA governado via CommercialPolicy** (24/72/48h, breach com relógio controlado, alerta não bloqueia) + **reembolso → Payout (uma vez, armadilha do merchant intacta)** + cancelamento → Booking + custo de servir. `./mvnw verify` 319 tests. |
| **8f** | Marketing | SPEC-0019 | ✅ Complete | Released `0.14.0` (tag). Módulo `marketing` (16º Modulith) — **consentimento LGPD append-only** (cidadão de 1ª classe; estado atual = última linha por `(subject, purpose)`) + **segmentação** (`criteria_json` validado contra catálogo fechado) + **campanha/newsletter via ACL** (`NewsletterSender`, filtra por consentimento antes de enviar, idempotente por `(campaign, recipient)`) + **atribuição** `code→booking` → `CampaignConverted` → Intelligence (consumer-leaf, `BookingConfirmed` intacto) + **apagamento LGPD** (remove PII, anonimiza o log preservando tombstone de revogação). `./mvnw verify` 342 tests. **DL-0058 = Conf. Baixa + Rev. Cara (escopo do apagamento LGPD) — confirmar com DPO antes do 1º uso real.** |
| **8g** | Portfolio | SPEC-0020 | ✅ Complete | Released `0.15.0` (tag). Módulo `portfolio` (17º Modulith) — marcas representadas (`brandRef` único) + **contratos de representação** (janela de validade, `documentId` da Compliance por valor, termos em jsonb; cobertura **alerta, nunca bloqueia** a venda) + alerta `RepresentationExpiring` (varredura 30d, relógio controlado) + **metas por marca** (VOLUME/REVENUE) com realizado **projetado sobre eventos de venda** (`BookingConfirmed`/`SpreadRealized`, idempotente, evento da venda intacto) → `goalProgress`. `./mvnw verify` 374 tests. **DL-0062 = Conf. Baixa (campo que identifica a marca na venda) — confirmar com o dono.** |
| **8h** | Assets | SPEC-0021 | ✅ Complete | Released `0.16.0` (tag). Módulo `assets` (18º Modulith, leaf) — registro de patrimônio (EQUIPMENT/SOFTWARE_LICENSE/OTHER; licença exige `expiresAt`) + documento de aquisição (Compliance) e custo (Finance) **por valor, sem FK** + baixa **auditada e terminal** (baixar 2x → 409) + alerta `AssetLicenseExpiring` (varredura 30d, relógio controlado, idempotente). Patrimônio, não produto (sem endpoint comercial). `./mvnw verify` 388 tests. Custo→Finance e insight→DSS ficam como **seam publicado** (eventos sem consumidor, DL-0067). |
| **8i** | People (jornada) | SPEC-0022 | ✅ Complete | Released `0.17.0` (tag). `people` estendido (módulo-folha) — `Employee` (jornada contratada, status, doc por valor) + **jornada/banco de horas** (`JourneyCalculator` puro, saldo = trabalhado−contratado, idempotente sobre o `snapshotRef` da Fase 6 por valor) + **divergências** (`ODD_PUNCH`/`MISSING_PUNCH`/`INCOHERENT_JOURNAL`, alerta+fila, nunca corrige) + **holerite no cofre Compliance** (PAYROLL, 5 anos, `documentId` por valor). `./mvnw verify` 411 tests. **DL-0070 = Conf. Baixa (política de banco de horas, janela CLT) — confirmar com RH/jurídico; v1 só mede.** |
| **8j** | Platform (contexto) | SPEC-0023 | ✅ Complete | Released `0.18.0` (tag). Módulo `platform` (20º Modulith) — **custódia e-CNPJ** (material cifrado at-rest AES-256-GCM via porta `SecretCipher`, chave mestra fora do banco; API só metadados, nunca a chave/senha; alerta de expiração por relógio controlado) + **governança de jobs** (registro `ScheduledJob`/`JobRun`, idempotência por janela, advisory lock no Postgres, falha→FAILED em tx própria nunca vira sucesso; liga os schedulers existentes) + **auditoria de sistema** append-only. Sem regra de domínio (ArchUnit + teeth test). `./mvnw verify` 434 tests. **DL-0074 = Conf. Baixa + Rev. Cara (cripto-at-rest do certificado / KMS×HSM, A1×A3) — confirmar com o dono.** |
| **8k** | Identity | SPEC-0024 | ⬜ Not started | Auth real (OIDC) + papéis/permissões; gradua o stub de auth. |
| **8l** | Admin | SPEC-0025 | ⬜ Not started | Fornecedores/contratos administrativos → Finance + Compliance. |
| **9** | Limpeza estrutural — remover `internal` do domain | ADR + chore | ⬜ Not started | (do fkerp-poc) Achatar `com.fksoft.domain.<módulo>.internal.*` → `…<módulo>` (11 módulos, main+test; herança Go). Estrutural, sem mudar contrato; encapsulação via `@NamedInterface`/ArchUnit; gates verdes. |
| **10** | UX & Frontend profissional | SPEC-0026 (nova) | ⬜ Not started | (do fkerp-poc) PrimeNG 21 (Aura) + Tailwind v4 + shell SaaS + command palette `Ctrl/Cmd+K` + tema claro/escuro + atalhos + a11y + `canDeactivate` + login/silent-refresh + dashboard KPIs. **Gradua DL-0003**. Repaginar TODAS as telas; estados loading/empty/error/permissão. |
| **11** | Observabilidade & monitoramento | SPEC-0027 (nova) | ⬜ Not started | (do fkerp-poc) Micrometer + Actuator/Prometheus + logs JSON + Prometheus/Loki/Grafana-Alloy/Grafana via compose + `GET /api/version`. Espelhar `infra/` da POC. |
| **12** | Qualidade & E2E | SPEC-0028 (nova) | ⬜ Not started | (do fkerp-poc) Playwright em stack isolada (`compose.e2e.yaml`, 4201, Postgres efêmero) + `@vitest/coverage-v8` + JaCoCo + sad paths; job de E2E no CI; nunca toca o banco de dev. |
| **13** | Identity/AuthZ profissional | gradua SPEC-0024 | ⬜ Not started | (do fkerp-poc) Spring Security + OAuth2 Resource Server (JWT), escopos → perfis; backend única autoridade; substitui `DevStubUserContextProvider`. Consolida a 8k. |
| **14** | Upgrade de stack (Spring Boot 4 / versões) | ADR (novo) | ⬜ Not started | (do fkerp-poc) Avaliar 3.5.16 → 4.x (Spring 7, Modulith 2.x); `ngx-graph` só se necessário. Só com gates verdes (DL-0002). |
| **15** | Documentação bilíngue pt-BR + en-US | regra + chore | ✅ Complete | Bilíngue: manual (`MANUAL.md`/`MANUAL.en-US.md`) **+ README** (`README.en-US.md`) **+ changelog en-US** (`docs/release-notes/CHANGELOG.en-US.md`); regra codificada em `CLAUDE.md` + `_TEMPLATE.md`. Relatórios técnicos/specs/ADRs/decision-log só pt-BR (Regra Zero). Chore de docs, sem bump de versão (ADR 0015). |

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

## Phase 7 — slice detail

| Slice | Spec | Deliverable | Status |
|---|---|---|---|
| 12a | SPEC-0013 | Módulo `intelligence` (12º Modulith) — framework `Insight` (evidência+proveniência / recomendação+ganho-risco / guardrail) + **`PromoFxAdvisor`** determinístico, listeners read-only de eventos; `V17` | ✅ |
| 12b | SPEC-0013 | **`OverrideNudge`** atrás de feature flag (default off até as faixas Q4); `POST /insights/{id}/decision` (registra decisão humana, sem ação) | ✅ |

**Phase 7 exit criteria:**
- [x] `cd backend && ./mvnw verify` green (219 tests; ArchUnit[10] + 12 Modulith modules **acíclico** + Spotless/Checkstyle) — reverificado pelo supervisor.
- [x] Migração `V17` aplicada e validada (read-models, sem FK cross-módulo).
- [x] **"Aconselha, nunca comanda"** provado: regra ArchUnit (intelligence não depende de `*Service`/`internal` de outros módulos) + **teeth test** + e2e que gera insight sem mutar a fonte.
- [x] Port LLM `InsightNarrator` com default determinístico (sem LLM vivo; **gate nunca depende de chamada externa**); IA real ficaria atrás do port (ACL), saída validada/versionada, `claude-opus-4-8`.
- [x] Merge em `develop`, release `0.8.0` (tag), merge em `main`; DL-0034…0036 (sem Confiança Baixa / Rev. Cara).
- [ ] **Q4 (faixas de override)** segue **aberta** (Nudge fica gated até o diretor fornecer) — explicitamente adiada, não inventada. Tela Angular / Micrometer — follow-up.

## Open architectural debts carried forward

| Item | Owner phase | Tracked in |
|---|---|---|
| ~~ADR 0014 (initial module set & order) not yet written~~ → **written by owner** | resolved | [ADR 0014](adr/0014-initial-modules-and-slice-order.md), [DL-0005](decision-log/DL-0005-adr-0014-ausente-adiar-fase-1.md) |
| ~~**Telas Angular da Fase 1**~~ → **entregues** em `0.2.1` (5 telas + nav; 14 testes) | resolved | [release-notes/0.2.1.md](release-notes/0.2.1.md) |
| PrimeNG + Tailwind not yet added (telas atuais em CSS puro) | **Fase 10 (UX, SPEC-0026)** | [DL-0003](decision-log/DL-0003-stack-frontend-fase-0.md) |
| Spring Boot 3.5 → 4.x upgrade | **Fase 14 (upgrade de stack, ADR)** | [DL-0002](decision-log/DL-0002-stack-versoes-backend.md) |

## How to update this file

1. When a slice goes green and is merged to `develop`, flip its row to ✅ and tick
   the matching exit-criteria checkboxes.
2. When all slices of a phase are ✅ and the release tag is cut, flip the phase to ✅.
3. Keep the "Open architectural debts" table current — move items out when resolved.

