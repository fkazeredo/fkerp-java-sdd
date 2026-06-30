# DL-0100 — Limiar de cobertura do frontend (Vitest/v8, gate no `ng test` via angular.json)

- **Fase:** 12
- **Spec(s):** SPEC-0028 (BR2, AC2)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A Fase 12 exige **cobertura do frontend via `@vitest/coverage-v8`** como **portão real** (limiar que
quebra o build, defensável e que o código atual passa). Faltava decidir: (a) onde configurar o limiar
(arquivo de runner-config × opções do builder); (b) quais métricas e valores; (c) como garantir que o
comando do CI (`ng test --watch=false`) aplica o gate.

## Decisão

- **Provider:** `v8` (`@vitest/coverage-v8`), o provider de cobertura do Vitest 4 (runner do builder
  `@angular/build:unit-test`).
- **Onde:** **nas opções do builder em `angular.json`** (`test.options.coverage`,
  `coverageReporters`, `coverageThresholds`), **não** num `vitest.config.ts` via `--runner-config`.
  Motivo: passar um `vitest.config.ts` com `include`/`provider` próprios **quebrava** o mapeamento de
  fontes do bundle instrumentado pelo Angular (cobertura reportava 0%). As opções nativas do builder
  são a via suportada e fazem o **`ng test` padrão** (o mesmo comando do CI) ser o gate — sem script
  extra nem arquivo de config paralelo.
- **Reporters:** `text-summary` (console), `html` (`coverage/`), `lcov` (para CI/ferramentas).
- **Limiares (`coverageThresholds`, globais):** `statements: 65`, `lines: 65`, `functions: 48`,
  `branches: 55`.

**Cobertura medida (baseline no momento da decisão, app inteiro, 57 testes verdes):**
statements **70.08%**, lines **72.37%**, functions **53.75%**, branches **59.62%**. Os pisos ficam
~5–6 pontos abaixo do medido — folga para flutuação normal, e ainda assim uma **regressão real** derruba
o `ng test` (provado: subir `statements` para 95 faz `EXIT=1` com "does not meet global threshold").

## Justificativa

- **`testing.md`:** cobertura é **sinal, não meta** — o gate é piso de **não-regressão**, não 100%.
- **Por que functions/branches mais baixos:** telas repaginadas na Fase 10 têm caminhos exercitados só
  pelo E2E (handlers de UI, ramos de erro de borda), que não contam na cobertura **unitária**. Fixar
  functions/branches no patamar atual seria frágil; os pisos escolhidos protegem contra queda material
  sem punir o estilo de teste unitário+E2E (a jornada de tela é coberta pelo Playwright nesta fase).
- **Fonte:** documentação do Angular `@angular/build:unit-test` (opções `coverage`/`coverageThresholds`)
  e do Vitest (provider v8, `coverage.thresholds`). O fkerp-poc traz `@vitest/coverage-v8` como
  dependência; aqui elevamos a relatório-com-gate, como a tarefa pede.

## Alternativas descartadas

- **`vitest.config.ts` via `--runner-config` com `include`/`provider`:** reportava **0%** (conflito com
  a instrumentação do builder). Descartado por não medir de verdade.
- **Limiar único alto (ex.: 80% em tudo):** frágil — functions/branches reais ficam abaixo por design
  (cobertos por E2E); quebraria o build sem regressão real.
- **`perFile: true`:** muito ruidoso para um piso de não-regressão global nesta fase; pode entrar como
  melhoria incremental futura.

## Impacto

- **Specs:** SPEC-0028 (BR2/AC2).
- **Arquivos:** `frontend/angular.json` (opções de cobertura/limiar no alvo `test`),
  `frontend/package.json` (dep `@vitest/coverage-v8`, script `test:coverage`).
- **Migrações/Contratos:** nenhum (tooling de teste; sem schema, sem OpenAPI).
- **CI:** o job `frontend` roda `npx ng test --watch=false`, que agora **é** o gate de cobertura.

## Como reverter

Ajustar os números em `angular.json > projects.frontend.architect.test.options.coverageThresholds`
(piso), ou remover o bloco `options` para voltar a `ng test` sem cobertura. Refactoring nulo — é
configuração de build.
