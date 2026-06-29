# Caderno de testes — Slice 5: Booking (SPEC-0006)

## Escopo

Reserva a partir de uma cotação: máquina de estados `ORDERED → PENDING → CONFIRMED →
(CHANGED|CANCELLED|NO_SHOW) → COMPLETED` (BR2), localizador INTERNAL gerado ou EXTERNAL digitado e
único (BR3), expiração automática de PENDING em 72h (BR4), e eventos de ciclo de vida (BR5).
Multiplicidade Quote→Booking conforme [DL-0010](../decision-log/DL-0010-booking-quote-multiplicidade.md)
(não 1:1; localizador é a trava).

## Casos de teste

### Unitário/domínio — `BookingStatus` (`BookingStatusTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `allowsTheValidLifecycleTransitions` | ORDERED→PENDING→CONFIRMED→(COMPLETED/NO_SHOW/CHANGED), CHANGED→CONFIRMED | BR2 |
| `rejectsInvalidTransitions` | ORDERED→CONFIRMED, COMPLETED→*, estados terminais → falham | BR2 |

### Integração (Testcontainers, fachada Quoting real) — `BookingIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `createsBookingInOrderedFromQuoteWithExternalLocator` | cria em `ORDERED`, copia accountId, localizador EXTERNAL | AC1 |
| `generatesInternalLocator` | INTERNAL gera código `INT-…` | BR3 |
| `runsLifecycleToCompletedAndRejectsConfirmingACompletedBooking` | order→pending→confirm→complete; confirmar COMPLETED → 409 | AC2 + regressão |
| `rejectsInvalidTransitionFromOrdered` | ORDERED→confirm → 409 `booking.transition.invalid` | AC3 |
| `cancelsWithReason` | cancelar grava `cancelReason` e estado CANCELLED | AC3 |
| `rejectsBookingForMissingQuote` | quote inexistente → 404 `booking.quote.not-found` | BR1 |
| `rejectsDuplicateLocator` | localizador repetido → 409 `booking.locator.duplicate` | BR3 |
| `rejectsBlankExternalLocator` | EXTERNAL sem código → 400 `booking.locator.invalid` | BR3 |
| `expiresPendingBookingsPastTheTimeout` | sweep com cutoff > pendingSince → CANCELLED `PENDING_TIMEOUT` | AC4 (job) |

### Arquitetura
Módulo `booking` (`@ApplicationModule`) verificado; valida o Quote **só pela fachada**
`QuoteDirectory` (sem tocar no repo de Quoting). O job de timeout é um adaptador técnico em
`infra.jobs` (driving) que delega a regra ao domínio (`BookingService.expirePendingBookings`).

## Resultado

`./mvnw verify` → **BUILD SUCCESS**. `Tests run: 73` (Slice 4: 62 → +11). Spotless clean, Checkstyle 0.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** (detalhe + ações conforme o estado) — **pendente** (leva de frontend da Fase 1).
- A **publicação** de `BookingConfirmed` é verificada **end-to-end na Slice 6** (Reconciliation abre
  um caso ao consumir o evento). Aqui validamos o efeito de estado (CONFIRMED + `confirmedAt`).
- `CHANGED` só marca estado (sem recomposição de preço) — fora de escopo (depende da fórmula, já
  decidida em DL-0009, mas a recomposição é spec futura).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=BookingStatusTest
./mvnw test -Dtest=BookingIntegrationTest
```
