# Caderno de testes — Slice 9b · Cancelamento rico + Armadilha do Merchant (SPEC-0010)

## Escopo

O **keystone** da fase. Congelamento da política na confirmação (`BookingCancellationSnapshot`,
BR1) com os valores de referência do Quote (sale BRL = `baseConverted`, supplier USD = `basePrice`);
cálculo dos encargos como **fatos distintos** (`CancellationCharges`, BR5/BR11) e a **armadilha do
merchant**: ALL_SALES_FINAL em venda *merchant of record* gera **SUPPLIER + CUSTOMER_REFUND** que
**não se anulam**. Endpoint `POST /api/bookings/{id}/cancel` enriquecido (`{reason, serviceStartsAt,
refundAmount}` → `CancellationResult`); persistência em `cancellation_charges` (BR7); eventos
`CancellationCharged` + `MerchantObligationIncurred`. Migração
`V13__create_cancellation_snapshot_and_charges.sql` (+ `bookings.scope_ref`). Janelas time-based com
**relógio controlado** (`serviceStartsAt` relativo a agora). Decisões: DL-0021, DL-0022, DL-0024.

## Casos de teste

### Unitário/Regressão — `CancellationChargesTest` (4 casos) — a armadilha, provada no domínio
| Caso | Verifica | Regra |
|---|---|---|
| standardCancellationProducesASinglePenaltyWithThePolicyCostBearer | STANDARD 50%/24h, 10h → 1 PENALTY = 240,00 BRL, costBearer AGENCY | BR2 |
| standardCancellationOutsideAnyWindowProducesNoCharge | 100h sem janela → nenhum encargo | BR2 |
| **merchantAllSalesFinalWithRefundProducesTwoObligationsThatDoNotNetOut** | **ALL_SALES_FINAL merchant + reembolso → 2 encargos: SUPPLIER 500 USD (integral, não 500−480) + CUSTOMER_REFUND 480 BRL; ambos costBearer ACME; moedas diferentes ⇒ nem poderiam ser líquidos** | **BR5/BR8/BR11 — a armadilha** |
| affiliateAllSalesFinalWithoutRefundStillChargesTheSupplierCostToTheSupplier | ALL_SALES_FINAL afiliado sem reembolso → 1 SUPPLIER, costBearer SUPPLIER | BR3/BR8 |

### Integração (Testcontainers/Postgres) — `MerchantTrapIntegrationTest` (2 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| standardCancellationInsideA50PercentWindowChargesTheCorrectPenaltyAndCostBearer | venda confirmada (scope STANDARD 50%/24h), cancel 10h → PENALTY 1350,00 BRL (50% de 2700), costBearer AGENCY | "Cancelar dentro de uma janela de 50% cobra a multa correta com o costBearer certo" |
| **merchantAllSalesFinalCancellationWithRefundRecordsTwoObligationsThatDoNotNetOut** | **ponta a ponta: PUT política ALL_SALES_FINAL merchant → confirma (congela) → cancel com refund 2700 → `CancellationResult` tem SUPPLIER 500 USD (ACME) + CUSTOMER_REFUND 2700 BRL (ACME); 2 linhas em `cancellation_charges`; supplier sobrevive ao reembolso** | "A venda ALL_SALES_FINAL cancelada com reembolso registra duas obrigações distintas e publica MerchantObligationIncurred" |

### Regressão de ciclo (SPEC-0006) — `BookingIntegrationTest`, `ReconciliationIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| cancelsWithReasonAndReturnsTheChargesEnvelope | cancel de booking PENDING (sem política) → STANDARD default, **0 encargos**; razão gravada | compat. SPEC-0006 |
| cancelsCaseWhenBookingCancelled (reconciliation) | `BookingCancelled` ainda dispara o cancelamento do caso (encargos não quebram o evento) | SPEC-0007 BR |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 151` (+6 da fatia). Portões verdes:
ArchUnit, Spring Modulith (10 módulos), Spotless, Checkstyle (0 violações). A armadilha do merchant
é provada por **dois níveis** (unit `CancellationChargesTest` + e2e `MerchantTrapIntegrationTest`):
o reembolso ao cliente **não** zera a obrigação com o fornecedor.

## Cobertura — o que NÃO está coberto (e por quê)

- **Execução** do reembolso (Payout/SPEC-0017), **chamado** de pós-venda (AfterSales/SPEC-0018) e o
  **lançamento contábil** (Finance/SPEC-0015): fora de escopo da SPEC-0010 (os eventos
  `CancellationCharged`/`MerchantObligationIncurred` ficam in-process sem consumidor obrigatório).
- **Conversão cambial** da multa/encargos: moeda original, sem conversão (DL-0022) — Finance consolida.
- **No-show**: fatia 9c.
- Tela Angular: backend-first.

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=CancellationChargesTest                 # unit (a armadilha, domínio)
cd backend && ./mvnw verify -Dtest=MerchantTrapIntegrationTest           # integração e2e (Docker up)
cd backend && ./mvnw verify                                              # tudo + portões
```
