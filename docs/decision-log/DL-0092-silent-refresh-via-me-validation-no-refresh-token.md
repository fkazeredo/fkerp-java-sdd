# DL-0092 — Silent refresh = revalidação do token via `GET /api/identity/me` (sem refresh token)

- **Fase:** 10 (UX & Frontend profissional)
- **Spec(s):** SPEC-0026; SPEC-0024 (auth real, 8k)
- **ADR relacionado:** 0005 (JWT)
- **Data:** 2026-06-30
- **Status:** RESOLVIDO na Fase 13 (ver "Atualização de status" abaixo)
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Atualização de status (Fase 13 — 2026-06-30)

**RESOLVIDO.** O stopgap desta DL ("silent refresh = revalidação por `GET /me`, sem refresh token")
foi **graduado** na Fase 13 para **silent-refresh real**: com o IdP externo (Keycloak) vivo, o frontend
passou a usar **Authorization Code + PKCE** (`angular-oauth2-oidc`) e **renova o access token por
refresh token** antes da expiração (silent-refresh de verdade), conforme previsto no item 3 desta DL e
em "Confiança Média". A revalidação por `/me` deixou de ser o mecanismo de sessão. Decisão da
graduação: **DL-0106** (frontend OIDC + silent-refresh real), apoiada por **DL-0103/DL-0104** (IdP +
Resource Server por JWKS). Esta DL deixa de ser uma dívida diferida.

## Lacuna

O ROADMAP pede "login com silent refresh". O backend de identidade entregue no 8k (SPEC-0024,
DL-0079) emite um **JWT HS256 de vida curta** e expõe `POST /login`, `GET /me` — **mas não há
endpoint de refresh token** (OIDC externo vivo é Fase 13). Falta decidir o que "silent refresh"
significa nesta fase **sem inventar** um fluxo de refresh que o backend não suporta.

## Decisão

"Silent refresh" nesta fase = **revalidação silenciosa da sessão**, não emissão de novo token:

1. **No boot do app** (`APP_INITIALIZER`/efeito): se há token salvo, chama `GET /api/identity/me`
   silenciosamente. 200 → sessão válida, atualiza o usuário no signal a partir da resposta
   verificada (a fonte é o backend, não o `localStorage`). 401 → limpa a sessão (logout silencioso).
2. **Antes da expiração:** o `AuthService` agenda, a partir de `expiresIn`, uma revalidação via
   `/me` perto do fim da validade; se ainda 200, mantém; se 401, faz logout e manda ao `/login`
   preservando a rota pretendida (`returnUrl`).
3. **Sem refresh token / sem renovação de JWT** nesta fase: quando o token expira de fato, o usuário
   reautentica. O seam para um refresh real fica documentado para a Fase 13 (OIDC), sem dívida falsa.

## Justificativa

- **Não inventar contrato (CLAUDE.md Regra 3 + simulation-and-mocking):** criar um `/refresh` no
  frontend que o backend não tem seria lógica falsa. `/me` já existe e é a verificação canônica do
  token contra o backend (a única autoridade — security.md).
- **Valor real:** cobre o caso de uso prático (token ainda válido após F5; sessão derrubada quando
  o backend rejeita) sem antecipar a Fase 13.
- **Confiança Média:** "silent refresh" idealmente renova o token; aqui só revalida. Como o backend
  não oferece renovação, é a interpretação mais defensável; a Fase 13 (OIDC) graduará para refresh
  real quando o emissor externo existir.

## Alternativas descartadas

- **Implementar refresh token agora:** exigiria backend novo (endpoint + rotação + revogação) —
  fora do escopo da Fase 10 (frontend) e antecipa a Fase 13.
- **Não validar no boot (confiar no `localStorage`):** deixaria o usuário "logado" com um token já
  inválido até a primeira chamada falhar; pior UX e fere "backend é a autoridade".
- **Renovar JWT chamando `/login` de novo:** não há credencial guardada (e não deve haver).

## Impacto

- **Arquivos:** `core/auth/auth.service.ts` (método `verifySession()` via `/me`, agendamento),
  `app.config.ts` (provider de inicialização), guard com `returnUrl`. Sem backend.
- **Migração/Contrato:** nenhum (usa `/me` existente).

## Como reverter

Remover a revalidação no boot e o agendamento; o login simples do 8k volta a valer. Moderada
(toca o serviço de auth e a inicialização), sem dado/contrato a migrar.
