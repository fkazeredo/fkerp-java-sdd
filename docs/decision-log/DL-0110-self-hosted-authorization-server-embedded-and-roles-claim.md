# DL-0110 — Identity (Fase 17): Spring Authorization Server **embutido** no app; access token com claim `realm_access.roles` (SUBSTITUI a DL-0103)

- **Fase:** 17 (remover Keycloak → AS self-hosted; re-gradua a SPEC-0024)
- **Spec(s):** SPEC-0024 (Goal; Scope; BR1/BR2/BR5; API Contracts — esquema de segurança); ADR-0018
- **ADR relacionado:** ADR-0018 (self-hosted AS); `architecture/security.md`; **SUBSTITUI a DL-0103**
  (Keycloak dev IdP) e reaponta a DL-0104 (Resource Server por JWKS/mapeamento de papéis)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

O dono não quer Keycloak (ROADMAP Fase 17). É preciso servir OIDC **pelo próprio Spring**, decidir
**onde** roda o Authorization Server (processo separado × embutido) e **como** o access token carrega os
papéis para o Resource Server, sem reescrever o mapeamento de papéis nem quebrar os 444 testes.

## Decisão

1. **Adotar o Spring Authorization Server (SAS)** via `spring-boot-starter-oauth2-authorization-server`
   (Boot 4.0.7 → SAS 7.0.6, gerido pelo BOM; no Spring Security 7 o SAS faz parte do Security).
2. **Rodar EMBUTIDO no app** (recomendação explícita do ROADMAP — "sem novo processo/Docker"), com
   três `SecurityFilterChain`s em `infra.security`: `@Order(1)` a cadeia do AS (endpoints
   `/oauth2/authorize|token|jwks`, `/.well-known/openid-configuration`, `/userinfo`, redireciona a
   `/login`), `@Order(2)` a cadeia Resource Server `/api/**` (a `SecurityConfig.configure(...)`
   inalterada), `@Order(3)` a cadeia de form-login (`/login`).
3. **Claim de papéis `realm_access.roles` (forma do Keycloak preservada).** Um
   `OAuth2TokenCustomizer<JwtEncodingContext>` injeta no **access token** `realm_access.roles`
   (papéis `ROLE_*` do usuário), `preferred_username` e `scope`. Assim o `JwtAuthenticationConverter`
   do `SecurityConfig`, o `TestJwtTokens` e os testes de segurança **não mudam** — só a *origem* do
   token muda (o próprio app). Assinatura **RS256** com par RSA local (JWKS servido pelo AS).

## Justificativa

- **Autoridade = pedido do dono** (remover Keycloak) e **recomendação do ROADMAP** (embutir).
- **`security.md`:** "Spring Security é o padrão; não reinvente auth" — o SAS é o caminho Spring nativo
  para ser IdP OIDC. Embutir evita um processo/imagem a mais (Regra Zero).
- **Preservar `realm_access.roles`** é o que torna a troca **mínima**: nenhum teste de segurança, nem o
  conversor de papéis do Resource Server, precisa mudar. Só a config de `issuer` muda.
- **Confiança=Média:** o formato do claim e a ordenação das cadeias são decisões de config isoladas em
  `infra.security`. **Reversibilidade=Moderada:** trocar por outro IdP volta a ser mudar `issuer-uri`.

## Alternativas descartadas

- **Manter Keycloak** — contra o pedido do dono.
- **AS em processo/módulo separado** — o ROADMAP recomenda embutir; separar sobe um processo à toa.
- **Novo claim `roles` (em vez de `realm_access.roles`)** — obrigaria a mudar o conversor de papéis e
  os testes; preservar a forma do Keycloak é mudança menor (Regra Zero).

## Impacto

- **Arquivos:** `pom.xml` (+ starter AS); `infra/security/AuthorizationServerConfig.java` (novo:
  cadeias, JWKSource RSA, token customizer, AuthorizationServerSettings); `SecurityConfig` ganha
  `@Order(2)` na cadeia de produção; `application.yml` (issuer-uri/jwk-set-uri → próprio app).
- **Contratos:** esquema de segurança OpenAPI → OIDC self-hosted (mesmo app).
- **Migração:** nenhuma nesta DL (store local em DL-0112).

## Como reverter

Remover o starter/`AuthorizationServerConfig` e reapontar `issuer-uri` para um IdP externo (reintroduz
a DL-0103). O conversor de papéis e a porta `UserContextProvider` não mudam. Moderada.
