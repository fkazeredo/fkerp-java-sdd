# Caderno de testes — Slice 12-5 (Job de E2E no CI)

## Escopo

- **Spec:** SPEC-0028 (AC9, BR5).
- **Entrega:** `.github/workflows/e2e.yml` — job `e2e` que sobe o stack isolado (`compose.e2e.yaml`),
  espera a saúde do backend, roda o Playwright **headless** e **sempre** derruba o stack
  (`if: always()`), anexando o `playwright-report/` como artefato. O `ci.yml` existente segue intacto.

## Casos de teste por tipo

| Caso | O que verifica | AC / regra |
|---|---|---|
| YAML válido (`e2e.yml`) | `js-yaml` parseia sem erro; job `e2e` com 9 steps | AC9 |
| `ci.yml` intacto | continua válido (backend/flyway/frontend) | BR3 |
| Sobe o stack isolado | step `docker compose -f compose.e2e.yaml up -d --build` | AC9/BR1 |
| Espera saúde | loop curl `http://localhost:8081/api/system/health` até `"status":"UP"` (timeout) | smoke/BR5 |
| Playwright headless | `npm run e2e` com `CI=true` (`forbidOnly`) e `E2E_BASE_URL=http://localhost:4201` | AC9/BR5 |
| Teardown sempre | steps com `if: always()`: upload do report + `docker compose ... down -v` | BR5 |

## Resultado

- **Validação de sintaxe:** `npx js-yaml .github/workflows/e2e.yml` → **EXIT=0**; `ci.yml` → **EXIT=0**.
- **Equivalência local:** o job replica exatamente o ciclo já **provado localmente** na slice 12-3/12-4
  (up → espera health → `npm run e2e` headless → `down -v`), que resultou em **11 specs verdes** e no
  **dev DB intacto**. O `if: always()` garante o teardown mesmo em falha; o `CI=true` ativa o
  `forbidOnly` (um `.only` esquecido falha o CI).
- **Não toca dev:** o job roda em runner efêmero do GitHub Actions; o `compose.e2e.yaml` usa Postgres
  tmpfs próprio — não há banco de dev no runner para tocar (isolamento por construção, BR1).

## Cobertura (o que NÃO está coberto e por quê)

- O job **não foi executado no GitHub Actions** dentro desta fatia (não há push ao remoto ainda no
  momento do registro); a garantia vem da **equivalência** com a corrida local provada + validação de
  sintaxe. Ao publicar, o workflow roda no push de `develop`.
- Multi-browser/sharding: Out of Scope (DL-0102).

## Como reproduzir

```bash
# localmente (o que o job faz):
cd frontend && npm ci && npx playwright install --with-deps chromium
docker compose -f ../compose.e2e.yaml up -d --build
# espera health em http://localhost:8081/api/system/health
CI=true E2E_BASE_URL=http://localhost:4201 npm run e2e
docker compose -f ../compose.e2e.yaml down -v
# validar o YAML:
npx js-yaml .github/workflows/e2e.yml
```
