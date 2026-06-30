# DL-0104 — Identity: backend é Resource Server validando JWT do IdP externo por JWKS (RS256/rotação); `realm_access.roles` + scopes → autoridades

- **Fase:** 13 (Identity/AuthZ profissional — gradua SPEC-0024)
- **Spec(s):** SPEC-0024 (BR1, BR2, BR5; API Contracts — esquema de segurança); ROADMAP Fase 13
  ("OAuth2 Resource Server JWT, escopos→perfis; backend é a única autoridade")
- **ADR relacionado:** `architecture/security.md`; DL-0079 (que esta DL **resolve**)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

O 8k (DL-0079) deixou o ERP como Resource Server do **próprio** emissor HS256 (segredo compartilhado,
sem JWKS, sem rotação). A Fase 13 exige validar **JWTs de um IdP externo** (Keycloak — DL-0103) **por
JWKS** com **rotação de chave**, e mapear **escopos/papéis do IdP → o modelo de papéis interno**. Falta
decidir: (a) como configurar o Resource Server contra o IdP; (b) de qual claim vêm os papéis; (c) o que
acontece com o emissor in-house e com `/login`.

## Decisão

1. **Resource Server por JWKS (issuer-uri).** Configurar
   `spring.security.oauth2.resourceserver.jwt.issuer-uri` (e/ou `jwk-set-uri`) apontando para o realm
   `acme`. O Spring Security passa a **buscar as chaves públicas via JWKS** e validar **assinatura
   (RS256), `iss`, `exp`** automaticamente, com **rotação** de chave transparente (cache + refetch do
   JWKS quando o `kid` muda). Removemos o `JwtEncoder`/`JwtDecoder` HS256 e o `JwtIssuer` in-house.
2. **Mapeamento papéis (graduando o claim-mapper do 8k).** Um `JwtAuthenticationConverter` extrai os
   papéis de **`realm_access.roles`** (formato do Keycloak) e os converte em autoridades Spring,
   preservando o prefixo `ROLE_` que os gates já usam (`hasRole(...)`/`@PreAuthorize`). Também
   coletamos o claim **`scope`/`scp`** como autoridades `SCOPE_*` (padrão Spring), de modo que a
   autorização "por escopo" do ROADMAP (`crm:lead:read:all`-style) fique disponível para refinos
   futuros — mas o **enforcement atual continua por papel** (catálogo fechado da DL-0082), sem
   reescrever as regras. O nome do principal vem de **`preferred_username`** (mesmo claim do 8k).
3. **`UserContextProvider` intacto (seam — DL-0081).** O `JwtUserContextProvider` segue lendo o
   `SecurityContext`; muda **só a origem do token** (IdP externo em vez do emissor próprio). Nenhum
   módulo de negócio é tocado.
4. **`uid`/identidade estável.** O `sub` do Keycloak é o id estável do usuário; mantemos a leitura do
   `uid`/`sub` no `SecurityPrincipals` (compat com o claim `uid` quando presente, senão `sub`).

## Justificativa

- **Fontes oficiais:** Spring Security (OAuth2 Resource Server) recomenda `issuer-uri` → descoberta
  OIDC + JWKS com rotação automática; é o caminho padrão e o que `security.md` manda ("não reinvente").
- **Keycloak** emite papéis em `realm_access.roles` — o claim-mapper espelha exatamente isso, mantendo
  o **modelo de papéis da SPEC-0024** como a única fonte de verdade interna (BR5). Os 6 papéis e o
  mapa de ações sensíveis (DL-0082) **não mudam**: só a *fonte* dos papéis (token do IdP).
- **Escopos disponíveis, papel como enforcement:** expor `SCOPE_*` cumpre o "escopos→perfis" do ROADMAP
  sem reescrever todos os gates por escopo agora (Regra Zero — o catálogo de papéis já cobre as ações
  sensíveis; escopos finos entram quando uma ação nova exigir).
- **Confiança=Média:** o formato do claim de papéis é específico do Keycloak (`realm_access.roles`);
  outro IdP usaria outro claim — por isso o mapeamento fica isolado num único conversor (troca barata).
- **Reversibilidade=Moderada:** trocar o IdP muda `issuer-uri` e (talvez) o claim de papéis no
  conversor — alteração localizada, sem tocar domínio.

## Alternativas descartadas

- **Manter HS256 in-house.** Descartada: é a dívida DL-0079 que esta fase fecha; não há JWKS/rotação.
- **Mapear papéis por `client_roles`/`resource_access`.** Descartada no v1: o realm export usa
  **realm roles** (papéis globais do ERP), mais simples e suficiente; client-roles entram se houver
  multi-client com papéis distintos (não é o caso).
- **Enforcement 100% por escopo agora (reescrever todos os gates).** Descartada (Regra Zero): o
  catálogo de papéis da DL-0082 já cobre as ações sensíveis; reescrever tudo por escopo é cerimônia
  sem necessidade atual. Os `SCOPE_*` ficam expostos para refino incremental.

## Impacto

- **Arquivos:** `infra.security.SecurityConfig` (remove encoder/decoder HS256; mantém o
  `JwtAuthenticationConverter` agora lendo `realm_access.roles` + `SCOPE_*`); remove `JwtIssuer`,
  `SecurityProperties` HS256; ajusta `SecurityPrincipals` (`sub`); `IdentityController` perde `/login`
  (DL-0105). `pom.xml` mantém `oauth2-resource-server`/`oauth2-jose`.
- **Config:** `application.yml` — `spring.security.oauth2.resourceserver.jwt.issuer-uri` por ambiente.
- **Migração:** nenhuma de schema obrigatória; o store local de usuários (`identity_users`) torna-se
  **opcional/legado** (usuários vivem no IdP) — ver DL-0107 para o tratamento do seam de papéis.
- **Contratos:** esquema de segurança OpenAPI passa de "bearer JWT in-house" para **OIDC/bearer (IdP
  externo, JWKS)**; `/login` aposentado (DL-0105).

## Como reverter

Reverter para o emissor in-house do 8k (restaurar `JwtIssuer`/HS256/`/login`) — não recomendado
(reintroduz DL-0079). Trocar para outro IdP é mudar `issuer-uri` e, se necessário, o claim de papéis no
conversor. Moderada e localizada em `infra.security`.
