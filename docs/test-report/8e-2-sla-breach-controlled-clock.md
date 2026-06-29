# Caderno de testes — Fatia 8e-2 (SLA: detecção de breach com relógio controlado + override por política)

> SPEC-0018 BR4. Detecção de violação de SLA por job de **relógio controlado** + prova de que uma
> **Diretiva** (CommercialPolicy) muda o SLA efetivo. DL-0052/0053.

## Escopo / Acceptance Criteria cobertos

- BR4 — `now > dueAt` e não resolvido → `BREACHED` + `SlaBreached` (alerta, **não bloqueia**).
- Detecção com relógio controlado (instante como parâmetro, padrão `expirePendingBookings`):
  dentro × fora do SLA para **first-response / resolução / reembolso**.
- BR1/DL-0052 — SLA resolvido da CommercialPolicy; uma **Diretiva** sobrepõe o SYSTEM_DEFAULT.
- "SLA estourado alerta sem travar a operação" (Acceptance Criteria).

## Casos de teste

### Unitário — `SupportCaseTest` (agregado, deadline efetivo)
| Caso | Verifica | Regra |
|---|---|---|
| `marksBreachOnlyWhenDuePassedAndNotTerminalAndIdempotently` | (após assign) dentro×fora do `dueAt` + idempotência | BR4 |
| `breachesTheFirstResponseDeadlineWhileStillOpen` | OPEN: deadline efetivo = 1ª resposta (24h); estoura cedo | BR4 |
| `oncePickedUpTheEffectiveDeadlineIsResolution` | após assign: deadline efetivo = resolução (72h) | BR4 |
| `doesNotMarkBreachOnAResolvedCase` | caso terminal não estoura | BR4 |

### Integração (Testcontainers/Postgres) — `AfterSalesSlaIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `doesNotBreachAStandardCaseWhileWithinTheResolutionSla` | dentro do SLA de resolução (após assign) → 0 breach | BR4 |
| `breachesAStandardResolutionSlaWhenTheDeadlinePassed` | fora do SLA → 1 breach; **status preservado** (IN_PROGRESS) | BR4 (não bloqueia) |
| `breachesTheTighterRefundSlaForACancellationOrRefundCase` | reembolso usa 48h; dentro×fora | BR1/BR4 |
| `firstResponseDeadlineIsTighterThanResolution` | firstResponseDueAt < dueAt (24h < 72h) | BR1 |
| `breachesTheFirstResponseSlaWhileStillOpenBeforeResolutionIsDue` | OPEN estoura entre 24h e 72h | BR4 |
| `theSweepIsIdempotentAndSkipsResolvedCases` | 2ª varredura não re-sinaliza | BR4 (idempotente) |
| `aDirectiveOverrideChangesTheEffectiveResolutionSla` | Diretiva 72h→1h muda o SLA efetivo; dentro×fora provados | BR1/DL-0052 |

Relógio controlado: o instante de avaliação é passado a `markBreaches(now)` (sem mockar o bean
`Clock`), exatamente como `BookingService.expirePendingBookings(cutoff)`. O scheduler técnico
`AfterSalesSlaScheduler` injeta o `Clock` em produção.

## Resultado

`./mvnw verify` → **BUILD SUCCESS**, **317 testes** (0 falhas), 0 Checkstyle, Spotless clean,
ArchUnit + Spring Modulith **acíclico**.

## Cobertura / o que NÃO está coberto nesta fatia

- Orquestração resolve→Payout/Booking + regressão merchant-trap + custo de servir ponta a ponta →
  fatia 8e-3.

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply && ./mvnw verify   # Docker no ar
./mvnw -Dtest='SupportCaseTest,AfterSalesSlaIntegrationTest' test
```
