# Keycloak — dev/E2E IdP (SPEC-0024 Fase 13 / DL-0103)

O ERP é um **OAuth2 Resource Server**: ele valida JWTs de um **IdP OIDC externo** por JWKS
(DL-0104). Para desenvolvimento local e para o stack E2E, esse IdP é um **Keycloak** em contêiner,
com o realm `acme` importado deste diretório (`realm-acme.json`).

> **Produção:** qual IdP a empresa adota (Keycloak self-hosted, Microsoft Entra ID, AWS Cognito…) é
> decisão do dono (DL-0103, Confiança=Baixa). O contrato OIDC é padrão — basta apontar
> `OIDC_ISSUER_URI` (backend) e o `issuer`/`clientId` (frontend) para o realm/tenant real.

## O que o realm contém

- **Realm:** `acme`.
- **Realm roles:** `ROLE_DIRECTOR`, `ROLE_FINANCE`, `ROLE_OPERATIONS`, `ROLE_IT`,
  `ROLE_POLICY_ADMIN`, `ROLE_VIEWER` — os mesmos papéis da SPEC-0024. O access token os carrega em
  `realm_access.roles`, que o backend mapeia para autoridades Spring (`ROLE_*`).
- **Client SPA público:** `acme-erp-web` — Authorization Code + **PKCE** (`S256`), refresh tokens,
  sem secret. `redirectUris`/`webOrigins` para `http://localhost:4200` (dev) e `:4201` (E2E).
- **Usuários seed (DEV/E2E ONLY, senha `dev12345`):** `director`, `finance`, `ops`, `it`, `policy`,
  `viewer` (um papel cada) e `dev` (todos os papéis). **Nunca** use estas credenciais em produção.

## Como sobe

`docker compose up` já inclui o serviço `keycloak`:

```
keycloak:  http://localhost:8088   (admin console: admin/admin)
realm:     http://localhost:8088/realms/acme
discovery: http://localhost:8088/realms/acme/.well-known/openid-configuration
```

O backend (`app`) recebe `OIDC_ISSUER_URI=http://localhost:8088/realms/acme` e resolve `localhost`
para o host (`extra_hosts: localhost:host-gateway`), de modo que o **`iss` do token** (gerado pelo
navegador via `localhost:8088`) **bate** com o issuer que o Resource Server valida. O stack E2E
(`compose.e2e.yaml`) usa a porta **8089** para rodar em paralelo ao dev.

## Editar/exportar o realm

O `realm-acme.json` é versionado e importado no boot (`--import-realm`). Para mudar papéis, client
ou usuários, edite o JSON e recrie o contêiner (`docker compose up -d --force-recreate keycloak`).
Não edite usuários pela console e espere persistência: o `start-dev` é efêmero — a fonte da verdade
é este arquivo.
