# DL-0022 — Multa e encargos na moeda original (sem conversão cambial nesta fase)

- **Fase:** 4 (Cancelamento como objeto + armadilha do merchant)
- **Spec(s):** SPEC-0010 (Open Question: "Multa em **moeda estrangeira** vs BRL (conversão pela taxa
  de quando?) — confirmar"; BR2/BR5; Out of Scope)
- **ADR relacionado:** 0012; relaciona-se com Exchange (SPEC-0003/0011)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A multa pode incidir sobre um valor em moeda estrangeira (custo do fornecedor em USD) ou em BRL (o
que o cliente pagou). A spec pergunta **se** e **quando** converter. Misturar moedas num mesmo
encargo, ou converter pela taxa errada, "perde dinheiro de forma invisível" — o oposto do objetivo
da fase.

## Decisão

- **Cada `Charge` carrega seu próprio `Money` (amount + currency); não há conversão nesta fase.**
  - `PENALTY` e `SUPPLIER` são calculados sobre o valor de referência **na moeda desse valor** (ex.:
    custo do fornecedor em USD ⇒ encargo em USD).
  - `CUSTOMER_REFUND` é na **moeda do que o cliente pagou** (informado no cancel como
    `refundAmount`, um `Money`).
  - A não-compensação (DL-0024) é, portanto, **também** uma consequência de moedas potencialmente
    diferentes: encargos em moedas distintas nem poderiam ser subtraídos — reforça a regra.
- O cálculo da multa usa **`Money.multiply(penaltyPct)`** (scale 2, HALF_UP) — a aritmética de
  dinheiro do kernel, que **exige** mesma moeda em soma/subtração.

## Justificativa

- **Out of Scope da própria SPEC-0010:** "execução do reembolso (Payout), lançamento contábil
  (Finance)". A **conversão** para um razão único é trabalho de Finance/Reconciliation (cruzar a
  pagar em moeda estrangeira × a receber em BRL — OVERVIEW 7.5), não do cancelamento.
- **Regra Zero:** inventar aqui uma taxa de cancelamento (qual? a congelada? a do dia?) seria regra
  de negócio não decidida — proibido (invariante 3). O `Exchange` congelado serve composição de
  cotação, não cancelamento; aplicar sua taxa a uma multa seria suposição não lastreada.
- Preserva a **proveniência**: o encargo guarda a moeda real do fato; quem consolidar (Finance) aplica
  a taxa correta no momento contábil, com rastro.

## Alternativas descartadas

- **Converter tudo para BRL na hora do cancelamento.** Descartada: exige decidir a taxa (negócio em
  aberto) e antecipa trabalho de Finance; perderia a moeda original (proveniência).
- **Forçar todos os encargos na mesma moeda.** Descartada: o custo merchant é em moeda do fornecedor
  e o reembolso na moeda do cliente — são naturalmente diferentes; forçar igualaria mentira.

## Impacto

- `Charge.amount` é `Money` (moeda explícita). `cancellation_charges` guarda `amount numeric` +
  `currency varchar` (como as demais tabelas com dinheiro).
- Nenhuma dependência de `Exchange` no cancelamento; nenhuma migração de câmbio.

## Como reverter

Quando Finance/Payout entrarem, a consolidação aplica a taxa adequada **na borda contábil** (aditivo).
Se o dono quiser converter já no cancelamento, adiciona-se um passo de conversão lendo o `Exchange`
— mudança **barata e localizada** no serviço (os encargos já carregam a moeda de origem).
