# Caderno de testes — Slice 6: Reconciliation (SPEC-0007)

## Escopo

Fecha o ciclo econômico: ao consumir `BookingConfirmed`, abre um `ReconciliationCase` copiando a
proveniência congelada do Quote (BR1, idempotente por `bookingId`); registra os realizados (BR3);
deriva `realizedSpread` (BR4), `fxGainLoss` (BR5) e a discrepância (BR7); cancela o caso em
`BookingCancelled` (BR2); lista priorizado por discrepância. Tolerância conforme
[DL-0011](../decision-log/DL-0011-reconciliation-tolerancia-discrepancia.md) (`max(R$1,00; 0,5%`).

## Casos de teste

### Unitário/domínio — `ReconciliationCase` (`ReconciliationCaseTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `computesRealizedSpreadAndFxGainLossAndFlagsDiscrepancy` | USD 500, pinned 5,40 vs 5,70 → `realizedSpread` 285, `fxGainLoss` −150, status DISCREPANCY | BR4/BR5/BR7 |
| `settlesWithinToleranceAsSettled` | realizado = esperado (135), settle 5,40 → fxGainLoss 0, SETTLED | BR6 |
| `keepsPartialSettlementPartiallySettled` | só uma ponta → PARTIALLY_SETTLED, sem derivados | BR6 |
| `rejectsCurrencyMismatch` | valor em USD → `reconciliation.currency.mismatch` | validação |

### Integração (Testcontainers, evento real cruzando módulos) — `ReconciliationIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `opensCaseOnBookingConfirmedWithExpectedSpread` | confirmar reserva abre caso com `expectedSpread` 135 (prova o evento `BookingConfirmed` fluindo) | AC1 |
| `recordsSettlementComputingRealizedSpreadAndFxGainLoss` | POST settlement → `realizedSpread` 285, `fxGainLoss` −150, DISCREPANCY | AC2 |
| `cancelsCaseWhenBookingCancelled` | cancelar reserva → caso CANCELLED | BR2 |
| `opensCaseIdempotentlyPerBooking` | reabrir o mesmo booking não duplica caso | BR1 (regressão) |
| `returns404ForUnknownCase` | caso inexistente → 404 `reconciliation.case.not-found` | API |

### Arquitetura
Módulo `reconciliation` (`@ApplicationModule`) verificado; consome `BookingConfirmed`/`BookingCancelled`
(eventos in-process, listener module-internal) e lê a proveniência **só pela fachada** `QuoteDirectory`.
Liquidação usa **lock pessimista** (`findByIdForUpdate`).

## Resultado

`./mvnw verify` → **BUILD SUCCESS**. `Tests run: 82` (Slice 5: 73 → +9). Spotless clean, Checkstyle 0.

## Nota de interpretação (SPEC-0007 SETTLED × DISCREPANCY)

O exemplo impresso da SPEC-0007 mostra `status: SETTLED` para o caso de liquidação a 5,70
(realizedSpread 285 vs expected 135). Esse exemplo é anterior à regra de tolerância; pela **BR7**
(normativa, "MUST ser marcado DISCREPANCY") + **DL-0011**, `|285 − 135| = 150` excede a tolerância
(`max(1,00; 0,675) = 1,00`), então o estado correto é **DISCREPANCY**. Adotada a regra normativa
sobre o exemplo ilustrativo; os valores (285 / −150) batem exatamente com o exemplo.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** (lista priorizada por discrepância) — **pendente** (leva de frontend da Fase 1).
- Posição agregada do livro e subsídio × drift — fora de escopo (SPEC-0011); AP/AR e automação de
  pagamento — SPEC-0015/0017.

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=ReconciliationCaseTest
./mvnw test -Dtest=ReconciliationIntegrationTest
```
