# DL-0099 — Limiar de cobertura do backend (JaCoCo INSTRUCTION ≥ 80%, gate no `verify`)

- **Fase:** 12
- **Spec(s):** SPEC-0028 (BR2, AC1)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A tarefa da Fase 12 exige **JaCoCo como portão real** no backend: relatório **+** um limiar
**defensável que o código atual passa** (não uma barra impossível), capaz de **quebrar o build** numa
queda. Faltava decidir: (a) qual contador (instruction/line/branch); (b) qual valor de piso; (c) o que
excluir; (d) onde o `check` roda.

## Decisão

- **Contador:** `INSTRUCTION` (`COVEREDRATIO`), o sinal mais estável do JaCoCo (granular e menos
  ruidoso que branch; não depende de formatação como line).
- **Piso:** **0.80 (80%)**, via propriedade `jacoco.min.instruction.ratio` no `pom.xml`.
- **Escopo da regra:** `BUNDLE` (o módulo inteiro).
- **Exclusões** (sem lógica testável significativa): `FkErpApplication` + `**/*Application.class`
  (bootstrap Spring Boot), `**/package-info.class`, `**/dto/**` (records de request/response),
  `**/config/**`.
- **Bind:** três execuções do `jacoco-maven-plugin` — `prepare-agent` (instrumenta o JVM de teste),
  `report` (HTML/XML/CSV em `target/site/jacoco`, fase `verify`) e `check` (fase `verify`, **falha** o
  build se a cobertura cair abaixo do piso).

**Cobertura medida (baseline no momento da decisão):** o relatório JaCoCo total marca **89% de
instruções** (3.225 de 30.464 perdidas) com os 477 testes verdes. O piso de **80%** fica ~9 pontos
abaixo do medido — folga suficiente para absorver flutuação normal, e ainda assim uma **regressão real**
(remover testes / adicionar código sem teste) derruba o build.

## Justificativa

- **Recomendações do ROADMAP / fontes:** a `testing.md` é explícita — "coverage is a signal, not the
  goal; high coverage with weak assertions is not quality". Logo, o gate não deve ser cosmético (100%),
  e sim um **piso de não-regressão**. A documentação do JaCoCo recomenda `INSTRUCTION` como métrica de
  gate padrão (mais estável). O fkerp-poc usa JaCoCo **só como relatório** (sem gate); aqui a tarefa
  pede explicitamente um **gate real**, então elevamos a barra da POC de forma defensável.
- **Por que 80% e não 89%:** fixar o piso exatamente no valor atual transformaria qualquer refator
  legítimo (extrair classe, adicionar branch defensivo) numa quebra de build espúria. 80% protege
  contra queda material sem punir manutenção normal. Subir o piso é trabalho incremental de fatias
  futuras (Out of Scope desta).

## Alternativas descartadas

- **Sem gate (só relatório, como a POC):** não cumpre o requisito "JaCoCo deve ser um gate de verdade".
- **Piso em 89% (igual ao atual):** frágil — quebra em refator legítimo; contraria "coverage é sinal".
- **Contador `LINE` ou `BRANCH`:** line depende de formatação; branch é mais ruidoso e exigiria um piso
  bem menor (o branch atual ronda 68%), dando um gate mais fraco. `INSTRUCTION` é o melhor equilíbrio.
- **`COMPLEXITY`/`METHOD`:** menos intuitivos como contrato de não-regressão.

## Impacto

- **Specs:** SPEC-0028 (BR2/AC1).
- **Arquivos:** `backend/pom.xml` (propriedade `jacoco.min.instruction.ratio` + plugin
  `jacoco-maven-plugin` com 3 execuções).
- **Migrações/Contratos:** nenhum (tooling de build; sem schema, sem OpenAPI).
- **CI:** o `./mvnw verify` (job `backend` do `ci.yml`) passa a gerar o relatório e a aplicar o gate.

## Como reverter

Baixar/elevar o valor de `jacoco.min.instruction.ratio` (uma linha), ou remover a execução
`jacoco-check` para voltar a relatório-só. Refactoring nulo — é configuração de build.
