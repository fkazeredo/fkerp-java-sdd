# DL-0011 — Reconciliation: tolerância de discrepância

- **Fase:** 1 (Núcleo comercial manual)
- **Spec(s):** SPEC-0007 (vira parâmetro governado na SPEC-0014)
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0007 (BR7) marca um caso como `DISCREPANCY` quando `|realizedSpread − expectedSpread|`
excede uma **tolerância configurável**, mas o valor default fica "a confirmar" (vira
`CommercialPolicy` na SPEC-0014).

## Decisão

Adotar a recomendação do arquiteto (`docs/ROADMAP.md`): **tolerância = maior entre `R$ 1,00` e
`0,5% do spread esperado`** — i.e. `tolerance = max(1.00, 0.005 × |expectedSpread|)`. Acima disso
o caso vira `DISCREPANCY` e publica `ReconciliationDiscrepancyFlagged`.

## Justificativa

- Recomendação explícita do ROADMAP para este parâmetro (adotada em modo autônomo, `RUN-PHASE.md`).
- O piso de **R$ 1,00** evita falso-positivo em spreads pequenos (centavos de arredondamento); o
  **0,5%** escala com o tamanho da venda.
- Entra como `SYSTEM_DEFAULT` quando a SPEC-0014 (parâmetros governados) graduar — sem refatorar a
  fórmula, só a fonte do número.

## Alternativas descartadas

- **Valor absoluto fixo (ex.: R$ 50).** Descartado: não escala com o tamanho da venda; gera ruído em
  vendas grandes e cega vendas pequenas.
- **Percentual puro (sem piso).** Descartado: em spreads pequenos, qualquer centavo de arredondamento
  viraria discrepância.

## Impacto

- `reconciliation`: cálculo de `DISCREPANCY` e publicação de `ReconciliationDiscrepancyFlagged`.
  O número é uma constante nesta fatia; migra para `CommercialPolicy` (SPEC-0014).

## Como reverter

Trocar a constante (ou plugar o `CommercialPolicy` provider) — mudança barata e localizada.
