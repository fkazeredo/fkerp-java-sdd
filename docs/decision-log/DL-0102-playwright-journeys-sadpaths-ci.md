# DL-0102 — Playwright: config, escopo das jornadas/caminhos tristes e job de E2E no CI

- **Fase:** 12
- **Spec(s):** SPEC-0028 (BR4, BR5, AC3–AC9)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A Fase 12 pede **Playwright nas jornadas críticas** (incluindo **caminhos tristes**) e um **job de E2E
no CI**. Faltava decidir: (a) config do Playwright (browser, baseURL, headless, retries); (b) **quais**
jornadas cobrir (o produto tem muitas telas); (c) como o CI sobe/derruba o stack isolado sem tocar dev.

## Decisão

**Config (`frontend/playwright.config.ts`):** `testDir: ./e2e`; `baseURL` via `E2E_BASE_URL`
(default `http://localhost:4201`); um projeto **`chromium`** (Desktop Chrome) — headless por padrão;
`forbidOnly: !!process.env.CI` (um `.only` esquecido falha o CI); `retries: 1` (absorve flutuação de
timing de infra das jornadas pesadas **sem** enfraquecer asserção — espelha a POC); `reporter: 'list'`
+ `html` no CI; `trace: 'on-first-retry'`.

**Escopo das jornadas (mínimo defensável — feliz + triste, derivado das telas existentes da Fase 10):**

- **`login.spec.ts`** — AC3 (login feliz `director`/`dev12345` → dashboard) **+** AC4 (login inválido →
  erro genérico, permanece em `/login`, não revela se o usuário existe).
- **`version.spec.ts`** — AC3 (a versão `vX.Y.Z` aparece na tela de login, vinda de `/api/version`).
- **`auth-guard.spec.ts`** — AC5 (rota protegida sem sessão → redireciona a `/login`).
- **`accounts.spec.ts`** — AC6 (fluxo central: logar, navegar a Contas, ver a lista **ou** o empty
  state; navegação do shell funciona).
- **`unsaved-changes.spec.ts`** — AC7 (alterar formulário e cancelar → aviso de não-salvos;
  "continuar editando" mantém na tela — `canDeactivate`).

> Caminhos tristes cobertos: **401/sem sessão** (guard), **credencial inválida** (login genérico),
> **estado vazio** (lista de contas), **proteção de não-salvos** (form sujo). Cenários adicionais de
> permissão (403 por papel) são exercitados no nível de **integração backend** (já há testes de 403 nas
> ações sensíveis — SecurityConfig/DL-0082); o E2E foca na borda visível ao usuário, evitando duplicar
> e mantendo a suíte enxuta (`testing.md`: "E2E for critical flows only").

**Job de CI (`.github/workflows/e2e.yml`):** `ubuntu-latest`; checkout; setup Node 22; `npm ci` no
frontend; `npx playwright install --with-deps chromium`; `npm run e2e:up`; espera o
`GET http://localhost:8081/api/system/health` responder UP (loop com timeout); `npm run e2e` headless;
**`if: always()`** → `npm run e2e:down` (derruba sempre, mesmo em falha) e upload do
`playwright-report/` como artefato. O `ci.yml` existente segue intacto (agora rodando também os gates
de cobertura JaCoCo/Vitest).

## Justificativa

- **Fonte:** espelha o `playwright.config.ts`, os specs de E2E e a abordagem do **fkerp-poc** (citado
  pelo ROADMAP). `testing.md`: "E2E for critical flows only" → cobrir login/guard/fluxo central/borda,
  não toda tela. Documentação oficial do Playwright (config, `forbidOnly`, retries, `webServer`/CI).
- **Por que chromium só:** a POC faz o mesmo; multi-browser é aditivo e fica para necessidade real
  (Out of Scope, SPEC-0028).
- **Por que 1 retry:** as jornadas dirigem muitos passos sequenciais contra um backend compartilhado;
  1 retry absorve flutuação de timing **sem** mascarar bug (a asserção é a mesma).

## Alternativas descartadas

- **Cobrir todas as telas no E2E:** caro e frágil; contraria "E2E só para fluxos críticos". As regras de
  negócio já têm cobertura unitária/integração.
- **Multi-browser/mobile no v1:** sem necessidade de produto agora.
- **`webServer` do Playwright (subir `ng serve`) em vez do compose:** não provaria o empacotamento real
  (Nginx + proxy + DB efêmero) nem o isolamento; o compose é a referência da POC e o requisito da fase.
- **Sem `forbidOnly`/sem `if: always()`:** deixaria um `.only` mascarar a suíte e um stack pendurado em
  falha; ambos são higiene básica de CI.

## Impacto

- **Specs:** SPEC-0028 (BR4/BR5/AC3–AC9).
- **Arquivos:** `frontend/playwright.config.ts`, `frontend/e2e/*.spec.ts`,
  `.github/workflows/e2e.yml`, `frontend/package.json` (dep `@playwright/test`), `.gitignore`
  (`playwright-report/`, `test-results/`).
- **Migrações/Contratos:** nenhum.

## Como reverter

Remover `e2e.yml`, `playwright.config.ts` e `frontend/e2e/`. Refactoring nulo — é suíte de teste e job
de CI isolados.
