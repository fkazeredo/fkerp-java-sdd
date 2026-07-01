# DL-0113 — Identity (Fase 17, frontend): `issuer` aponta para o AS self-hosted; silent-refresh por **iframe silencioso** (reaponta a DL-0106)

- **Fase:** 17 (remover Keycloak → AS self-hosted)
- **Spec(s):** SPEC-0024 (BR1/BR15; frontend espelha o backend); ADR-0018
- **ADR relacionado:** ADR-0018; **reaponta a DL-0106** (frontend OIDC code+PKCE + silent-refresh);
  `architecture/frontend-angular.md`
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

Na Fase 13 o frontend fazia OIDC code+PKCE contra o Keycloak com **silent-refresh por refresh token**
(DL-0106). O AS self-hosted (SAS) **não emite refresh token para client público** (limitação do SAS —
ADR-0018). É preciso: (a) reapontar o `issuer` para o próprio app; (b) escolher um mecanismo de
silent-refresh suportado.

## Decisão

1. **`issuer` = o próprio app.** O `resolveIssuer()` do `oidc.config.ts` passa a devolver a origem do
   **backend/AS** (mesma origem que serve `/oauth2/*` e `/.well-known/openid-configuration`) em vez do
   host:porta do Keycloak. Em dev/E2E o AS roda embutido no backend (8080 dev, 8081 E2E); o frontend
   (4200/4201) descobre o issuer pela porta. `clientId` permanece `acme-erp-web`.
2. **Silent-refresh por iframe silencioso.** Como não há refresh token para o client público, ativa-se
   `useSilentRefresh: true` + `silentRefreshRedirectUri` (`/silent-refresh.html`) e
   `setupAutomaticSilentRefresh()`: o `angular-oauth2-oidc` renova o access token num **iframe oculto**
   usando a sessão SSO do AS (cookie `JSESSIONID` do AS, mesma origem). Substitui o refresh-token da
   DL-0106.
3. **Interceptor/guard/`AuthService` API pública inalterados.** O bearer continua anexado pelo
   `authInterceptor`; o `authGuard` só reflete a sessão; `GET /me` continua confirmando o usuário e
   registrando o `AUTH_LOGIN`. O login continua sendo `initLoginFlow()` → `/login` (form) do AS.

## Justificativa

- **Mecanismo suportado pelo SAS + `angular-oauth2-oidc`:** o iframe silencioso é o caminho padrão da
  lib para SPA sem refresh token; renova enquanto a sessão SSO do AS vive. Forçar refresh token no SAS
  para client público exigiria converter/provider custom (frágil — Regra Zero, ADR-0018).
- **Mudança mínima no frontend:** só o `issuer` e a config de refresh mudam; telas/guard/interceptor
  ficam iguais. A API pública do `AuthService` não muda (as telas não sabem que o IdP trocou).
- **Confiança=Média:** o timing/redirect exato do silent-iframe depende do AS; isolado no `core/auth`.
  **Reversibilidade=Moderada:** trocar de IdP/mecanismo mexe só no `core/auth`.

## Alternativas descartadas

- **Manter refresh-token silent-refresh (DL-0106).** Descartada: o SAS não emite refresh token a client
  público — não é uma opção sem BFF ou hack.
- **Sem silent-refresh (re-login ao expirar).** Descartada: pioraria a UX que a Fase 13 melhorou; o
  iframe silencioso mantém a sessão fluida com o mecanismo suportado.
- **BFF com cookie de sessão.** Descartada nesta fase (rearquitetura — ADR-0018).

## Impacto

- **Arquivos:** `frontend/src/app/core/auth/oidc.config.ts` (issuer → próprio app; `useSilentRefresh:
  true` + `silentRefreshRedirectUri`), `frontend/public/silent-refresh.html` (novo), specs de
  `auth.service`/`login-page` conforme necessário; `e2e/helpers.ts` (login pelo `/login` do AS).
- **Contrato backend:** nenhum (o backend só valida o token — DL-0110).

## Como reverter

Reapontar `issuer` a um IdP externo e restaurar o refresh-token silent-refresh (se o IdP emitir).
Confinado ao `core/auth`. Moderada.
