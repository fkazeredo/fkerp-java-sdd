# Caderno de testes — Fase 17: remover Keycloak → Authorization Server self-hosted (re-gradua SPEC-0024)

## Escopo

Re-graduação da SPEC-0024 (ADR-0018 / DL-0110..0114): remover 100% do Keycloak e servir OIDC pelo
**Spring Authorization Server embutido no app**. Trocamos **só o IdP** — o Resource Server, o
mapeamento de papéis (`realm_access.roles`→`ROLE_*` preservado), a porta `UserContextProvider`, os gates
por papel e o gancho `AUTH_LOGIN` **não mudam**. Reintroduz o store local de usuários (V32, BCrypt) para
o AS autenticar; o frontend mantém `angular-oauth2-oidc` (code+PKCE), só reaponta o `issuer` e usa
silent-refresh por iframe. Acceptance Criteria cobertos: **login e papéis funcionando sem Keycloak**
(dev + E2E), `./mvnw verify` + gates de front verdes, nenhum portão afrouxado.

## Casos por tipo

### Integração — backend (Testcontainers; AS de verdade, profiles dev,e2e)

`com.fksoft.security.AuthorizationServerIntegrationTest` (**novo**; sobe o AS embutido — não é o profile
`test`, então as três cadeias e o seeder rodam de verdade):

- **servesTheOidcDiscoveryDocumentWithTheStandardEndpoints** — `GET /.well-known/openid-configuration`
  → 200, contém `issuer`, `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/userinfo`. (DL-0110 —
  o AS embutido serve o contrato OIDC padrão.)
- **servesTheAuthorizationServerJwkSet** — `GET /oauth2/jwks` → 200, uma JWK RSA (`kty=RSA`). (DL-0110 —
  chave RS256 local servida pelo AS.)
- **seedsTheLocalUserStoreAndResolvesRolesAndPassword** — o `UserDetailsService` resolve `director` com
  `ROLE_DIRECTOR` e a senha bate por BCrypt (`dev12345`). (DL-0112 — store local V32 + seeder dev/E2E;
  BR4 — só hash.)
- **seedsTheSuperUserWithEveryBaseRole** — `dev` tem os 6 papéis base. (DL-0112.)

### Integração — backend (segurança preservada; profile `test`, tokens RS256 locais — DL-0105)

Inalterados e **verdes** (provam que a troca de IdP não mexeu no enforcement — o claim
`realm_access.roles` foi preservado):

`com.fksoft.identity.ResourceServerIntegrationTest`:

- **aTokenWithRealmRolesResolvesToTheMatchingRoles** — token `realm_access.roles=[ROLE_FINANCE]` → `/me`
  200 com `ROLE_FINANCE`. (BR1/BR13.)
- **theRealUserContextResolvesRolesFromTheToken** — `UserContextProvider` resolve username/roles do
  token verificado. (BR1 — porta preservada.)
- **anInvalidTokenYieldsAGeneric401** — token malformado → 401 genérico `auth.unauthenticated`. (BR4.)

`com.fksoft.identity.AccessControlIntegrationTest` (5 casos): NF sem ROLE_FINANCE → 403 auditado; com
ROLE_FINANCE → passa o gate; job exige ROLE_IT; diretiva exige ROLE_DIRECTOR; `/access-audit`/`/roles`
protegidos; primeiro toque autenticado grava `AUTH_LOGIN` (sem token no detalhe). (BR2/BR3/BR10/DL-0082/0083.)

### Arquitetura (back)

- ArchUnit (17 regras) + Spring Modulith verdes: o AS/user store vivem em `infra.security` (não em
  `domain`); o `UserContextProvider` segue o único ponto que toca o `SecurityContextHolder`. Nenhuma
  regra afrouxada.

### Unitário / componente — frontend (Vitest)

- `auth.service.spec.ts` (OIDC, delega ao `OAuthService` stub): expõe o token, `login()` inicia o fluxo,
  `bootstrapSession` confirma via `/me` e espelha o usuário, `verifySession` limpa em 401,
  `clearLocalSession` sem redirect. **Inalterados e verdes** — a API pública do `AuthService` não mudou.
- `login-page.spec.ts` (botão "Entrar" inicia o fluxo com/sem returnUrl). Verde.

### E2E (Playwright) — autoradas contra o AS self-hosted

- `login.spec.ts` — login feliz pelo form `/login` do próprio app → dashboard; sad path (senha errada)
  fica na página de login do AS (`/login?error`), app não é alcançado.
- `permission.spec.ts` — viewer 403 na diretiva director-only; sem token 401; director passa o gate. O
  token vem do **fluxo real code+PKCE no browser** (o SAS, OAuth 2.1, não tem direct-grant), lido do
  sessionStorage.
- `helpers.ts` — `login()` usa o form do AS; `tokenFor(browser, user)` faz o login real e extrai o token.
- Demais jornadas (accounts, finance, aftersales, intelligence, platform-people, smoke, guard,
  unsaved-changes) atualizadas para o `tokenFor(browser, …)`.

## Resultado

- **Backend `./mvnw verify`:** **BUILD SUCCESS** — **480 testes**, 0 falhas/erros; ArchUnit 17; Spring
  Modulith; Spotless (814 arquivos limpos); Checkstyle **0 violações**; JaCoCo **≥ 0,80** (all coverage
  checks met). Jar `acme-travel-erp-0.28.0.jar`. Nenhum portão afrouxado.
- **Frontend:** `ng lint` **0**; `ng test --watch=false` **264 testes** (49 arquivos), 0 falhas;
  cobertura **acima dos limites** (stmts 73,4% / branches 56,5% / funcs 51,1% / lines 78,8%; pisos
  65/55/48/65); `ng build` (produção) OK.
- **E2E:** **19 jornadas** (10 arquivos) **autoradas e compiladas** (`npx playwright test --list` → exit
  0). **Não executadas neste ambiente**: o `npm run e2e:up` precisa **construir a imagem do backend em
  contêiner**, o que requer rede/cache Maven de artefatos indisponível no sandbox — limite de
  infraestrutura, não do código. Os portões autoritativos (backend `verify` + front lint/test/build)
  estão verdes no host.

## Cobertura — o que NÃO está coberto e por quê

- **Execução real do fluxo OIDC end-to-end** (redirect ao `/oauth2/authorize`, form `/login`, troca de
  código, iframe de silent-refresh) — coberto pelas jornadas Playwright, **autoradas mas não executadas**
  no sandbox (ver acima). O boot do AS, o discovery, o JWKS e o store local **são** exercitados de
  verdade pelo `AuthorizationServerIntegrationTest` (Testcontainers).
- **Silent-refresh por iframe**: mecanismo do `angular-oauth2-oidc`; a config é unit-testável só de forma
  rasa (o comportamento real depende da sessão SSO do AS no browser).

## Como reproduzir

```bash
# Backend (Docker no ar para Testcontainers)
cd backend && ./mvnw verify
# só a suíte do AS embutido:
cd backend && ./mvnw -Dtest=AuthorizationServerIntegrationTest test

# Frontend
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build

# E2E (fora do sandbox, com rede de artefatos p/ build da imagem):
cd frontend && npm run e2e:up && npm run e2e && npm run e2e:down
# compilar/coletar sem executar:
cd frontend && npx playwright test --list
```
