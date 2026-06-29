# DL-0024 — Encargos são fatos distintos que NUNCA se compensam (a armadilha do merchant, modelada)

- **Fase:** 4 (Cancelamento como objeto + armadilha do merchant)
- **Spec(s):** SPEC-0010 (BR5; Business Context; "duas obrigações distintas que **não se anulam**";
  Tests Required: Regressão "reembolso ao cliente **não** zera a obrigação com o fornecedor")
- **ADR relacionado:** 0012
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Cara

## Lacuna

A sutileza econômica central da fase: sob `ALL_SALES_FINAL` merchant, a Acme **deve à marketplace** o
custo da reserva **e** (por decisão comercial) **reembolsa o cliente** — duas obrigações. Tratá-las
como uma só (subtrair uma da outra, "netting") **perde dinheiro de forma invisível**. Falta cravar a
modelagem que torna isso **impossível** de acontecer por acidente.

## Decisão

- Modelar cada obrigação como um **`Charge {kind, amount: Money, costBearer}`** independente, gravado
  em `cancellation_charges` (uma linha por encargo). `ChargeKind ∈ {PENALTY, SUPPLIER,
  CUSTOMER_REFUND, NO_SHOW}`.
- **Não existe operação de netting no domínio:** o serviço de cancelamento **adiciona** encargos a
  uma lista; **nunca** subtrai um do outro nem deriva um "valor líquido". `Money.subtract` jamais é
  usado entre `SUPPLIER` e `CUSTOMER_REFUND`.
- No `ALL_SALES_FINAL` merchant com reembolso, o cancelamento materializa **dois** registros:
  `SUPPLIER` (custo irrecuperável, devido) **e** `CUSTOMER_REFUND` (devolução ao cliente) — ambos
  positivos, ambos persistidos, ambos no `CancellationCharged`; e publica
  `MerchantObligationIncurred` para **tornar visível** a obrigação que não se anula (8.2-G/H).
- **Teste de regressão (a trava da armadilha):** cancelar uma venda `ALL_SALES_FINAL` com reembolso
  produz **exatamente** os dois encargos, com valores **independentes** (a soma das obrigações ≠ a
  diferença entre elas), e a obrigação com o fornecedor **permanece** mesmo havendo reembolso. Falha
  antes (se alguém "compensar"), passa depois.

## Justificativa

- **BR5 literal:** "registrar os encargos resultantes como **fatos distintos** … Eles **NÃO se
  compensam** automaticamente." A modelagem "lista de Charges sem subtração" é a tradução fiel.
- **Business Context da spec:** "Tratar como uma só (anular) **perde dinheiro de forma invisível**."
  A ausência de qualquer caminho de netting no código é o que garante a invariante.
- **Tornar visível** (OVERVIEW 8.2-G/H): o evento `MerchantObligationIncurred` existe justamente para
  o Intelligence/Finance enxergarem a exposição merchant em aberto — não some na conciliação.

## Alternativas descartadas

- **Calcular um "valor líquido a liquidar".** Descartada: é exatamente a armadilha; apagaria a
  obrigação com o fornecedor sob o reembolso (perda invisível).
- **Um único `Charge` com sinal (+/-) e somatório.** Descartada: convida ao netting e mistura moedas
  (DL-0022); a spec quer fatos distintos, não um saldo.

## Impacto

- `Charge` (value object), `CancellationCharge` (entidade interna), `cancellation_charges` (V13).
- `BookingService.cancel` só **acumula** encargos; `CancellationCharged` carrega a lista.
- Evento `MerchantObligationIncurred`. Teste de regressão da armadilha (a prova exigida pela fase).

## Como reverter

Reversão **cara**: a não-compensação é a tese econômica da fase. Mudar para netting exigiria
redesenhar o modelo de encargos, os eventos e os consumidores futuros (Finance/Payout/Intelligence)
— e contrariaria BR5 da spec. Só com decisão de negócio explícita e nova spec.
