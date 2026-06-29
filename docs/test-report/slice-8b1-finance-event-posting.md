# Caderno de testes — Slice 8b-1 · Finance: lançamento automático de AP/AR por evento (SPEC-0015 full)

## Escopo

Estende o seam de Fase 2 do Finance **sem quebrar contratos**: implementa **BR5** (eventos de negócio
viram lançamentos AP/AR) com **idempotência** (Validation Rules). O Finance passa a consumir, como
**consumidor-folha**, os eventos já publicados e órfãos de lançamento do Booking (SPEC-0010):
`CancellationCharged`, `MerchantObligationIncurred`, `NoShowCharged`, e posta os lançamentos
PROVISIONAL no **período do fato** (`occurredAt` em UTC). Idempotência por tabela `posted_event_entries`
com **UNIQUE (source_ref, charge_kind)** + pré-check de existência, na mesma transação do lançamento
(migração **V19**). A obrigação de fornecedor do ALL_SALES_FINAL (publicada em dose dupla) é postada
**uma vez** (via `MerchantObligationIncurred`), preservando a armadilha do merchant (DL-0024: PAYABLE
fornecedor e PAYABLE/REFUND cliente coexistem, sem netting). Decisões: **DL-0041** (mapa + idempotência;
comissão/SupplierSettlement diferidos por falta de produtor), **DL-0042** (reafirma comprar-vs-construir
o GL pleno).

## Mapa evento → lançamento (DL-0041)

| Evento (booking) | Encargo | direction | entryType | party | idempotência |
|---|---|---|---|---|---|
| `CancellationCharged` | PENALTY | RECEIVABLE | PENALTY | AGENCY(bookingId) | (bookingId, PENALTY) |
| `CancellationCharged` | CUSTOMER_REFUND | PAYABLE | REFUND | AGENCY(bookingId) | (bookingId, CUSTOMER_REFUND) |
| `CancellationCharged` | SUPPLIER | — (ignorado aqui) | — | — | postado via MerchantObligation |
| `MerchantObligationIncurred` | SUPPLIER | PAYABLE | SUPPLIER_SETTLEMENT | SUPPLIER(bookingId) | (bookingId, SUPPLIER) |
| `NoShowCharged` (fee≠null,!waived) | NO_SHOW | RECEIVABLE | PENALTY | AGENCY(bookingId) | (bookingId, NO_SHOW) |

## Casos de teste

### Integração (Testcontainers/Postgres) — `FinanceEventPostingIntegrationTest` (4 casos)
| Caso | Verifica | Regra |
|---|---|---|
| cancellationChargesBecomeApArEntriesInThePeriodOfTheFact | `CancellationCharged` (PENALTY 1350 BRL + CUSTOMER_REFUND 2700 BRL, occurredAt 2026-06-15) → 2 lançamentos: RECEIVABLE/PENALTY e PAYABLE/REFUND, período **2026-06** (do fato), status PROVISIONAL | BR5, DL-0041, DL-0013 |
| **reDeliveringTheSameCancellationEventDoesNotDoublePost** | **re-publicar o mesmo evento → continua 1 lançamento (não duplica)** — idempotência provada | **Validation Rules / DL-0041** |
| merchantObligationPostsTheSupplierPayableOnceAndCancellationSupplierIsNotDuplicated | ALL_SALES_FINAL publica SUPPLIER no `CancellationCharged` **e** em `MerchantObligationIncurred` → exatamente **1** PAYABLE SUPPLIER_SETTLEMENT (500 USD) **+** 1 REFUND; coexistem, não netam | BR5, DL-0024, DL-0041 |
| noShowWithFeePostsAReceivablePenaltyAndWaivedPostsNothing | `NoShowCharged` com fee 80 BRL → 1 RECEIVABLE/PENALTY; `NoShowCharged` waived (fee null) → **nenhum** lançamento | BR5, DL-0041 |

### Regressão — a regra de ouro e o seam de Fase 2 (continuam verdes)
| Caso | Verifica | Regra |
|---|---|---|
| `CloseVetoIntegrationTest.entryWithoutDocumentBlocksTheCloseThenClosesOnceAttached` | lançamento sem documento **bloqueia** o fechamento (409 `finance.period.cannot-close`); anexado o documento, o mesmo período **fecha** (200) | SPEC-0008 BR6 + SPEC-0015 BR3 — **a regra de ouro, intacta** |
| `FinanceIntegrationTest` (6) | criar/confirmar/listar lançamentos, fechar período vazio, rejeitar período fechado/ malformado, totais por moeda — contratos públicos do seam **inalterados** | SPEC-0015 BR1-BR4 |
| `MerchantTrapIntegrationTest`, `NoShowIntegrationTest`, `BookingIntegrationTest` | cancelamento/no-show das reservas continuam corretos; agora também **postam** lançamentos (limpeza de `ledger_entries`/`posted_event_entries` adicionada para isolamento) | SPEC-0010 |

### Arquitetura — `ArchitectureTest` (10) + `ModularityTests` (Spring Modulith `verify()`)
| Caso | Verifica |
|---|---|
| verifiesModularStructure | Spring Modulith **acíclico** com a nova dependência `finance → booking` (Finance lê só os eventos EXPOSTOS do Booking; Booking não depende de Finance/Compliance; `compliance → finance → booking` sem ciclo) |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 243, Failures: 0, Errors: 0, Skipped: 0`
(+4 da fatia, 239 → 243). Portões verdes: ArchUnit (10), Spring Modulith (acíclico), Spotless (405
arquivos limpos), Checkstyle (0 violações).

## Cobertura — o que NÃO está coberto e por quê

- **Accrual de comissão na confirmação (`ExpectedCommissionAccrued`) e liquidação ao fornecedor
  (`SupplierSettlement`):** **não há produtor** desses eventos hoje (nenhum módulo os publica) —
  consumi-los exigiria inventar o produtor, fora do escopo desta spec (Rule Zero). **Seam diferido**
  registrado em DL-0041: quando o evento existir, adiciona-se um listener idempotente análogo.
- **Concorrência real de re-entrega (race):** a proteção é a UNIQUE `(source_ref, charge_kind)` + o
  tratamento de `DataIntegrityViolationException`; no fluxo in-process síncrono a corrida não ocorre,
  então o teste exercita o caminho do **pré-check** (sequencial), que é o caso real. A UNIQUE cobre o
  caso concorrente por construção do banco.
- **Lançamento automático em período já CLOSED:** caminho de borda (descartado + logado, BR4) coberto
  por inspeção do código; não há teste dedicado porque o fluxo do Booking sempre usa o mês corrente.

## Como reproduzir

```bash
cd backend
./mvnw -q -Dtest=FinanceEventPostingIntegrationTest test     # os 4 casos da fatia
./mvnw -q -Dtest=CloseVetoIntegrationTest test               # a regra de ouro (regressão)
./mvnw verify                                                # tudo + portões (Docker no ar)
```
