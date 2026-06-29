# Relatório da Fase 4 — Cancelamento como objeto + Armadilha do Merchant + No-Show

- **Spec:** SPEC-0010 (gradua SPEC-0006). ADRs: 0011, 0012, 0014, 0015.
- **Versão:** `0.5.0` (MINOR, ADR 0015). Migrações `V12`, `V13`.
- **Resultado:** `cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 157, Failures: 0,
  Errors: 0, Skipped: 0` (135 → 157, +22). 0 violações Checkstyle. 10 módulos Modulith (o
  cancelamento mora no `booking` — DL-0020). Docker up (Testcontainers/Postgres real).

## Fatias entregues

| Fatia | Branch | Entrega | Testes |
|---|---|---|---|
| 9a | `feature/slice-9a-cancellation-policy-object` | `CancellationPolicy` como objeto (cálculo de multa por janela, costBearer merchant) + fonte administrável (`V12`) + `GET/PUT /api/products/{ref}/cancellation-policy` | 145 |
| 9b | `feature/slice-9b-cancel-charges-merchant-trap` | Congelamento na confirmação (`V13`) + cancelamento rico + **armadilha do merchant** (2 obrigações que não se anulam) + eventos | 151 |
| 9c | `feature/slice-9c-no-show-policy` | `NoShowPolicy` + `POST /api/bookings/{id}/no-show` (dispensa por prova) + `NoShowCharged` | 157 |

Cada fatia: feature branch a partir de `develop` → RED → verde → `./mvnw verify` → merge `--no-ff` em
`develop` → push. Release `release/0.5.0` → `main` + `develop` → tag `0.5.0`.

## Como a armadilha do merchant é modelada e testada

- **Modelagem:** `CancellationCharges.compute(...)` (calculador de domínio **puro**) só **acumula**
  `Charge`s numa lista; **nunca** subtrai um do outro nem deriva um valor líquido (DL-0024). Sob
  `ALL_SALES_FINAL`, emite `SUPPLIER` (custo integral, irrecuperável) **e**, havendo `refundAmount`,
  um `CUSTOMER_REFUND` separado; o `costBearer` de ambos vem da regra merchant (BR8/DL-0021:
  merchant → ACME). Cada `Charge` carrega seu próprio `Money` (moedas diferentes ⇒ nem poderiam ser
  líquidos — DL-0022). Persistidos em `cancellation_charges` (uma linha por encargo) e expostos via
  `CancellationCharged` + `MerchantObligationIncurred` (8.2-G/H).
- **Prova (regressão exigida pela fase):**
  - **Unit** `CancellationChargesTest.merchantAllSalesFinalWithRefundProducesTwoObligationsThatDoNotNetOut`:
    ALL_SALES_FINAL merchant + reembolso → SUPPLIER 500 USD (integral, não 500−480) + CUSTOMER_REFUND
    480 BRL; ambos ACME.
  - **E2E** `MerchantTrapIntegrationTest.merchantAllSalesFinalCancellationWithRefundRecordsTwoObligationsThatDoNotNetOut`:
    ponta a ponta (política → confirma/congela → cancel com reembolso) → `CancellationResult` com os
    dois encargos **e** duas linhas em `cancellation_charges`; o custo com o fornecedor **sobrevive**
    ao reembolso.

## Arquivos criados/alterados (principais)

- **Domínio (`com.fksoft.domain.booking`):** `CancellationPolicy`, `CancellationType`, `PenaltyWindow`,
  `CostBearer`, `NoShowPolicy`, `Charge`, `ChargeKind`, `CancellationCharges`, `CancellationResult`,
  `NoShowResult`, eventos `CancellationCharged`/`MerchantObligationIncurred`/`NoShowCharged`,
  `CancellationPolicyView`, `CancellationPolicyInvalidException`, `CancellationPolicyAdminService`;
  `BookingService` (congela snapshot, `cancel`, `noShow`); `package-info` atualizado.
- **Internal:** `CancellationPolicySource`(+repo), `BookingCancellationSnapshot`(+repo),
  `CancellationCharge`(+repo), `PenaltyWindowsCodec`; `Booking` (+`scopeRef`).
- **Delivery:** `CancellationPolicyAdminController`; `BookingController` (cancel/no-show);
  DTOs `CancellationPolicyRequest`, `CancelBookingRequest` (rico), `CreateBookingRequest` (+scopeRef),
  `NoShowRequest`.
- **Infra/recursos:** `HttpErrorMapping` (+`cancellation.policy.invalid`); `messages*.properties`;
  `OpenApiConfig`; `V12`/`V13`.
- **Docs:** plano `docs/plan/phase-4-cancellation-merchant.md`; DL-0020…0024 + INDEX; SPEC-0010
  (Open Questions → ASSUMIDO, BR8…BR11); cadernos `docs/test-report/slice-9a/9b/9c` + INDEX;
  `docs/MANUAL.md`; `docs/release-notes/0.5.0.md`.

## Testes por tipo

- **Unit/domínio:** `CancellationPolicyTest` (6), `CancellationChargesTest` (4, inclui a armadilha),
  `NoShowPolicyTest` (4).
- **Integração (Testcontainers):** `CancellationPolicyAdminIntegrationTest` (4),
  `MerchantTrapIntegrationTest` (2, inclui a armadilha e2e), `NoShowIntegrationTest` (2); regressões de
  ciclo em `BookingIntegrationTest`/`ReconciliationIntegrationTest` ajustadas e verdes.
- **Arquitetura:** ArchUnit + Spring Modulith + `HttpErrorMappingCompletenessTest` verdes.
- **Total:** 157 (+22 na fase).

## Impacto em OpenAPI

`cancel` e `no-show` mudaram o corpo de resposta (de `BookingView` para `CancellationResult`/
`NoShowResult`) e o corpo de requisição do cancel; novos endpoints de política. `OpenApiConfig` em
`0.5.0`. É a graduação prevista da SPEC-0006 → MINOR (ADR 0015).

## Decisões (decision-log)

| DL | Título | Conf. | Rev. |
|---|---|---|---|
| [DL-0020](decision-log/DL-0020-cancellation-lives-in-booking-module.md) | Cancelamento mora no `booking` | Alta | Moderada |
| [DL-0021](decision-log/DL-0021-merchant-of-record-attribute-default-affiliate.md) | merchantOfRecord atributo; default afiliado | Alta | Moderada |
| [DL-0022](decision-log/DL-0022-penalty-currency-no-conversion.md) | Encargos na moeda original (sem conversão) | Média | Barata |
| [DL-0023](decision-log/DL-0023-no-show-waiver-proof-flag.md) | No-show: prova rastreável | Média | Barata |
| [DL-0024](decision-log/DL-0024-charges-are-distinct-facts-never-netted.md) | Encargos nunca se compensam | Alta | **Cara** |

> **Destaque:** DL-0024 é **Reversibilidade Cara** (a tese econômica da fase). Nenhuma decisão desta
> fase ficou **Confiança Baixa** (a única Open Question de negócio, Q3, segue a Recomendação do
> ROADMAP — DL-0021, Confiança Alta; o **default** afiliado continua confirmável pelo dono).

## Riscos / pendências

- Eventos de encargo **sem consumidor** (Finance/Payout/Intelligence — fases futuras): in-process.
- Conversão cambial da multa adiada (DL-0022); conformidade do documento de no-show adiada (DL-0023).
- Telas Angular: backend-first (dívida das Fases 2–3).

## Para a próxima fase

Fase 5 — Câmbio com exposição (subsídio × drift) + primeiros relatórios (SPEC-0011).
