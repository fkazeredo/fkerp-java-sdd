# Caderno de testes — Slice 8d-1: Payout (agregado + parcelamento + API)

## Escopo

SPEC-0017 BR1/BR2/BR6/BR7 + Acceptance Criteria (parte). Novo módulo folha `com.fksoft.domain.payout`
(14º Modulith): agregado `Payout` (kind/payee/status; settlementRate→settledBrl; parcelas), API
`POST/GET/LIST`, `V21`. Cobre: conversão BRL pela taxa; máquina de status; parcelamento com centavos
exatos; REFUND exige origem.

## Casos de teste

### Unitário — `InstallmentPlanTest` (5)
| Caso | Verifica | Regra |
|---|---|---|
| splittingThatDoesNotDivideEvenly…SumsExactly | R$100/3 = 33,34+33,33+33,33; soma == 100,00 | BR6/DL-0050 (centavos exatos) |
| splittingThatDividesEvenlySumsExactly | R$90/3 = 30+30+30 | BR6/DL-0050 |
| aSingleInstallmentEqualsTheWholeTotal | 1 parcela = total | BR6 (à vista) |
| anExplicitPlanThatDoesNotSumToTheTotalIsRejected | plano 110 ≠ total 100 → erro | BR6/DL-0050 |
| anExplicitPlanThatSumsToTheTotalIsAccepted | 40+60 == 100 aceito | BR6/DL-0050 |

### Unitário — `PayoutAggregateTest` (8)
| Caso | Verifica | Regra |
|---|---|---|
| supplierSettlementInUsdConvertsToBrlByTheSettlementRate | USD 500 × 5,70 = R$ 2.850,00; rate escala 6 | BR1/DL-0049 |
| aRefundWithoutOriginIsRejected | REFUND sem origem → erro | BR7 |
| aRefundWithOriginIsAccepted | REFUND com originRef aceito | BR7 |
| aPayoutIsExecutedOnlyWhenAllInstallmentsAreExecuted | 3 parcelas: EXECUTING até todas; depois EXECUTED | BR6 |
| aFailedInstallmentLeavesThePayoutFailedNotExecuted | parcela FAILED → Payout FAILED | BR2 (sem pago falso) |
| confirmingAnAlreadyExecutedInstallmentIsAnIdempotentNoOp | reconfirmar = no-op | BR3 |
| aZeroOrNegativeAmountIsRejected | valor ≤ 0 → erro | BR1 |
| aNonPositiveSettlementRateIsRejected | rate ≤ 0 → erro | BR1 |

### Integração (Testcontainers/Postgres) — `PayoutApiIntegrationTest` (3)
| Caso | Verifica | Regra |
|---|---|---|
| aForeignSupplierSettlementPersistsTheRateAndTheBrlBaixa | persiste rate 5,700000 + settled_brl 2850,00 + PENDING; 1 parcela | BR1/V21 |
| anInstallmentPlanPersistsTheExactCentDistribution | 3 parcelas 33,34/33,33/33,33 no banco; soma == 100 | BR6/DL-0050 |
| listingFiltersByKindAndStatus | filtro kind+status | API |

## Resultado

✅ `./mvnw verify` BUILD SUCCESS — **281 testes** (16 novos), 0 Checkstyle, ArchUnit + Spring Modulith
(14 módulos, acíclico) + Spotless verdes. V21 aplicada e validada (Postgres real).

## Cobertura

Coberto: agregado, parcelamento (centavos), conversão BRL, status, REFUND origem, persistência, API
create/get/list. **Não** coberto nesta fatia: execução/webhook (8d-2) e wiring Finance/comprovante
(8d-3). Tela Angular — backend-first (follow-up).

## Como reproduzir

```bash
cd backend && ./mvnw test -Dtest='InstallmentPlanTest,PayoutAggregateTest,PayoutApiIntegrationTest'
cd backend && ./mvnw verify   # Docker no ar
```
