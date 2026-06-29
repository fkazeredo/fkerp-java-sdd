# Relatório — Fase 7: Intelligence (DSS) prescritivo (SPEC-0013)

- **Versão/tag:** `0.8.0` (MINOR — ADR 0015) · **Data:** 2026-06-29 · **Modo:** autônomo (RUN-PHASE)
- **Resultado final:** `./mvnw verify` → **BUILD SUCCESS**, `Tests run: 219, Failures: 0, Errors: 0,
  Skipped: 0` (baseline 206 → +13).

## Fatias entregues

| Fatia | Branch | Entrega | Testes |
|---|---|---|---|
| 12a | `feature/slice-12a-intelligence-promofx` | 12º módulo `intelligence`; framework `Insight`; `PromoFxAdvisor` determinístico; consumo read-only de `BookingConfirmed`/`RateSubsidyAccrued`/`FxPositionClosed`; V17; GET API; ArchUnit "aconselha, nunca comanda" + teste de dentes | 216 (verde) |
| 12b | `feature/slice-12b-nudge-decision` | `OverrideNudge` seam desligado por flag (BR6/Q4); `POST /decision` (BR4, sem ação); `InsightDecisionInvalidException`; porta `InsightNarrator`; observabilidade | 219 (verde) |

Ambas mergeadas em `develop` com `--no-ff`, `./mvnw verify` verde em develop, push. Release `0.8.0`:
`release/0.8.0` → `main` + `develop` → tag `0.8.0`.

## "Aconselha, nunca comanda" — como é provado

- **ArchUnit `INTELLIGENCE_ADVISES_NEVER_COMMANDS`** (`ArchitectureTest`): `..domain.intelligence..`
  não depende de nenhum `*Service` (fachada de comando) de outro módulo nem de qualquer pacote
  `internal` de outro módulo. Pode ler eventos/views/value objects expostos + `money` + kernel de erro.
- **Teste de dentes** (`ArchitectureRulesHaveTeethTest`): fixture `archfixture.intelligence.CommandingInsight`
  depende de `BookingService`; a regra (re-apontada ao pacote do fixture) **falha** → o portão tem dentes.
- **Spring Modulith `verify()` acíclico** com o 12º módulo: intelligence é **folha** (ninguém depende
  dele) e nunca chama de volta um produtor — correlaciona `booking→account` só por evento (DL-0034).
- **e2e** (`IntelligencePromoFxIntegrationTest.doesNotMutateSourceAggregatesWhenGeneratingInsight` e
  `IntelligenceDecisionAndNudgeIntegrationTest.recordsHumanDecisionWithoutExecutingAnyAction`): o
  insight nasce de eventos consumidos e a decisão é registrada **sem mutar** a origem (booking
  CONFIRMED; 6 posições FX seguem CLOSED).

## Porta LLM

Introduzida a porta de domínio `InsightNarrator` (seam de IA, DL-0036) com default determinístico
`RuleBasedInsightNarrator`. **Nenhum LLM real é wired**; nos testes o narrator é o default
determinístico (nenhuma API key, o gate nunca depende de chamada externa). Qualquer provedor real
futuro fica atrás da porta (ACL), com stub nos testes, saída validada/versionada e dado pessoal
mascarado; id de modelo `claude-opus-4-8` para wiring real.

## Arquivos criados/alterados (resumo)

- **Domínio (base):** `InsightType`, `SubjectKind`, `InsightStatus`, `Verdict`, `PromoFxSignal`,
  `PromoFxAssessment`, `PromoFxAdvisor`, `InsightEvidence/Recommendation/Guardrail/View`,
  `InsightGenerated`, `InsightDecided`, `InsightNarrator`, `InsightNotFoundException`,
  `InsightDecisionInvalidException`, `IntelligenceService`, `package-info`.
- **Domínio (internal):** `Insight`, `BookingAttribution`, `AgencyFxAccrual` (+repositórios),
  `SourcesCodec`, `FxSalesEventsListener`, `OverrideEventsListener`, `RuleBasedInsightNarrator`.
- **Delivery:** `IntelligenceController`, `DecideInsightRequest`.
- **Infra:** `HttpErrorMapping` (+2 exceções), `messages*.properties` (+2 chaves).
- **Persistência:** `V17__create_intelligence.sql` (3 read-models).
- **Testes:** `PromoFxAdvisorTest` (unit), `IntelligencePromoFxIntegrationTest`,
  `IntelligenceDecisionAndNudgeIntegrationTest` (e2e), `ArchitectureTest`/`ArchitectureRulesHaveTeethTest`
  (regra + dentes), fixture `archfixture/intelligence/CommandingInsight`.
- **Docs:** plano `docs/plan/phase-7-intelligence.md`, DL-0034/35/36 + INDEX, spec 0013 (Open Questions
  → ASSUMIDO), cadernos `docs/test-report/slice-12a|12b` + INDEX, release note `0.8.0`, este relatório.

## Testes por tipo

- **Unitário/domínio:** `PromoFxAdvisorTest` (5 casos — CONVERTE, gap=0 recupera subsídio,
  QUEIMA_MARGEM+guardrail, silêncio dentro da tolerância, silêncio por volume baixo).
- **Integração (Testcontainers/Postgres):** PromoFx ponta-a-ponta com proveniência; não-mutação da
  origem; 404; decisão registra sem ação; decisão inválida 400; OverrideNudge off sem dado falso.
- **Arquitetura:** `INTELLIGENCE_ADVISES_NEVER_COMMANDS` + dentes; Modulith acíclico (12 módulos).
- **Completude de erro:** `HttpErrorMappingCompletenessTest` verde (2 novas exceções mapeadas).
- **Resultado:** `./mvnw verify` BUILD SUCCESS, 219/219.

## Impacto em OpenAPI / migrações
- OpenAPI: 3 endpoints novos sob `/api/intelligence/insights` (auto via springdoc).
- Migração: `V17` (aditiva; nunca editar aplicada). Sem contrato quebrado → MINOR.

## Decisões (links)
- [DL-0034](decision-log/DL-0034-promofx-subject-is-agency-event-derived.md) — sujeito=agência; folha — Média/Moderada.
- [DL-0035](decision-log/DL-0035-promofx-verdict-thresholds-and-deterministic-advisor.md) — advisor determinístico + limites — Média/Barata.
- [DL-0036](decision-log/DL-0036-overridenudge-gated-flag-and-llm-port-seam.md) — Nudge off por flag; LLM não wired — Média/Barata.
- **Nenhuma** DL desta fase é Confiança=Baixa nem Reversibilidade=Cara.

## Riscos e o que ficou para a próxima fase
- **Q4 (faixas de override)** permanece em aberto — Nudge atrás de flag, sem dado falso; ligar quando a
  tabela existir. **Eixo rota/produto** e enriquecer `SpreadRealized` com `bookingId` = melhorias
  futuras. **Micrometer/Prometheus** e **telas Angular** = pendentes (backend-first). Demais relatórios
  do catálogo 8.2 = specs próprias. **LLM real** = adapter futuro atrás da porta `InsightNarrator`.
