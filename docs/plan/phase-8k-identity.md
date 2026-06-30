# Plano — Fase 8k (Identity: auth real + papéis/permissões + auditoria de acesso, graduando o stub)

> Spec: **SPEC-0024** (Related ADRs 0011, 0012, 0014; herda SPEC-0001). Base: `origin/develop`
> @645beea (release 0.18.0; fases 0–8j; 20 módulos Modulith; 434 testes). Release-alvo: **0.19.0**.
> Migração: **V29**.

## Objetivo da fase

**Graduar o `DevStubUserContextProvider`** em **autenticação real** com o **backend como única
autoridade** (security.md, SPEC-0024):

1. **Autenticação real (Spring Security + JWT in-house, DL-0079):** `POST /api/identity/login` autentica
   contra usuários locais (BCrypt) e emite um **JWT (HS256)**; um filtro verifica o Bearer e popula o
   `SecurityContext`. O ERP é Resource Server do seu próprio emissor. IdP externo vivo = **Fase 13**.
2. **Modelo de papéis/permissões (DL-0080/0082):** módulo `domain.identity` (21º) dono de
   `Role`/`Permission`/`IdentityUser`; seed de 6 papéis + permissões nomeadas; ações sensíveis exigem o
   papel (DIRECTIVE→DIRECTOR, NF→FINANCE, job→IT) via Spring Security + a checagem de domínio existente.
3. **Auditoria de acesso (DL-0083):** login e negação gravados no `system_audit` do Platform
   (`AUTH_LOGIN`/`ACCESS_DENIED`), sem token/segredo; `GET /api/identity/access-audit` lê o seam.
4. **Graduação sem quebrar o build (DL-0081):** porta `UserContextProvider` intacta; `JwtUserContextProvider`
   em prod/default, stub atrás de `dev`/`test`; `TestSecurityConfig` mantém os 434 testes verdes com a
   segurança **montada** (não removida).

## Decisões (decision-log) tomadas antes do código

| DL | Tema | Confiança | Reversib. |
|---|---|---|---|
| DL-0079 | **Auth real in-house (Spring Security + JWT HS256) no 8k; OIDC externo vivo na Fase 13; porta `UserContextProvider` é o seam** | **Baixa** | **Cara** |
| DL-0080 | Novo módulo `domain.identity` (21º) + usuário local mínimo (V29); auditoria reusa `system_audit` | Alta | Moderada |
| DL-0081 | Gradua o stub: porta intacta; JWT em prod; stub atrás de profile `dev`/`test`; `TestSecurityConfig` mantém 434 testes verdes | Alta | Barata |
| DL-0082 | Modelo papel→permissão + mapa das ações sensíveis; enforcement HTTP + reafirma a checagem de domínio (DL-0038) | Média | Moderada |
| DL-0083 | Auditoria de acesso reusa o `system_audit` (Platform/8j); sem tabela nova (Regra Zero) | Alta | Moderada |

> **Destaque (Baixa/Cara):** DL-0079 — **comprar/qual IdP é Open Question do dono**. 8k entrega o modelo
> real + papéis + auditoria com emissor JWT in-house; a Fase 13 consolida o OIDC externo vivo. A porta
> `UserContextProvider` e o modelo de papéis sobrevivem; o emissor/verificador/login não → reversão Cara.

## Fatias (ordem de dependência)

### 8k-1 — Módulo `identity` + login JWT + `JwtUserContextProvider` (gradua o stub)
- **Entrega:** módulo `domain.identity` (`Role`, `Permission`, `IdentityUser`, `IdentityService`,
  views, exceções, eventos `UserAuthenticated`); V29 (tabelas + seed papéis/permissões/usuários dev);
  `infra.security`: `SecurityConfig` (cadeia real), `JwtIssuer`/`JwtDecoder` (Nimbus), filtro Bearer,
  `JwtUserContextProvider` (prod/default), encoder BCrypt; `POST /api/identity/login`,
  `GET /api/identity/me`; profiles: stub `dev`/`test`, real default; `TestSecurityConfig` em `src/test`
  para manter os 434 testes verdes.
- **RED:** teste de integração: login com credencial válida → 200 + JWT + roles; inválida → 401
  genérico; `GET /me` sem token → 401; `GET /me` com token → user/roles do token; o `verify` inteiro
  (434 testes) segue verde com a `TestSecurityConfig`.
- **Aceite:** `UserContextProvider` real resolve roles do token; stub só em dev/test; 434 testes verdes.

### 8k-2 — Papéis/permissões: ações sensíveis exigem o papel + auditoria de acesso
- **Entrega:** `SecurityConfig` mapeia endpoints sensíveis a `hasRole(...)`
  (NF→FINANCE, job/cert→IT, diretiva/regra→DIRECTOR/POLICY_ADMIN); `AccessDeniedHandler`/
  `AuthenticationEntryPoint` retornam **403/401** no contrato `ApiErrorResponse` (`access.denied`/
  `auth.unauthenticated`, i18n pt-BR+fallback) e **auditam** no `system_audit` (`ACCESS_DENIED`);
  login audita `AUTH_LOGIN`; enum `AuditType` ganha `AUTH_LOGIN`/`ACCESS_DENIED`; `GET /api/identity/roles`
  e `GET /api/identity/access-audit`.
- **RED (regressão de fronteira, Tests Required):** emitir NF (`POST /api/billing/invoices/{id}/issue`)
  **sem** `ROLE_FINANCE` → 403 + auditoria (falha antes — stub deixava passar —, passa depois);
  **com** o papel → permitido; `GET /access-audit` mostra a negação; token inválido → 401 genérico.
- **Aceite:** ações sensíveis exigem o papel correspondente e ficam auditadas.

### 8k-3 — Frontend (login + me) + manual bilíngue + release 0.19.0
- **Entrega:** tela Angular de **login** (token guardado, `Authorization` no interceptor, `/me` no
  topo, logout) + guard de rota; estados loading/erro; nav atualizada. Manual pt-BR + en-US (login,
  papéis, permissões), README se necessário, release note 0.19.0 + CHANGELOG.en-US, OpenAPI.
- **Aceite:** `ng lint`/`test`/`build` verdes; `./mvnw verify` verde; docs bilíngues em sincronia.

> Se o esforço de tela exceder o necessário para a fronteira de segurança, a 8k-3 entrega o **login
> mínimo** (interceptor + guard + /me) — o valor de segurança é backend-first (as fases 2–8j também
> entregaram telas como follow-up). O backend (8k-1/8k-2) é o coração da fase.

## Portões e Definition of Done (cada fatia)
- `./mvnw verify` verde (ArchUnit + 21 Modulith + Spotless/Checkstyle) antes de cada merge `--no-ff`.
- V29 idempotente; nunca editar migração aplicada. DomainException code == chave i18n (pt-BR+fallback).
- Sem token/segredo/hash em log/erro/audit/DTO (BR4). Sem FK cross-contexto. Constructor injection.
- Caderno de testes por fatia em `docs/test-report/` + INDEX. Conventional Commits, commits pequenos.

## Riscos
- **Quebrar os 434 testes** ao ligar o Spring Security — mitigado pela `TestSecurityConfig` (ator de
  teste com acesso total, segurança montada) e por subir o caminho real só nos testes de segurança.
- **Modulith acíclico:** `identity` chama a fachada pública `SystemAuditService` (comando) do
  `platform`; `platform` não importa `identity` → sem ciclo. Verificar no gate.
- **Confiança=Baixa do IdP (DL-0079):** confirmar com o dono na Fase 13; o seam protege a troca.
