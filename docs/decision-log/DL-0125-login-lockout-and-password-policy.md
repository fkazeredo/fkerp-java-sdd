# DL-0125 — Login: lockout de força-bruta (DB) + política mínima de senha

- **Fase:** 19c
- **Spec(s):** SPEC-0024 (BR4 — erro genérico; brute-force)
- **ADR relacionado:** ADR-0018 (AS self-hosted)
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O form login do Spring Authorization Server embutido (DL-0110) não tinha **proteção de
força-bruta** nem **política de senha** — um atacante podia tentar senhas ilimitadamente.

## Decisão

1. **Lockout DB-backed** (`login_attempts`, V38): após `security.login.max-attempts` (default 5)
   falhas consecutivas, o usuário é bloqueado por `security.login.lock-seconds` (default 900);
   sucesso zera o contador. O bloqueio é aplicado **dentro do `AppUserDetailsService`** (a conta é
   apresentada como `accountLocked` → `LockedException` antes de checar a senha); os handlers de
   sucesso/falha da form-login chain atualizam o contador. Erro genérico (BR4 — não revela se o
   usuário existe/está bloqueado). Sem ShedLock/infra nova (contador simples no Postgres).
2. **Política mínima de senha** (`PasswordPolicy`, pura): comprimento ≥ 8 e não-caractere-único,
   aplicada no seeder (e no futuro fluxo de gestão de usuários). Força real = BCrypt + lockout, não
   complexidade teatral.

## Justificativa

- Brute-force é o risco óbvio de um form login com user store local; o lockout por contador é o
  mecanismo mínimo eficaz sem infra nova.
- O lockout no `UserDetailsService` reusa o caminho padrão do Spring Security (`accountLocked`).

## Alternativas descartadas

- **Rate-limit por IP (bucket):** complementar, mas o lockout por conta cobre o alvo principal
  (adivinhar a senha de um usuário); seam para IP fica aberto.
- **Política de senha rica (símbolos/maiúsculas):** teatro de complexidade; BCrypt+lockout supera
  regras ornamentais (NIST 800-63B desencoraja complexidade forçada).

## Impacto

- **Arquivos:** `LoginAttempt`/`LoginAttemptRepository`/`LoginAttemptService`, `PasswordPolicy`;
  `AppUserDetailsService` (accountLocked), `DevUserSeeder` (valida a senha), `AuthorizationServerConfig`
  (handlers de sucesso/falha). Migração **V38**. Testes: `LoginAttemptServiceIntegrationTest`,
  `PasswordPolicyTest`.
- **Config:** `security.login.max-attempts`/`lock-seconds`.

## Como reverter

Moderada: remover os handlers + o `accountLocked` (o lockout deixa de valer) e a V38 fica órfã
(aditiva). A política de senha é uma função pura removível.
