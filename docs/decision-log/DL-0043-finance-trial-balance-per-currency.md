# DL-0043 — Finance: relatório de período / trial-balance por moeda (sem plano de contas)

- **Fase:** 8b (Finance — SPEC-0015 full)
- **Spec(s):** SPEC-0015 (API Contracts `GET /periods/{yyyymm}` "status + totais AP/AR";
  Observability "saldos AP/AR por período"; Goal "livro-caixa operacional")
- **ADR relacionado:** 0012 ; `architecture/persistence.md` (Reports: "Never force every read through
  domain aggregates. Use query services, projections, DTOs")
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0015 pede "saldos AP/AR por período" e o `GET /periods/{yyyymm}` já devolve totais AP/AR por
moeda. Para a entrega "full" faltava decidir **o que** o relatório de período mais rico expõe sem
cair em contabilidade plena (Razão/plano de contas — fora de escopo, DL-0042): basicamente, **como**
apresentar o balancete (trial-balance) operacional.

## Decisão

Adicionar **`GET /api/finance/periods/{yyyymm}/trial-balance`** que devolve, **por moeda** (DL-0013,
nunca somando moedas), e **por status** (PROVISIONAL/CONFIRMED/SETTLED):

- `payable` e `receivable` (totais por moeda);
- `net = receivable − payable` por moeda (saldo operacional do período — **não** é resultado contábil);
- contagem de lançamentos por status;
- o `status` do período (OPEN/CLOSING/CLOSED).

Tudo derivado dos `ledger_entries` do período via **query/projeção de leitura** (não força o agregado),
escala 2 HALF_UP. **Sem** plano de contas, **sem** débito/crédito, **sem** DRE: é um **balancete de
caixa operacional**, coerente com o "livro-caixa" da spec. O `GET /periods/{yyyymm}` existente fica
**intacto** (back-compat); o trial-balance é um endpoint **novo e aditivo**.

## Justificativa

- A spec pede saldos AP/AR por período (Observability) e o livro-caixa operacional (Goal); o
  trial-balance por moeda/status é a forma natural e barata de entregar isso.
- `persistence.md` (Reports): leitura por projeção/DTO, não pelo agregado — exatamente o que se faz.
- **Não** invadir contabilidade plena (DL-0042): por isso `net` é rotulado como saldo **operacional**
  e não há plano de contas/partidas dobradas.

## Alternativas descartadas

- **Razão por conta (chart of accounts).** Descartada: é contabilidade plena (fora de escopo, DL-0042).
- **Somar moedas com conversão para um total único.** Descartada: viola DL-0013 (moeda original, sem
  conversão); o período agrega **por moeda**.
- **Só ampliar o `PeriodView` existente.** Descartada para o status/contagens: mudaria o JSON público
  do `GET /periods/{yyyymm}` (contrato). Endpoint novo aditivo é mais seguro (modules-and-apis.md).

## Impacto

- **Novo:** `GET /periods/{yyyymm}/trial-balance` + DTO de resposta (`TrialBalanceView`) + uma query
  de agregação por (moeda, direção, status). Sem migração (leitura pura sobre `ledger_entries`).
- OpenAPI atualizada; MANUAL.md ganha a jornada "consultar balancete do período".

## Como reverter

Reversão **barata**: remover o endpoint e o DTO. Nenhuma tabela/contrato existente muda (é puramente
aditivo, só leitura).
