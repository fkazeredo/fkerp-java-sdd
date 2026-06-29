# DL-0014 — Finance: construir o seam mínimo agora (comprar a contabilidade plena depois)

- **Fase:** 2 (Compliance mínimo)
- **Spec(s):** SPEC-0015 (Open Question "Comprar vs. construir")
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0015 marca o Finance como **subdomínio genérico** ("avaliar comprar") e deixa em aberto se a
Acme vai **construir** a contabilidade ou **integrar/comprar** um ERP contábil.

## Decisão

**Construir agora apenas o seam mínimo** que o resto do sistema exige: razão AP/AR (`LedgerEntry`) e a
máquina de período/fechamento (`AccountingPeriod`), porque é **pré-requisito do veto do Compliance**
(SPEC-0008 BR6; ROADMAP linha 184-187 "co-entrega na Fatia 2"). **Não** construir contabilidade plena
(plano de contas, partidas dobradas, DRE, SPED/ECD): se o cliente exigir, **integrar/comprar** um ERP
contábil e este módulo vira o **adaptador** que sincroniza lançamentos/fechamento.

## Justificativa

- O Compliance não pode vetar o fechamento se não existe quem **detenha o período e o calendário** —
  esse é o Finance. Sem o seam, a Fatia 2 não fecha o ciclo (cofre + veto).
- A Regra Zero (CLAUDE.md) e `core-principles.md` (necessidade atual sobre futura) pedem o **mínimo**:
  AP/AR + período resolvem o problema real desta fase sem reimplementar um ERP contábil.
- A própria spec recomenda este caminho ("esta spec define o seam"; "decisão do dono" só para a
  contabilidade plena).

## Alternativas descartadas

- **Construir contabilidade plena agora.** Descartado: overengineering (Regra Zero); fora de escopo da
  fatia; muito risco/escopo para um subdomínio genérico.
- **Adiar o Finance e simular o período no Compliance.** Descartado: colocaria a máquina de período no
  módulo errado (o veto é do Compliance, a trava é do Finance — SPEC-0008 BR6) e quebraria a fronteira.
- **Comprar/integrar já um ERP contábil.** Descartado: decisão do dono, sem fornecedor definido; o seam
  permite plugar depois sem retrabalho.

## Impacto

- `finance`: módulo novo com `LedgerEntry` + `AccountingPeriod`; portas `CloseGuard` (consumida) e
  `LedgerDirectory` (exposta). Migração `V7__create_finance.sql`.
- Se houver compra futura, o módulo passa a **adaptador** de sincronização (a fronteira já isola isso).

## Como reverter

Substituir a persistência local por um adaptador que delega ao ERP contábil comprado, preservando as
portas (`CloseGuard`, `LedgerDirectory`) — refactoring moderado, contido na fronteira do módulo.
