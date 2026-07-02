# DL-0129 — Multi-instância/HA: JWK persistido + estado do AS em JDBC + sessão JDBC (ADR-0020)

- **Fase:** 19g (Refactoring de maturidade — multi-instância/HA)
- **Spec(s):** SPEC-0024 (AS self-hosted)
- **ADR relacionado:** **ADR-0020** (novo — revisa o ADR-0002 single-instance); ADR-0018
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

Três pontos de estado em memória de processo impediam réplicas e até um restart limpo: a **chave
RSA do AS** (gerada a cada boot — restart deslogava todo mundo), os **clients/authorizations do AS
em memória** e a **sessão do form-login** no servlet container.

## Decisão

1. **`PersistedJwk`**: chave de assinatura carregada de `OIDC_JWK_PRIVATE_KEY` (base64 DER PKCS#8)
   com `kid` estável (`OIDC_JWK_KEY_ID`; em branco → derivado deterministicamente do módulo).
   Sem config → efêmera (dev/test). **Produção exige a persistida** (check novo no
   `ProdReadinessValidator`). Material inválido → fail-fast nomeando só a propriedade (nunca o
   valor).
2. **Estado do AS em JDBC** (migração **V39**, DDL padrão do SAS ajustado a Postgres):
   `JdbcRegisteredClientRepository` com **bootstrap idempotente** do client SPA (reusa o `id`
   existente ao re-salvar) + `JdbcOAuth2AuthorizationService`.
3. **Sessão do form-login via Spring Session JDBC** (starter `spring-boot-starter-session-jdbc`;
   tabelas `spring_session*` na V39; `initialize-schema=never` — schema é do Flyway).
4. **Mantidos por instância (decisão):** circuit breakers de saída (proteção local correta);
   dispatcher do mock de pagamento (corrida entre réplicas é no-op — receiver idempotente).
   Jobs já eram HA-safe (advisory lock por janela, DL-0075).

## Justificativa

- A chave efêmera era dor **atual** (todo restart deslogava), não só bloqueio de HA.
- Postgres já é o único stateful — sessão/estado no banco evita infra nova (Redis) — Regra Zero.
- Testes provam a propriedade de HA: duas "instâncias" com a mesma chave compartilham kid/chave
  pública (`PersistedJwkTest`); client/authorization salvos por uma instância são lidos por outra
  (`JdbcAuthorizationStateIntegrationTest`); o AS real sobe com os repositórios JDBC
  (`AuthorizationServerIntegrationTest` inalterado e verde).

## Alternativas descartadas

- **Redis (sessão/estado):** infra nova sem necessidade.
- **Sticky session:** não resolve restart/failover.
- **Rotação multi-chave já:** especulativo; seam documentado no ADR-0020.

## Impacto

- **Arquivos:** `PersistedJwk` (novo); `AuthorizationServerConfig` (jwkSource por config, Jdbc
  repos, bootstrap idempotente); `ProdReadinessValidator` (+1 check); `application.yml`
  (`app.oidc.jwk.*`, `spring.session.jdbc.initialize-schema=never`); pom
  (`spring-boot-starter-session-jdbc`). Migração **V39**.
- **Testes:** `PersistedJwkTest` (4), `JdbcAuthorizationStateIntegrationTest` (2),
  `ProdReadinessValidatorTest` (+1).
- **Contratos:** nenhum `/api` muda.

## Como reverter

Moderada: voltar aos beans in-memory e à chave efêmera (a V39 fica órfã, aditiva). Reverter
perderia a propriedade "restart não desloga" — não recomendado.
