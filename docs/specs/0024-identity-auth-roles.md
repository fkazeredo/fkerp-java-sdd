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

- Autenticação via IdP (OIDC) — o ERP é **Resource Server**; não há tela de senha própria se o IdP cuidar.
- `GET /api/identity/me` → usuário/roles do token (para o frontend).
- `GET /api/identity/roles` / `POST .../roles` (se papéis forem geridos localmente) — autorização: admin.
- `GET /api/identity/access-audit?actor=&action=&from=&to=&page=&size=` → auditoria de acesso.
- OpenAPI atualizada; esquema de segurança (bearer/OIDC) documentado.

## Events

- `UserAuthenticated` — `{userId, at}` (auditoria). Produtor: `identity`.
- `AccessDenied` — `{userId, action, resource, at}`. Consumidor: segurança/auditoria.

## Persistence Changes

```txt
V24__create_identity.sql
  roles( name varchar PK, description varchar null )
  role_permissions( role_name varchar not null, permission varchar not null,
                    PRIMARY KEY (role_name, permission) )
  -- usuários NÃO ficam aqui se o IdP for a fonte; se geridos localmente, tabela mínima:
  -- users( id uuid PK, external_subject varchar UNIQUE, display_name varchar, status varchar )
  access_audit( id uuid PK, actor varchar null, action varchar not null, resource varchar null,
                result varchar not null, occurred_at timestamptz not null, correlation_id varchar null )
-- seed: papéis base (ROLE_DIRECTOR, ROLE_FINANCE, ROLE_OPERATIONS, ROLE_IT, ROLE_VIEWER) + permissões
```

A configuração de segurança (Spring Security) e a integração OIDC ficam em `infra` (`security.md`). O
contexto de segurança alimenta o `UserContextProvider` real.

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

- **Comprar/usar IdP** (Keycloak/Entra/Cognito) × IAM próprio — **recomendado IdP**; decisão do dono.
- Papéis/permissões finais e seu mapeamento exato para cada ação sensível — consolidar com o dono à
  medida que as fatias expõem ações.
- Usuários geridos no IdP × localmente (define se a tabela `users` existe).

## Out of Scope

SSO/gestão de usuários completa (IdP), identidade comercial das contas (SPEC-0002), criptografia em
repouso (infra/Platform).
