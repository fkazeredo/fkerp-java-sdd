# Caderno de testes — Slice 12a: Intelligence (DSS) framework + PromoFxAdvisor (SPEC-0013)

## Escopo

O 12º módulo `com.fksoft.domain.intelligence` (Spring Modulith): o framework de **Insight**
(read-model: evidência+proveniência / recomendação+ganho / guardrail / status humano — BR1) e o
**PromoFxAdvisor** (BR5). Read-model **consumidor-folha** que escuta eventos de outros contextos
(`BookingConfirmed`, `RateSubsidyAccrued`, `FxPositionClosed`) e **aconselha, nunca comanda** (BR2/BR3),
projetando um insight por **agência** (DL-0034) com veredito **CONVERTE × QUEIMA_MARGEM** determinístico
(DL-0035). Saída validada antes de persistir (BR7). Cobre os Acceptance Criteria de geração do
PromoFxAdvisor por sujeito com veredito/ganho citando fontes, e o portão de arquitetura "só lê, aconselha".

## Casos de teste

### Unitário/domínio — `PromoFxAdvisorTest` (a prova de regra exigida pela fase)
| Caso | Verifica | Regra |
|---|---|---|
| `convertsWhenGapNonNegativeWithEnoughVolume` | gap 4900 ≥ 0 e volume 38 ≥ 5 → **CONVERTE**, ganho 4900, sem guardrail, fontes preservadas | BR5 / DL-0035 |
| `convertsRecoveringSubsidyWhenGapIsExactlyZero` | gap 0 e volume 10 → CONVERTE, ganho = subsídio recuperado 4200 | DL-0035 (ganho gap=0) |
| `burnsMarginWhenNegativeGapCrossesThresholdAndAttachesGuardrail` | gap −1500, |gap|>1000 → **QUEIMA_MARGEM**, risco 1500, guardrail 1000 | BR5/BR3 / DL-0035 |
| `staysSilentWhenNegativeGapIsWithinTolerance` | gap −900 ≤ limite 1000 → sem insight (sem ruído) | DL-0035 (silêncio) |
| `staysSilentWhenGapPositiveButVolumeTooLow` | gap 4900 mas volume 4 < 5 → sem insight | DL-0035 (volume mín.) |

### Integração (Testcontainers) — `IntelligencePromoFxIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `generatesConvertePromoFxInsightPerAgencyFromConsumedEventsWithProvenance` | 6 vendas confirmadas+liquidadas (mercado 5,55, pinned 5,40, liq. 5,70 → subsídio 75 + drift 75 = gap 150 cada) → 1 insight `PROMO_FX_ADVISOR` por agência, **CONVERTE**, ganho **900,00**, volume 6, fontes `[RateSubsidyAccrued, FxPositionClosed, BookingConfirmed]` | AC1 (gera por sujeito, veredito+ganho, cita fontes) |
| `doesNotMutateSourceAggregatesWhenGeneratingInsight` | após gerar insight, a reserva de origem permanece **CONFIRMED** (intelligence não escreve de volta) | **Regressão BR2** (advises, never commands) |
| `returns404ForUnknownInsight` | id inexistente → 404 `intelligence.insight.not-found` | Error Behavior |

### Arquitetura (ArchUnit) — a trava do "aconselha, nunca comanda" (BR2)
- `ArchitectureTest.INTELLIGENCE_ADVISES_NEVER_COMMANDS`: `..domain.intelligence..` **não** depende de
  nenhum `*Service` (fachada de comando) de outro módulo nem de qualquer pacote `internal` de outro
  módulo. Pode ler eventos/views/value objects expostos + `money` + kernel de erro. **Verde.**
- `ArchitectureRulesHaveTeethTest.intelligenceRuleFailsWhenIntelligenceDependsOnACommandFacade`:
  fixture `archfixture.intelligence.CommandingInsight` planta dependência em `BookingService`;
  a regra (re-apontada ao pacote do fixture) **falha** → prova que o portão tem dentes.
- Spring Modulith `verify()` **acíclico** com o 12º módulo (intelligence é folha; ninguém depende dele).
- `Insight`/`BookingAttribution`/`AgencyFxAccrual` e repositórios em `internal`;
  `InsightNotFoundException` registrada em `HttpErrorMapping`; i18n pt-BR + fallback en.

## Resultado

`./mvnw verify` → **BUILD SUCCESS**, `Tests run: 216, Failures: 0, Errors: 0, Skipped: 0`
(baseline 206 + 5 unit advisor + 3 integração + 2 arquitetura — regra nova `INTELLIGENCE_*` e teste de
dentes). ArchUnit 10 regras verdes; Modulith `verify()` verde; Spotless + Checkstyle verdes.

## Cobertura — o que NÃO está coberto e por quê
- **OverrideNudge / endpoint de decisão / observabilidade de aceitos×rejeitados**: slice 12b.
- **Eixo rota/produto**: fora de escopo (DL-0034) — eventos atuais não carregam rota/produto; enum
  `subjectKind` deixa o seam pronto.
- **`SpreadRealized` como receita atraída**: não usado (DL-0034 §3) — chaveado por `caseId`, sem booking.
- **LLM real**: não wired (DL-0036) — porta `InsightNarrator` com default determinístico.

## Como reproduzir
```bash
cd backend && ./mvnw -Dtest=PromoFxAdvisorTest test                       # unit do advisor
cd backend && ./mvnw -Dtest=IntelligencePromoFxIntegrationTest test       # e2e Testcontainers (Docker up)
cd backend && ./mvnw -Dtest=ArchitectureTest,ArchitectureRulesHaveTeethTest test  # portões + dentes
cd backend && ./mvnw verify                                               # build completo
```
