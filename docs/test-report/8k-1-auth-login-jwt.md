# Caderno de testes — Slice 8k-1 (Identity: autenticação real + login JWT, graduando o stub)

## Escopo

- **Spec:** SPEC-0024 (BR1 — `UserContextProvider` real deriva usuário/roles de token verificado;
  BR4 — credenciais/erro genéricos; BR6/BR9 — stub atrás de profile; BR7/BR8 — auth in-house + módulo
  `identity`).
- **DLs:** DL-0079 (auth in-house JWT HS256, OIDC externo na Fase 13), DL-0080 (módulo `identity` +
  V29), DL-0081 (gradua o stub mantendo a porta; `TestSecurityConfig` mantém os 434 testes verdes).
- **Acceptance Criteria cobertos:** "Autenticação real resolve usuário/roles" (login JWT + `/me`); "O
  stub permanece só em dev (perfil)" (DevStub `@Profile("dev")`, JWT provider default/test); "`./mvnw
  verify` verde".

## Casos de teste

### Integração (Testcontainers + Postgres real) — `IdentityLoginIntegrationTest`
| Caso | O que verifica | Regra |
|---|---|---|
| `aSeededUserLogsInAndGetsATokenWithItsRoles` | `POST /login` (user `finance`/`dev12345`) → 200 + `Bearer` token + `expiresIn` + roles `[ROLE_FINANCE]` | BR1; login real |
| `theRealUserContextResolvesRolesFromTheToken` | `GET /me` com o token → 200 + username/roles do token (via `JwtUserContextProvider` real) | BR1; gradua o stub |
| `anInvalidTokenYieldsAGeneric401` | `GET /me` com token inválido → 401 `auth.unauthenticated` (genérico) | BR4 |
| `badCredentialsYieldAGeneric401WithoutRevealingUserExistence` | senha errada **e** usuário inexistente → mesmo 401 `identity.credentials.invalid` | BR4 (não revela existência) |

### Regressão (a maior restrição da fase: não quebrar o build)
- **434 testes pré-existentes verdes** com a segurança **montada** (não removida): a
  `TestSecurityConfig` (profile `test`) autentica um ator de teste com acesso total **somente** quando
  não há header `Authorization` — então os testes antigos seguem sem mandar token e passam, enquanto
  os testes de segurança (que mandam token) exercitam o caminho real (401/403). Confirmado por
  amostragem (`CommercialPolicyIntegrationTest` mostra `definedBy=test-actor`; `PlatformJobApiIntegrationTest`
  trigger 202; `FinanceIntegrationTest` close por `test-actor`).

### Arquitetura
- **Modulith:** `identity` é o **21º** módulo `@ApplicationModule`; depende da fachada pública
  `SystemAuditService` do Platform (consumer-leaf que não importa `identity`) → grafo **acíclico**.
  `ModularityTests.verify()` verde.
- **ArchUnit (15 regras):** domínio não depende de infra/application; a config Spring Security/JWT
  vive em `infra.security` atrás da porta `UserContextProvider`/`PasswordHasher`.
- **HttpErrorMappingCompletenessTest:** `InvalidCredentialsException` mapeada (401) — completo.

## Resultado

```
./mvnw verify  → BUILD SUCCESS
Tests run: 438, Failures: 0, Errors: 0, Skipped: 0   (prior 434 + 4 novos de login)
ArchitectureTest: 15 regras verdes · Spring Modulith: 21 módulos acíclicos
Checkstyle: 0 violations · Spotless: OK
Flyway: V1…V29 aplicadas (V29 = create identity)
```

## Cobertura

- **Coberto:** login (sucesso/falha genérica), resolução do contexto real pelo token, stub atrás de
  profile, seed dev/test, migração V29, gates.
- **Não coberto nesta fatia (intencional):** enforcement por papel das ações sensíveis (403) e a
  auditoria de acesso → **slice 8k-2**; tela de login Angular → **slice 8k-3**. IdP externo OIDC vivo
  → **Fase 13** (DL-0079).

## Como reproduzir

```bash
cd backend && ./mvnw -o -Dtest=IdentityLoginIntegrationTest test   # só o login (Docker no ar)
cd backend && ./mvnw verify                                        # suíte completa + portões
```
