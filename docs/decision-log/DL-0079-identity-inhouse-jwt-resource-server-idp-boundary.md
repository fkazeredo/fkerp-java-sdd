# DL-0079 — Identity: autenticação real in-house (Spring Security + JWT HS256) no 8k; OIDC externo vivo fica para a Fase 13

- **Fase:** 8k (Identity)
- **Spec(s):** SPEC-0024 (Goal; Scope; BR1; Open Question "Comprar/usar IdP × IAM próprio"); SPEC-0001
  (fundação do `UserContextProvider` stub)
- **ADR relacionado:** `architecture/security.md` (Spring Security é o padrão; backend é a autoridade
  final); ROADMAP Fase 13 (OAuth2 Resource Server JWT profissional)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Cara

## Lacuna

A SPEC-0024 deixa **em aberto** (Open Question) **comprar/usar um IdP externo (Keycloak/Entra/Cognito)**
× **IAM próprio**, e se a autenticação acontece via **Resource Server OIDC** lendo tokens de um IdP de
terceiros. O ROADMAP recomenda IdP, mas marca a **Fase 13** como a consolidação profissional
("Spring Security + OAuth2 Resource Server (JWT), escopos→perfis"). A pergunta que **trava o código do
8k** é: a fase precisa **integrar um IdP externo vivo** agora, ou pode entregar o **modelo de auth real
+ papéis/permissões + auditoria** com um caminho in-house defensável e deixar o IdP vivo para a 13?

Esta decisão é especialmente sensível porque o sistema inteiro roda hoje sobre o
`DevStubUserContextProvider` e **434 testes de integração assumem um ator autenticado com acesso total**.

## Decisão

Entregar no 8k a **autenticação real in-house**, sem amarrar um IdP externo vivo:

1. **Spring Security ligado** como camada de autenticação/autorização (security.md). O ERP é a
   **única autoridade** de autorização interna (BR5).
2. **Emissão e verificação de JWT próprias (HS256)**: um endpoint `POST /api/identity/login` autentica
   contra uma tabela local mínima (`identity_users`, senha com **BCrypt**) e emite um **JWT assinado**
   (claims `sub`, `preferred_username`, `roles`, `exp`). As requisições seguintes mandam
   `Authorization: Bearer <jwt>`; um filtro verifica assinatura/expiração e popula o
   `SecurityContext`. O ERP é, na prática, **Resource Server do seu próprio emissor** — o mesmo
   contrato (Bearer JWT + claims de papéis) que um IdP externo entregaria.
3. **A porta `UserContextProvider` é o seam estável** (DL-0081): o adapter real
   (`JwtUserContextProvider`) lê o token; trocar o emissor in-house por um **OIDC externo vivo** na
   Fase 13 é trocar **a configuração do verificador** (chave HS256 → JWKS do IdP, claim-mapper) e o
   `/login` (delegado ao IdP), **sem tocar** nos módulos que consomem o contexto.
4. **A fronteira com a Fase 13 fica registrada**: 8k entrega o **modelo de auth real, papéis/
   permissões e auditoria de acesso**; a 13 **consolida** o Resource Server profissional contra um IdP
   externo (JWKS/rotação de chave, refresh/silent-refresh no front, escopos finos `crm:lead:read:all`
   → perfis). O 8k **não** entrega gestão de usuários/SSO corporativo (Out of Scope da spec).

## Justificativa

- **ROADMAP é autoridade** e ele mesmo **separa** 8k (gradua o stub: SPEC-0024) de 13 (Resource Server
  OAuth2/JWT profissional). Logo, entregar o **modelo real + enforcement + auditoria** no 8k e deixar
  o **IdP vivo + escopos finos** para a 13 é a leitura fiel do roadmap — não inventar escopo.
- **security.md:** "Spring Security is the default… Do not reinvent auth mechanisms." Usamos Spring
  Security e a infraestrutura JWT padrão (`spring-boot-starter-oauth2-resource-server` /
  `oauth2-jose` Nimbus) — **não** um esquema caseiro de criptografia. HS256 com segredo por ambiente é
  um esquema padrão, suficiente para um emissor único in-house, e migra para RS256/JWKS na 13.
