# Caderno de testes — Fatia 8e-1 (SupportCase + máquina de estado)

> SPEC-0018. Módulo `aftersales` (15º Modulith) + agregado `SupportCase` + máquina de estado +
> SLA derivado da CommercialPolicy no `open` (V23). DL-0052/0053/0054.

## Escopo / Acceptance Criteria cobertos

- BR1 — `SupportCase` referencia uma Booking (valor), tem type/status/openedAt; prazos de SLA
  derivam do type/política (parâmetro governado — resolvidos da CommercialPolicy no `open`).
- BR4 (parcial) — máquina de status; flag `breached` (a varredura é da fatia 8e-2).
- Error Behavior — `aftersales.case.not-found` (404), `aftersales.case.transition.invalid` (409),
  `aftersales.case.invalid` (400).

## Casos de teste

### Unitário — `SupportCaseStatusTest`
| Caso | Verifica | Regra |
|---|---|---|
| `allowsTheValidLifecycleTransitions` | OPEN→IN_PROGRESS/RESOLVED; IN_PROGRESS→WAITING/RESOLVED; WAITING→IN_PROGRESS/RESOLVED; RESOLVED→CLOSED/IN_PROGRESS (reabertura) | máquina válida |
| `rejectsInvalidTransitions` | OPEN↛WAITING/CLOSED; WAITING↛CLOSED; CLOSED↛*; RESOLVED↛WAITING | máquina inválida |
| `onlyResolvedAndClosedAreTerminalForSla` | predicado `isTerminal()` (insumo da varredura SLA) | BR4 |

### Unitário — `SupportCaseTest` (agregado)
| Caso | Verifica | Regra |
|---|---|---|
| `opensInOpenStatusWithTheGivenDeadlines` | abre OPEN, dueAt/firstResponseDueAt setados, custo zero | BR1 |
| `rejectsOpeningWithoutBookingReference` | `SupportCaseInvalidException` sem bookingId | BR1 |
| `walksTheValidLifecycle` | OPEN→IN_PROGRESS→WAITING→IN_PROGRESS | máquina |
| `rejectsAnInvalidTransitionWithTheSpecificException` | `SupportCaseTransitionInvalidException` (estado preservado) | máquina |
| `reopeningFromResolvedIncrementsTheReopenCountAndClearsResolution` | reabertura ++reopenCount e limpa resolução | BR5 |
| `accumulatesCostToServeFromHandlingAndRefund` | handling 15+5=20, refund 480, total 500 | BR5 |
| `marksBreachOnlyWhenDuePassedAndNotTerminalAndIdempotently` | dentro×fora do SLA + idempotência | BR4 |
| `doesNotMarkBreachOnAResolvedCase` | caso terminal não estoura | BR4 |

### Integração (Testcontainers/Postgres) — `AfterSalesApiIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `openingACasePersistsItOpenWithGovernedSlaDeadlines` | persiste OPEN; SLA 24h/72h resolvido da CommercialPolicy (seed V23) | BR1/DL-0052 |
| `cancellationAndRefundCasesUseTheTighter48hResolutionSla` | REFUND_REQUEST/CANCELLATION_REQUEST → 48h | BR1/DL-0052 |
| `drivesTheValidLifecycleAndRejectsInvalidTransitions` | transições válidas via fachada + inválida 409 | máquina |
| `aMissingCaseIsNotFound` | `SupportCaseNotFoundException` | Error Behavior |
| `listsCasesFilteredByTypeAndStatus` | filtros de listagem | API Contracts |

## Resultado

`./mvnw verify` → **BUILD SUCCESS**, **308 testes** (0 falhas), 0 Checkstyle, Spotless clean,
ArchUnit (12 regras) verde, Spring Modulith **acíclico** (15 módulos, AfterSales incluído).

## Cobertura / o que NÃO está coberto nesta fatia

- Varredura de breach (job) e prova de override de SLA por Diretiva → fatia 8e-2.
- Orquestração resolve→Payout/Booking e regressão merchant-trap → fatia 8e-3.

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply && ./mvnw verify   # Docker no ar (Testcontainers)
# só os unitários do módulo:
./mvnw -Dtest='SupportCaseStatusTest,SupportCaseTest' test
```
