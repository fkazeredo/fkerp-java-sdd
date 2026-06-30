# Caderno de testes — Slice 10-1: Stack (PrimeNG 21 + Tailwind v4) + Shell SaaS + Tema

## Escopo
SPEC-0026 — AC1 (build/lint), AC2 (shell), AC3 (tema). DL-0090 (stack), DL-0091 (tema).

## Casos de teste (component/unit — vitest headless)

| Caso | Verifica | AC / BR |
|---|---|---|
| ThemeService: aplica/remove `.app-dark` | toggle adiciona/remove a classe no `documentElement` | AC3 / BR3 |
| ThemeService: persiste em localStorage | `setTheme`/`toggle` gravam `acme.erp.theme` | AC3 / BR3 |
| ThemeService: restaura tema salvo | construção lê o tema do localStorage | AC3 / BR3 |
| ThemeService: segue preferência do SO | sem valor salvo, usa `prefers-color-scheme` | AC3 / BR3 |
| Shell: renderiza navegação completa | nº de itens == NAV_ITEMS; mostra usuário | AC2 / BR2 |
| Shell: alterna tema pela topbar | clique no botão chama `ThemeService.toggle` | AC3 / BR3 |
| Shell: abre/fecha o drawer mobile | `toggleDrawer`/`closeDrawer` mudam o signal | AC2 / BR2 |
| App: host com router-outlet | App renderiza `<router-outlet>` (shell virou layout route) | AC2 |

## Resultado
- `npm run lint` → **All files pass linting**.
- `CI=true npx ng test --no-watch` → **10 arquivos / 25 testes — todos verdes** (baseline era 8/18;
  +ThemeService 4, +Shell 3, App atualizado).
- `npm run build` → **sucesso**, sem warning de budget (initial 647.60 kB raw / 141.62 kB transfer;
  budget de warning ajustado para 900 kB raw por causa do PrimeNG, error mantido como portão em 1.5 MB).

## Cobertura
- Coberto: serviço de tema (4 caminhos), shell (nav/tema/drawer), troca de layout (App→Shell).
- Não coberto aqui: silent refresh/login/paleta (10-2), estados das telas (10-3), dashboard (10-4).

## Como reproduzir
```bash
cd frontend && npm ci
npm run lint
CI=true npx ng test --no-watch
npm run build
```
