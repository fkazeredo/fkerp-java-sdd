# DL-0107 — Identity: catálogo papel→permissão **permanece local** (fonte de verdade do enforcement); store local de **usuários** é aposentado (usuários vivem no IdP)

- **Fase:** 13 (Identity/AuthZ profissional — gradua SPEC-0024)
- **Spec(s):** SPEC-0024 (BR5, BR8; `GET /api/identity/roles`; Persistence); DL-0080 (store local do 8k);
  DL-0082 (catálogo papel→permissão)
- **ADR relacionado:** `architecture/security.md`; DL-0104/DL-0105
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O 8k (DL-0080) criou um **store local** com duas responsabilidades: (a) o **catálogo papel→permissão**
(`roles`/`role_permissions`) e (b) os **usuários locais** (`identity_users`/`user_roles`, hash BCrypt)
para o `/login` próprio. Com o login no IdP (DL-0105), os **usuários** migram para o Keycloak. Falta
decidir o que fazer com cada tabela: manter, migrar ou descartar — sem quebrar `GET /identity/roles`
nem o enforcement.

## Decisão

1. **Catálogo papel→permissão PERMANECE local** (`roles`, `role_permissions`). É a **única fonte de
   verdade do enforcement interno** (SPEC-0024 BR5): o mapa "ação sensível → papel/permissão" (DL-0082)
   é regra de negócio do ERP, **não** do IdP. `GET /api/identity/roles` segue lendo essas tabelas. O
   IdP só diz *quais papéis o usuário tem*; o ERP diz *o que cada papel pode fazer*.
2. **Store local de USUÁRIOS é aposentado.** `identity_users`/`user_roles` e o `DevUserSeeder`/
   `PasswordHasher`/`BCryptPasswordHasher` são **removidos** — os usuários (e suas senhas) vivem no
   Keycloak (dev/E2E via realm seed; produção no IdP do dono). A migração **V31** dropa essas duas
   tabelas (idempotente, `DROP TABLE IF EXISTS`), mantendo `roles`/`role_permissions`.
3. **Auditoria de acesso inalterada** (DL-0083): `system_audit` (`AUTH_LOGIN`/`ACCESS_DENIED`) segue
   sendo a trilha; `GET /api/identity/access-audit` não muda.

## Justificativa

- **Separação correta de responsabilidades:** autenticação e gestão de usuários = IdP (subdomínio
  genérico, "comprar" — redesenho/Goal da SPEC-0024); **autorização de negócio** (o que cada papel
  faz) = ERP. Manter o catálogo local honra BR5 sem reinventar um IAM.
- **Regra Zero:** não duplicar o catálogo no IdP (manutenção dupla) nem manter usuários locais órfãos
  (o ERP deixa de custodiar senha — melhora a postura de segurança e fecha a superfície do `/login`).
- **`GET /roles` continua válido e útil** ao frontend/operador (mostra o mapa de permissões) sem
  depender da Admin API do Keycloak.
- **Confiança=Alta:** decisão estrutural clara; **Reversibilidade=Moderada:** recriar o store de
  usuários exigiria nova migração e re-seed, mas o catálogo (o que importa para enforcement) fica.

## Alternativas descartadas

- **Manter `identity_users` como projeção do IdP.** Descartada no v1 (Regra Zero): nada no ERP precisa
  da lista local de usuários hoje; sincronizar projeção é custo sem demanda. Entra se um relatório de
  usuários surgir (seam: consumir a Admin API/eventos do IdP).
- **Mover o catálogo papel→permissão para o Keycloak (client scopes).** Descartada: o mapa de ações
  sensíveis é regra do ERP (BR5); espalhá-lo no IdP acopla autorização de negócio ao provedor e
  dificulta troca de IdP.
- **Deixar as tabelas de usuário órfãs (sem dropar).** Descartada: schema morto é dívida; `ddl-auto:
  validate` + Modulith preferem o schema refletir o domínio vivo.

## Impacto

- **Arquivos removidos:** `domain/identity` `IdentityUser`/`IdentityUserRepository`/`PasswordHasher`/
  `AuthenticatedUser`/`InvalidCredentialsException`/`UserAuthenticated`; `infra/security`
  `JwtIssuer`/`DevStubUserContextProvider`?(ver nota)/`DevUserSeeder`/`BCryptPasswordHasher`/
  `SecurityProperties`(HS256). `IdentityService.login(...)` removido; `listRoles()`/`recordAccessDenied`
  permanecem.
- **Migração:** **V31__retire_local_user_store.sql** — `DROP TABLE IF EXISTS user_roles, identity_users`
  (mantém `roles`/`role_permissions`). Idempotente; nunca edita V29.
- **Nota DevStub:** o `DevStubUserContextProvider` (profile `dev`, login-less) **pode** permanecer para
  conveniência local sem subir o Keycloak — mas como o frontend dev agora usa OIDC real, ele é
  removido para evitar dois caminhos; o dev sobe o Keycloak do compose. (Decisão: remover.)

## Como reverter

Recriar as tabelas de usuário (nova migração) + `DevUserSeeder` + `IdentityService.login` (reintroduz
DL-0079). O catálogo papel→permissão não precisa voltar (nunca saiu). Moderada.
