# Plano — Fase 7: Intelligence (DSS) prescritivo (SPEC-0013)

> Modo autônomo (RUN-PHASE, FASE-ALVO=7). Entrega o **12º módulo** `com.fksoft.domain.intelligence`:
> um **read-model que escuta eventos de todos os contextos e ACONSELHA, NUNCA COMANDA** (guardrail
> alerta; o humano decide). A "inteligência" da v1 é **prescritiva a partir de fatos** — advisors
> **determinísticos** (Rule Zero), sem dependência externa. O módulo é **consumidor-folha**: ninguém
> depende dele e ele **nunca** chama de volta um produtor (redesenho Parte 8).

## Objetivo

- **Framework de Insight** (read-model): `Insight {type, subjectKind, subjectRef, evidence(números +
  proveniência), recommendation(ação + estimatedGain/Risk), guardrail, status, generatedAt,
  decidedBy/decidedAt}` (BR1).
- **`PromoFxAdvisor`** (lucro direto): por **agência** (DL-0034), compara subsídio acumulado
  (`RateSubsidyAccrued`) com o gap realizado/volume (`FxPositionClosed`, `BookingConfirmed`) e
  classifica **CONVERTE × QUEIMA_MARGEM** com ganho/risco estimado e **proveniência** (BR5, DL-0035).
- **`OverrideNudge`** (seam): listener de `PriceOverridden` **desligado por feature flag** (Q4/BR6,
  DL-0036) — sem dado falso.
- **Decisão humana** registrada (`POST /decision`) sem disparar ação (BR4); observabilidade de IA
  (gerados/aceitos×rejeitados) por log de evento de negócio.
- **"Aconselha, nunca comanda" provado**: ArchUnit (intelligence não depende de fachada de comando de
  outro módulo) com **teste de dentes**; e2e provando que consumir eventos gera Insight **sem mutar a
  origem**.

## Decisões registradas antes do código (decision-log)

| DL | Lacuna (Open Question) | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0034 | Eixo do PromoFxAdvisor (rota×agência×produto); como correlacionar sem chamar produtor | Sujeito = **agência** (único eixo derivável de evento, `BookingConfirmed.accountId`); `subjectKind` enum plugável; correlação `booking→account` **só por evento** (consumidor-folha) | Média | Moderada |
| DL-0035 | Fórmula/limite do veredito e do ganho; regra × modelo | **Determinístico**; CONVERTE se `volume≥5 ∧ gap≥0`, QUEIMA_MARGEM se `gap<0 ∧ |gap|>R$1.000`; ganho = gap (constantes governadas, SPEC-0014); saída validada + fallback | Média | Barata |
| DL-0036 | OverrideNudge sem faixas (Q4); entra LLM?; cadência | Nudge **desligado por flag** (default false), seam pronto; **nenhum LLM wired** (porta `InsightNarrator` default determinístico); recomputação **on-event** | Média | Barata |

## Fatias (ordem de dependência)

### Slice 12a — Framework de Insight + PromoFxAdvisor  ·  `feature/slice-12a-intelligence-promofx`
- **Entrega:** módulo `com.fksoft.domain.intelligence` (`@ApplicationModule`, 12º); base público +
  `internal` module-private.
  - Agregado/read-model `Insight` (entidade JPA) com evidência/recomendação/guardrail em colunas
    estruturadas (sem jsonb — Rule Zero, como o codec de penalty windows; evidência tem campos fixos).
  - `PromoFxAdvisor` (domínio puro): regra do veredito + ganho (DL-0035), testado com fixtures exatas.
  - `IntelligenceService` (`@Service`): consome eventos via listener interno; mantém a atribuição
    `booking→account` e os acumulados por agência; gera/atualiza o `Insight` PROMO_FX_ADVISOR;
    `getById`, `list(type,subjectRef,status,page,size)` (sort por `estimatedGain desc`); valida a
    saída antes de persistir (BR7) — fallback = nenhum insight.
  - Listener interno `ExchangeSalesEventsListener` (consome `BookingConfirmed`, `RateSubsidyAccrued`,
    `FxPositionClosed` — tipos **expostos** dos módulos donos), **read-only**.
  - Evento `InsightGenerated` (produtor: intelligence).
  - `GET /api/intelligence/insights` + `GET /api/intelligence/insights/{id}` (404
    `intelligence.insight.not-found`).
  - Migração **V17__create_intelligence.sql** (`insights` + `insight_subsidy_attribution` p/ correlação).
  - i18n `intelligence.insight.not-found` (pt-BR + fallback en); registro em `HttpErrorMapping`.
  - **ArchUnit** `INTELLIGENCE_ADVISES_NEVER_COMMANDS` (intelligence não depende de
    quoting/booking/exchange/reconciliation/... services) + **teste de dentes** (planta dependência → falha).
  - Testes: unit do `PromoFxAdvisor` (2 cenários + proveniência); e2e Testcontainers (eventos →
    Insight por agência, relógio controlado; origem não mutada).

### Slice 12b — OverrideNudge (gated) + decisão humana + porta narrator + observabilidade  ·  `feature/slice-12b-nudge-decision`
- **Entrega:**
  - `InsightType.OVERRIDE_NUDGE`; listener de `PriceOverridden`; serviço **curto-circuita sob flag
    off** (`intelligence.override-nudge.enabled=false`) — não persiste Nudge (BR6).
  - `POST /api/intelligence/insights/{id}/decision` registra `{decision, note}` (BR4) **sem** ação;
    erro `intelligence.decision.invalid` (400) p/ decisão fora do enum; evento `InsightDecided`.
  - Porta de domínio `InsightNarrator` + `RuleBasedInsightNarrator` (default determinístico) — seam de
    IA, **não** wired a provedor externo (DL-0036).
  - Observabilidade: `InsightGenerated`/`InsightDecided` logados como evento de negócio (insightId,
    type, subjectRef, correlationId); `decidedBy`/`decidedAt` = métrica aceitos×rejeitados.
  - Testes: e2e — decisão registra sem disparar ação (regressão: estado operacional inalterado);
    Nudge fica off sem o modelo de faixas (flag off → nenhum insight); decisão inválida → 400.

## Portões (todas as fatias)
ArchUnit + Spring Modulith (verify **acíclico**, intelligence é folha) + Spotless + Checkstyle, sempre
verdes; `./mvnw spotless:apply` antes de `./mvnw verify`. Nenhum portão afrouxado. Migração Flyway
idempotente (V17, nunca editar aplicada). DomainException com code == chave i18n. Evento de negócio
logado, correlation id, sem dado pessoal. Merge `--no-ff` em develop só com verify verde; push.

## Entregáveis
Plano (este), caderno de testes (`docs/test-report/slice-12a-*`, `slice-12b-*` + INDEX), release note
`docs/release-notes/0.8.0.md` (SemVer, MINOR — ADR 0015), pom bump 0.7.0 → 0.8.0, tag `0.8.0`.

## Fora de escopo (próximas specs)
Demais relatórios do catálogo 8.2 (churn, forecast, mix, supplier leverage…) — cada um vira spec
própria. Modelo de faixas de override (Q4) e qualquer ação automática sobre a operação (proibida por
princípio).
