# Plano — Fase 13: Identity/AuthZ profissional (gradua SPEC-0024)

> Objetivo: trocar o emissor JWT **in-house** (8k, HS256) por um **IdP externo OIDC vivo (Keycloak)**;
> o backend vira **Resource Server validando JWT por JWKS** (RS256/rotação), mapeando
> `realm_access.roles` → o modelo de papéis da SPEC-0024; o frontend faz **login OIDC code+PKCE** com
> **silent-refresh real**. Resolve as dívidas **DL-0079** (IdP externo vivo) e **DL-0092**
> (silent-refresh real). Decisões: DL-0103..0107.

## Princípios

- **Rule Zero:** trocar a *fonte* do token, não reescrever o domínio. A porta `UserContextProvider` e o
  modelo de papéis (DL-0082) sobrevivem. Sem novo IAM caseiro.
- **Não quebrar o build:** caminho de teste com **JWKS local (par RSA de teste)** mantém os 477 testes
  verdes e exercita o decoder JWKS real. `TestSecurityConfig` (DL-0081) segue autenticando o ator
  full-access quando não há header `Authorization`.
- **Portões sempre verdes:** ArchUnit/Modulith/Spotless/Checkstyle/JaCoCo (back); lint/test/cobertura/
  build (front); E2E. Nenhum afrouxado.

## Fatias

### 13-1 — Backend Resource Server por JWKS + mapeamento de papéis (DL-0104/0105/0107)
- `pom.xml`: já tem `oauth2-resource-server` + `oauth2-jose`. Mantém.
- `application.yml`: `spring.security.oauth2.resourceserver.jwt.issuer-uri` (Keycloak realm) por
  ambiente; remove `identity.jwt.*` (HS256).
- `SecurityConfig`: remove `JwtEncoder`/`JwtDecoder` HS256; o decoder passa a vir do `issuer-uri`
  (auto-config). `JwtAuthenticationConverter` lê `realm_access.roles` (+ `SCOPE_*`). Em **test**, um
  `JwtDecoder` com a chave pública de teste (DL-0105).
- Remove `JwtIssuer`, `SecurityProperties`(HS256), `DevUserSeeder`, `BCryptPasswordHasher`,
  `DevStubUserContextProvider`, `PasswordHasher`/`IdentityUser`/`IdentityUserRepository`/
  `AuthenticatedUser`/`InvalidCredentialsException`/`UserAuthenticated`; `IdentityService.login` e o
  endpoint `POST /api/identity/login` + DTOs `LoginRequest`/`LoginResponse`.
- `IdentityService`: mantém `listRoles()`/`recordAccessDenied()`; ganha auditoria de **primeiro toque**
  autenticado (AUTH_LOGIN) em `/me`.
- Migração **V31**: `DROP TABLE IF EXISTS user_roles, identity_users` (mantém `roles`/`role_permissions`).
- Testes: `TestJwtTokens` (mint RS256 + JWKS local); reescreve `IdentityLoginIntegrationTest` →
  `ResourceServerIntegrationTest`; `AccessControlIntegrationTest`/`ActuatorExposureIntegrationTest`
  trocam `login(user)` por `mintToken(user, roles...)`. ArchUnit/Modulith verdes.
- **Gate:** `./mvnw verify` verde.

### 13-2 — Dev IdP (Keycloak) no compose (DL-0103)
- `infra/keycloak/realm-acme.json`: realm `acme`, 6 realm roles, client `acme-erp-web` (público, PKCE,
  refresh), usuários seed (um por papel + `dev`), redirect/web-origins 4200/4201.
- `docker-compose.yml`: serviço `keycloak` (import realm); `app` aponta `issuer-uri` ao Keycloak.
- `compose.e2e.yaml`: serviço `keycloak` (efêmero), `app` com `issuer-uri` interno, `frontend` com a
  config OIDC do E2E.
- **Gate:** compose sobe; smoke manual do discovery `/.well-known/openid-configuration`.

### 13-3 — Frontend OIDC code+PKCE + silent-refresh real (DL-0106)
- `package.json`: `+ angular-oauth2-oidc`.
- `core/auth/auth.service.ts`: delega à `OAuthService` (login, logout, token, silent-refresh real);
  mantém a API pública (`user`/`isAuthenticated`/`hasRole`/`token`). `core/auth/oidc.config.ts`.
- `auth.interceptor.ts`/`auth.guard.ts`: token agora vem da lib; 401 → re-login OIDC.
- `app.config.ts`: provê `OAuthService` + config; bootstrap do fluxo (discovery + tryLogin + silent
  refresh).
- `features/login/login-page.ts`: botão "Entrar com SSO" → `initLoginFlow()`.
- Specs atualizadas (`auth.service.spec`, `login-page.spec`). i18n dos rótulos.
- **Gate:** `ng lint` + `ng test` (cobertura) + `ng build` verdes.

### 13-4 — E2E login real contra o Keycloak (DL-0103/0106)
- `compose.e2e.yaml` com Keycloak; `frontend/e2e/helpers.ts` dirige a tela de login do Keycloak
  (form usuário/senha → redirect de volta). `login.spec.ts`/`auth-guard.spec.ts`/`permission.spec.ts`
  atualizados ao fluxo OIDC. Banco de dev intacto (DL-0101).
- **Gate:** Playwright verde na 4201.

### 13-5 — Docs + versão
- SPEC-0024: graduação (Business Rules BR12+, seção "Graduação Fase 13", Open Questions resolvidas).
- `docs/MANUAL.md` + `docs/MANUAL.en-US.md`: novo login SSO + perfis/permissões.
- `docs/release-notes/0.23.0.md` (pt-BR, **breaking destacado**) + `CHANGELOG.en-US.md`.
- `docs/test-report/13-*.md` + INDEX.
- OpenAPI: esquema de segurança → OIDC/bearer (JWKS).
- `pom.xml` → `0.23.0`; tag `0.23.0`.

## Riscos
- **Breaking:** remoção de `POST /api/identity/login` (destacado, ADR 0015 §4).
- **E2E:** dirigir a tela do Keycloak no Playwright (form HTML padrão) + timing do redirect.
- **JaCoCo:** remoção de código de login muda a base de cobertura — manter ≥80%.
