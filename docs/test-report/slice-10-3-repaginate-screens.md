# Caderno de testes — Slice 10-3: Repaginação das telas + estados reais + canDeactivate

## Escopo
SPEC-0026 — AC9 (estados loading/empty/error/permissão), AC8 (canDeactivate nas telas com rascunho),
AC1 (lint/test/build). Telas: Accounts, Exchange, Quoting, Booking, Reconciliation, Health.

## Mudanças
- Componente compartilhado `ScreenState` (loading/empty/error/permissão) — 403 (`access.denied`)
  vira estado de permissão, não erro cru.
- Todas as telas migradas para PrimeNG (Table, Select, InputText, InputNumber, Password, Tag,
  Message, Card, Button, ProgressSpinner) com os tokens `--app-*`.
- `canDeactivate` ligado em Accounts/Exchange/Quoting (formulários com rascunho); `isDirty()` próprio.
- Rotas das features convertidas para **lazy `loadComponent`** → bundle inicial enxuto (516 kB).

## Casos de teste (component/unit — vitest headless)

| Caso | Verifica | AC / BR |
|---|---|---|
| ScreenState: loading | mostra spinner+texto | AC9 / BR8 |
| ScreenState: empty | mostra vazio | AC9 / BR8 |
| ScreenState: error + retry | mostra erro e botão | AC9 / BR8 |
| ScreenState: permissão (access.denied) | mostra permissão, não erro | AC9 / BR8 |
| ScreenState: success projeta conteúdo | `<ng-content>` | AC9 |
| Accounts: success | estado/lista | AC9 |
| Accounts: error code | erro mostrado | AC9 |
| Accounts: empty | listState == empty | AC9 |
| Accounts: 403 mantém access.denied | listState error + código | AC9 / BR8 |
| Accounts: isDirty quando documento preenchido | guarda de saída | AC8 / BR9 |
| Exchange/Booking/Quoting/Reconciliation/Health | estados preservados (success/error/loading) | AC9 |

(Os specs das telas existentes seguem verdes; ganharam `provideNoopAnimations()` por usarem PrimeNG.)

## Resultado
- `npm run lint` → **All files pass linting** (regras a11y do template ativas).
- `CI=true npx ng test --no-watch` → **15 arquivos / 50 testes — todos verdes** (era 14/42).
- `npm run build` → **sucesso**; com lazy-load das features o bundle inicial caiu para **516.46 kB raw /
  118.67 kB transfer** (budget de warning restaurado para 700 kB, error 1 MB — gate real).

## Cobertura
- Coberto: o componente de estados (5 caminhos), os estados/erros de cada tela, dirty-check do Accounts.
- Não coberto: render pixel-a-pixel dos componentes PrimeNG (fora do escopo de teste de unidade);
  o e2e das jornadas fica para a Fase 12 (Playwright).

## Como reproduzir
```bash
cd frontend && npm ci
npm run lint
CI=true npx ng test --no-watch
npm run build
```
