# 0026 - UX & Frontend profissional (shell SaaS, tema, paleta de comandos, login, dashboard)

Status: Approved
Fase: 10
Related ADRs: 0008 (Frontend Stack — alvo), 0005 (JWT), 0015 (SemVer)
Related DLs: DL-0090 (stack PrimeNG/Tailwind — gradua DL-0003), DL-0091 (tema), DL-0092 (silent
refresh), DL-0093 (paleta/atalhos), DL-0094 (dashboard KPIs)

## Goal

Elevar o frontend Angular do ERP ao **padrão profissional** do projeto irmão fkerp-poc, sem mudar o
backend: shell SaaS (sidebar + topbar + drawer mobile), tema claro/escuro, paleta de comandos
`Ctrl/Cmd+K` com atalhos e ajuda, login com revalidação silenciosa de sessão, proteção de
não-salvos (`canDeactivate`), **estados reais** (loading/empty/error/permissão) em **todas** as
telas e um **dashboard com KPIs**. Acessível (WCAG 2.1 AA como meta) e todo texto via i18n.

## Scope

**Em escopo (frontend):**
- Stack de UI: PrimeNG 21 (preset **Aura** via `@primeuix/themes`) + primeicons + `@angular/cdk` +
  Tailwind v4 integrado por camadas CSS (DL-0090). Mantém Angular 22 zoneless + signals + ngx-translate.
- **Shell SaaS**: sidebar de navegação orientada a workflow, topbar (marca, busca/paleta, tema,
  usuário/sair), **drawer** colapsável em telas pequenas; `<router-outlet>` na área de conteúdo.
- **Tema claro/escuro** (DL-0091): `ThemeService`, seletor `.app-dark`, tokens `--app-*`, toggle na
  topbar, persistência local, default = preferência do SO.
- **Paleta de comandos** `Ctrl/Cmd+K` (DL-0093): busca de comandos (navegação + tema + sair),
  navegação por teclado, autofoco; **atalhos globais** (`g`+tecla para navegar) que ignoram campos
  editáveis; **`?`** abre a ajuda de atalhos.
- **Login + revalidação silenciosa de sessão** (DL-0092): tela de login repaginada; no boot, valida o
  token salvo via `GET /api/identity/me`; agenda revalidação perto da expiração; em 401, logout +
  redireciono ao login preservando `returnUrl`. Interceptor bearer e guard já existentes do 8k.
- **Proteção de não-salvos**: guard `canDeactivate` reusável para telas com formulário "sujo".
- **Repaginação de todas as telas** (Accounts, Exchange, Quoting, Booking, Reconciliation, Health) com
  componentes PrimeNG e os estados **loading / empty / error / permissão / submetendo**.
- **Dashboard com KPIs** (DL-0094): cartões de Contas, Reservas, Conciliação e Câmbio, calculados no
  cliente a partir dos endpoints de lista existentes; vira a rota raiz protegida.
- i18n: todos os rótulos/estados/toasts via ngx-translate (pt-BR + en).

**Fora de escopo:**
- Qualquer mudança de backend (endpoint novo, schema, migração, contrato) — a fase é frontend-only
  (DL-0094). Refresh token / OIDC externo vivo = Fase 13 (SPEC-0024/Fase 13).
- Observabilidade (métricas/Grafana/`/api/version`) = Fase 11. E2E Playwright = Fase 12.
- Telas de módulos ainda sem frontend (Compliance/Finance/Billing/Payout/etc.) — esta fase repagina
  as telas **já existentes** e entrega o dashboard; novas telas de módulo nascem nas suas fases.

## Business Context

O frontend até a Fase 9 é funcional mas "plano" (CSS próprio, DL-0003), com uma barra de navegação
simples. O operador da Acme Travel precisa de uma experiência de ERP profissional: navegar rápido
(teclado/paleta), enxergar o estado do negócio num relance (dashboard), trabalhar em tema claro ou
escuro, e nunca perder dados por sair de um formulário sem salvar. A autoridade de autorização
**continua sendo o backend** (security.md, SPEC-0024); o frontend apenas espelha papéis para
navegação/ocultação e reage ao veredito do backend (401/403).

