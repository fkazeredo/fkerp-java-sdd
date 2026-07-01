# Plano — Fase 17: remover Keycloak → Spring Authorization Server self-hosted

> Base: `origin/develop` (0.27.0). Alvo: re-graduar a SPEC-0024 trocando **só o IdP** (Keycloak →
> Spring Authorization Server embutido), preservando o frontend OIDC+PKCE e o backend Resource Server.
> Decisões: **ADR-0018** + **DL-0110..0114**. Versão alvo: **0.28.0** (MINOR, breaking — Keycloak sai).

## Princípio

A Fase 13 desacoplou os testes do IdP: `TestJwtTokens` já minta RS256 no formato Keycloak
(`realm_access.roles`) e o `SecurityConfig` já mapeia esse claim. Se o AS self-hosted **emitir o mesmo
claim**, o Resource Server, o `JwtUserContextProvider`, os gates por papel e os 444 testes ficam
**inalterados**. A fase é, portanto, majoritariamente **adição** (o AS) + **reaponte de config** +
**remoção** do Keycloak — não um rip-and-replace do enforcement.

## Fatias (ordem de dependência)

### Fatia 17a — Authorization Server embutido (backend)
- `pom.xml`: + `spring-boot-starter-oauth2-authorization-server` (versão via BOM Boot 4.0.7 → SAS 7.0.6).
- `infra/security/AuthorizationServerConfig.java`:
  - `@Bean @Order(1) authorizationServerSecurityFilterChain` (OAuth2AuthorizationServerConfigurer,
    OIDC, redirect a `/login`).
  - `@Bean @Order(3) loginSecurityFilterChain` (form-login em `/login`, recursos do login públicos).
  - `SecurityConfig` recebe `@Order(2)` na cadeia de produção `/api/**`.
  - `@Bean JWKSource<SecurityContext>` (par RSA gerado no boot), `@Bean JwtDecoder` (self, para a
    cadeia AS), `@Bean AuthorizationServerSettings` (default), `@Bean RegisteredClientRepository`
    (`acme-erp-web` público PKCE — DL-0111).
  - `@Bean OAuth2TokenCustomizer<JwtEncodingContext>`: injeta `realm_access.roles`,
    `preferred_username`, `scope` no access token (DL-0110).
- `application.yml`: `issuer-uri`/`jwk-set-uri` → próprio app.
- **Teste vermelho→verde:** subir contexto com o AS; `GET /.well-known/openid-configuration` responde;
  o Resource Server continua validando os tokens de teste (suíte existente permanece verde).

### Fatia 17b — Store local de usuários (backend)
- `V32__reintroduce_local_user_store.sql` (idempotente): recria `identity_users` + `user_roles`.
- `infra/security/AppUser` (entidade), `AppUserRepository`, `AppUserDetailsService` (UserDetailsService).
- `PasswordEncoder` (BCrypt) bean; `DevUserSeeder` (`@Profile dev,e2e`) — `dev` + 1 por papel, `dev12345`.
- **Teste:** `UserDetailsService` resolve papéis; seeder cria usuários; login form autentica (integração).

### Fatia 17c — Testes de segurança adaptados honestamente
- `TestJwtTokens`/`TestSecurityConfig`: inalterados (o claim `realm_access.roles` foi preservado) —
  confirmar verde. Novo teste de integração: o AS emite token via `/oauth2/token`? (Opcional; o
  fluxo code+PKCE é coberto no E2E.) Adicionar teste do token customizer se necessário.
- Rodar `./mvnw verify` — 444 testes + novos, JaCoCo ≥ 0,80.

### Fatia 17d — Frontend reapontado
- `core/auth/oidc.config.ts`: `issuer` → próprio app; `useSilentRefresh: true` +
  `silentRefreshRedirectUri`. `public/silent-refresh.html` (novo).
- Ajustar specs `auth.service`/`login-page` se necessário.
- `npm run lint && npm test && npm run build` verdes.

### Fatia 17e — Remover Keycloak
- `docker-compose.yml`/`compose.e2e.yaml`: remover serviço `keycloak` + `depends_on`; reapontar OIDC.
- `infra/keycloak/` deletado; `.env.example` sem `KEYCLOAK_*`, `OIDC_*` → próprio app.
- `e2e/helpers.ts`: login pelo `/login` do AS; `permission.spec.ts`/`login.spec.ts` reautorados.

### Fatia 17f — Docs + release
- SPEC-0024: seção "Re-graduação — Fase 17" + Open Questions atualizadas.
- `pom.xml` → 0.28.0; OpenAPI security scheme → OIDC self-hosted.
- Release note `docs/release-notes/0.28.0.md` (pt-BR) + `CHANGELOG.en-US.md`.
- `MANUAL.md` + `MANUAL.en-US.md`: login local/perfis; nota "Keycloak removido".
- `docs/test-report/phase-17-self-hosted-as.md` + INDEX.

## Portões (inegociáveis)
`./mvnw verify` verde (ArchUnit, Modulith, Spotless, Checkstyle, JaCoCo ≥ 0,80); frontend
lint/test/coverage/build verdes; nenhum gate afrouxado; migração V32 idempotente; nada de token/segredo
logado; OpenAPI atualizada.

## Riscos
- **SAS sem refresh token para client público** → silent-refresh por iframe (DL-0113). Mitigado.
- **Boot 4 auto-config do AS** pode exigir ordenar cadeias com cuidado (AS @1, RS @2, login @3).
- **E2E** pode não executar no sandbox (build de imagem in-container precisa de rede) → autorar os
  journeys para COMPILAR (`playwright test --list`) e reportar "authored, not executed".
