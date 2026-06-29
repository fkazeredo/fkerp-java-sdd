# DL-0026 — Escopo do congelamento: global por par de moeda (v1)

- **Fase:** 5 (Câmbio com exposição + relatórios)
- **Spec(s):** SPEC-0011 (Open Questions: "Escopo do congelamento (global vs por agência/produto — 7.3
  'a confirmar') afeta como as posições agrupam; assumido global no v1")
- **ADR relacionado:** 0003 (single-tenant), 0014 (módulos iniciais)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A taxa congelada poderia, no futuro, ser escopada por **agência/produto** (OVERVIEW 7.3 "a confirmar").
Isso muda **como as `FxPosition` se agrupam** na exposição agregada do livro (`ExchangeExposure`): por par
apenas, ou por par × escopo.

## Decisão

- No v1, o congelamento é **global por par de moeda** (ex.: `USD/BRL` tem uma única taxa congelada vigente),
  exatamente como a SPEC-0003 já implementou (`PinnedSellRate` por par, sem escopo).
- A `FxPosition` guarda o `pinnedRate` e o `marketAtFreeze` que valeram **no instante da composição/
  confirmação** (proveniência — BR7), então o agregado `ExchangeExposure` soma por **par/moeda do livro**
  inteiro. Não há dimensão de escopo (agência/produto) nos relatórios desta fase.

## Justificativa

- Recomendação explícita do ROADMAP ("Recomendações para as Open Questions" → Câmbio congelado é taxa
  única global) e do OVERVIEW 7.3 ("Câmbio congelado é taxa única global").
- Alinha com a SPEC-0003 já entregue (taxa por par, sem escopo): introduzir escopo agora criaria
  divergência entre a taxa servida (global) e a posição (escopada) — incoerente.
- Rule Zero: não modelar uma dimensão de agrupamento que o negócio ainda não pediu; a proveniência
  guardada por posição permite reagrupar depois sem perda de dado.

## Alternativas descartadas

- **Escopo por agência/produto já no v1.** Descartada: o negócio marcou "a confirmar"; adicionaria uma
  dimensão especulativa aos read-models (`LiveExposure`/`PromoFxResult`) sem requisito, e desalinharia da
  taxa congelada global da SPEC-0003.

## Impacto

- `FxPosition` e os read-models agregam por par/livro, sem coluna de escopo. Se o escopo entrar depois, a
  proveniência (`pinnedRate`, `marketAtFreeze`, `bookingId`) já está guardada por posição; bastará um
  `scopeRef` adicional e novo agrupamento nos relatórios.

## Como reverter

Reversão **moderada**: adicionar `scope_ref` em `fx_positions` (migração aditiva) e um eixo de agrupamento
nos read-models. Não invalida posições já abertas (proveniência preservada). Depende de o congelamento por
escopo entrar antes na SPEC-0003/0014.