## Business Rules

```txt
BR1  O frontend usa PrimeNG 21 (preset Aura), Tailwind v4 e @angular/cdk, em Angular 22 zoneless +
     signals; texto de usuário SEMPRE via i18n (ngx-translate) — nada hardcoded. (ASSUMIDO ver DL-0090)
BR2  O shell SaaS MUST ter sidebar de navegação, topbar e drawer responsivo; o item de navegação ativo
     é destacado; a navegação reflete os papéis (itens cujo acesso o papel não tem ficam ocultos),
     mas isso é só UX — o backend é a autoridade.
BR3  Tema claro/escuro MUST ser alternável em runtime (sem rebuild) via classe `.app-dark`,
     persistido localmente; sem escolha salva, segue `prefers-color-scheme`. (ASSUMIDO ver DL-0091)
BR4  `Ctrl/Cmd+K` abre a paleta de comandos com autofoco; a paleta lista comandos de navegação, tema e
     sessão; navega por ↑/↓; executa com Enter; fecha com Esc. (ASSUMIDO ver DL-0093)
BR5  Atalhos globais de letra (ex.: `g a`) navegam, mas MUST ser ignorados quando o foco está em
     campo editável (input/textarea/select/contenteditable); `?` abre a ajuda de atalhos, derivada da
     mesma fonte de comandos. (ASSUMIDO ver DL-0093)
BR6  Login válido MUST guardar a sessão e navegar ao app; credenciais inválidas mostram erro genérico
     (o backend não revela se o usuário existe — SPEC-0024 BR4). Submetendo desabilita o botão.
BR7  No boot, havendo token salvo, o app MUST revalidá-lo silenciosamente via `GET /api/identity/me`;
     200 mantém a sessão (usuário vindo do backend), 401 limpa a sessão. Perto da expiração, revalida;
     401 → logout + redireciono ao login preservando a rota pretendida (`returnUrl`). NÃO há refresh
     token nesta fase. (ASSUMIDO ver DL-0092)
BR8  Toda tela MUST tratar os estados reais aplicáveis: loading, empty, error (com "tentar novamente"),
     submetendo (botão desabilitado), e permissão negada (403 vira estado de permissão, não erro cru).
BR9  Sair de uma tela com formulário "sujo" (alterado e não salvo) MUST pedir confirmação
     (`canDeactivate`); cancelar mantém na tela.
BR10 O dashboard é a rota raiz protegida e MUST mostrar KPIs de Contas, Reservas, Conciliação e Câmbio,
     calculados a partir dos endpoints de lista existentes; cada cartão tem seus próprios estados
     loading/empty/error/permissão. (ASSUMIDO ver DL-0094)
BR11 Acessibilidade: foco visível, rótulos em formulários, botões nativos, `aria` onde necessário,
     foco preso em diálogos (CDK), anúncios de mudança de rota/abertura de paleta; meta WCAG 2.1 AA.
```

## Telas / Jornadas

1. **Login** — usuário/senha; submetendo; erro genérico; ao logar vai ao dashboard (ou ao `returnUrl`).
2. **Shell** — sidebar (Dashboard, Contas, Câmbio, Cotações, Reservas, Conciliação, Saúde), topbar
   (busca/paleta, tema, usuário/sair), drawer no mobile.
3. **Dashboard** — cartões de KPI com estados; clicar num cartão leva à tela do domínio.
4. **Paleta de comandos** — `Ctrl/Cmd+K`; buscar e executar; ajuda em `?`.
5. **Telas de domínio repaginadas** — Accounts, Exchange, Quoting, Booking, Reconciliation, Health, com
   componentes PrimeNG e os estados reais; formulários com `canDeactivate` quando há rascunho.

## Acceptance Criteria

