# 0024 - Identity (Autenticação, Papéis e Auditoria de Acesso)

Status: Approved
Related ADRs: 0011, 0012, 0014

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
```

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

ASSUMIDO (ver DL-0079/0083): no 8k o ERP é Resource Server do **seu próprio emissor JWT** (in-house).

- `POST /api/identity/login` (público) → `{username, password}` → `{token, tokenType:"Bearer",
  expiresIn, user:{userId, username, roles}}`. Credencial inválida → **401 genérico** (BR4).
- `GET /api/identity/me` (autenticado) → usuário/roles do token (para o frontend).
- `GET /api/identity/roles` → catálogo de papéis + permissões (autorização: `identity:role:read`).
- `GET /api/identity/access-audit?actor=&action=&from=&to=&page=&size=` → auditoria de acesso, lida do
  `system_audit` (autorização: `identity:audit:read`).
- Endpoints sensíveis passam a exigir o papel (BR10): ex. `POST /api/billing/invoices/{id}/issue`
  exige `ROLE_FINANCE` → 403 + auditoria sem o papel.
- OpenAPI atualizada; esquema de segurança **bearer JWT** documentado.

## Events

- `UserAuthenticated` — `{userId, at}` (auditoria). Produtor: `identity`.
- `AccessDenied` — `{userId, action, resource, at}`. Consumidor: segurança/auditoria.

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

A configuração de segurança (Spring Security + emissão/verificação JWT in-house, DL-0079) fica em
`infra.security` (`security.md`). O contexto de segurança alimenta o `UserContextProvider` real
(`JwtUserContextProvider`). A integração OIDC com IdP externo vivo é a Fase 13.

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

- **Integração (Testcontainers + IdP/JWT fake):** requisição sem papel → 403 + auditoria; com papel →
  permitido; token inválido → 401 genérico; `UserContextProvider` real resolve roles do token.
- **Regressão (fronteira de segurança):** uma ação sensível citada em outra spec (ex.: emitir NF) passa
  a exigir o papel certo (falha antes — stub deixava passar —, passa depois).
- **Arquitetura:** o stub de dev está atrás de profile e desativado em produção.

## Acceptance Criteria

- Autenticação real via IdP resolve usuário/roles; ações sensíveis exigem o papel correspondente e ficam auditadas.
- O stub da SPEC-0001 permanece só em dev (perfil), desligado em produção.
- `./mvnw verify` verde.

## Open Questions

> As Open Questions abaixo foram resolvidas em modo autônomo no 8k (ver Business Rules BR7–BR11 e os
> DLs citados). Permanecem listadas para o dono **confirmar/trocar** — são decisões assumidas, não
> definitivas.

- ~~**Comprar/usar IdP** (Keycloak/Entra/Cognito) × IAM próprio~~ → **ASSUMIDO (DL-0079):** auth real
  in-house (JWT HS256) no 8k; **IdP externo OIDC vivo fica para a Fase 13**. **Decisão do dono: qual
  IdP** (Confiança=Baixa).
- ~~Papéis/permissões finais e mapeamento exato~~ → **ASSUMIDO (DL-0082):** 6 papéis base + catálogo
  fechado de permissões mapeando as ações sensíveis já citadas. Consolidar o resto à medida que novas
  ações surgirem (Confiança=Média).
- ~~Usuários no IdP × localmente~~ → **ASSUMIDO (DL-0080):** localmente no v1 (`identity_users`); migram
  ao IdP na Fase 13.

## Out of Scope

SSO/gestão de usuários completa (IdP), identidade comercial das contas (SPEC-0002), criptografia em
repouso (infra/Platform).
