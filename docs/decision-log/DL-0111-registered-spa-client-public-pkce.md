# DL-0111 — Identity (Fase 17): client SPA público `acme-erp-web` (Authorization Code + PKCE, sem consent) registrado no AS self-hosted

- **Fase:** 17 (remover Keycloak → AS self-hosted)
- **Spec(s):** SPEC-0024 (BR1; frontend espelha o backend); ADR-0018
- **ADR relacionado:** ADR-0018; reaponta a parte "client SPA público PKCE" da DL-0103/DL-0106
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

O realm Keycloak definia o client SPA público `acme-erp-web` (code+PKCE, redirect/origens para
localhost:4200/4201). Ao remover o Keycloak, é preciso **registrar o mesmo client no SAS** para o
frontend mudar só o `issuer`, decidindo autenticação do client, PKCE e tela de consentimento.

## Decisão

Registrar em memória (`RegisteredClientRepository`) o client **`acme-erp-web`**:

- **`clientAuthenticationMethod = NONE`** (client público — SPA no browser, sem secret).
- **`authorizationGrantType = AUTHORIZATION_CODE`** (o SAS não emite refresh token a client público —
  o silent-refresh é por iframe, DL-0113).
- **`clientSettings.requireProofKey(true)`** (PKCE S256 obrigatório — previne downgrade de PKCE) e
  **`requireAuthorizationConsent(false)`** (sem tela de consentimento — mesma UX do Keycloak: login
  direto de usuário interno do ERP, não federação de terceiros).
- **redirectUris/origens:** `http://localhost:4200/*` (dev) e `http://localhost:4201/*` (E2E);
  post-logout idem. Scopes `openid profile`.
- **`tokenSettings`:** access token 5 min (espelha o `accessTokenLifespan` do realm).

## Justificativa

- **Espelhar o client do Keycloak** mantém o frontend praticamente inalterado (muda só o `issuer`).
- **PKCE + client público** é a recomendação da OAuth 2.0 Security BCP (RFC 9700) para SPA; é o mesmo
  fluxo já usado (DL-0106). `requireProofKey(true)` é a config canônica do how-to-pkce do SAS.
- **Sem consent:** os usuários são internos do ERP (não terceiros); a tela de consentimento seria
  cerimônia (Regra Zero) e o Keycloak também não a mostrava.
- **Confiança=Média / Reversibilidade=Barata:** é uma `@Bean` de registro; ajustar redirect/scope é
  trivial (uma linha).

## Alternativas descartadas

- **Client confidencial + BFF.** Descartada nesta fase (rearquitetura do frontend — ADR-0018).
- **`requireAuthorizationConsent(true)`.** Descartada: fricção sem valor para usuário interno.
- **Registro persistido em tabela (`oauth2_registered_client`).** Descartada (Regra Zero): um único
  client conhecido não justifica schema/CRUD; em memória basta e é reprodutível.

## Impacto

- **Arquivos:** `infra/security/AuthorizationServerConfig.java` (a `@Bean RegisteredClientRepository`).
- **Frontend:** `core/auth/oidc.config.ts` aponta o `issuer` ao próprio app (DL-0113); `clientId`
  permanece `acme-erp-web`.

## Como reverter

Trocar o `RegisteredClient` (ou movê-lo para outro IdP). Barata — é um bean de configuração.