```txt
AC1  (build/lint) `npm ci` instala o stack; `ng lint`, `ng test` (headless/CI) e `ng build` passam
     verdes com PrimeNG/Tailwind/CDK integrados; budget de bundle respeitado.
AC2  (shell) O shell renderiza sidebar + topbar; o item ativo é destacado; em viewport estreito o
     drawer abre/fecha; todos os rótulos vêm do i18n.
AC3  (tema) Alternar o tema adiciona/remove `.app-dark` no documento e persiste a escolha; ao recarregar
     a escolha é respeitada; sem escolha salva, segue a preferência do SO.
AC4  (paleta) `Ctrl/Cmd+K` abre a paleta com foco no campo; digitar filtra; Enter executa o comando
     selecionado (ex.: navegar para Contas); Esc fecha.
AC5  (atalhos) Um atalho de navegação (`g a`) navega quando o foco NÃO está em campo editável e é
     ignorado quando está; `?` abre a ajuda listando os atalhos.
AC6  (login) Login válido guarda a sessão e navega; inválido mostra o código de erro genérico e não
     navega; durante a requisição o estado "submetendo" desabilita o botão.
AC7  (silent refresh) No boot com token salvo, o app chama `GET /me`; em 200 mantém a sessão a partir
     da resposta; em 401 limpa a sessão. (testado no AuthService)
AC8  (canDeactivate) Tentar sair de um formulário sujo dispara a confirmação; aceitar navega, recusar
     permanece.
AC9  (estados) Cada tela repaginada exibe loading ao carregar, empty quando a lista vem vazia, error com
     "tentar novamente" em falha, e estado de permissão em 403.
AC10 (dashboard) O dashboard carrega os KPIs dos endpoints existentes e mostra os números; cada cartão
     trata loading/empty/error; é a rota raiz e exige autenticação.
AC11 (a11y/i18n) Não há texto de usuário fora do i18n nas telas repaginadas; diálogos prendem o foco;
     formulários têm rótulos associados.
```

## Tests Required

- **Component/unit (vitest + TestBed, headless):**
  - `ThemeService`: aplica/remove `.app-dark`, persiste, lê preferência do SO (AC3).
  - `CommandRegistry`/`ShortcutService`: registra comandos; `Ctrl/Cmd+K` abre; atalho de letra navega e
    é ignorado em campo editável (AC4, AC5).
  - `AuthService.verifySession()`: 200 mantém, 401 limpa (AC7); login mantém os testes do 8k verdes (AC6).
  - `canDeactivate` guard + um componente com `isDirty()` (AC8).
  - Cada página repaginada: testes de estado (loading/empty/error) preservados/atualizados (AC9).
  - `DashboardPage`: agrega os KPIs de serviços mockados; estados por cartão (AC10).
  - Shell: renderiza navegação e toggle de tema; rótulos i18n (AC2).
- **Gate de build:** `ng lint` + `ng test --no-watch` + `ng build` verdes (AC1).
- **Backend:** inalterado; `./mvnw verify` permanece verde (468 testes) por construção (sem mudança).

## Input/Output Examples

```http
# Revalidação silenciosa no boot (DL-0092) — usa o endpoint existente do 8k
GET /api/identity/me           Authorization: Bearer <token>
200 OK  { "userId":"u1","username":"director","displayName":"Diretor","roles":["ROLE_DIRECTOR"] }
401 Unauthorized               → o frontend limpa a sessão e manda ao /login (returnUrl preservado)
```

```txt
# KPIs do dashboard (DL-0094) — compostos no cliente, sem endpoint novo
Contas        ← GET /api/accounts                 (total + por status)
Reservas      ← GET /api/bookings                 (total + por status; destaque PENDING/CONFIRMED)
Conciliação   ← GET /api/reconciliation           (nº casos; OPEN/DISCREPANCY; Σ expectedSpread)
Câmbio        ← GET /api/exchange/pinned-rates/current  (taxa congelada vigente)
```

## Out of Scope / Non-goals

- Backend novo de qualquer tipo (refresh token, endpoint de dashboard agregado, schema). Fica para as
  fases dedicadas (13 OIDC; read-models de Intelligence/observabilidade nas fases 7/11).
- Internacionalização além de pt-BR/en já existentes; novas telas de módulos sem frontend.

## Open Questions

(nenhuma em aberto — as lacunas de UX foram resolvidas como ASSUMIDO em DL-0090..DL-0094; ver as
referências em cada Business Rule. A semântica de "silent refresh" sem refresh token é a de DL-0092,
a ser graduada na Fase 13 quando houver emissor OIDC.)
