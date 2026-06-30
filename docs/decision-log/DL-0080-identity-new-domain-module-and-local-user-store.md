# DL-0080 — Identity: novo módulo `domain.identity` (21º) + tabela local mínima de usuários/papéis (V29)

- **Fase:** 8k (Identity)
- **Spec(s):** SPEC-0024 (Persistence Changes V24→aqui **V29**; API Contracts; BR1/BR2/BR5)
- **ADR relacionado:** 0012 (camadas), 0014 (conjunto de módulos); `architecture/modules-and-apis.md`
  (fronteiras; id de outro contexto é valor)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A spec descreve a persistência de Identity (`roles`, `role_permissions`, opcional `users`,
`access_audit`) mas deixa **em aberto** se os **usuários são geridos no IdP × localmente** (define se a
tabela `users` existe) e **onde** o modelo de papéis mora (módulo próprio × infra). Falta decidir a
**fronteira de módulo** e o **modelo de dados** concreto antes de escrever a migração e o domínio.

## Decisão

1. **Novo módulo Modulith `com.fksoft.domain.identity`** (o **21º**), `@ApplicationModule`
   "Identity" com `internal` module-private — espelha o padrão dos demais contextos. A **configuração
   de segurança** (Spring Security, filtro/decoder JWT, `JwtUserContextProvider`) fica em
   `com.fksoft.infra.security` (security.md: a infra de segurança é `infra`), **atrás das portas** do
   módulo. O módulo `identity` é dono do **modelo de papéis/permissões** e do **usuário local**.
2. **Usuários geridos localmente no v1** (a Open Question resolvida): como o 8k usa emissor JWT
   in-house (DL-0079), existe uma tabela **mínima** `identity_users` (id, username único, BCrypt
   `password_hash`, `display_name`, `status`, `version`). Quando a Fase 13 trouxer o IdP externo, a
   fonte do usuário migra para lá; a tabela vira opcional/somente-leitura. Modelar agora é o caminho
   mais defensável e não bloqueia.
3. **Migração `V29__create_identity.sql`** (idempotente, nunca editada depois):
   - `roles(name PK, description)`
   - `role_permissions(role_name, permission, PK(role_name, permission))`
   - `identity_users(id uuid PK, username unique, password_hash, display_name, status, created_at,
     version)`
   - `user_roles(user_id, role_name, PK(user_id, role_name))` — papéis do usuário (M:N; `role_name`
     **por valor**, sem FK cross-contexto para outra base de domínio; FK interna ao próprio módulo é
     permitida pois é a mesma fronteira)
   - **seed** dos papéis base (`ROLE_DIRECTOR`, `ROLE_FINANCE`, `ROLE_OPERATIONS`, `ROLE_IT`,
     `ROLE_POLICY_ADMIN`, `ROLE_VIEWER`) + permissões (DL-0082) + **usuários dev seed** (um por papel,
     senha dev) para os testes/365 e o front exercitarem login real.
4. **Auditoria de acesso NÃO ganha tabela nova** (`access_audit`): reusa o `system_audit` do Platform
   (DL-0083, Regra Zero). A spec previa `access_audit`, mas o seam append-only já existe.

## Justificativa

- **modules-and-apis.md / ADR 0014:** cada contexto do redesenho vira um módulo Modulith com `internal`
  privado; Identity é um contexto genérico do mapa (OVERVIEW linha 136). Pôr o modelo de papéis num
  módulo próprio mantém a fronteira dura (ArchUnit/Modulith) e a autoridade única de autorização (BR5).
- **security.md:** a *configuração* de Spring Security/JWT é `infra`; o *modelo* de papéis/permissões é
  domínio. Separar os dois respeita as camadas (ADR 0012) sem o domínio depender de `infra`.
- **Usuário local mínimo** é o necessário e suficiente para o emissor in-house (DL-0079) e para os
  testes de login real; não é um IAM completo (Out of Scope).
- **Confiança=Alta:** o padrão de "novo módulo + V_n + seed" se repetiu em 8c…8j sem surpresa; o modelo
  de dados é o mínimo da spec.

## Alternativas descartadas

- **Sem módulo próprio (papéis em `infra.security`).** Descartada: misturaria modelo de domínio com
  configuração de transporte/segurança e violaria a separação de camadas; papéis/permissões são
  linguagem de negócio (DIRECTIVE→diretor) e merecem um contexto.
- **Sem tabela de usuários (só IdP).** Descartada no v1: sem IdP vivo (DL-0079) não há de onde resolver
  o login; a tabela mínima é o que torna o login real testável. Vira opcional na Fase 13.
- **Tabela `access_audit` dedicada.** Descartada: duplicaria o `system_audit` append-only do Platform
  (DL-0083) — Regra Zero.

## Impacto

- **Módulos:** +1 Modulith (`identity`), total **21**.
- **Migração:** `V29__create_identity.sql` (tabelas + seed de papéis/permissões/usuários dev).
- **Arquivos:** `domain.identity` (`IdentityService`, `Role`/`Permission`, `internal.IdentityUser`,
  repositórios, views, exceções, eventos `UserAuthenticated`/`AccessDenied`); `infra.security`
  (config/JWT). `package-info.java` com `@ApplicationModule`.
- **Testes:** sobe o número de módulos Modulith para 21 (gate atualiza naturalmente — não é afrouxar).

## Como reverter

Reversão **moderada**: remover os usuários locais e apontar a resolução de papéis para o IdP (Fase 13)
mantém `roles`/`role_permissions` e o módulo `identity`. Remover o módulo inteiro só faria sentido se a
autorização saísse do backend — o que contraria security.md/BR5; não é um caminho previsto.
