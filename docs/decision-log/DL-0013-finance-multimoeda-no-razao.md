# DL-0013 — Finance: razão em moeda original (sem conversão)

- **Fase:** 2 (Compliance mínimo)
- **Spec(s):** SPEC-0015 (Open Question "Tratamento de multimoeda no razão")
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0015 deixa em aberto se o razão AP/AR guarda o lançamento em **moeda original** ou **convertido**
para uma moeda de apresentação.

## Decisão

Guardar cada `LedgerEntry` em **moeda original** (o `Money` do lançamento: `amount` + `currency`),
**sem conversão** no momento do registro. O totalizador do período (`GET /api/finance/periods/{m}`)
agrega **por moeda** (um saldo AP e um AR por moeda presente), nunca somando moedas diferentes.

## Justificativa

- O Finance aqui é o **seam mínimo / livro-caixa operacional** (SPEC-0015 Goal), não o ERP contábil:
  converter exigiria política de taxa/quando (que é do `Exchange`/`Reconciliation`) e introduziria
  perda de informação no registro.
- A composição já nasce em BRL no Quoting (DL-0009); pagamentos a fornecedor são em moeda estrangeira
  (OVERVIEW 7.2). Preservar a moeda original mantém o razão fiel e deixa a decomposição
  subsídio×drift onde ela mora (Exchange, SPEC-0011).
- Reusa o kernel `Money` (regra do projeto) e a sua invariante "não somar moedas diferentes".

## Alternativas descartadas

- **Converter tudo para BRL no registro.** Descartado: acopla o Finance a uma fonte/momento de taxa,
  perde a moeda de origem e duplica responsabilidade do Exchange/Reconciliation.
- **Moeda única travada (só BRL).** Descartado: o negócio paga fornecedor em USD; o razão precisa
  representar AP em moeda estrangeira (SPEC-0015 BR1 "amount (Money)").

## Impacto

- `finance`: coluna `amount numeric(18,2)` + `currency varchar(3)`; total do período por moeda.
- Não há conversão; relatórios de câmbio (subsídio/drift) ficam na Fase 5 (SPEC-0011).

## Como reverter

Introduzir uma camada de conversão (taxa de apresentação) no totalizador/relatório, consumindo o
`Exchange` — refactoring moderado, mas o dado de origem (moeda original) continua preservado, então a
reversão é aditiva, não destrutiva.
