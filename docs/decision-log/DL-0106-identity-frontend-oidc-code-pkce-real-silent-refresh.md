# DL-0106 — Identity (frontend): login OIDC Authorization Code + PKCE com `angular-oauth2-oidc`; **silent-refresh real** (refresh token), graduando DL-0092

- **Fase:** 13 (Identity/AuthZ profissional — gradua SPEC-0024)
- **Spec(s):** SPEC-0024 (BR1; frontend espelha o backend); SPEC-0026 (login com silent refresh);
  ROADMAP Fase 13 ("login + refresh")
- **ADR relacionado:** `architecture/frontend-angular.md`; DL-0092 (que esta DL **resolve**)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

No 8k/Fase 10 o frontend fazia login por **`POST /login`** (form usuário/senha → JWT in-house) e
"silent refresh" era apenas **revalidação via `/me`** (DL-0092), porque o backend não tinha refresh
token. Com o IdP externo vivo (Keycloak — DL-0103), o login deve ser o **fluxo OIDC padrão** e o
silent-refresh deve **renovar o token de verdade**. Falta decidir a biblioteca e o fluxo.

## Decisão

1. **Authorization Code + PKCE** via **`angular-oauth2-oidc`** (lib madura, certificada OIDC). Config:
   `issuer` = realm `acme`, `clientId` = `acme-erp-web` (público, sem secret), `responseType=code`,
   `scope='openid profile'`, `redirectUri` = origem do app. O login deixa de ser um form próprio: o
   botão "Entrar" chama `initLoginFlow()` → redireciona ao Keycloak → volta com `code` → a lib troca
   por tokens (PKCE).
2. **Silent-refresh real (refresh token).** `angular-oauth2-oidc` renova o access token via
   **refresh token** (rotação habilitada no client) antes da expiração
   (`setupAutomaticSilentRefresh()` / `useSilentRefresh`). Substitui a revalidação por `/me` da
   DL-0092: agora há renovação de token de verdade. A revalidação por `/me` deixa de ser o mecanismo de
   sessão (o token é a fonte; o `/me` vira só leitura de perfil para o shell).
3. **Interceptor/guard preservam o contrato.** O `authInterceptor` anexa `Authorization: Bearer
   <accessToken>` (agora obtido da lib). O `authGuard` segue só refletindo a sessão (o backend é a
   autoridade — BR1). Em 401, faz logout/redireciona ao login OIDC.
4. **`AuthService` mantém a mesma API pública** (`user`, `isAuthenticated`, `hasRole`, `token`,
   `login`, `logout`) — as telas/guards não mudam; muda a **implementação interna** (delega à lib).
   Os papéis exibidos vêm do claim `realm_access.roles` do access token (decodificado no cliente só
   para UI; o backend reimpõe).

## Justificativa

- **Padrão OIDC para SPA:** Authorization Code + PKCE é a recomendação atual da OAuth 2.0 Security BCP
  (RFC 9700) para apps de browser (implicit flow está depreciado). `angular-oauth2-oidc` é a lib de
  referência no ecossistema Angular.
- **Resolve DL-0092 de verdade:** o "silent refresh" agora é renovação por refresh token (o que a
  DL-0092 dizia que a Fase 13 faria), não mais um stopgap de revalidação.
- **frontend-angular.md:** segue o padrão do projeto (core/auth concentra a infra de auth; sem
  espalhar HttpClient). A troca é interna ao `core/auth`.
- **Confiança=Média:** a configuração exata de redirect/timeout do silent-refresh depende do realm;
  isolada no `core/auth`. **Reversibilidade=Moderada:** trocar a lib/IdP é mexer só no `core/auth`.

## Alternativas descartadas

- **Manter o form próprio + Resource Owner Password Grant (ROPC) contra o Keycloak.** Descartada: ROPC
  é desencorajado pela OAuth 2.0 BCP (expõe a senha ao app); perde o login federado/SSO que é o ponto
  do IdP.
- **Implementar code+PKCE à mão (sem lib).** Descartada (Regra Zero): a lib resolve PKCE, discovery,
  refresh e silent-iframe testados; reimplementar é risco sem valor.
- **Continuar com `/me`-revalidation (DL-0092) sem refresh real.** Descartada: a Fase 13 existe para
  graduar isso; manter seria não fechar DL-0092.

## Impacto

- **Arquivos:** `core/auth/auth.service.ts` (delega à `OAuthService`), `auth.interceptor.ts`,
  `auth.guard.ts`, `app.config.ts` (provê `OAuthService`/config OIDC + bootstrap do fluxo),
  `features/login/login-page.ts` (botão "Entrar com SSO" → `initLoginFlow`). `package.json` +
  `angular-oauth2-oidc`. Specs de teste de `auth.service`/`login-page` atualizadas.
- **Migração/Contrato backend:** nenhum (o backend só valida o token — DL-0104).

## Como reverter

Remover a lib e restaurar o form `/login` do 8k (reintroduz DL-0079/0092). Trocar de IdP é mudar
`issuer`/`clientId` no `app.config`. Moderada, confinada ao `core/auth`.
