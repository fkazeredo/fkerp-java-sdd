# 0024 - Identity (Autenticação, Papéis e Auditoria de Acesso)

Status: Approved — **re-graduado na Fase 17 (OIDC self-hosted, Keycloak removido; ver "Re-graduação —
Fase 17"). A "Graduação — Fase 13" fica como histórico.**
Related ADRs: 0011, 0012, 0014, 0015, **0018**

> Convenções herdadas da **SPEC-0001** (que entregou o `UserContextProvider` **stub de dev**). Esta spec
> **gradua** esse stub em autenticação real. **Subdomínio genérico** (redesenho linha 136: "avaliar
> comprar"): preferir **IdP externo (OIDC)**; a entrega aqui é a integração real + modelo de papéis +
> auditoria de acesso, não um IAM caseiro.

## Goal

Substituir o stub de identidade por **autenticação real** (Spring Security, idealmente via **IdP
OIDC**), um **modelo de papéis/permissões** que os módulos já assumem (papel diretor para diretivas,
papéis operacional/TI/financeiro), e **auditoria de acesso** — fechando a fronteira de segurança que as
demais specs pressupõem (`security.md`).

## Scope

**Em escopo:** integração de autenticação (Spring Security + Resource Server OIDC, ou provedor
equivalente); a implementação **real** do `UserContextProvider` (usuário/claims/roles a partir do token);
o modelo `Role`/`Permission` e o mapeamento para as autorizações já citadas nas specs (ex.: DIRECTIVE →
papel diretor; emitir NF → financeiro; disparar crawler → TI); **auditoria de acesso** (login, ações
sensíveis, negações).

**Fora de escopo:** **gestão de usuários/SSO corporativo** completa (preferir o **IdP** como fonte);
identidade **comercial** das contas (isso é Accounts/SPEC-0002 — agências/agentes ≠ usuários internos do
ERP); criptografia de dados em repouso (infra/Platform).

## Business Context

Várias specs já dizem "papel diretor", "papel financeiro", "papel TI". Até agora, isso era um **stub**.
Identidade real é pré-requisito para produção: sem ela, autorização e auditoria de acesso são fictícias.
Como é subdomínio **genérico**, o caminho recomendado é **delegar a um IdP** (Keycloak/Entra/Cognito) e
manter aqui só o **modelo de papéis** e o **enforcement/auditoria**.

## Business Rules

```txt
BR1  Requisições autenticadas MUST derivar o usuário/roles de um token verificado (OIDC/JWT); o
     UserContextProvider real substitui o stub, sem mudar a interface que os módulos consomem.
BR2  Autorização MUST ser por papel/permissão; as ações sensíveis já marcadas nas specs MUST exigir o
     papel correspondente (ex.: DIRECTIVE → ROLE_DIRECTOR; issue NF → ROLE_FINANCE; trigger crawler →
     ROLE_IT). Negação => 403 + auditoria.
BR3  Cada login e cada ação sensível (e cada negação) MUST ser auditado (ator, ação, recurso, quando,
     correlation id) — `security.md`.
BR4  Segredos/tokens MUST NUNCA ser logados; mensagens de erro de auth MUST ser genéricas (não revelar
     se o usuário existe).
BR5  O modelo de papéis MUST ser a única fonte de verdade de autorização interna; módulos não
     reimplementam regra de acesso — consomem o contexto de segurança.
BR6  Em ambiente de dev, o stub da SPEC-0001 pode permanecer atrás de profile; em produção, MUST estar
     desativado (perfil/flag).
BR7  ASSUMIDO (ver DL-0079). No 8k a autenticação real é **in-house**: Spring Security + emissão/
     verificação de **JWT (HS256)** próprias (o ERP é Resource Server do seu próprio emissor). Um
     **IdP externo OIDC vivo** (JWKS/rotação, escopos finos) é a consolidação da **Fase 13**; a porta
     `UserContextProvider` é o seam que sobrevive à troca.
BR8  ASSUMIDO (ver DL-0080). Novo módulo Modulith `domain.identity` (21º) é dono do modelo de papéis/
     permissões e do **usuário local mínimo** (`identity_users`, senha BCrypt). A configuração de
     segurança/JWT mora em `infra.security`. Usuários geridos **localmente** no v1 (migram ao IdP na 13).
BR9  ASSUMIDO (ver DL-0081). Graduar ≠ rip-and-replace: a porta `UserContextProvider` não muda; o
     adapter real (`JwtUserContextProvider`) vale em produção/default; o stub permissivo fica atrás dos
     profiles `dev`/`test`. Nos testes, a segurança fica **montada** (não removida) — uma
     `TestSecurityConfig` autentica um ator de teste com acesso total, mantendo os 434 testes verdes; os
     testes de segurança novos sobem o caminho JWT real (401/403).
BR10 ASSUMIDO (ver DL-0082). Papéis base: ROLE_DIRECTOR, ROLE_FINANCE, ROLE_OPERATIONS, ROLE_IT,
     ROLE_POLICY_ADMIN, ROLE_VIEWER. Permissões nomeadas (catálogo fechado) mapeiam as ações sensíveis:
     `policy:directive:write`→DIRECTOR; `policy:rule:write`→DIRECTOR/POLICY_ADMIN;
     `billing:invoice:issue` e `finance:period:close`→FINANCE; `platform:job:trigger` e
     `platform:certificate:write`→IT; `identity:role:read`/`identity:audit:read`→IT/DIRECTOR. Enforcement
     na camada HTTP (Spring Security) + reafirma a checagem de domínio já existente (DL-0038).
BR11 ASSUMIDO (ver DL-0083). A auditoria de acesso **reusa o `system_audit` do Platform** (8j): login →
     `AUTH_LOGIN`, negação → `ACCESS_DENIED` (metadados only, nunca token/segredo/hash). Não há tabela
     `access_audit` nova (Regra Zero); `GET /api/identity/access-audit` é uma leitura focada sobre o seam.
BR12 ASSUMIDO (ver DL-0103). **GRADUAÇÃO Fase 13 — IdP externo vivo.** A autenticação real passa a ser
     delegada a um **IdP OIDC externo (Keycloak)**; o emissor JWT in-house (HS256) e o `POST
     /api/identity/login` são **aposentados**. Em dev/E2E o Keycloak roda em contêiner com um realm
     `acme` importado (papéis base + client SPA público PKCE + usuários seed). **Qual IdP em produção
     segue decisão do dono** (Confiança=Baixa); o contrato OIDC é padrão e a troca é por configuração.
BR13 ASSUMIDO (ver DL-0104). O backend é **Resource Server** validando o JWT do IdP **por JWKS** (RS256,
     rotação de chave automática via `issuer-uri`/`jwk-set-uri`). Os **papéis vêm de
     `realm_access.roles`** e são mapeados às autoridades Spring preservando o prefixo `ROLE_`; o claim
     `scope` é exposto como `SCOPE_*` para refino futuro. O **enforcement continua por papel** (catálogo
     fechado da BR10/DL-0082) — só muda a *fonte* dos papéis. A porta `UserContextProvider` (BR1/BR9)
     **não muda**.
BR14 ASSUMIDO (ver DL-0105). O caminho de **teste/dev-CI** valida o JWT com um **JWKS local (par RSA de
     teste)**, sem IdP na internet: os testes de segurança mintam tokens RS256 determinísticos no formato
     do Keycloak (`realm_access.roles`) e exercitam o decoder JWKS real (401/403); a `TestSecurityConfig`
     (BR9/DL-0081) segue autenticando o ator full-access quando não há header `Authorization`. **Breaking
     change:** `POST /api/identity/login` é removido (login move ao IdP) — destacado no release 0.23.0.
BR15 ASSUMIDO (ver DL-0106). O **frontend** faz login **OIDC Authorization Code + PKCE**
     (`angular-oauth2-oidc`) e **silent-refresh real** (renovação por refresh token antes da expiração),
     graduando a revalidação por `/me` da Fase 10 (DL-0092). O backend continua a única autoridade de
     autorização (BR5); o frontend só espelha papéis para UI/rotas.
BR16 ASSUMIDO (ver DL-0107). O **catálogo papel→permissão permanece local** (`roles`/`role_permissions`)
     como única fonte de verdade do enforcement interno (BR5); o **store local de usuários**
     (`identity_users`/`user_roles`, hash BCrypt) é **aposentado** (migração V31), pois os usuários e suas
     senhas passam a viver no IdP — o ERP deixa de custodiar senha.
BR17 ASSUMIDO (ver ADR-0018 / DL-0110..0114). **RE-GRADUAÇÃO Fase 17 — OIDC self-hosted.** Decisão do
     dono: **remover 100% do Keycloak** e servir OIDC pelo próprio Spring via **Spring Authorization
     Server EMBUTIDO no app** (sem novo processo/Docker). O app passa a ser **IdP e Resource Server**:
     expõe `/.well-known/openid-configuration`, `/oauth2/authorize|token|jwks`, `/userinfo` e um form
     `/login`; assina RS256 com chave RSA local. O **claim de papéis `realm_access.roles` é preservado**
     (via `OAuth2TokenCustomizer`), então o Resource Server (BR13), a porta `UserContextProvider` (BR1),
     os gates por papel (BR2/BR10) e os testes **não mudam**. O client SPA público `acme-erp-web`
     (code+PKCE) é registrado no AS (DL-0111). O **store local de usuários é REINTRODUZIDO** (migração
     **V32**, BCrypt, `UserDetailsService`, seeder dev/E2E) — desfaz o drop da BR16/V31, pois o AS
     autentica localmente; o catálogo papel→permissão permanece intacto. O **frontend** mantém
     `angular-oauth2-oidc` (code+PKCE); só o `issuer` aponta para o próprio app e o silent-refresh passa
     a ser por **iframe** (o SAS não emite refresh token a client público — DL-0113). **DL-0103
     substituída; DL-0104..0107 reapontadas.** Breaking (Keycloak sai) destacado no release `0.28.0`.
BR18 ASSUMIDO (ver DL-0119). **Fase 19a — matriz de autorização default-deny.** O catálogo de ações
     sensíveis (BR10) é estendido a TODA a superfície de escrita: cada `POST/PUT/PATCH/DELETE` sob
     `/api/**` MUST constar da `ApiAuthorizationMatrix` (registro ordenado em `infra.security`) com o
     papel dono do balcão — Finance/Billing/Payout/Admin/liquidação da conciliação/expurgo do cofre →
     `FINANCE`; ciclo comercial (Accounts/Sourcing/Quoting/Booking/AfterSales/Marketing/Portfolio/
     taxa de mercado/política de cancelamento) → `OPERATIONS`; People/Ponto/Assets/Platform →
     `IT`; alavancas do diretor (taxa congelada, diretivas, apagamento LGPD) → `DIRECTOR`; cadastros →
     `POLICY_ADMIN`. **Escrita não mapeada é NEGADA por default** (fallback `denyAll`); a completude é
     um portão de build (`ApiAuthorizationMatrixCompletenessTest`: todo write endpoint casa com uma
     regra e toda regra casa com ≥1 endpoint real). Leituras sensíveis também são gated: dados
     pessoais de People/Ponto → `IT` (LGPD); download de CONTEÚDO do cofre → exclui `VIEWER`;
     superfície Platform → `IT`/`DIRECTOR`. O blanket `permitAll` de `/api/integration/**` é
     **estreitado** aos 2 endpoints M2M com HMAC (quotation-site inbound, webhook de payout) — o
     upload de AFD e o gatilho de crawl (antes alcançáveis SEM credencial) passam a exigir `ROLE_IT`.
     `ROLE_VIEWER` não escreve nada. O `POST /api/commissioning/preview` (cálculo stateless) permanece
     para qualquer autenticado, decisão explícita na matriz.
```

## Graduação — Fase 13 (OIDC externo vivo)

A Fase 13 consolida a fronteira que o 8k havia registrado (DL-0079) e fecha a dívida de silent-refresh
da Fase 10 (DL-0092). O que mudou, em uma frase: **o ERP deixou de ser Resource Server do próprio
emissor HS256 e passou a validar JWTs de um IdP OIDC externo (Keycloak) por JWKS/RS256.**

| Aspecto | 8k (antes) | Fase 13 (agora) |
|---|---|---|
| Emissor do token | in-house (`JwtIssuer`, HS256) | **IdP externo (Keycloak), RS256** |
| Verificação | segredo HS256 compartilhado | **JWKS por `issuer-uri` (rotação de chave)** |
| Login | `POST /api/identity/login` (form) | **OIDC Authorization Code + PKCE no IdP** (endpoint in-house removido) |
| Silent-refresh | revalidação por `GET /me` (DL-0092) | **refresh token real** (`angular-oauth2-oidc`) |
| Papéis | claim `roles` do emissor próprio | **`realm_access.roles` do IdP** → mesmas autoridades `ROLE_*` |
| Usuários/senhas | `identity_users` (BCrypt) local | **no IdP** (store local aposentado, V31) |
| Catálogo papel→permissão | local (`roles`/`role_permissions`) | **local (inalterado — fonte do enforcement, BR5)** |
| Porta `UserContextProvider` | inalterada | **inalterada (seam preservado)** |

Decisões: DL-0103 (Keycloak dev IdP), DL-0104 (Resource Server por JWKS + mapeamento de papéis),
DL-0105 (JWKS local de teste + remoção de `/login`), DL-0106 (frontend OIDC + silent-refresh real),
DL-0107 (catálogo local mantido; store de usuários aposentado). **DL-0079 e DL-0092 ficam RESOLVIDOS.**

## Re-graduação — Fase 17 (OIDC self-hosted; Keycloak removido)

Decisão do dono: **não usar Keycloak**. A Fase 17 remove 100% do Keycloak e serve OIDC pelo próprio
Spring, via **Spring Authorization Server (SAS) embutido no app** (ADR-0018). Em uma frase: **o IdP
externo (Keycloak) virou um IdP self-hosted embutido; o Resource Server, o modelo de papéis e o
frontend OIDC foram preservados — só a *origem* do token mudou (o próprio app).**

| Aspecto | Fase 13 (Keycloak) | Fase 17 (self-hosted) |
|---|---|---|
| Emissor do token | Keycloak (contêiner), RS256 | **Spring Authorization Server EMBUTIDO no app**, RS256 |
| Processo/Docker | 1 contêiner Keycloak (dev + E2E) | **nenhum** (embutido no app) |
| Endpoints OIDC | realm do Keycloak | **`/oauth2/authorize|token|jwks`, `/.well-known/openid-configuration`, `/userinfo`, `/login`** |
| Verificação (RS) | JWKS do realm | **JWKS do próprio app** (`issuer-uri`/`jwk-set-uri` → app) |
| Claim de papéis | `realm_access.roles` (Keycloak) | **`realm_access.roles` (preservado via token customizer)** |
| Client SPA | `acme-erp-web` no realm | **`acme-erp-web` registrado no SAS** (code+PKCE, público) |
| Usuários/senhas | no Keycloak | **store local BCrypt reintroduzido (V32)** + `UserDetailsService` |
| Silent-refresh (front) | refresh token | **iframe silencioso** (SAS não emite refresh token a client público) |
| Catálogo papel→permissão | local (inalterado) | **local (inalterado — BR5)** |
| Porta `UserContextProvider` | inalterada | **inalterada (seam preservado)** |

Decisões: DL-0110 (AS embutido + claim `realm_access.roles`; **substitui DL-0103**), DL-0111 (client SPA
público PKCE), DL-0112 (store local reintroduzido — V32/BCrypt; reaponta DL-0107), DL-0113 (frontend
issuer → app + silent-refresh por iframe; reaponta DL-0106), DL-0114 (Keycloak removido do compose/
infra/env). **DL-0104/0105 reapontadas ao AS self-hosted (mesma mecânica JWKS/RS256, mesmo claim).**

## Input/Output Examples

```txt
Requisição com Bearer token (OIDC) -> UserContextProvider resolve {userId, roles:[ROLE_FINANCE]}
POST /api/billing/invoices/{id}/issue  (usuário sem ROLE_FINANCE)  -> 403 + system_audit(DENY)
POST /api/commercial-policy/directives (usuário com ROLE_DIRECTOR) -> 201 + audit(DIRECTIVE_ISSUED)
```

```http
GET /api/identity/access-audit?actor=ana&from=2026-06-01&to=2026-06-30
200 OK  { "items":[ {"actor":"ana","action":"ISSUE_INVOICE","resource":"nf12...","at":"...","result":"ALLOW"} ] }
```

## API Contracts

ASSUMIDO (ver DL-0104/0105): na **Fase 13** o ERP é Resource Server de um **IdP OIDC externo (Keycloak)**;
o login ocorre no IdP (code+PKCE). O `POST /api/identity/login` in-house do 8k foi **removido**
(breaking — BR14).

- ~~`POST /api/identity/login`~~ → **REMOVIDO na Fase 13** (login no IdP). Breaking change destacado em
  0.23.0 (ADR 0015 §4).
- `GET /api/identity/me` (autenticado) → usuário/roles resolvidos do token do IdP (para o frontend).
- `GET /api/identity/roles` → catálogo de papéis + permissões **local** (autorização:
  `identity:role:read`; papéis `ROLE_DIRECTOR`/`ROLE_IT`).
- `GET /api/identity/access-audit?actor=&type=&from=&to=&page=&size=` → auditoria de acesso, lida do
  `system_audit` (autorização: `identity:audit:read`).
- Endpoints sensíveis exigem o papel (BR10): ex. `POST /api/billing/invoices/{id}/issue`
  exige `ROLE_FINANCE` → 403 + auditoria sem o papel.
- OpenAPI atualizada; esquema de segurança **OIDC/bearer (JWKS do IdP)** documentado.

## Events

- `AccessDenied` — `{userId, action, resource, at}`. Consumidor: segurança/auditoria.
- (Fase 13) O evento `UserAuthenticated` do 8k foi removido junto com o `/login` in-house: como o login
  ocorre no IdP, a auditoria `AUTH_LOGIN` é registrada no **primeiro toque autenticado** (`/me`) via
  `system_audit` (BR3/DL-0083), sem evento de domínio dedicado.

## Persistence Changes

ASSUMIDO (ver DL-0080/0083): a migração é **V29** (não V24 — V24 já está aplicada). A auditoria de acesso
**não** ganha tabela nova: reusa o `system_audit` do Platform (V28).

```txt
V29__create_identity.sql
  roles( name varchar PK, description varchar null )
  role_permissions( role_name varchar not null, permission varchar not null,
                    PRIMARY KEY (role_name, permission) )
  identity_users( id uuid PK, username varchar UNIQUE not null, password_hash varchar not null,
                  display_name varchar, status varchar not null, created_at timestamptz not null,
                  version bigint not null )
  user_roles( user_id uuid not null, role_name varchar not null, PRIMARY KEY (user_id, role_name) )
-- seed: papéis base (ROLE_DIRECTOR, ROLE_FINANCE, ROLE_OPERATIONS, ROLE_IT, ROLE_POLICY_ADMIN,
--       ROLE_VIEWER) + permissões nomeadas (DL-0082) + usuários dev (um por papel) para login real
-- auditoria de acesso: system_audit do Platform (V28), tipos AUTH_LOGIN / ACCESS_DENIED (DL-0083)
```

A configuração de segurança (Spring Security + emissão/verificação JWT in-house, DL-0079) ficou em
`infra.security` (`security.md`). O contexto de segurança alimenta o `UserContextProvider` real
(`JwtUserContextProvider`).

**GRADUAÇÃO Fase 13 (DL-0104/0105/0107):** a verificação passa a ser **por JWKS contra o IdP externo**
(`spring.security.oauth2.resourceserver.jwt.issuer-uri`); o emissor in-house (`JwtIssuer`/HS256) e o
`POST /login` são removidos. Migração **V31__retire_local_user_store.sql** dropa `user_roles` e
`identity_users` (mantém `roles`/`role_permissions` — catálogo do enforcement, BR16). O
`UserContextProvider` (`JwtUserContextProvider`) **não muda** — só a fonte do token.

**RE-GRADUAÇÃO Fase 17 (ADR-0018/DL-0110/0112):** o IdP passa a ser **self-hosted embutido**; o
`issuer-uri`/`jwk-set-uri` apontam para o **próprio app**. Migração **V32__reintroduce_local_user_store.
sql** **recria** `identity_users` + `user_roles` (idempotente, `CREATE TABLE IF NOT EXISTS`; nunca edita
V29/V31), pois o AS autentica localmente (BCrypt); `roles`/`role_permissions` seguem intactos. O store de
usuários, o `UserDetailsService` e o seeder dev/E2E vivem em `infra.security` (concern do IdP/AS). O
`UserContextProvider` **continua inalterado**.

## Validation Rules

- Segurança: verificação de token (assinatura/expiração/audiência); enforcement por papel (BR2).
- Application: auditoria de acesso obrigatória em ações sensíveis (BR3).
- Princípio: módulos consomem o contexto, não reimplementam acesso (BR5).

## Error Behavior

`401 Unauthorized` (token ausente/inválido — mensagem genérica); `403 Forbidden` (papel insuficiente,
auditado). Sem código que vaze existência de usuário (BR4). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar login/negação/ação sensível como evento de **segurança** (ator, ação, recurso, result,
  correlation id), **sem token/segredo**. Métricas: `auth_logins_total`, `access_denied_total{action}`.

## Tests Required

- **Integração (Testcontainers + JWKS local de teste — BR14):** requisição sem papel → 403 + auditoria;
  com papel → permitido; token inválido/assinatura errada → 401 genérico; `UserContextProvider` real
  resolve os papéis de `realm_access.roles`. Os tokens são mintados em RS256 com a chave de teste
  (formato Keycloak) e validados pelo Resource Server por JWKS.
- **Regressão (fronteira de segurança):** uma ação sensível citada em outra spec (ex.: emitir NF) exige
  o papel certo (403 sem o papel, passa com o papel).
- **Arquitetura:** ArchUnit/Modulith verdes após a remoção do emissor in-house; o `UserContextProvider`
  segue sendo o único ponto que toca o `SecurityContextHolder`.
- **E2E (Fase 13):** login real no Keycloak (form do IdP → redirect) leva ao dashboard; sad path
  (credenciais erradas no IdP) não autentica; rota protegida sem sessão → login.

## Acceptance Criteria

- Autenticação real via IdP resolve usuário/roles; ações sensíveis exigem o papel correspondente e ficam auditadas.
- O stub da SPEC-0001 permanece só em dev (perfil), desligado em produção.
- `./mvnw verify` verde.

## Open Questions

> As Open Questions abaixo foram resolvidas em modo autônomo no 8k (ver Business Rules BR7–BR11 e os
> DLs citados). Permanecem listadas para o dono **confirmar/trocar** — são decisões assumidas, não
> definitivas.

- ~~**Comprar/usar IdP** (Keycloak/Entra/Cognito) × IAM próprio~~ → **RE-RESOLVIDO na Fase 17
  (DL-0110/ADR-0018):** decisão do dono = **não usar Keycloak**; OIDC é servido pelo **Spring
  Authorization Server self-hosted embutido no app**. **Qual IdP em produção** (SAS embutido × Entra ×
  Cognito × outro) segue decisão do dono (Confiança=Baixa) — o contrato OIDC é padrão e a troca é por
  configuração (`issuer`), preservando `UserContextProvider` e o modelo de papéis.
- ~~Usuários no IdP × localmente~~ → **RE-RESOLVIDO na Fase 17 (DL-0112):** com o AS self-hosted, os
  usuários voltam a viver **localmente** (store BCrypt reintroduzido, V32) — o ERP volta a custodiar
  hash de senha (trade-off aceito no ADR-0018; mitigado por hash-only + seed forte só em dev/E2E). O
  seam `UserDetailsService` permite plugar um diretório corporativo depois.
- ~~Papéis/permissões finais e mapeamento exato~~ → **ASSUMIDO (DL-0082):** 6 papéis base + catálogo
  fechado de permissões mapeando as ações sensíveis. Na Fase 13 os papéis vêm de `realm_access.roles`
  do IdP (DL-0104); o catálogo papel→permissão permanece local (BR16).
- ~~Usuários no IdP × localmente~~ → **RESOLVIDO na Fase 13 (DL-0107):** usuários migram ao **IdP**; o
  store local (`identity_users`) é aposentado (V31). O ERP deixa de custodiar senha.
- ~~**Silent-refresh real** (Fase 13)~~ → **RESOLVIDO (DL-0106):** OIDC code+PKCE + renovação por refresh
  token no frontend; fecha o stopgap DL-0092.

## Out of Scope

SSO/gestão de usuários completa (IdP), identidade comercial das contas (SPEC-0002), criptografia em
repouso (infra/Platform).
