# DL-0051 — Payout: `SupplierSettled` consumido pelo Finance (folha, acíclico); refund não cancela fornecedor

- **Fase:** 8d (Payout — SPEC-0017)
- **Spec(s):** SPEC-0017 (BR4 baixar/lançar no Finance; BR5 `SupplierSettled` consumido por Reconciliation
  e Exchange; BR7 REFUND referencia a obrigação de origem; Events `SupplierSettled`/`RefundExecuted`/
  `AgentCommissionPaid`)
- **ADR relacionado:** 0001 (monólito modular), 0012; DL-0041/DL-0047 (Finance consome evento,
  idempotente); DL-0024 (armadilha do merchant — cobranças nunca se compensam); DL-0028 (Reconciliation
  já fecha FX na própria liquidação)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0017 diz que `SupplierSettled` é consumido por **Reconciliation** e **Exchange**. Mas (a) o
supervisor exige que o **Finance** poste o AP da liquidação **exatamente uma vez** ao consumir o evento;
(b) Reconciliation/Exchange **já fecham** a posição FX pela própria liquidação (`recordSettlement` →
`FxPosition.closePosition`, DL-0028) — religar os dois a `SupplierSettled` **duplicaria** o fechamento e
**arriscaria um ciclo** no grafo Modulith. Era preciso decidir quem consome o evento sem quebrar a
acaclicidade e sem dupla contagem, e como o REFUND se relaciona com a obrigação do fornecedor.

## Decisão

1. **`SupplierSettled` é consumido pelo Finance** — novo listener module-internal
   `SupplierSettlementEventsListener` em `finance.internal`, **idêntico em forma** ao listener de Billing
   (DL-0047) e Booking (DL-0041): reusa `FinanceService.postFromCharge(sourceRef=payoutId,
   chargeKind="SUPPLIER_SETTLEMENT", PAYABLE, EntryType.SUPPLIER_SETTLEMENT, paidBrl)` — **idempotente**
   por `(payoutId, "SUPPLIER_SETTLEMENT")` (UNIQUE + state-check). Reentrega = no-op (postado **uma vez**,
   provado por teste). `finance → payout` (consome o evento EXPOSTO), **sem** Payout chamar o Finance.
2. **Payout é folha:** depende só dos kernels (`money`, `error`) e da sua porta `PaymentGateway`. **Não**
   importa finance/compliance/reconciliation/exchange. Publica eventos; quem precisa, consome.
   `SupplierSettled` carrega `{payoutId, bookingId, settlementRate, paidBrl, occurredAt}`.
3. **Reconciliation/Exchange NÃO são religados a `SupplierSettled` nesta fatia.** Eles já fecham o caso e
   a posição FX pela liquidação própria (DL-0028). Reapontá-los para o evento do Payout duplicaria o
   fechamento e poderia formar ciclo (`reconciliation → payout → … `). Mantém-se o fechamento existente; a
   **costura "Payout dirige o fechamento de FX"** fica **registrada como pendência** (uma fatia futura
   pode migrar o gatilho de `recordSettlement` para `SupplierSettled` se o negócio quiser que a liquidação
   física do fornecedor — não o registro contábil — seja o marco). Isso **não** reduz cobertura: a
   consistência ponta a ponta `settlementRate → fxGainLoss/totalGap` continua provada pela regressão
   existente da SPEC-0007/0011.
4. **REFUND não cancela a obrigação do fornecedor (armadilha do merchant intacta, DL-0024):** um Payout
   `REFUND` referencia a obrigação de origem (`originRef`, BR7) e, ao executar, gera **comprovante
   REFUND_PROOF** e a baixa do reembolso — **sem** tocar nenhuma cobrança/obrigação do fornecedor. As duas
   são fatos distintos que nunca se compensam. Regressão verde: executar um REFUND ao cliente deixa a
   PAYABLE do fornecedor (do Booking/Finance) **intacta**.

## Justificativa

- O supervisor é explícito: **Finance posta a liquidação do fornecedor exatamente uma vez**; o padrão
  event-driven idempotente do Finance (DL-0041/0047) já existe e é reusado — risco mínimo, sem ciclo.
- `messaging-and-integrations.md`/Modulith exigem grafo **acíclico**; Payout folha + Finance consumidor é
  a forma já validada três vezes no projeto (booking, billing).
- DL-0024 é tese da Fase 4 (Reversibilidade=Cara): reembolso e cobrança do fornecedor **nunca** se
  anulam. Payout só **executa** o reembolso que já foi decidido (Cancellation/AfterSales) — não decide nem
  estorna a obrigação do fornecedor.
- Não duplicar o fechamento de FX respeita Rule Zero (a Reconciliation já faz; não criar um segundo
  caminho que poderia divergir).

## Alternativas descartadas

- **Religar Reconciliation/Exchange a `SupplierSettled` agora.** Descartada: duplicaria o fechamento de FX
  (DL-0028) e arriscaria ciclo `reconciliation → payout`. A consistência já é provada sem isso. Fica
  registrada como costura adiada, não como dívida silenciosa.
- **Payout chamar `FinanceService` direto.** Descartada: acopla e quebra a folha; o evento idempotente é o
  padrão.
- **REFUND estornar/anular a cobrança do fornecedor.** Descartada: viola DL-0024 (armadilha do merchant) —
  são obrigações distintas.

## Impacto

- **Specs:** SPEC-0017 BR4/BR5/BR7 concretizadas; Events confirmados; nota de que Reconciliation/Exchange
  permanecem fechando FX pela liquidação própria (costura ao evento adiada).
- **Arquivos:** `SupplierSettlementEventsListener` em `finance.internal`; eventos `SupplierSettled`/
  `AgentCommissionPaid`/`RefundExecuted` no `payout`; `posted_event_entries` reusado.
- **Modulith:** `payout`→∅; `finance → payout` (consome o evento). **Grafo acíclico.**

## Como reverter

Reversão **moderada**: para fazer Reconciliation/Exchange reagirem a `SupplierSettled`, adicionar um
listener nesses módulos consumindo o evento **e** remover o fechamento por `recordSettlement` (para não
duplicar) — refator localizado, com cuidado para não criar ciclo. Remover o consumo do Finance é apagar um
listener. Nenhum contrato público muda — só ganham/perdem consumidores.
