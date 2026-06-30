# DL-0069 — Jornada/banco como serviço de domínio puro sobre o snapshot operacional (sem reescrever o crawler)

- **Fase:** 8i
- **Spec(s):** SPEC-0022 (BR2/BR3), SPEC-0012 (snapshot operacional)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0022 (BR2) manda **montar a jornada do período por colaborador a partir das marcações
operacionais** do snapshot. Mas o `PointSnapshot` existente (SPEC-0012, módulo `people`) guarda
apenas o **agregado** do período — `marks` (nº total de marcações) e um `payloadRef` **opaco** (o
espelho bruto, armazenado via `FileStorage`). Não há, hoje, detalhe **por colaborador** no modelo.
Como montar a jornada por colaborador sem (a) reescrever o crawler/snapshot da Fase 6, (b) inventar
um parser especulativo do formato de espelho de um portal fictício?

## Decisão

1. **Não reescrever o crawler nem o `PointSnapshot`.** Eles continuam donos do snapshot agregado
   operacional (idempotente por `(sourceRef, periodRef)`, `operationalOnly=true`). Esta fase
   **constrói por cima**, no mesmo módulo `people`.
2. **Cálculo de jornada/banco = serviço de domínio puro e testável** (`JourneyCalculator`):
   recebe `workedMinutes` (operacional do período, por colaborador) e `contractedMinutes` e devolve
   o **saldo** (= trabalhado − contratado) e a classificação. É o "miolo testável (datas/timezone)"
   que a spec pede em *Persistence Changes/Validation Rules*.
3. **Seam de consumo do snapshot**, idempotente por `(employee, period)`: o uso de caso
   `processJourney` registra o **`snapshotRef`** consumido (valor — não FK) na linha `journeys`, e
   recebe os **minutos trabalhados operacionais por colaborador** mais a contagem de marcações
   esperadas/efetivas (para a divergência, DL-0071). Em v1 esses minutos chegam como **entrada
   operacional explícita** (no mundo real, extraídos do espelho por colaborador; o snapshot raspado
   é, por contrato, operacional e **não-legal** — BR3/BR6). Reprocessar o mesmo `(employee, period)`
   atualiza no lugar (idempotência por UNIQUE).

## Justificativa

- **Regra Zero:** evita um parser frágil de um espelho de portal fictício e evita reabrir o
  agregado da Fase 6. O valor desta fatia é a jornada/banco/divergência — não um novo crawler.
- **`architecture/persistence.md`:** "cálculo de jornada/banco é serviço de domínio testável
  (datas/timezone)" — exatamente o que a própria SPEC-0022 escreve.
- **`architecture/modules-and-apis.md` / Modulith:** o `snapshotRef` é **valor** (sem FK
  cross-contexto). People permanece dono da sua jornada; o snapshot operacional segue sendo a fonte
  consumida, sem que People reescreva o miolo de outro caso de uso.
- **SPEC-0022 BR6:** o snapshot nunca é tratado como documento legal — o seam só lê o agregado e
  recebe minutos operacionais; nenhum `Document`/retenção nasce aqui.

## Alternativas descartadas

- **Estender o `PointSnapshot` para carregar marcações por colaborador** (parser do espelho):
  reabre o agregado da Fase 6 e exige um parser especulativo de um formato externo fictício — viola
  Regra Zero e o escopo de "não reescrever o crawler".
- **People ler o `payloadRef`/`FileStorage` e parsear o espelho:** acopla People ao formato bruto
  do portal (que é da camada ACL `infra.integration.pointclock`) e fura a fronteira; além de
  especulativo.
- **Folha completa (eSocial/cálculo real por marcação):** Out of Scope explícito (comprar/integrar).

## Impacto

- **Specs:** SPEC-0022 — move BR2 para "ASSUMIDO (ver DL-0069)"; mantém o snapshot como fonte
  operacional consumida por valor.
- **Arquivos:** novo serviço de domínio `JourneyCalculator`; `PeopleService.processJourney`;
  entidade `Journey` (coluna `snapshot_ref uuid`).
- **Migração:** `V27` cria `journeys(... snapshot_ref uuid not null, worked_minutes, balance_minutes,
  ... UNIQUE(employee_id, period))`.
- **Contratos:** `GET /employees/{id}/journey?period=`, `GET /employees/{id}/timebank?period=`.

## Como reverter

Quando o snapshot passar a carregar marcações por colaborador (evolução da SPEC-0012), trocar a
**fonte** dos `workedMinutes` no `processJourney` (de entrada operacional explícita para derivação
do snapshot detalhado), preservando o `JourneyCalculator` e o contrato de API. Refactoring
**localizado** no seam de consumo (Moderado), sem mexer no cálculo nem nas tabelas.
