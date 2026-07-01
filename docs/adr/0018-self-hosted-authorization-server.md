# ADR 0018: Servidor de autorização OIDC self-hosted (Spring Authorization Server embutido) — sai o Keycloak

## Status

Accepted (Fase 17 — troca o IdP; **substitui a DL-0103** (Keycloak dev IdP) e reaponta
DL-0104/0105/0106/0107 para o AS self-hosted; re-gradua a SPEC-0024). Breaking permitido em `0.y`
(ADR 0015 §4), destacado no release note `0.28.0`.

## Context

A Fase 13 (DL-0103..0107) profissionalizou a identidade delegando a autenticação a um **IdP OIDC
externo (Keycloak)** rodando em contêiner: o backend virou Resource Server validando JWTs por JWKS,
o frontend passou a fazer OIDC Authorization Code + PKCE, e o store local de usuários foi aposentado
(V31). Funcionou, mas trouxe um **processo/imagem Docker a mais** (Keycloak) no stack dev e no stack
E2E efêmero, além de um realm export a manter.

**Decisão do dono (ROADMAP Fase 17):** *não* quer Keycloak. Quer **remover 100% do Keycloak** (serviço
no `docker-compose.yml` e no `compose.e2e.yaml`, `infra/keycloak/`, variáveis `KEYCLOAK_*`) e **servir
OIDC pelo próprio Spring** via **Spring Authorization Server (SAS)** self-hosted — trocando **apenas o
IdP**, preservando o frontend OIDC+PKCE e o backend Resource Server. Recomendação explícita do ROADMAP:
*rodar o AuthZ Server embutido no app para não subir novo processo.*

Estado do ecossistema pesquisado antes de decidir (jul/2026):

- No **Spring Boot 4 / Spring Security 7** o Spring Authorization Server **passou a fazer parte do
  Spring Security** (coordenadas `org.springframework.security:spring-security-oauth2-authorization-server`,
  agora versão **7.0.x**, gerida pelo BOM do Boot). Existe o starter
  **`spring-boot-starter-oauth2-authorization-server`** (Boot 4.0.7 → traz SAS 7.0.6). Confirmado
  offline no `~/.m2` e por `dependency:get`.
- O SAS expõe os endpoints OIDC padrão: `/.well-known/openid-configuration`, `/oauth2/authorize`,
  `/oauth2/token`, `/oauth2/jwks`, `/userinfo`, e um `/login` (form). Assina RS256 com uma chave RSA
  local (JWKS servido pelo próprio AS).
- **Limitação relevante do SAS para SPA pública (PKCE):** o SAS **não emite refresh token para client
  público** (`ClientAuthenticationMethod.NONE`), recomendando BFF como alternativa. Isso muda o
  mecanismo de silent-refresh do frontend (ver Consequences e DL-0113).

## Decision

**Rodar o Spring Authorization Server EMBUTIDO no app principal** (recomendação do ROADMAP; sem novo
processo/Docker), com **três `SecurityFilterChain`s** por ordem de precedência, todas em `infra.security`:

1. **`@Order(1)` — cadeia do Authorization Server.** Aplica o `OAuth2AuthorizationServerConfigurer`
   (via `securityMatcher(endpointsMatcher)`), habilita OIDC 1.0, e redireciona para `/login`
   (form) quando não autenticado. Serve `/.well-known/openid-configuration`, `/oauth2/authorize`,
   `/oauth2/token`, `/oauth2/jwks`, `/userinfo`.
2. **`@Order(2)` — cadeia Resource Server (`/api/**`).** É a `SecurityConfig.configure(...)` já
   existente (stateless, matchers públicos, gates por papel DL-0082, `oauth2ResourceServer().jwt(...)`,
   handlers 401/403 auditados). Ordenada **depois** do AS e **antes** da cadeia de form-login.
3. **`@Order(3)` — cadeia de form-login (`/login`, recursos estáticos do login).** Autentica o usuário
   no próprio AS por usuário/senha (BCrypt), via `UserDetailsService` local.

**Design de token/claims (mudança mínima):** um `OAuth2TokenCustomizer<JwtEncodingContext>` injeta no
**access token** o claim **`realm_access.roles`** (mesma forma que o Keycloak emitia) a partir das
authorities `ROLE_*` do usuário autenticado, além de `preferred_username` e `scope`. Assim o
`JwtAuthenticationConverter` do Resource Server (`SecurityConfig`), o utilitário de teste
`TestJwtTokens` e **todos os testes de segurança continuam inalterados** — só a *origem* do token muda
(o próprio app em vez do Keycloak). Assinatura **RS256** com par RSA local (JWKS servido pelo AS).

