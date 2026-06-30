# DL-0103 — Identity: IdP de desenvolvimento = Keycloak (realm `acme` importado, clients, roles, usuários seed)

- **Fase:** 13 (Identity/AuthZ profissional — gradua SPEC-0024)
- **Spec(s):** SPEC-0024 (Goal; Scope; BR1; Open Question "Comprar/qual IdP"); SPEC-0028 (E2E isolado)
- **ADR relacionado:** `architecture/security.md` (Spring Security é o padrão; backend é a autoridade
  final); `architecture/delivery.md` (Docker Compose para serviços externos); ROADMAP Fase 13 (SEC-1)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Cara

## Lacuna

A SPEC-0024 deixava como **Open Question do dono** *qual* IdP externo adotar (Keycloak / Microsoft
Entra ID / AWS Cognito). A Fase 13 precisa de um **IdP OIDC vivo** para o backend validar JWTs por
JWKS e para o frontend fazer login real — mas não pode depender de um IdP na internet (precisa rodar
local, em CI e no stack E2E descartável). É preciso escolher o produto e como provisioná-lo
(realm/clients/roles/usuários) sem internet.

## Decisão

Adotar **Keycloak** como o IdP de desenvolvimento/E2E, rodando em contêiner via Docker Compose:

1. **Imagem:** `quay.io/keycloak/keycloak` (start-dev), no `docker-compose.yml` (stack local) e no
   `compose.e2e.yaml` (stack E2E efêmero). Sem dependência de internet.
2. **Realm importado** (`infra/keycloak/realm-acme.json`, montado em `/opt/keycloak/data/import`):
   realm `acme`, **realm roles** = os 6 papéis base da SPEC-0024 (`ROLE_DIRECTOR`, `ROLE_FINANCE`,
   `ROLE_OPERATIONS`, `ROLE_IT`, `ROLE_POLICY_ADMIN`, `ROLE_VIEWER`).
3. **Client SPA público** `acme-erp-web` (Authorization Code + **PKCE**, `publicClient=true`, sem
   secret), com `refresh tokens` habilitados, `redirectUris`/`webOrigins` para `http://localhost:4200`
   (dev) e `http://localhost:4201` (E2E). É o client que o Angular usa.
4. **Usuários seed** (dev/E2E **apenas**, senha fraca conhecida `dev12345`): um por papel
   (`director`/`finance`/`ops`/`it`/`policy`/`viewer`) + um super-usuário `dev` com todos os papéis —
   espelha o `DevUserSeeder` do 8k para manter as jornadas de login e os testes determinísticos.
5. **Mapeamento de claims:** o realm emite `realm_access.roles` (papéis) no access token; o backend
   mapeia esses papéis → autoridades Spring (DL-0104). O backend é a **única autoridade** de
   autorização (BR5) — o Keycloak só **autentica** e carrega papéis.

## Justificativa

- **ROADMAP é autoridade** e cita **Keycloak** explicitamente como o "dev IdP" da Fase 13. A
  recomendação do arquiteto na SPEC-0024 é "preferir IdP externo (OIDC)".
- **Open-source, on-prem, sem custo e sem internet:** Keycloak roda 100% local/CI, importa realm por
  arquivo (reprodutível, versionado), e é o IdP OIDC de referência (CNCF-adjacent, amplamente usado).
  Entra/Cognito são SaaS — exigiriam tenant na nuvem e credenciais, inviável para local/E2E.
- **security.md:** "Spring Security é o padrão… não reinvente mecanismos de auth". Keycloak + Resource
  Server é o caminho padrão OIDC; nada caseiro.
- **Confiança=Baixa:** *qual IdP a empresa adota em produção* segue sendo decisão do dono — Keycloak é
  o degrau de desenvolvimento mais defensável e o realm export documenta exatamente o que um Entra/
  Cognito precisaria espelhar (mesmos papéis, mesmo client público PKCE, mesmo claim de papéis).
- **Reversibilidade=Cara:** trocar de IdP em produção mexe em provisionamento de usuários, client e
  claim-mapping. Porém o **contrato OIDC** (issuer-uri/JWKS, `realm_access.roles`→papéis, code+PKCE) é
  padrão, então o backend e o frontend mudam só por **configuração** (issuer-uri/client-id), não por
  código — o seam `UserContextProvider` e o modelo de papéis sobrevivem.

## Alternativas descartadas

- **Microsoft Entra ID / AWS Cognito (SaaS).** Descartadas para dev/E2E: exigem tenant na nuvem e
  segredo — não rodam local/CI/efêmero. Continuam viáveis em produção (o contrato OIDC é o mesmo).
- **Manter o emissor in-house HS256 do 8k.** Descartada: é exatamente a dívida que a Fase 13 resolve
  (DL-0079); não exercita JWKS/rotação nem um IdP real.
- **Keycloak Testcontainer nos testes de integração.** Descartada para a suíte unitária/integração:
  subir um Keycloak por execução é lento/frágil e dispararia download de imagem no CI. Os testes usam
  um **JWKS local com par RSA de teste** (DL-0105); o Keycloak vivo entra na stack dev e no E2E.

## Impacto

- **Specs:** SPEC-0024 — Open Question "qual IdP" → Business Rule "ASSUMIDO (ver DL-0103)"; seção de
  graduação.
- **Infra:** `infra/keycloak/realm-acme.json` (realm export); serviço `keycloak` em `docker-compose.yml`
  e `compose.e2e.yaml`.
- **Config backend:** `spring.security.oauth2.resourceserver.jwt.issuer-uri`/`jwk-set-uri` apontando
  para o realm (DL-0104).
- **Frontend:** client `acme-erp-web` consumido pelo `angular-oauth2-oidc` (DL-0106).
- **Contratos:** `POST /api/identity/login` in-house é aposentado — login passa a ser no Keycloak
  (breaking change destacado no release note 0.23.0, ADR 0015 §4; ver DL-0105).

## Como reverter

Trocar o IdP é trocar `issuer-uri`/`jwk-set-uri` (backend) e `issuer`/`clientId` (frontend) para o novo
provedor, e reprovisionar realm/client/usuários lá. O domínio (papéis, `UserContextProvider`,
auditoria) não muda. Reverter para o emissor in-house do 8k seria restaurar `JwtIssuer`/`/login` — não
recomendado (reintroduz DL-0079). Caro por reprovisionamento, barato em código.
