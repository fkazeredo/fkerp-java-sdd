# DL-0105 — Identity: caminho de teste/dev-CI usa JWKS local (par RSA de teste); `POST /api/identity/login` in-house é aposentado (login move para o IdP)

- **Fase:** 13 (Identity/AuthZ profissional — gradua SPEC-0024)
- **Spec(s):** SPEC-0024 (Tests Required; API Contracts; BR4); SPEC-0028 (E2E)
- **ADR relacionado:** `architecture/testing.md`; ADR 0015 §4 (breaking change em `0.y`); DL-0081
  (TestSecurityConfig mantém a suíte verde); DL-0104
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

Ao trocar o Resource Server para validar JWTs do IdP externo por JWKS (DL-0104), os **477 testes de
backend** (e o E2E) — que hoje **mintam tokens chamando `POST /api/identity/login`** do emissor
in-house — quebram. Precisamos: (a) manter a suíte verde **sem um IdP na internet** nem um Keycloak
Testcontainer por execução; (b) decidir o destino do contrato `POST /api/identity/login` (in-house),
que deixa de fazer sentido quando o login é no IdP.

## Decisão

1. **JWKS local de teste (par RSA gerado no teste).** Sob o profile `test`, o Resource Server é
   configurado com um **`JwtDecoder` baseado numa chave pública RSA de teste** (não no JWKS do
   Keycloak). Um utilitário `TestJwtTokens` (em `src/test/java`) **gera tokens RS256** assinados pela
   chave privada correspondente, com `iss` esperado, `preferred_username`, `sub` e
   **`realm_access.roles`** — exatamente o formato do Keycloak. Assim:
   - os testes de segurança que **enviam** token exercem o **caminho JWKS/RS256 genuíno** (assinatura,
     `iss`, `exp`, mapeamento de papéis) — 401 para token inválido, 403 para papel insuficiente;
   - o `TestSecurityConfig` (DL-0081) **continua** autenticando o ator full-access quando **não há**
     header `Authorization`, mantendo verdes os testes que não se importam com auth.
2. **`POST /api/identity/login` in-house é REMOVIDO (breaking change).** O login passa a ser no IdP
   (code+PKCE no frontend — DL-0106). Os testes de integração de login do 8k
   (`IdentityLoginIntegrationTest`) que batiam em `/login` são **substituídos** por testes que mintam o
   token de teste e validam o Resource Server (resolução de papéis, 401/403). `GET /api/identity/me`,
   `GET /api/identity/roles` e `GET /api/identity/access-audit` **permanecem** (lêem o token/realm).
3. **`UserAuthenticated`/`AUTH_LOGIN` audit no login real.** Como o login agora ocorre no IdP, a
   auditoria de login (BR3) é registrada na **primeira chamada autenticada** do usuário (um
   `AuthenticationSuccess`/`first-touch` em `/me`), reusando o `system_audit` (DL-0083). A negação
   (`ACCESS_DENIED`) segue auditada no `RestAccessDeniedHandler` (inalterado).

## Justificativa

- **Não quebrar o build é a maior restrição da fase.** Um JWKS local com par RSA de teste dá tokens
  **determinísticos** e roda o **mesmo** caminho de validação por assinatura que o Keycloak — sem
  download de imagem nem flakiness. É o padrão recomendado para testar Resource Servers (Spring
  Security test utils + Nimbus): validar a *mecânica* JWT localmente, deixar o IdP vivo para o E2E.
- **Aposentar `/login` é a consequência correta da graduação** (DL-0079/§ "Como reverter"): com IdP
  vivo, manter um emissor próprio seria dívida e dois caminhos de auth. ADR 0015 §4 permite breaking em
  `0.y` desde que **destacado** no release note — e será (0.23.0).
- **BR4 preservada:** credenciais inválidas agora são tratadas **pelo Keycloak** (tela de login do IdP)
  com mensagem genérica; o backend nunca mais vê senha (melhora a postura — o ERP deixa de custodiar
  hash de senha).
- **Confiança=Média / Reversibilidade=Moderada:** o utilitário de teste e a remoção de `/login` são
  localizados; reverter é restaurar o emissor (não desejado).

## Alternativas descartadas

- **Keycloak Testcontainer na suíte de integração.** Descartada: lento/frágil/baixa o CI (ver DL-0103).
- **Manter `/login` in-house como "fallback".** Descartada: dois emissores = dívida e superfície de
  ataque; contraria a graduação (Regra Zero).
- **`@WithMockUser`/mock do SecurityContext em vez de token real.** Descartada para os testes de
  segurança: não exercitam o caminho JWKS/decoder real (o ponto da fase). Usado só onde já era o padrão
  (TestSecurityConfig full-access para testes não-auth).

## Impacto

- **Arquivos:** `src/test/.../TestJwtTokens` (novo, mint RS256 + JWKS local); `TestSecurityConfig`
  (decoder de teste com a chave pública de teste); `IdentityLoginIntegrationTest` → reescrito como
  `ResourceServerIntegrationTest`; `AccessControlIntegrationTest`/`ActuatorExposureIntegrationTest`
  trocam `login(user)` por `mintToken(user, roles...)`. `IdentityController` perde `/login` e o DTO
  `LoginRequest`/`LoginResponse`; `JwtIssuer`/`DevUserSeeder` removidos (usuários vivem no IdP).
- **Migração:** nenhuma (DL-0107 trata o store local).
- **Contratos:** `POST /api/identity/login` **removido** (breaking, destacado em 0.23.0).

## Como reverter

Restaurar `JwtIssuer`/`/login`/HS256 e o `DevUserSeeder` (reintroduz DL-0079 — não recomendado). O
utilitário de teste e a config de decoder de teste são removíveis sem efeito de produção. Moderada.
