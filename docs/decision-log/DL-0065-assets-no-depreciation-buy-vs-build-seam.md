# DL-0065 — Assets sem depreciação/gestão plena: registro + seam (comprar vs. construir)

- **Fase:** 8h (Assets)
- **Spec(s):** SPEC-0021 (Goal, Scope, Out of Scope, Open Questions)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A segunda *Open Question* da SPEC-0021 pergunta se há **necessidade de depreciação contábil / gestão
plena de ativos** — "se sim, **comprar** e usar este módulo como registro/seam — decisão do dono".
Sem decidir, não se sabe se a fatia entrega cálculo de depreciação, manutenção, chamados de TI, etc.

## Decisão

**Não implementar depreciação contábil nem gestão plena de ativos no v1.** Assets entrega o
**registro de patrimônio** + os **vínculos por valor** (custo no Finance, documento no Compliance) +
o **alerta de licença a vencer**, e fica como **seam** ("comprar vs. construir"): se o dono exigir
gestão de ativos plena (depreciação, manutenção, chamados, inventário de revenda), **compra-se** um
sistema dedicado e este módulo permanece o registro/ponto de integração.

Ficam explicitamente **fora de escopo** (não há código especulativo para eles):
- depreciação contábil (curva, método, valor residual, lançamento de depreciação);
- gestão de manutenção / chamados de TI;
- controle de estoque de revenda (a Acme não revende patrimônio — BR5).

## Justificativa

- **SPEC-0021 Goal/Scope/Out of Scope:** o objetivo é "sem virar um sistema de gestão de ativos
  completo (redesenho linha 162)"; *Out of Scope* lista depreciação, manutenção e estoque de revenda
  e diz "se o cliente exigir gestão de ativos plena, **comprar**".
- **ROADMAP (índice de specs, nota dos genéricos):** "Finance/Identity/Admin/Assets são **genéricos**:
  a spec entrega a **fronteira + o seam + a decisão comprar vs. construir** (não um sistema caseiro
  completo)."
- **OVERVIEW Parte 5:** Assets é **Supporting/Generic** — commodity; padrão é comprar o que for plena
  gestão de ativos, construir só o registro que amarra custo↔documento.
- **Regra Zero (`CLAUDE.md`) / `core-principles.md`:** *current business need over speculative future
  need* — não antecipar um motor de depreciação sem regra de negócio que o exija.

## Alternativas descartadas

- **Implementar depreciação linear "porque é comum":** inventaria regra de negócio (método, vida útil,
  valor residual, periodicidade do lançamento) que só o contador define — proibido pela invariante 3
  do `CLAUDE.md`; e a spec a marca *Out of Scope*.
- **Modelar manutenção/chamados como seam ativo agora:** sem produtor/consumidor real, seria módulo
  vazio especulativo — viola `workflow.md` (*MUST NOT create fake bounded contexts / placeholder
  classes*).

## Impacto

- **Specs:** SPEC-0021 — mover esta Open Question para *Business Rules* ("ASSUMIDO (ver DL-0065)").
- **Arquivos:** nenhuma classe de depreciação/manutenção. O custo de aquisição é guardado como
  `Money` no registro; a ligação ao lançamento de custo do Finance é por id (valor), via
  `financeEntryId`.
- **Migração:** sem colunas de depreciação em `V26`.

## Como reverter

Se o dono pedir gestão de ativos: a recomendação é **integrar/comprar** um sistema dedicado e manter
este módulo como registro/seam. Construir depreciação aqui seria uma **nova spec** (curva, método,
lançamentos no Finance) — refactoring moderado a caro, fora desta fatia.
