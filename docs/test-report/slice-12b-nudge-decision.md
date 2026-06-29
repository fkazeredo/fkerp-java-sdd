# Caderno de testes — Slice 12b: OverrideNudge (gated) + decisão humana + observabilidade (SPEC-0013)

## Escopo

O seam do **OverrideNudge** desligado por feature flag (BR6, Q4 — DL-0036), o endpoint de **decisão
humana** (BR4) que registra sem disparar ação (BR2), e a observabilidade de IA (eventos de negócio
`InsightGenerated`/`InsightDecided` logados; `decidedBy`/`decidedAt` = métrica aceitos×rejeitados).
Cobre os Acceptance Criteria: "registrar uma decisão humana não executa nenhuma ação automática" e
"`OverrideNudge` só liga quando o modelo de faixas existir (flag), sem dado falso".

## Casos de teste

### Integração (Testcontainers) — `IntelligenceDecisionAndNudgeIntegrationTest`
| Caso | Verifica | Acceptance Criteria / Regra |
|---|---|---|
| `recordsHumanDecisionWithoutExecutingAnyAction` | `POST /insights/{id}/decision {ACCEPTED, note}` → 200, status ACCEPTED, `decidedBy`/`decidedAt` preenchidos; **regressão**: as 6 posições FX continuam CLOSED, nenhuma ação operacional disparada | AC2 + **BR2/BR4** (decisão não age) |
| `rejectsAnOutOfEnumDecisionWith400` | decisão `MAYBE_LATER` (fora do enum) → 400 `intelligence.decision.invalid` | Error Behavior / BR4 |
| `overrideNudgeStaysOffWithoutTheTierModel` | override de preço publica `PriceOverridden`; com a flag off (default) → **0** insights `OVERRIDE_NUDGE` (sem dado falso) | AC3 + **BR6** (gated) |

> A trava "aconselha, nunca comanda" (ArchUnit `INTELLIGENCE_ADVISES_NEVER_COMMANDS` + teste de
> dentes) já está provada na slice 12a e continua verde com o novo listener de `PriceOverridden` —
> que também só consome o tipo **exposto** de quoting e nunca chama de volta o produtor.

## Implementação relevante
- `IntelligenceService.onPriceOverridden(quoteId)` curto-circuita sob `intelligence.override-nudge.enabled`
  (default false, `@Value`): seam pronto (enum `OVERRIDE_NUDGE`, listener `OverrideEventsListener`),
  **sem** cálculo de faixas até a Q4 existir — não gera dado falso; com a flag on (futuro) loga aviso
  em vez de falhar a transação do override.
- `IntelligenceService.decide(id, decision, actor)` valida a decisão no **domínio**
  (`InsightDecisionInvalidException` → 400 para valor fora de {ACCEPTED, REJECTED, DISMISSED} ou NEW),
  registra `decidedBy/decidedAt`, publica `InsightDecided` e loga o evento de negócio (correlation id).
- `InsightDecisionInvalidException` registrada em `HttpErrorMapping` (verde no `HttpErrorMappingCompletenessTest`);
  i18n `intelligence.decision.invalid` pt-BR + fallback en.

## Resultado

`./mvnw verify` → **BUILD SUCCESS**, `Tests run: 219, Failures: 0, Errors: 0, Skipped: 0`
(216 da 12a + 3 integração da 12b). ArchUnit 10 regras verdes; Modulith `verify()` acíclico verde
(o novo listener não cria ciclo — intelligence segue folha); Spotless + Checkstyle verdes;
`HttpErrorMappingCompletenessTest` verde (nova exceção mapeada).

## Cobertura — o que NÃO está coberto e por quê
- **Cálculo do OverrideNudge (distância à próxima faixa)**: fora de escopo até a tabela de faixas (Q4)
  existir (BR6/DL-0036) — só o seam é entregue, com teste provando que fica off sem dado falso.
- **LLM real**: não wired (DL-0036) — porta `InsightNarrator` com default determinístico; quando entrar
  um provedor, fica atrás da porta (ACL) com stub nos testes, saída validada/versionada e dado pessoal
  mascarado.
- **Tela Angular do DSS**: pendente (como nas demais fases backend-first).

## Como reproduzir
```bash
cd backend && ./mvnw -Dtest=IntelligenceDecisionAndNudgeIntegrationTest test  # e2e (Docker up)
cd backend && ./mvnw -Dtest=HttpErrorMappingCompletenessTest test             # exceção mapeada
cd backend && ./mvnw verify                                                   # build completo
# Ligar o seam do Nudge (futuro, quando a Q4 existir):
#   intelligence.override-nudge.enabled=true  (application property)
```
