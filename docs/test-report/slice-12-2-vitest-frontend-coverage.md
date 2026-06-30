# Caderno de testes — Slice 12-2 (Vitest/v8: cobertura do frontend como portão)

## Escopo

- **Spec:** SPEC-0028 (AC2, BR2, BR3).
- **Entrega:** `@vitest/coverage-v8` + opções de cobertura no `angular.json` (`coverage: true`,
  `coverageReporters`, `coverageThresholds`), de modo que o **`ng test` padrão** (o mesmo comando do CI)
  colete cobertura v8 e **falhe** abaixo do piso (statements/lines 65, functions 48, branches 55 —
  DL-0100). Nenhum dos 57 testes existentes foi alterado/removido.

## Casos de teste por tipo

| Caso | O que verifica | AC / regra |
|---|---|---|
| `ng test --watch=false` com cobertura | 57 testes verdes + relatório v8 (`coverage/`) | AC2/BR3 |
| Limiar aplicado pelo builder | cobertura atual ≥ piso → `EXIT=0` | AC2/BR2 |
| Gate é de verdade | piso acima do medido → `EXIT=1` ("does not meet global threshold") | BR2 |
| `ng lint` | "All files pass linting." com o `angular.json` alterado | BR3 |
| `ng build` | build de produção verde | BR3 |

## Resultado

- `cd frontend && npx ng test --watch=false` → **EXIT=0**, **57 testes passam** (17 arquivos).
- **Cobertura medida (app inteiro):** Statements **70.08%** (1101/1571), Branches **59.62%** (319/535),
  Functions **53.75%** (129/240), Lines **72.37%** (786/1086). Todos acima do piso (DL-0100) com folga.
- **Prova do dente:** com `statements: 95` no `angular.json`, `ng test` falha com
  `ERROR: Coverage for statements (70.08%) does not meet global threshold (95%)` e `EXIT=1` (restaurado
  para 65).
- `ng lint` → **All files pass linting.** `ng build` → verde (ver merge de integração).

## Cobertura (o que NÃO está coberto e por quê)

- Functions/branches têm piso menor (48/55) porque muitos handlers de UI e ramos de erro de borda das
  telas repaginadas (Fase 10) são exercitados pelo **E2E (Playwright, slices 12-3/12-4)**, não pelo
  teste unitário. Fixar esses contadores no patamar atual seria frágil; o piso protege contra **queda
  material** sem punir o estilo unitário+E2E (DL-0100; `testing.md`: "E2E for critical flows only").

## Como reproduzir

```bash
cd frontend
npm ci
npx ng test --watch=false      # gate de cobertura (mesmo comando do CI)
npm run test:coverage          # alias equivalente
# relatório HTML:
open frontend/coverage/index.html
# prova do gate (deve FALHAR): editar coverageThresholds.statements para 95 em angular.json
```
