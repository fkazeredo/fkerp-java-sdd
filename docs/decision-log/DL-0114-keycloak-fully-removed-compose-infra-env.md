# DL-0114 — Infra (Fase 17): Keycloak **removido 100%** (compose dev/E2E, `infra/keycloak/`, `KEYCLOAK_*`/`OIDC_*`)

- **Fase:** 17 (remover Keycloak → AS self-hosted)
- **Spec(s):** SPEC-0024; SPEC-0028 (E2E isolado); ADR-0018; `architecture/delivery.md`
- **ADR relacionado:** ADR-0018; **encerra a infra da DL-0103** (serviço/realm/vars do Keycloak)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A DL-0103 adicionou o serviço `keycloak` ao `docker-compose.yml` e ao `compose.e2e.yaml`, o realm
export em `infra/keycloak/` e as variáveis `KEYCLOAK_*`/`OIDC_*` no `.env.example`. Com o AS embutido
no app (DL-0110), tudo isso vira dívida morta e precisa sair — sem quebrar os demais serviços.

## Decisão

1. **`docker-compose.yml`:** remover o serviço `keycloak` e o `depends_on: keycloak` do `app`. O `app`
   passa a servir OIDC de si mesmo; `OIDC_ISSUER_URI`/`OIDC_JWK_SET_URI` apontam para `http://app:8080`
   internamente e para `http://localhost:8080` como issuer público (mesma origem — o AS é embutido).
2. **`compose.e2e.yaml`:** remover o serviço `keycloak` e seu `depends_on`. O backend E2E serve OIDC
   embutido; o issuer público E2E é `http://localhost:8081`. **O stack E2E não sobe mais Keycloak.**
3. **`infra/keycloak/`:** deletar `realm-acme.json` e `README.md` (pasta inteira).
4. **`.env.example`:** remover `KEYCLOAK_PORT`/`KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` e reapontar
   `OIDC_ISSUER_URI` para o próprio app (self-hosted). Comentários atualizados.
5. **Perfil `dev`/`e2e`:** o app roda com o AS embutido ativo; o seed de usuários (DL-0112) roda nesses
   profiles. Nenhum novo contêiner.

## Justificativa

- **Regra Zero / `delivery.md`:** schema/infra morta é dívida; remover o serviço e o realm export deixa
  o stack mínimo e reprodutível. O AS embutido elimina o processo externo.
- **Aceite da Fase 17:** "login e papéis funcionando sem Keycloak (dev + E2E)". Remover o serviço é
  parte do entregável, não opcional.
- **Confiança=Alta:** é remoção mecânica + reaponte de issuer. **Reversibilidade=Moderada:** repor o
  Keycloak seria restaurar o serviço/realm/vars (a DL-0103).

## Alternativas descartadas

- **Deixar o serviço Keycloak "desligado" no compose.** Descartada: dívida e confusão; o dono pediu
  remoção 100%.
- **Manter o realm export "para referência".** Descartada: sem consumidor, é arquivo morto (Regra Zero);
  a DL-0103 documenta o histórico.

## Impacto

- **Arquivos:** `docker-compose.yml`, `compose.e2e.yaml`, `.env.example` (editados);
  `infra/keycloak/realm-acme.json` e `infra/keycloak/README.md` (removidos).
- **Frontend/E2E:** `e2e/helpers.ts` deixa de apontar para o Keycloak (DL-0113).

## Como reverter

Restaurar o serviço `keycloak` no compose, o `infra/keycloak/realm-acme.json` e as vars — reintroduz a
DL-0103. Moderada (infra + config, sem código de domínio).
