# Plano — Fase 10: UX & Frontend profissional (SPEC-0026)

> Frontend-only. Base: `origin/develop` (0.20.1). Versão alvo: **0.21.0** (MINOR, ADR 0015 —
> nova capacidade retro-compatível). Branch de integração: `feature/10-integration`.

## Objetivo

Entregar a SPEC-0026: shell SaaS (PrimeNG 21 Aura + Tailwind v4 + CDK), tema claro/escuro, paleta de
comandos + atalhos + ajuda, login com revalidação silenciosa, `canDeactivate`, estados reais em todas
as telas e dashboard com KPIs — mantendo os gates verdes (lint/test/build do front; `./mvnw verify`
do back intacto em 468 testes, pois o backend não muda).

## Decisões (decision-log, escritas antes do código)

- **DL-0090** stack PrimeNG 21 Aura + Tailwind v4 + CDK + primeicons (gradua **DL-0003**).
- **DL-0091** tema claro/escuro (`.app-dark`, tokens `--app-*`, persistência, default = SO).
- **DL-0092** silent refresh = revalidação via `GET /me` (sem refresh token; Fase 13 gradua).
- **DL-0093** paleta `Ctrl/Cmd+K` + atalhos próprios (registro central, sem lib).
- **DL-0094** dashboard KPIs calculados no cliente dos endpoints existentes (sem backend).

## Fatias (uma branch por fatia, merge --no-ff em feature/10-integration, gate verde a cada uma)

### 10-1 — Stack + shell SaaS + tema
- Instalar PrimeNG 21, @primeuix/themes, primeicons, @angular/cdk, tailwindcss v4 + @tailwindcss/postcss.
- `.postcssrc.json`, `styles.scss` com camadas CSS + tokens `--app-*` (claro/escuro).
- `app.config.ts`: `providePrimeNG({ theme: Aura, darkModeSelector: '.app-dark', cssLayer })` +
  `provideAnimationsAsync()`.
- `core/theme/theme.service.ts` (+ spec).
- Shell: `core/layout/shell` (sidebar + topbar + drawer) — App passa a renderizar o shell.
- Gate: lint + test + build verdes. Test-report 10-1.

### 10-2 — Login/silent-refresh + guards + paleta + atalhos + a11y
- `AuthService.verifySession()` via `/me`; agendamento perto da expiração; `returnUrl` no guard.
- `APP_INITIALIZER`/efeito de boot para revalidar token salvo.
- `core/commands/command-registry.service.ts`, `core/commands/shortcut.service.ts`.
- `shared/command-palette/*` (Dialog + lista navegável + autofoco, CDK A11y), `shared/keyboard-help/*`.
- `core/guards/can-deactivate.guard.ts` + interface `FormLeaveGuard`.
- Login repaginado (PrimeNG). a11y baseline (LiveAnnouncer na navegação, foco preso em diálogos).
- Gate verde. Test-report 10-2.

### 10-3 — Repaginar telas + estados + canDeactivate
- Accounts, Exchange, Quoting, Booking, Reconciliation, Health: componentes PrimeNG (Table, Card,
  InputText, Select, Button, Tag, Message, Toast/ConfirmDialog quando fizer sentido) e os estados
  loading/empty/error/permissão/submetendo. Estado de permissão a partir de `error.code` 403/`access.denied`.
- `canDeactivate` nas telas com formulário sujo.
- Atualizar/adicionar specs de componente preservando os testes de estado existentes.
- Gate verde. Test-report 10-3.

### 10-4 — Dashboard KPIs
- `features/dashboard/*`: página + serviço que orquestra accounts/bookings/reconciliation/exchange.
- Cartões com estados por KPI; rota raiz `''` → dashboard (protegida); navegação a partir dos cartões.
- Gate verde. Test-report 10-4.

### 10-5 — Docs + release 0.21.0
- MANUAL.md + MANUAL.en-US.md (shell, navegação, paleta `Ctrl/Cmd+K`, atalhos, tema, login, dashboard).
- README.md + README.en-US.md se mudarem (stack/uso).
- release-notes/0.21.0.md (pt-BR) + CHANGELOG.en-US.md (append).
- Bump `backend/pom.xml` 0.20.1 → 0.21.0; OpenAPI `version` 0.21.0.
- test-report/INDEX + relatório de fase. Tag 0.21.0.

## Gates (a cada fatia)
`cd frontend && npm ci && npm run lint && CI=true npx ng test --no-watch && npm run build`.
Backend: `cd backend && ./mvnw -q verify` ao final (esperado verde, 468 testes; backend intocado).

## Riscos
- Peer-deps PrimeNG 21 × Angular 22 (mitigado: versões estáveis recomendadas pelo ROADMAP).
- Tailwind v4 + PrimeNG camadas CSS (mitigado: `cssLayer` + `@layer` documentado).
- Budget de bundle do `ng build` (ajustar budget se PrimeNG estourar o warning — sem afrouxar gate de erro).
- "Silent refresh" sem refresh token é revalidação (DL-0092); expectativa de renovação real fica p/ Fase 13.