**Client SPA público registrado** (`RegisteredClientRepository` em memória): `acme-erp-web`,
`ClientAuthenticationMethod.NONE`, `authorization_code`, `requireProofKey(true)` (PKCE S256),
`requireAuthorizationConsent(false)` (mesma UX do Keycloak, sem tela de consentimento), redirect/origem
para `http://localhost:4200` (dev) e `http://localhost:4201` (E2E), scopes `openid profile`. Espelha o
client `acme-erp-web` do realm Keycloak para o frontend mudar só o `issuer`.

**Store local de usuários reintroduzido** (migração **V32**, idempotente): recria `identity_users` +
`user_roles` (que a V31 dropou), com hash **BCrypt**; um `UserDetailsService` lê desse store; um
seeder dev/E2E popula os usuários seed (`dev` + um por papel, senha fraca `dev12345`, **apenas** nos
profiles `dev`/`e2e`). O catálogo papel→permissão (`roles`/`role_permissions`, DL-0107) permanece.

**Backend Resource Server reapontado:** `issuer-uri`/`jwk-set-uri` apontam para o **próprio app**
(mesma base URL). No profile `test`, o `JwtDecoder` local de teste (DL-0105) continua valendo — nada
contata o AS na suíte. `SecurityConfig`, `JwtUserContextProvider`, a porta `UserContextProvider` e
todos os gates por papel ficam **inalterados em comportamento**. O gancho `AUTH_LOGIN` do `system_audit`
permanece (`GET /me` no primeiro toque).

**Frontend:** `angular-oauth2-oidc` permanece (Auth Code + PKCE). O `issuer` passa a ser o próprio app
(mesma origem/porta do backend). O login usa o `/login` (form) do próprio SAS; o botão "Entrar"
continua chamando `initLoginFlow()` que redireciona ao `/login` do AS. O silent-refresh passa a ser por
**iframe silencioso** (SSO do AS), pois o SAS não emite refresh token a client público (DL-0113).

## Consequences

**Positivas:** um processo a menos (sem contêiner Keycloak em dev e no E2E), sem realm export para
manter, tudo Spring (Regra Zero: `security.md` — "não reinvente auth; use Spring Security"). O contrato
OIDC continua padrão; o backend e o frontend mudam **só por configuração** de `issuer`. Os 444 testes
e o mapeamento de papéis sobrevivem sem edição porque o claim `realm_access.roles` foi preservado.

**Negativas / limites:**
- **Sem refresh token para o client público** (limitação do SAS): o silent-refresh passa a usar o
  **iframe silencioso** contra a sessão SSO do AS (mesma origem). É o padrão do `angular-oauth2-oidc`
  para SPA sem refresh token; sessão renovada enquanto o cookie SSO do AS é válido. (DL-0113.)
- **Usuários voltam a viver no ERP** (store local BCrypt) — o ERP volta a custodiar hash de senha, o
  oposto do que a DL-0105/0107 celebrava. É consequência direta da decisão do dono de remover o IdP
  externo; mitigado por: só hash BCrypt (nunca plaintext/token), seed forte só em dev/E2E, e o seam
  `UserDetailsService` permite plugar um diretório corporativo depois.
- **Chave RSA de assinatura em memória** (gerada no boot): tokens são invalidados a cada restart (o
  app é single-instance — ADR 0002). Produção com múltiplas instâncias exigiria chave persistida/
  externalizada (seam registrado; fora do escopo desta fase — Rule Zero).

## Alternatives Considered

- **Manter o Keycloak.** Descartada: é exatamente o que o dono pediu para remover (autoridade =
  pedido do dono > tudo).
- **AS como módulo/serviço co-localizado separado (novo processo).** Descartada: o ROADMAP recomenda
  embutir para não subir processo; embutir é mais simples e não precisa de novo Docker (Regra Zero).
- **Forçar refresh token no client público** (converter/provider custom do SAS). Descartada (Regra
  Zero): reescrever o fluxo de refresh do SAS para contrariar seu default é código frágil; o iframe
  silencioso resolve o mesmo problema com o mecanismo suportado.
- **BFF (backend for frontend) com client confidencial.** Descartada nesta fase: é uma rearquitetura
  ampla do frontend (cookies de sessão, proxy de token) — desproporcional para trocar só o IdP.
- **Manter usuários fora (sem store local), lendo de um diretório.** Descartada: sem IdP externo não há
  de onde ler; o store local BCrypt é o caminho mínimo para autenticar no `/login` do AS.

## Notes

Endpoints do AS ficam em `infra.security` (não em `domain` — ArchUnit/Modulith verdes). Nada de
segredo/token logado; hash BCrypt only. OpenAPI atualizada (esquema OIDC → AS self-hosted, mesmo app).
Decisões detalhadas: **DL-0110** (AS embutido + claims), **DL-0111** (client SPA público PKCE +
sem-consent), **DL-0112** (store local V32 + UserDetailsService + seeder), **DL-0113** (frontend
issuer + silent-refresh por iframe), **DL-0114** (Keycloak removido do compose/infra/env).
