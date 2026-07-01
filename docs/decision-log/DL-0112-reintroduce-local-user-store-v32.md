# DL-0112 — Identity (Fase 17): store local de usuários **reintroduzido** (V32, BCrypt, UserDetailsService, seeder dev/E2E) — reaponta a DL-0107

- **Fase:** 17 (remover Keycloak → AS self-hosted)
- **Spec(s):** SPEC-0024 (BR8; Persistence Changes; Tests Required); ADR-0018
- **ADR relacionado:** ADR-0018; **reaponta a DL-0107** (que aposentou o store) e a DL-0105
  (usuários seed); `architecture/security.md` (BR4 — só hash, nunca senha/token)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A DL-0107/V31 dropou `identity_users`/`user_roles` porque os usuários passaram a viver no Keycloak.
Sem Keycloak, o AS self-hosted precisa de **onde autenticar** o usuário no `/login`. É preciso
reintroduzir um store local de usuários (schema + entidade + `UserDetailsService` + seed) sem
quebrar o catálogo papel→permissão (que a V31 manteve).

## Decisão

1. **Migração `V32__reintroduce_local_user_store.sql`** (idempotente, `CREATE TABLE IF NOT EXISTS`):
   recria `identity_users(id uuid PK, username unique, password_hash, display_name, status,
   created_at, version)` e `user_roles(user_id, role_name)`. **Mantém** `roles`/`role_permissions`
   (catálogo do enforcement — BR16/DL-0107 intactos). Nunca edita V29/V31 já aplicadas.
2. **Só o hash BCrypt** é persistido (BR4) — nunca plaintext/token. `PasswordEncoder` = `BCryptPasswordEncoder`.
3. **`UserDetailsService` local** (`infra/security`) lê `identity_users` + `user_roles` e devolve as
   authorities `ROLE_*` — é o que o AS usa no form-login e o que alimenta o token customizer (DL-0110).
4. **Seeder dev/E2E** (`@Profile({"dev","e2e"})`): popula `dev` (todos os papéis) + um usuário por papel
   (`director/finance/ops/it/policy/viewer`), senha fraca conhecida `dev12345` (**apenas** dev/E2E),
   via o `PasswordEncoder` — nunca hash hardcoded. Espelha o realm seed do Keycloak (mesmos usuários).
5. **Localização:** o store de usuários vive em `infra.security` (concern do IdP/AS), separado do
   `domain.identity`, que segue dono **apenas** do catálogo papel→permissão (autorização de negócio).

## Justificativa

- **Consequência direta de remover o IdP externo:** sem Keycloak, o AS precisa de um user store para
  autenticar. É o caminho mínimo (Regra Zero) — reusa `roles`/`role_permissions` que nunca saíram.
- **Separação correta:** autenticação/usuários = concern do AS (`infra.security`); autorização de
  negócio (o que cada papel faz) = `domain.identity`. Isso mantém ArchUnit/Modulith verdes (o AS é
  infra) e não repõe lógica de negócio em `infra`.
- **BR4/`security.md`:** só hash BCrypt; senha fraca só em dev/E2E (nunca produção). O ERP volta a
  custodiar hash — trade-off aceito no ADR-0018 (decisão do dono).
- **Confiança=Alta:** modelo de user store BCrypt + `UserDetailsService` é padrão Spring Security.
  **Reversibilidade=Moderada:** dropar o store de novo (se um diretório externo entrar) é uma migração.

## Alternativas descartadas

- **`InMemoryUserDetailsManager` (sem tabela).** Descartada: perderia os usuários seed reprodutíveis por
  profile e a paridade com o schema anterior; um store persistido é mais honesto e o schema já existia.
- **Colocar o user store em `domain.identity`.** Descartada: autenticação não é regra de negócio do ERP;
  misturar user store com o catálogo acoplaria o IdP ao domínio.
- **Reaproveitar `identity_users` sem migração (V31 nunca aplicada?).** Descartada: V31 está aplicada
  em ambientes existentes; a recriação idempotente V32 é a forma correta (nunca editar V31).

## Impacto

- **Migração:** `V32__reintroduce_local_user_store.sql`.
- **Arquivos:** `infra/security/AppUser.java` (entidade), `AppUserRepository`, `AppUserDetailsService`,
  `DevUserSeeder` (`@Profile dev,e2e`), `PasswordEncoder` bean. `domain.identity` inalterado.
- **Config:** profile `e2e` reconhecido (além de `dev`) para o seed no stack E2E.

## Como reverter

Nova migração dropando `identity_users`/`user_roles` + remover `UserDetailsService`/seeder (se a auth
voltar a um IdP externo/diretório). O catálogo papel→permissão não é afetado. Moderada.
