# Caderno de testes — Slice 8k-3 (Identity: login no frontend + docs bilíngues + release 0.19.0)

## Escopo

- **Spec:** SPEC-0024 (API `GET /me`; experiência de login para o operador; o backend é a autoridade —
  o front só espelha).
- **DLs:** DL-0079/0081 (a porta/login no front consome o emissor in-house; espelha o token).
- **Entrega:** tela Angular de **login**, `AuthService` (token em localStorage + usuário do token como
  signal), **interceptor** (anexa o Bearer; no 401 limpa sessão e vai ao login), **guard** de rota,
  usuário + "Sair" no topo; i18n pt-BR/en; OpenAPI 0.19.0; manual bilíngue; release note + CHANGELOG.

## Casos de teste (frontend — Vitest)

### `login-page.spec.ts`
| Caso | O que verifica |
|---|---|
| navigates to the app after a successful login | submit OK → não-submetendo, sem erro, navega para `/accounts` |
| shows the generic error code when the credentials are rejected | erro do backend → `errorCode='identity.credentials.invalid'`, não navega |
| stays in the submitting state until the response resolves | estado de carregamento até resolver |
| does not submit when fields are empty | sem usuário/senha não chama o serviço |

### `app.spec.ts` (atualizado)
| Caso | O que verifica |
|---|---|
| creates the app shell with the navigation and a login link when logged out | shell renderiza; deslogado mostra o link **Entrar** (7 âncoras: 6 features + login) |

## Resultado

```
frontend:
  npm run lint  → All files pass linting
  npm test      → 8 test files, 18 tests passed (eram 14; +4 do login)
  npm run build → Application bundle generation complete (sucesso)

backend (regressão após OpenAPI 0.19.0 + pom 0.19.0):
  ./mvnw verify → BUILD SUCCESS, 444 tests, ArchUnit 15, Modulith 21, Checkstyle 0
```

## Cobertura

- **Coberto:** sucesso/erro/carregando do login, validação de campos vazios, shell ciente do usuário.
- **Não coberto (intencional):** silent-refresh do token, tela de gestão de usuários/papéis e
  repaginação visual completa → **Fase 10 (UX)/13**; o E2E Playwright é a **Fase 12**. O valor de
  segurança é **backend-first** (8k-1/8k-2): o backend é a única autoridade.

## Como reproduzir

```bash
cd frontend && npm ci && npm run lint && npm test && npm run build
cd backend && ./mvnw verify
```
