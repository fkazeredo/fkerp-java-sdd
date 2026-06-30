# DL-0082 — Identity: modelo papel→permissão e o mapeamento das ações sensíveis já citadas nas specs

- **Fase:** 8k (Identity)
- **Spec(s):** SPEC-0024 (BR2 — autorização por papel/permissão; ações sensíveis exigem o papel;
  Input/Output Examples; Open Question "papéis/permissões finais")
- **ADR relacionado:** `architecture/security.md` (autorização de negócio; backend é a autoridade);
  DL-0038 (autorização da diretiva já existente em CommercialPolicy)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0024 deixa **em aberto** o **conjunto final de papéis/permissões e o mapeamento exato** para
cada ação sensível ("consolidar com o dono à medida que as fatias expõem ações"). Para entregar
enforcement real é preciso fixar, no v1, **quais papéis existem**, **quais permissões cada um tem** e
**quais endpoints sensíveis** passam a exigir papel.

## Decisão

1. **Papéis base** (seed V29) — os já citados pelas specs/ROADMAP, com `ROLE_POLICY_ADMIN` herdado da
   DL-0038: `ROLE_DIRECTOR`, `ROLE_FINANCE`, `ROLE_OPERATIONS`, `ROLE_IT`, `ROLE_POLICY_ADMIN`,
   `ROLE_VIEWER`.
2. **Permissões nomeadas** (catálogo fechado, `role_permissions`) — uma permissão por **capacidade
   sensível**, agrupada por papel:
   - `policy:directive:write` → `ROLE_DIRECTOR`
   - `policy:rule:write` → `ROLE_DIRECTOR`, `ROLE_POLICY_ADMIN`
   - `billing:invoice:issue` → `ROLE_FINANCE`
   - `finance:period:close` → `ROLE_FINANCE`
   - `platform:job:trigger` → `ROLE_IT`
   - `platform:certificate:write` → `ROLE_IT`
   - `identity:role:read` / `identity:audit:read` → `ROLE_IT`, `ROLE_DIRECTOR`
   - leitura geral de negócio → todos os papéis autenticados (inclusive `ROLE_VIEWER`).
3. **Enforcement em duas camadas, sem reimplementar regra (BR5):**
   - **HTTP (Spring Security):** ações sensíveis exigem o papel via `@PreAuthorize("hasRole('…')")` /
     regras na `SecurityFilterChain`. Papel insuficiente → **403** com `ApiErrorResponse`
     (`access.denied`), **auditado** (DL-0083). Token ausente/inválido → **401** genérico
     (`auth.unauthenticated`, BR4).
   - **Domínio (reafirma a autoridade final):** a checagem que **já existe** em
     `CommercialPolicyService` (DIRECTIVE→`ROLE_DIRECTOR`, DL-0038) **permanece** — agora os papéis
     vêm do token real, não do stub. Isso prova a regressão da spec ("emitir NF / diretiva passa a
     exigir o papel certo: falhava antes — stub deixava passar —, passa depois") **sem** duplicar a
     regra em cada módulo: os módulos **consomem** o contexto; o mapa papel→ação mora aqui + na config
     de segurança.
4. **Ação sensível escolhida para a regressão de fronteira (Tests Required):** **emitir NF**
   (`POST /api/billing/invoices/{id}/issue`) passa a exigir `ROLE_FINANCE` na cadeia HTTP — falha
   (403) sem o papel, passa com o papel. Espelha o exemplo da spec.

## Justificativa

- **BR2/security.md:** autorização por papel, backend como autoridade; o frontend nunca decide. As
  ações sensíveis citadas (DIRECTIVE→diretor, NF→financeiro, crawler/job→TI) são as do próprio texto
  da spec e do ROADMAP — não são inventadas.
- **Reaproveita a DL-0038:** a diretiva já tinha autorização por papel; o 8k apenas troca a **fonte**
  dos papéis (stub→token) e padroniza o enforcement HTTP, sem regredir o que existe.
- **Confiança=Média:** o **conjunto exato** de permissões e o mapa para **todas** as ações é algo que a
  spec diz para "consolidar com o dono à medida que as fatias expõem ações". Fixamos o conjunto mínimo
  defensável (as ações já marcadas) e o resto fica governável (DL-0080: `role_permissions` é dado).

## Alternativas descartadas

- **Só papéis, sem permissões nomeadas.** Descartada: a spec cita `Permission` e o modelo
  `role_permissions`; permissões nomeadas deixam o mapa auditável e evoluível por dado, não por deploy.
- **Enforcement só no domínio (sem camada HTTP).** Descartada: deixaria endpoints sensíveis abertos por
  esquecimento; a camada HTTP é a malha de segurança padrão (security.md) e o 403 auditável vem dela.
- **Enforcement só no HTTP (remover a checagem de domínio da DL-0038).** Descartada: removeria uma
  regra de negócio já testada; defesa em profundidade é barata e a checagem de domínio é a autoridade
  final quando a chamada não passa pelo controller (ex.: evento/job interno).

## Impacto

- **Migração V29:** seed de `roles` + `role_permissions` (catálogo acima).
- **Config:** `SecurityConfig` mapeia os endpoints sensíveis a `hasRole(...)`; `HttpErrorMapping`/i18n
  ganham `access.denied` (403) e `auth.unauthenticated` (401).
- **Sem mudança** nos módulos de negócio além do enforcement HTTP declarativo; a checagem de domínio
  existente é preservada.

## Como reverter

Reversão **moderada**: o mapa papel→permissão é **dado** (seed) + regras declarativas na config; ajustar
papéis exige editar o seed e as anotações/`SecurityFilterChain` (ponto único). Não há schema a
desfazer além do seed.