- **Não quebrar o build (a maior restrição da fase):** um IdP externo vivo nos testes exigiria um
  contêiner de IdP (Keycloak) ou um emissor falso elaborado, aumentando custo e fragilidade. Emitir/
  verificar o próprio JWT permite que os **testes de segurança** gerem tokens determinísticos e que o
  **profile `test`/`dev`** mantenha o ator permissivo (DL-0081) — 434 testes seguem verdes.
- **Confiança=Baixa:** **qual IdP** a empresa vai adotar (Keycloak/Entra/Cognito) e **se** quer um IdP
  é decisão do **dono** (a spec marca como Open Question "decisão do dono"). O valor in-house é o mais
  defensável que **não inventa** essa escolha e **não bloqueia** a fase.
- **Reversibilidade=Cara:** trocar para um IdP externo vivo (Fase 13) mexe no emissor, no verificador
  (JWKS/rotação), no fluxo de login do front e na gestão de usuários — refator amplo, ainda que o
  **seam `UserContextProvider`** e o **modelo de papéis** sejam preservados. Por isso fica destacada.

## Alternativas descartadas

- **Integrar um IdP externo vivo (Keycloak) já no 8k.** Descartada: é exatamente o escopo da **Fase 13**
  no ROADMAP; exigiria contêiner de IdP nos testes (custo/fragilidade) e força a escolha de produto que
  **só o dono** faz (Open Question). Antecipá-la contraria a Regra Zero e o sequenciamento do roadmap.
- **Continuar só com o stub (não graduar).** Descartada: a SPEC-0024 existe para **graduar** o stub;
  manter o stub deixa autorização/auditoria fictícias (Business Context da spec).
- **IAM caseiro completo (gestão de usuários/SSO).** Descartada: Out of Scope explícito da spec
  ("subdomínio genérico… não um IAM caseiro"); overengineering (Regra Zero).
- **JWT RS256 com JWKS já no 8k.** Descartada por ora: sem IdP externo, o par de chaves seria interno e
  sem ganho real frente ao HS256 com segredo por ambiente; RS256/JWKS é justamente o degrau da Fase 13.

## Impacto

- **Specs:** SPEC-0024 — itens de Open Question resolvidos movidos para Business Rules como "ASSUMIDO
  (ver DL-0079/0081/0082/0083)".
- **Dependências (`pom.xml`):** `spring-boot-starter-security` +
  `spring-boot-starter-oauth2-resource-server` (Nimbus JOSE para emitir/verificar o JWT).
- **Arquivos:** nova `infra.security` (config Spring Security, filtro/decoder JWT, `JwtUserContextProvider`,
  `JwtIssuer`, encoder BCrypt); módulo `domain.identity` (DL-0080); controllers `IdentityController`.
- **Migração:** V29 (DL-0080) com `identity_users`, `roles`, `role_permissions` (+ seed).
- **Config:** `identity.jwt.secret`/`ttl` por ambiente; profile `dev`/`test` mantém o stub permissivo.
- **Contratos:** `POST /api/identity/login`, `GET /api/identity/me`, `GET /api/identity/roles`,
  `GET /api/identity/access-audit`; esquema de segurança Bearer documentado na OpenAPI.

## Como reverter

Reversão para um **IdP externo vivo** (o caminho natural da Fase 13): trocar o `JwtDecoder` in-house pelo
**Resource Server OIDC** (issuer-uri/JWKS do IdP), aposentar o `POST /login` próprio (login passa a ser
no IdP) e mover a gestão de usuários para o IdP. O **seam `UserContextProvider`** e o **modelo de
papéis/permissões + auditoria** permanecem — por isso o domínio não muda, mas a troca do emissor/
verificador e do fluxo de login é um refator **caro** (e exige reprovisionar usuários no IdP).
