# Caderno de testes — Fatia 8e-3 (Resolução: orquestração Payout/Booking + custo de servir + armadilha do merchant)

> SPEC-0018 BR2/BR3/BR5/BR6. `resolve` orquestra os donos por fachada, idempotente, sem tocar a
> obrigação do fornecedor. DL-0054.

## Escopo / Acceptance Criteria cobertos

- BR3 — `REFUND_APPROVED` cria um Payout `REFUND` referenciando a origem (caseId), **uma vez**.
- BR2 — `CANCEL_APPROVED` aciona `BookingService.cancel` (SPEC-0010); AfterSales não muda a reserva.
- BR5 — custo de servir acumula (handling + reembolso).
- BR6/DL-0024 — o reembolso ao cliente **não cancela** a obrigação do fornecedor (armadilha do
  merchant intacta).
- Acceptance Criteria: "abrir um chamado de reembolso e aprová-lo cria um Payout REFUND
  referenciando a origem"; "um chamado de cancelamento resolvido aciona a Booking".

## Casos de teste

### Integração (Testcontainers/Postgres) — `AfterSalesResolveOrchestrationIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `resolvingARefundCaseTriggersExactlyOnePayoutAndKeepsTheSupplierObligation` | aprova reembolso → **1** Payout REFUND (origin_ref=caseId); custo de servir = 12+480=492; **PAYABLE/charge do fornecedor intacto**; 2ª aprovação → `aftersales.refund.duplicate` e ainda **1** Payout | BR3/BR5/BR6, **armadilha do merchant** (DL-0024/0051) |
| `resolvingACancellationCaseDrivesTheBookingCancellation` | `CANCEL_APPROVED` → `BookingService.cancel` (ALL_SALES_FINAL) → reserva **CANCELLED**; AfterSales não muda a reserva | BR2 |

Fixtura: reserva CONFIRMED sob política `ALL_SALES_FINAL` (merchant of record) montada via REST
(mesma receita do `MerchantTrapIntegrationTest`); o caso é dirigido pela fachada `AfterSalesService`,
que chama as fachadas **reais** de Payout e Booking (colaboração só por fachada pública — acíclico).

## Prova da armadilha do merchant (regressão verde)

O cancelamento do booking gera uma `cancellation_charge` SUPPLIER (a obrigação do fornecedor, que o
Finance posta como SUPPLIER_SETTLEMENT PAYABLE). O reembolso encaminhado pelo AfterSales cria um
Payout REFUND **separado** referenciando o caso — **sem** apagar/compensar a obrigação do fornecedor.
A contagem de cobranças SUPPLIER permanece a mesma antes e depois do reembolso. A regressão original
`RefundMerchantTrapIntegrationTest` (Payout) também segue verde no `verify`.

## Resultado

`./mvnw verify` → **BUILD SUCCESS**, **319 testes** (0 falhas), 0 Checkstyle, Spotless clean,
ArchUnit + Spring Modulith **acíclico** (`aftersales → payout, booking, commercialpolicy`).

## Cobertura / o que NÃO está coberto

- Canais de atendimento (e-mail/WhatsApp) — fora de escopo (Open Question em aberto na spec).
- Consumo de `SupportCaseOpened/Resolved` pela Intelligence — produzido aqui; o consumidor é
  evolução da SPEC-0013 (não exigido nesta fase).

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply && ./mvnw verify   # Docker no ar
./mvnw -Dtest='AfterSalesResolveOrchestrationIntegrationTest' test
```
