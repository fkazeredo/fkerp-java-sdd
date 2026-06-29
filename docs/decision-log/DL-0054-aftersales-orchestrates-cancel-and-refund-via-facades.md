# DL-0054 — AfterSales orquestra cancelamento (Booking) e reembolso (Payout) via fachadas, idempotente, sem ciclo

- **Fase:** 8e (AfterSales — SPEC-0018)
- **Spec(s):** SPEC-0018 (BR2 CANCELLATION_REQUEST → Booking.cancel; BR3 REFUND_REQUEST → Payout
  REFUND referenciando a origem, sem reembolso solto; BR6 AfterSales não reverte comissão nem lança
  financeiro por conta própria; Validation: encaminhamentos idempotentes — não cria 2 Payouts).
- **ADR relacionado:** 0012 (camadas/fronteiras), modules-and-apis.md (colaboração só via fachadas
  públicas + eventos); DL-0024/DL-0051 (armadilha do merchant); DL-0049/DL-0050 (Payout).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0018 diz que o AfterSales **encaminha** ao Booking e ao Payout, mas não fixa (a) **como**
chama (fachada direta × evento); (b) **como** garante idempotência do reembolso; (c) o
**posicionamento no grafo** Modulith (quem depende de quem, sem ciclo); (d) a forma da
`resolution`.

## Decisão

1. **Chamada por fachada pública (síncrona) no `resolve`.** O `AfterSalesService.resolve` é o
   orquestrador: para `REFUND_APPROVED` chama `PayoutService.create(CreatePayoutCommand(kind=REFUND,
   originRef=<caseId>, amount, payee=CUSTOMER, bookingId))`; para `CANCEL_APPROVED` chama
   `BookingService.cancel(bookingId, reason, serviceStartsAt, refundAmount, actor)`. AfterSales
   **depende** das fachadas `payout` e `booking` (são módulos folha quanto a AfterSales — nenhum
   deles importa `aftersales`), então o grafo Modulith continua **acíclico**.
2. **Reembolso idempotente (não cria 2 Payouts, BR3/Validation):** o caso guarda `linkedPayoutId`.
   Se `resolve(REFUND_APPROVED)` é chamado e o caso **já** tem `linkedPayoutId`, **não** cria outro
   Payout (retorna o existente / lança `aftersales.case.transition.invalid` se já RESOLVED). O
   `originRef` do Payout = o **id do caso** (referência estável à obrigação de origem, BR3) — um
   reembolso por caso. Assim a reentrega/duplo-submit não duplica a saída financeira.
3. **AfterSales não muda estado de reserva nem lança financeiro (BR2/BR6):** quem cancela a reserva
   e aplica a multa é o Booking (SPEC-0010); quem executa o pagamento e baixa no Finance é o
   Payout/Finance (eventos dos donos, DL-0051). O AfterSales só **decide e encaminha** e registra o
   `linkedPayoutId`/custo de servir. **A armadilha do merchant fica intacta** (DL-0024): o Payout
   REFUND referencia a origem e **não** cancela a obrigação do fornecedor — regressão existente
   (`RefundMerchantTrapIntegrationTest`) **permanece verde** e ganhamos uma regressão própria
   (caso de reembolso resolvido aciona Payout exatamente uma vez e a PAYABLE do fornecedor fica).
4. **`resolution` é um enum** `CaseResolution` (ex.: `REFUND_APPROVED`, `CANCEL_APPROVED`,
   `RESOLVED_NO_ACTION`, `REJECTED`) — enum-com-comportamento define se aciona Payout e/ou Booking.
   `INFO`/`COMPLAINT` resolvem sem efeito colateral externo.
5. **Eventos próprios:** `SupportCaseOpened`/`SupportCaseResolved` (produtor `aftersales`,
   consumidor `intelligence` — custo de servir/sinais). Consumo de eventos de outros módulos
   (ex.: `BookingCancelled`) **não** é necessário para o fluxo desta fatia e fica de fora (Rule
   Zero — só consumir quando a spec exigir); AfterSales permanece o mais **folha** possível.

## Justificativa

- modules-and-apis.md manda colaboração via **fachadas públicas + eventos**; o `resolve` é um
  caso de uso síncrono onde o usuário espera o `payoutId`/resultado — fachada direta é o ajuste
  (o exemplo da spec retorna `payoutId` no 200). Evento seria assíncrono e esconderia o resultado.
- Idempotência por `linkedPayoutId` usa "state-check antes de infra complexa"
  (messaging-and-integrations.md) — o caso é o registro idempotente natural (um refund por caso).
- O supervisor exige: "trigger it through the existing Payout module via event/command — do NOT
  duplicate refund execution, and the merchant-trap invariant must stay intact". A chamada de
  fachada com `originRef` + guarda `linkedPayoutId` cumpre isso sem reimplementar reembolso.
- Acíclico: `aftersales → {payout, booking, commercialpolicy}`; nenhum deles → `aftersales`.

## Alternativas descartadas

- **Encaminhar por evento (AfterSales publica, Payout consome).** Descartada no v1: o `resolve`
  precisa devolver o `payoutId` síncrono (contrato da spec) e a idempotência fica mais simples com
  a fachada + `linkedPayoutId`. (Pode evoluir para outbox se o volume exigir.)
- **AfterSales criar a baixa financeira do reembolso.** Descartada: viola BR6 — Finance baixa
  consumindo `RefundExecuted` do Payout (DL-0051).
- **AfterSales estornar/anular a obrigação do fornecedor ao reembolsar.** Descartada: viola
  DL-0024 (armadilha do merchant). São fatos distintos.
- **Consumir `BookingCancelled`/`BookingChanged` agora.** Descartada: não exigido pelo fluxo desta
  fatia; adicionaria acoplamento sem necessidade (Rule Zero).

## Impacto

- **Specs:** SPEC-0018 — BR2/BR3/BR6 e Validation (idempotência) concretizadas como "ASSUMIDO
  (ver DL-0054)".
- **Arquivos:** `AfterSalesService.resolve` (orquestra), `CaseResolution` (enum), `linkedPayoutId`
  no agregado; depende de `PayoutService`/`BookingService` (fachadas). Eventos `SupportCaseOpened`/
  `SupportCaseResolved`.
- **Modulith:** `aftersales → payout, booking, commercialpolicy`. **Grafo acíclico.**
- **Contratos:** `POST /cases/{id}/resolve` retorna `status` + `payoutId` (quando reembolso).

## Como reverter

Moderada: trocar a fachada síncrona por evento/outbox é introduzir um produtor em AfterSales e um
consumidor no Payout, removendo a chamada direta (e ajustando o retorno do `resolve` para
assíncrono) — refator localizado de uma classe. A guarda de idempotência (`linkedPayoutId`)
permanece útil em qualquer das formas.
