# DL-0028 — Abertura da `FxPosition` em `BookingConfirmed`; fechamento reusa a taxa de liquidação de Reconciliation

- **Fase:** 5 (Câmbio com exposição + relatórios)
- **Spec(s):** SPEC-0011 (BR2 abertura na confirmação da venda com custo em moeda estrangeira; BR5
  fechamento na liquidação "SPEC-0007 fornece supplierSettlementRate"; Tests Required: Regressão
  "`totalGap` da posição == per-case `fxGainLoss` (sinal) da SPEC-0007")
- **ADR relacionado:** 0012 (camadas), 0001 (monólito modular, eventos in-process)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0011 diz **quando** abrir ("ao confirmar uma venda com custo em moeda estrangeira precificada à
taxa congelada") e **quando** fechar ("ao registrar a liquidação, SPEC-0007 fornece o
`supplierSettlementRate`"), mas não crava **qual gatilho técnico** dispara cada um nem **como evitar
duplicar** a matemática por-caso que Reconciliation já faz.

## Decisão

- **Abertura:** o módulo `exchange` consome o evento **`BookingConfirmed`** (já publicado por Booking,
  já consumido por Reconciliation — mesma fonte de verdade). No listener, lê o `QuoteSnapshot` via a porta
  pública `QuoteDirectory`:
  - `foreignAmount = basePrice.amount()`, `currency = basePrice.currency()` (o custo em moeda estrangeira);
  - `pinnedRate = snapshot.pinnedRate()`;
  - `marketAtFreeze = MarketRateProvider.marketRateAt(pair, confirmationInstant)`.
  - `subsidy = (marketAtFreeze − pinnedRate) × foreignAmount` (BR3), escala 2 HALF_UP. Publica
    `RateSubsidyAccrued`. **Idempotente** por `bookingId` (UNIQUE + pré-checagem), igual a Reconciliation.
  - **Só abre** quando há custo em moeda estrangeira (`currency ≠ BRL`) **e** existe MarketRate vigente; se
    faltar a taxa de mercado, registra log de negócio e **não** abre (não inventa um `marketAtFreeze`).
- **Fechamento (reuso, sem duplicar):** Reconciliation já calcula `fxGainLoss` por caso a partir do
  `supplierSettlementRate`. Para **fechar** a posição, o `exchange` consome um evento de liquidação que
  carrega a taxa: estende `SpreadRealized` de Reconciliation (SPEC-0007) com o `bookingId` e o
  `supplierSettlementRate` já registrados — o `exchange` é **consumidor** desse fato, não recalcula o
  per-case. Com a taxa:
  - `realizedDrift = (settlementRate − marketAtFreeze) × foreignAmount` (BR5);
  - `totalGap = subsidy + realizedDrift`, que por identidade algébrica `== (settlementRate − pinnedRate) ×
    foreignAmount`. Publica `FxPositionClosed`.
- **Consistência provada (regressão):** `totalGap` (com o **sinal** subsídio+drift do `exchange`) e o
  `fxGainLoss` por-caso de Reconciliation derivam ambos de `(taxa_liquidação − taxa_congelada) ×
  foreignAmount` — o teste cruza os dois e prova que batem em módulo (atenção ao sinal: Reconciliation
  define `fxGainLoss = (pinned − settlement) × amount`, então `totalGap == −fxGainLoss`; o teste fixa essa
  relação).

## Justificativa

- **Não duplicar** (instrução da fase + BR5): o `supplierSettlementRate` e o per-case já vivem em
  Reconciliation; o `exchange` **reusa/agrega** consumindo o fato, não recomputando.
- **Mesmo gatilho que Reconciliation** (`BookingConfirmed`) garante que toda venda confirmada com custo
  estrangeiro abre exatamente uma posição, atomicamente com a transição (listener in-process, como
  `BookingEventsListener` de Reconciliation).
- A identidade `subsidy + realizedDrift == (settlement − pinned) × amount` é a tese econômica do 7.2 —
  testá-la prova que a decomposição não cria nem destrói dinheiro.

## Alternativas descartadas

- **Abrir a posição direto no Quoting/Booking (acoplamento de escrita).** Descartada: violaria as
  fronteiras (Modulith) e a regra "exposição é capacidade do `Exchange`"; eventos in-process são o
  mecanismo correto.
- **Recalcular o gap por caso dentro do `exchange`, ignorando Reconciliation.** Descartada: duplicaria a
  matemática por-caso (a instrução proíbe) e arriscaria divergência entre os dois números.
- **Fechar por chamada de porta síncrona do controller de settlement.** Descartada em favor do evento
  (`SpreadRealized` estendido): mantém o `exchange` como consumidor desacoplado e idempotente.

## Impacto

- Listener `BookingEventsListener`-equivalente em `exchange.internal`; consumo de `SpreadRealized` (estendido
  com `bookingId` + `supplierSettlementRate`) de Reconciliation; `FxPosition` (entidade), `fx_positions`
  (`V15`); eventos `RateSubsidyAccrued`, `BookPositionDrifted`, `FxPositionClosed`. SPEC-0007: `SpreadRealized`
  ganha campos aditivos (retrocompatível — MINOR).

## Como reverter

Reversão **moderada**: trocar o gatilho de abertura ou a fonte da taxa de liquidação exigiria mexer no
listener e no evento consumido, mas não na matemática (que é a identidade do 7.2). A não-duplicação é
requisito da fase; abandoná-la contrariaria BR5 e a instrução.
