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

> **Refinamento de fronteira (Modulith):** `quoting → exchange` e `booking → quoting → exchange` já
> existem. Se o `exchange` lesse `quoting`/`booking` para abrir a posição, formaria **ciclo** (
> `exchange → quoting → exchange`). A verificação do Spring Modulith reprovou exatamente isso. Solução
> sem afrouxar gate: **Reconciliation** (que já segura o `QuoteSnapshot` e o `supplierSettlementRate`,
> e é o módulo *folha* — nada depende dele) **dirige** a posição chamando o caso de uso de `exchange`
> com **valores primitivos**. Direção `reconciliation → exchange` é acíclica. O `exchange` continua
> dono da matemática de subsídio/drift/gap; nada é duplicado.

- **Abertura:** ao abrir o `ReconciliationCase` (em `BookingConfirmed`), Reconciliation chama
  `FxPositionService.openPosition(bookingId, foreignCost, pinnedRate, freezeInstant)` com o
  `snapshot.basePrice()` (custo em moeda estrangeira) e o `snapshot.pinnedRate()`. Dentro do `exchange`:
  - `marketAtFreeze = MarketRateProvider.marketRateAt(pair, freezeInstant)`;
  - `subsidy = (marketAtFreeze − pinnedRate) × foreignAmount` (BR3), escala 2 HALF_UP; publica
    `RateSubsidyAccrued`. **Idempotente** por `bookingId` (UNIQUE + pré-checagem).
  - **Só abre** quando o custo é em moeda estrangeira (`currency ≠ BRL`) **e** existe MarketRate vigente;
    senão registra log de negócio e **não** abre (não inventa `marketAtFreeze`).
- **Fechamento (reuso, sem duplicar):** ao registrar a liquidação, Reconciliation chama
  `FxPositionService.closePosition(bookingId, supplierSettlementRate, settleInstant)` **reusando** a taxa
  que ele já registrou (não recalcula o per-case). Dentro do `exchange`:
  - `realizedDrift = (settlementRate − marketAtFreeze) × foreignAmount` (BR5);
  - `totalGap = subsidy + realizedDrift`, que por identidade algébrica `== (settlementRate − pinnedRate) ×
    foreignAmount`. Publica `FxPositionClosed`.
- **Consistência provada (regressão):** `totalGap` (subsídio+drift do `exchange`) e o `fxGainLoss`
  por-caso de Reconciliation derivam ambos de `(taxa_liquidação − taxa_congelada) × foreignAmount`. O teste
  cruza os dois: Reconciliation define `fxGainLoss = (pinned − settlement) × amount`, logo
  `totalGap == −fxGainLoss`; o teste fixa essa relação com números exatos.

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

- `FxPositionService` (caso de uso público de `exchange`, recebe primitivos), `FxPosition` (entidade
  interna), `fx_positions` (`V15`); eventos `RateSubsidyAccrued`, `BookPositionDrifted`, `FxPositionClosed`.
- `ReconciliationService` passa a injetar `FxPositionService` e chamar `openPosition`/`closePosition`
  (direção `reconciliation → exchange`, acíclica). Nenhuma mudança de contrato em eventos existentes.

## Como reverter

Reversão **moderada**: trocar o gatilho de abertura ou a fonte da taxa de liquidação exigiria mexer no
listener e no evento consumido, mas não na matemática (que é a identidade do 7.2). A não-duplicação é
requisito da fase; abandoná-la contrariaria BR5 e a instrução.
