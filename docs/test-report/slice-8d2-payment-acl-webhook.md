# Caderno de testes — Slice 8d-2: ACL de pagamento + webhook assíncrono idempotente

## Escopo

SPEC-0017 BR2/BR3 + Scope (ACL) + ADR 0006/DL-0048. Porta `PaymentGateway` + `MockPaymentGateway`
(request→PENDING; confirmação por webhook assinado HMAC-SHA256); `MockPayoutJobDispatcher` (@Scheduled,
`deliverDue()` para teste); `PaymentWebhookReceiver` (verifica assinatura, traduz DTO externo,
idempotente por `(payoutId, installmentSeq, providerRef)`); `PayoutExecutionService` (orquestrador);
endpoints `POST /api/payouts/{id}/execute` e `POST /api/webhooks/payouts/mock`; `V22`. Regra ArchUnit:
DTO do provedor não cruza para o domínio.

## Casos de teste

### Integração (Testcontainers) — `PayoutExecutionWebhookIntegrationTest` (4)
| Caso | Verifica | Regra |
|---|---|---|
| executingRequestsTheGatewayAndStaysExecutingUntilTheWebhookConfirms | execute→EXECUTING + job na fila (nada pago síncrono); webhook→EXECUTED | BR2/ADR 0006 |
| aReDeliveredWebhookDoesNotDoubleConfirm | reentrega do mesmo callback: **1** processed-webhook, segue EXECUTED | **BR3 (idempotência)** |
| aFailedPaymentLeavesAnExplicitFailedStateNeverAFalsePaid | outcome FAILED → FAILED explícito | **BR2 (sem pago falso)** |
| aFailedInstallmentCanBeRetriedAndThenSucceed | FAILED → retry SUCCEEDED → EXECUTED | BR3 (retry seguro) |

### Integração HTTP (TestRestTemplate) — `PayoutWebhookHttpIntegrationTest` (2)
| Caso | Verifica | Regra |
|---|---|---|
| aValidSignedWebhookConfirmsTheInstallment | callback assinado → 202 + EXECUTED | ADR 0006 (assinatura) |
| anInvalidSignatureIsRejectedAndNothingIsApplied | assinatura errada → 401; nada processado; segue EXECUTING | Error Behavior |

### Arquitetura — ArchUnit
- `DOMAIN_MUST_NOT_DEPEND_ON_PAYMENT_ADAPTER`: `domain` não depende de `..infra.integration.payment..`
  (DTO do provedor não vaza — ACL). Coberta também pela regra genérica domain→infra (teeth test).

## Resultado

✅ `./mvnw verify` BUILD SUCCESS — **288 testes** (7 novos), 0 Checkstyle, ArchUnit (regra do ACL de
pagamento) + Modulith + Spotless verdes. V22 aplicada e validada (Postgres real).

## Cobertura

Coberto: request→PENDING, entrega assíncrona assinada, confirmação/falha idempotente, retry, assinatura
inválida (401). **Não** coberto: wiring `SupplierSettled`→Finance e comprovante (8d-3). O dispatcher
agendado (`@Scheduled`) não é exercitado em teste (intervalo empurrado para fora); a entrega é dirigida
deterministicamente por `deliverDue()`.

## Como reproduzir

```bash
cd backend && ./mvnw test -Dtest='PayoutExecutionWebhookIntegrationTest,PayoutWebhookHttpIntegrationTest'
cd backend && ./mvnw verify   # Docker no ar
```
