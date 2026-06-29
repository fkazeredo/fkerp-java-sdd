# Caderno de testes — Slice 8d-3: SupplierSettled→Finance + comprovante + reembolso (merchant trap)

## Escopo

SPEC-0017 BR4/BR5/BR7 + Acceptance Criteria + DL-0049/DL-0051. Listener `PayoutEventsListener` em
`finance.internal` consome `SupplierSettled`/`AgentCommissionPaid`/`RefundExecuted` e posta o AP
idempotente (`finance → payout`, acíclico). Orquestrador arquiva o comprovante no Compliance
(`PAYMENT_PROOF`/`REFUND_PROOF`) antes de confirmar a parcela. Reembolso **não** cancela a obrigação do
fornecedor (armadilha do merchant).

## Casos de teste

### Integração (Testcontainers) — `SupplierSettlementFinanceIntegrationTest` (2)
| Caso | Verifica | Regra |
|---|---|---|
| liquidatingASupplierAt570PostsR2850ToFinanceOnceAndArchivesThePaymentProof | USD500×5,70 → execute+webhook → EXECUTED; **1** AP SUPPLIER_SETTLEMENT PAYABLE R$ 2.850 BRL; **1** PAYMENT_PROOF no cofre | **BR5 (posta uma vez)** + BR4 + Acceptance |
| aReDeliveredSupplierSettledEventDoesNotDoublePostToFinance | reentrega do webhook: **1** AP + **1** comprovante | **BR3/DL-0051 (idempotente)** |

### Integração (Testcontainers) — `RefundMerchantTrapIntegrationTest` (2)
| Caso | Verifica | Regra |
|---|---|---|
| executingACustomerRefundDoesNotCancelTheSupplierObligation | dado AP SUPPLIER_SETTLEMENT pré-existente, executa REFUND → AP do fornecedor **intacto** (1); REFUND posta AP REFUND próprio (1) + `REFUND_PROOF` (1) | **BR7 + armadilha do merchant (DL-0024/0051)** |
| aRefundWithoutOriginIsRejected | REFUND sem origem → erro | BR7 |

## Resultado

✅ `./mvnw verify` BUILD SUCCESS — **292 testes** (4 novos), 0 Checkstyle, ArchUnit + Spring Modulith
(14 módulos, **acíclico** com `finance → payout`) + Spotless verdes. Sem nova migração (reusa
`posted_event_entries` e o cofre).

## Cobertura

Coberto (requisitos do supervisor): liquidação do fornecedor posta ao Finance **exatamente uma vez**;
webhook confirma/falha idempotente; comprovante arquivado (PAYMENT_PROOF/REFUND_PROOF); reembolso
executa **sem** cancelar a obrigação do fornecedor (regressão da armadilha do merchant verde); falha →
FAILED explícito (8d-2). **Adiado** (DL-0051, sem perda de cobertura): consumo de `SupplierSettled` por
`reconciliation`/`exchange` (já fecham FX pela liquidação própria); `aftersales` (módulo 8e ainda não
existe). Tela Angular — backend-first.

## Como reproduzir

```bash
cd backend && ./mvnw test -Dtest='SupplierSettlementFinanceIntegrationTest,RefundMerchantTrapIntegrationTest'
cd backend && ./mvnw verify   # Docker no ar
```
