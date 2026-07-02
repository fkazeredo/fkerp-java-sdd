# DL-0049 — Payout: liquidação do fornecedor com `settlementRate` (moeda estrangeira) + baixa em BRL

- **Fase:** 8d (Payout — SPEC-0017)
- **Spec(s):** SPEC-0017 (Open Question "Pagamento ao fornecedor em moeda estrangeira … vs. liquidação
  em BRL — confirmar o fluxo real"; BR1 `settlementRate` escala 6 > 0 + valor em BRL liquidado; BR5
  `SupplierSettled {bookingId, settlementRate, paidBrl}`; Acceptance "liquidar a 5,70 baixa R$ 2.850")
- **ADR relacionado:** `architecture/backend.md` (money, escala/HALF_UP); SPEC-0011 (FxPosition,
  `realizedDrift`/`totalGap`) e SPEC-0007 (`fxGainLoss`) que consomem a taxa de liquidação
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Cara

## Lacuna

A SPEC deixa em aberto se o fornecedor é pago **em moeda estrangeira** (remessa internacional, fechamento
de câmbio) ou **liquidado em BRL** à taxa real. O número exige modelagem precisa porque é ele que fecha o
`fxGainLoss` (SPEC-0007) e o `realizedDrift` (SPEC-0011).

## Decisão

**Modelar ambos os lados, sem escolher um fluxo bancário concreto:**

1. O `Payout` carrega `amount` na **moeda original** (ex.: USD 500) e, quando estrangeira,
   `settlementRate` (`numeric(18,6)`, **escala 6, > 0** — BR1) e `settledBrl` = `amount × settlementRate`
   normalizado a **escala 2, HALF_UP** (kernel `Money`). Ex.: USD 500 × 5,70 = **R$ 2.850,00** (baixa em
   BRL, Acceptance Criteria).
2. A baixa no Finance (via `SupplierSettled` → listener) usa **`settledBrl`** (a obrigação em reais que o
   razão fecha). O `settlementRate` viaja no evento para que Reconciliation/Exchange possam fechar
   `fxGainLoss`/`drift` (consistência ponta a ponta — Tests Required de regressão).
3. O **fechamento de câmbio / remessa internacional real** (qual banco, IOF, spread bancário) é
   **integração futura**, atrás do mesmo `PaymentGateway` (DL-0048) — não modelado agora (Rule Zero: não
   antecipar o que o negócio ainda não deu).

## Justificativa

- BR1 e o exemplo de Input/Output e o Acceptance Criteria são **explícitos**: USD com `settlementRate`
  6 casas e `settledBrl`. Modelar os dois lados é o que a spec pede e o que fecha a conciliação.
- Guardar a taxa **e** o BRL liquidado (não só um) preserva a proveniência: a conciliação compara a taxa
  servida (congelada) × a taxa real da liquidação — só mensurável se ambas existem (OVERVIEW 7.2/7.5).
- Escala 6 na taxa e 2 no dinheiro segue a convenção de money (`backend.md`); a multiplicação normaliza
  uma vez (sem vazamento de arredondamento).

## Alternativas descartadas

- **Liquidar só em BRL (sem guardar a taxa).** Descartada: perderia o número que fecha `fxGainLoss`/drift;
  contraria BR1/BR5.
- **Pagar só em moeda estrangeira (sem `settledBrl`).** Descartada: o razão e o fechamento mensal são em
  BRL; sem a baixa em reais a conciliação não fecha.
- **Implementar remessa internacional real agora.** Descartada: fluxo bancário é Open Question de negócio;
  fixá-lo seria inventar regra. O mock + o par taxa/BRL provam o contrato.

## Impacto

- **Specs:** SPEC-0017 Open Question "moeda estrangeira vs BRL" → **ASSUMIDO (ver DL-0049)**.
- **Arquivos:** colunas `settlement_rate`/`settled_brl` em `payouts` (V21); cálculo em
  `Payout`/`PayoutService`; `SupplierSettled` carrega `settlementRate`+`paidBrl`; listener do Finance usa
  `paidBrl`.
- **Contratos:** evento `SupplierSettled` (consumido por Finance; Reconciliation/Exchange = costura
  adiada, DL-0051).

## Como reverter

Reversão **cara**: se o negócio definir que o fornecedor é pago **só** em uma das formas, muda-se a
modelagem do agregado (uma das colunas deixa de existir), o evento e o que a conciliação espera — e
liquidações já gravadas ficam com semântica diferente (dado histórico). Por isso **Reversibilidade=Cara**:
mexe na tese econômica de câmbio que três contextos (Payout/Reconciliation/Exchange) compartilham.

## Revisão — Fase 19b (2026-07-02)

**MANTIDA.** O par `amount` (moeda estrangeira) + `settlementRate` + `settledBrl` é compatível
com o fluxo cambial real brasileiro (contrato de câmbio; marco legal da Lei 14.286/2021). Custos
de liquidação (IOF/spread bancário) entram como campos opcionais na fatia de forwards (19h),
fechando o seam sem mudar a tese. O fluxo bancário real segue Open Question (checklist 19l).
