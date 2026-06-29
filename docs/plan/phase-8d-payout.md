# Plano — Sub-fase 8d: Payout (SPEC-0017) — repasse, liquidação, reembolso e parcelamento

> Modo autônomo (RUN-PHASE, FASE-ALVO=8, **escopo SPEC-0017 apenas**). Novo módulo **folha**
> `com.fksoft.domain.payout` (14º módulo Modulith). Executa as **saídas financeiras** da operação:
> **repassa a comissão ao agente**, **liquida o fornecedor** (moeda estrangeira, à taxa real),
> **executa reembolsos** (originados em cancelamento) e suporta **parcelamento** — sempre com
> **comprovante** arquivado no Compliance e baixa no Finance. A execução vai ao mundo externo por uma
> **ACL** (porta `PaymentGateway` + adaptador mock rastreável com **webhook assíncrono**, ADR 0006).
> Não toca nenhum outro SPEC.

## Objetivo (Acceptance Criteria da SPEC-0017)

1. Liquidar o fornecedor a **5,70** baixa **R$ 2.850** (USD 500 × 5,70), arquiva o comprovante e publica
   `SupplierSettled` (que o Finance consome → posta o AP exatamente uma vez).
2. Repassar a comissão ao agente e executar um reembolso de cancelamento geram comprovante e lançamento.
3. **Execução idempotente** (sem pagamento dobrado); webhook reentregue não confirma duas vezes.
4. Reembolso ao cliente **não** cancela a obrigação do fornecedor (armadilha do merchant intacta).
5. Pagamento que falha deixa estado **FAILED** explícito (sem "pago" falso).
6. `./mvnw verify` verde (ArchUnit + Spring Modulith + Spotless + Checkstyle).

## Decisões registradas ANTES do código (decision-log)

| DL | Lacuna | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| **DL-0048** | **Meio de pagamento/gateway real** (Open Question SPEC-0017) | Porta `PaymentGateway` + adaptador **mock rastreável com webhook assíncrono** (ADR 0006): `request` devolve `PENDING`; o callback `POST /api/webhooks/payouts/mock` confirma/falha; idempotente por `(payoutId, installmentSeq, providerRef)` (state-check + UNIQUE). Provedor real é trocar o adaptador. | **Baixa** | Moderada |
| **DL-0049** | Pagamento ao fornecedor em **moeda estrangeira** vs. liquidação em BRL (Open Question) | Modelar **ambos**: `amount` na moeda original (USD) + `settlementRate` (escala 6) + `settledBrl` (a baixa em BRL). É o número que fecha `fxGainLoss`/`realizedDrift`. Remessa internacional real fica para integração futura (mesmo adaptador). | **Baixa** | Cara |
| **DL-0050** | Política de **parcelamento** (quem pode, juros) — depende de CommercialPolicy (Open Question) | v1 **sem juros**; N parcelas com vencimento; soma das parcelas == total **exata** (distribuição de centavos: resto vai à 1ª parcela); cada parcela executa/comprova individualmente; Payout só `EXECUTED` quando **todas** executam. Juros/elegibilidade ficam para SPEC-0014/0017 futura. | Média | Moderada |
| **DL-0051** | Quem consome `SupplierSettled`; manter Payout folha | `SupplierSettled` é consumido **pelo Finance** (novo listener idempotente em `finance.internal`, igual a `finance → billing`/`finance → booking`). Reconciliation/Exchange já fecham FX pela liquidação própria (DL-0028) — **não** religados aqui para não duplicar e manter o grafo acíclico; ligação fica registrada como adiada. Payout é **folha**: publica eventos, não chama outros módulos. | Média | Moderada |

## Fronteira / Spring Modulith (acíclico)

- **`domain.payout` é folha:** depende só dos kernels (`money`, `error`) e da sua porta `PaymentGateway`.
  **Não importa** `finance` nem `compliance`.
- **Finance → Payout:** novo listener `SupplierSettlementEventsListener` em `finance.internal` consome
  `SupplierSettled` (igual a `finance → billing` do DL-0047). Posta `SUPPLIER_SETTLEMENT` PAYABLE
  idempotente por `(payoutId, "SUPPLIER_SETTLEMENT")` via `postFromCharge`. Sem ciclo.
- **Orquestração de execução e arquivamento** em `infra.integration.payment` (`PayoutExecutionService`):
  chama `PayoutService` (domínio), `PaymentGateway` (porta), `ComplianceService.upload` (fachada). Infra
  é isento da regra de ciclo entre módulos de domínio — mesmo padrão de `BillingIssuanceService` (8c) e
  `AfdIngestionService` (6).
- **ArchUnit novo:** `domain` não depende de `..infra.integration.payment..` (o vendor DTO do provedor de
  pagamento não vaza — ACL), análogo às Fases 3/6/8c. O teste-com-dentes planta a violação e falha.

## Webhook assíncrono (ADR 0006)

1. `POST /api/payouts/{id}/execute` (ou a próxima parcela) → `PayoutExecutionService` pede ao
   `PaymentGateway.request(...)`; o mock persiste um `MockPayoutJob` e devolve `providerRef` + `PENDING`.
   O payout/parcela vai a `EXECUTING` (não há "pago" síncrono).
2. Um job (`@Scheduled`, atraso configurável; em teste, disparo determinístico) faz o mock **POSTar** um
   webhook assinado (HMAC-SHA256) para `POST /api/webhooks/payouts/mock` com o desfecho
   (`SUCCEEDED`/`FAILED`, configurável por metadata para exercitar a falha).
3. O handler valida a assinatura, processa **idempotente** por `(payoutId, installmentSeq, providerRef)`
   (UNIQUE + state-check): sucesso → parcela `EXECUTED`, arquiva comprovante, publica evento; falha →
   `FAILED` (sem comprovante, sem evento de sucesso). Reentrega = no-op.

## Fatias (ordem de dependência)

- **8d-1** — Agregado `Payout` + parcelas + API `POST/GET/LIST` + `V21`. Máquina de status
  PENDING→EXECUTING→EXECUTED|FAILED; parcelas; conversão BRL por `settlementRate`; locking pessimista.
  Testes de domínio (centavos exatos, REFUND exige origem, status).
- **8d-2** — Porta `PaymentGateway` + adaptador mock + **webhook assíncrono** + `PayoutExecutionService`
  + handler idempotente + `V22` (mock jobs/processed webhooks). Testes de integração: confirma/falha
  idempotente; falha → FAILED.
- **8d-3** — `SupplierSettled` + listener no Finance (posta uma vez) + arquivamento do **comprovante**
  (PAYMENT_PROOF/REFUND_PROOF) no Compliance + reembolso (armadilha do merchant intacta). Testes de
  integração ponta a ponta + regressão.

## Definition of Done

- `./mvnw verify` verde; ArchUnit/Modulith (14 módulos, acíclico)/Spotless/Checkstyle passam.
- `V21`/`V22` aplicadas e validadas (Postgres real); i18n pt-BR + fallback; `HttpErrorMapping` completo.
- Specs Open Questions → ASSUMIDO (ver DL-0048..0051); caderno de testes em docs/test-report/ + INDEX.
- Merge por fatia em `develop`; release `0.12.0` (tag, main+develop), release note.
