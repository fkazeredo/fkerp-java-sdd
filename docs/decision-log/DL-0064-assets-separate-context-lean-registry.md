# DL-0064 — Assets é contexto separado, entregue como registro enxuto de patrimônio

- **Fase:** 8h (Assets)
- **Spec(s):** SPEC-0021 (Assets); OVERVIEW Parte 5 (linha 134/162); relacionada à DL-0060 (Portfolio)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A **Q2** do redesenho (OVERVIEW Parte 13, item 2; SPEC-0021 *Open Questions*) pergunta se
`Portfolio` (representação comercial) e `Assets` (patrimônio interno) devem ser **dois contextos
ou um só**. Se "inventário" fosse unificado, a SPEC-0021 funde com a SPEC-0020. Sem decidir isto,
não dá para saber se Assets é um módulo Modulith próprio ou parte de Portfolio.

## Decisão

**Dois contextos separados.** `Assets` é um **módulo Spring Modulith próprio** (`com.fksoft.domain.assets`,
o 18º módulo de negócio), distinto de `Portfolio`. Entrega **enxuta**: o agregado `Asset` (registro de
patrimônio interno — equipamentos, licenças de software, outros bens) com ciclo de vida básico
(ACTIVE → RETIRED) e os vínculos por **valor** que o resto do sistema precisa (custo no Finance,
documento no Compliance) — **não** um sistema de gestão de ativos completo.

## Justificativa

- **ROADMAP "Recomendações para as Open Questions" (Q2):** recomenda explicitamente **"Dois contextos
  separados"** — "Perguntas diferentes (o que a Acme **representa** × **patrimônio** interno),
  linguagem/ciclo/dono distintos. Unir acoplaria comercial a TI. Assets (genérico) pode ser o último
  a entrar."
- **Consistência com a DL-0060:** a Fase 8g (Portfolio) já fixou os dois contextos separados pelo lado
  do Portfolio; esta decisão fecha o lado do Assets, mantendo a coerência.
- **OVERVIEW Parte 5/6:** lista `Portfolio` (Supporting) e `Assets` (Supporting/Generic) como linhas
  distintas do mapa de subdomínios, com papéis distintos (representação × patrimônio).
- **Regra Zero / `core-principles.md`:** entrega só o que a spec pede (registro enxuto); depreciação e
  gestão plena ficam fora (ver DL-0065).

## Alternativas descartadas

- **Fundir Assets em Portfolio (um "inventário" único):** acoplaria o comercial (marcas representadas)
  à TI (patrimônio), com linguagem ubíqua e donos diferentes — viola fronteira de contexto e a
  recomendação do arquiteto.
- **Sub-pacote de outro módulo (ex.: Finance ou Platform):** Assets tem identidade, ciclo de vida e
  eventos próprios; seria um módulo de negócio disfarçado, ferindo a clareza de fronteira.

## Impacto

- **Specs:** SPEC-0021 — mover Q2 de *Open Questions* para *Business Rules* ("ASSUMIDO (ver DL-0064)").
- **Arquivos:** novo módulo `com.fksoft.domain.assets` (+ `package-info.java` com `@ApplicationModule`),
  `AssetsController` em `application.api`, DTOs em `application.api.dto`.
- **Migração:** `V26__create_assets.sql` (tabela `assets`).
- **Contratos:** novos endpoints `/api/assets*`; OpenAPI atualizada.
- **Modulith:** 18º módulo de negócio; grafo deve permanecer acíclico (ver DL-0067).

## Como reverter

Caso o dono decida fundir os inventários: mover os tipos de `assets` para dentro de `portfolio`
(ou um módulo `inventory`), unificar as migrações e os endpoints. Refactoring **moderado**
(estrutural, com mudança de contrato REST), mas localizado — Assets não tem dependentes que
chamem suas fachadas (é módulo-folha).
