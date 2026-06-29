# DL-0042 — Finance: comprar vs. construir a contabilidade plena — decisão reafirmada na entrega "full"

- **Fase:** 8b (Finance — SPEC-0015 full)
- **Spec(s):** SPEC-0015 (Open Question "Comprar vs. construir" / BR7; Out of Scope "plano de
  contas/partidas dobradas, DRE, SPED/ECD — comprar/integrar")
- **ADR relacionado:** 0014 ; OVERVIEW Parte 5/12 (Finance = subdomínio **genérico**: "avaliar
  comprar"); ROADMAP linha 182 ("Genéricos = fronteira + seam + decisão comprar vs. construir")
- **Data:** 2026-06-29
- **Status:** ASSUMIDO (reafirma e estende DL-0014)
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O DL-0014 (Fase 2) decidiu **construir o seam mínimo** AP/AR + período e **comprar depois** a
contabilidade plena. A entrega "full" da SPEC-0015 (esta fase) reabre a pergunta na prática: ao
ampliar o Finance (lançamento automático por evento + relatório de período), **constrói-se agora um
razão contábil completo** (plano de contas, partidas dobradas, DRE, SPED/ECD) ou **mantém-se o seam**
genérico e a decisão de compra para quando o negócio exigir contabilidade plena?

## Decisão

**Manter a estratégia do DL-0014 e reafirmá-la explicitamente para o escopo "full": NÃO construir
contabilidade plena.** A entrega "full" desta spec é o **livro-caixa operacional** (AP/AR + período +
fechamento + lançamento automático idempotente + trial-balance por moeda) — **não** um Razão (GL)
contábil. Permanecem **fora de escopo** e marcados como **comprar/integrar** quando o dono exigir:

- **Plano de contas (chart of accounts)** e **partidas dobradas (double-entry/débito-crédito)**;
- **DRE / Balanço / DFC** contábeis;
- **SPED Contábil (ECD) / ECF / obrigações acessórias fiscais**;
- **Apuração de tributos contábil, conciliação bancária contábil, livro Diário/Razão oficiais**.

Se exigida a contabilidade plena, **integra-se/compra-se um ERP contábil** e este módulo vira o
**adaptador de sincronização** — as portas `CloseGuard` (veto) e `LedgerDirectory` (leitura) já
isolam essa troca; o lançamento automático por evento (DL-0041) é o ponto natural onde o adaptador
publicaria os lançamentos para o sistema contábil externo.

## Justificativa

- **Rule Zero / `core-principles.md` (necessidade atual sobre futura):** nenhuma fatia downstream
  (Billing/Payout/Reconciliation/Compliance) precisa de partidas dobradas ou SPED hoje; precisam do
  registro do que se deve/recebe e do calendário de fechamento — que o seam entrega.
- **OVERVIEW/ROADMAP são explícitos:** Finance é **genérico** ("avaliar comprar"); a spec entrega
  "fronteira + seam + decisão comprar vs. construir", **não** um sistema caseiro completo. A própria
  SPEC-0015 (Out of Scope, BR7) diz "se o cliente exigir contabilidade plena, integrar/comprar".
- **Decisão do dono permanece:** **quando** a contabilidade plena for exigida (e **qual** fornecedor)
  é decisão de negócio não dada — registrada como tal, sem invenção.

## Alternativas descartadas

- **Construir GL com partidas dobradas e plano de contas agora.** Descartada: overengineering de um
  subdomínio genérico; meses de trabalho fiscal/contábil sem demanda downstream; alto risco
  regulatório (SPED/ECD) que um ERP contábil maduro resolve melhor.
- **Construir só o plano de contas, adiar o resto.** Descartada: plano de contas sem partidas dobradas
  e sem obrigações acessórias é meia-ponte que não serve a nada hoje — pura especulação.
- **Comprar/integrar já o ERP contábil nesta fatia.** Descartada: sem fornecedor/decisão do dono; o
  seam + adaptador permitem plugar depois sem retrabalho (a fronteira já isola).

## Impacto

- **Sem código novo de GL.** O "full" entrega AP/AR automático + relatório, não Razão contábil.
- O `package-info` do Finance e a SPEC-0015 continuam descrevendo o módulo como **seam/livro-caixa**;
  esta reafirmação fica registrada para a auditoria da fase "full".
- Caminho de compra futuro inalterado: substituir a persistência local por adaptador que sincroniza
  com o ERP contábil, preservando `CloseGuard`/`LedgerDirectory` (DL-0014 "Como reverter").

## Como reverter

Não há o que reverter (é uma decisão de **não** construir). Se o dono optar pela compra, segue-se o
caminho do DL-0014: transformar o módulo em adaptador de sincronização — refactoring moderado contido
na fronteira do `finance`.
