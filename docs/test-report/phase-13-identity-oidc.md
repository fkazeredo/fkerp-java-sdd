# Caderno de testes — Fase 13: Identity/AuthZ profissional (gradua SPEC-0024)

## Escopo

Graduação da SPEC-0024: o ERP deixa de ser Resource Server do próprio emissor HS256 e passa a validar
JWTs de um **IdP OIDC externo (Keycloak)** por **JWKS** (RS256, rotação), mapeando `realm_access.roles`
→ autoridades `ROLE_*`. Login no frontend por **OIDC code+PKCE** com **silent-refresh real**. Resolve
**DL-0079** (IdP vivo) e **DL-0092** (silent-refresh real). Acceptance Criteria cobertos: autenticação
real via IdP resolve usuário/roles; ações sensíveis exigem o papel e são auditadas; login + refresh;
testes de guard/interceptor e de autorização (sad paths); `./mvnw verify` verde.

## Casos por tipo

### Integração — backend (Testcontainers + JWKS local de teste, DL-0105)

`com.fksoft.identity.ResourceServerIntegrationTest` (envia tokens RS256 mintados por `TestJwtTokens`):

- **aTokenWithRealmRolesResolvesToTheMatchingRoles** — token com `realm_access.roles=[ROLE_FINANCE]` →
  `/me` 200, roles contém `ROLE_FINANCE`. (BR1/BR13 — mapeamento de papéis do IdP.)
- **theRealUserContextResolvesRolesFromTheToken** — `UserContextProvider` real resolve username/roles do
  token verificado. (BR1 — porta preservada.)
- **anInvalidTokenYieldsAGeneric401** — token malformado → 401 genérico `auth.unauthenticated`. (BR4.)

`com.fksoft.identity.AccessControlIntegrationTest`:

- **issuingAnInvoiceWithoutTheFinanceRoleIsForbiddenAndAudited** — director (sem ROLE_FINANCE) emite NF →
  **403** `access.denied` + linha `ACCESS_DENIED` no `system_audit` (actor=director). (BR2/BR3/DL-0082/0083.)
- **issuingAnInvoiceWithTheFinanceRolePassesTheSecurityGate** — finance (ROLE_FINANCE) → passa o gate
  (404 no id falso = chegou ao controller). (BR2.)
- **triggeringAJobRequiresTheItRole** — director 403, it 202. (BR2 — ROLE_IT.)
- **issuingADirectiveRequiresTheDirectorRole** — finance 403 `access.denied`. (BR2 — ROLE_DIRECTOR.)
- **accessAuditAndRolesEndpointsAreThemselvesProtected** — viewer 403 no `/access-audit`; it 200 no
  `/roles` (catálogo local com `ROLE_DIRECTOR`/`billing:invoice:issue`). (BR16 — catálogo local.)
- **firstAuthenticatedTouchIsRecordedInTheAccessAudit** — `/me` registra `AUTH_LOGIN` (actor=finance),
  detalhe sem o token. (BR3/BR4.)

`com.fksoft.observability.ActuatorExposureIntegrationTest` (tokens mintados): prometheus sem token 401,
viewer 403, it 200; env/beans 404. `BusinessMetricsIntegrationTest`: `/me` publica `UserAuthenticated`
→ `acme_identity_logins_total` no `/actuator/prometheus`. `SensitiveDataNotLoggedIntegrationTest`: o
caminho autenticado nunca loga o bearer (BR5).

### Arquitetura — backend

- **ArchitectureTest** (17 regras ArchUnit + Spring Modulith) verde após remover o emissor in-house e o
  store local; `UserContextProvider` segue o único ponto que toca `SecurityContextHolder`; grafo de
  módulos acíclico (Identity → Platform audit facade).

### Unitário — frontend (Vitest)

- `auth.service.spec.ts` (DL-0106): expõe o access token do `OAuthService`; `login()` chama
  `initLoginFlow(returnUrl)`; `bootstrapSession` confirma a sessão por `/me` e espelha o usuário +
  agenda silent-refresh; sem token válido fica deslogado; `verifySession` limpa em 401;
  `clearLocalSession` desloga sem redirect ao IdP.
- `login-page.spec.ts`: o botão **"Entrar com SSO"** inicia o OIDC com `/` ou o `returnUrl`.

### E2E — Playwright (11 specs, fluxo OIDC real contra o Keycloak na 8089)

- `login.spec.ts`: login feliz (SSO → tela do Keycloak → token → **/dashboard**, "Painel" visível);
  credenciais erradas rejeitadas **na tela do IdP** (não chega ao app).
- `auth-guard.spec.ts`: rota protegida sem sessão → `/login?returnUrl=...` (botão SSO visível).
- `permission.spec.ts`: viewer 403 numa diretiva; chamada sem token 401; director **não** 401/403
  (tokens mintados pelo endpoint de token do IdP via cliente direct-grant de E2E).
- `accounts.spec.ts`, `unsaved-changes.spec.ts`: login OIDC real → jornada de contas/formulário.
- `smoke.spec.ts`: tela de login (botão SSO), health e `/api/version` via proxy.

### Smoke / manual (operacional)

- `docker compose up` (dev): Keycloak 8088 importa o realm `acme`; discovery `iss=http://localhost:8088/realms/acme`;
  JWKS expõe chave **RS256/sig**; backend sobe e valida tokens (`/me` 200, 403/401 por papel).
- Stack E2E (`compose.e2e.yaml`): Keycloak 8089 healthy; **11 specs verdes** com DB efêmero limpo.

## Resultado

- **Backend `./mvnw verify`: BUILD SUCCESS — 476 testes (0 falhas), ArchUnit 17, Spotless/Checkstyle 0
  violações, JaCoCo ≥80% atendido.** (Era 477 no 8k/Fase 12: −4 do `IdentityLoginIntegrationTest`
  removido, +3 do `ResourceServerIntegrationTest`.)
- **Frontend:** `ng lint` limpo; **56 testes** Vitest verdes; cobertura 70.8/73.3/55.5/61.0 (acima dos
  pisos 65/65/48/55); `ng build` OK.
- **E2E:** **11 specs Playwright verdes** na 4201 contra o Keycloak real, em DB efêmero.

## Cobertura — o que NÃO está coberto e por quê

- **IdP de produção (Entra/Cognito/Keycloak self-hosted):** não testado — é decisão do dono (DL-0103);
  o contrato OIDC é o mesmo, a troca é por configuração (issuer/clientId).
- **Rotação de chave do IdP em runtime:** não há teste automatizado (exigiria forçar o Keycloak a rodar
  a chave); a capacidade vem do Spring (cache + refetch do JWKS por `kid`) — coberta por construção.
- **Autorização fina por escopo** (`SCOPE_*`): exposta mas não usada no enforcement (Regra Zero) — sem
  testes de escopo até existir uma ação que exija.
- **Verificação no Grafana / operacional do compose:** fora do `./mvnw verify` (BR8); validada
  manualmente nesta fase.

## Como reproduzir

```bash
# Backend (Docker no ar p/ Testcontainers)
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run lint && npm test && npm run build

# E2E (stack isolado com Keycloak)
cd frontend && npm run e2e:up
E2E_BASE_URL=http://localhost:4201 npm run e2e
npm run e2e:down

# Smoke manual do IdP dev
docker compose up -d keycloak
curl -s http://localhost:8088/realms/acme/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'
```
