# ADR 0020: Multi-instance ready (revisa o ADR 0002 — single instance)

## Status

Accepted (Fase 19g — Refactoring de maturidade; DL-0129)

## Context

O ADR 0002 fixou **single-instance** como premissa de implantação — adequada ao estágio do
produto, mas com três pontos de estado **em memória de processo** que impediam qualquer segunda
instância (e até um restart limpo):

1. **Chave RSA do Authorization Server** gerada a cada boot → um restart invalidava todos os
   tokens; uma réplica não validaria tokens da outra.
2. **Clients e authorizations do AS em memória** (`InMemoryRegisteredClientRepository` e o
   authorization service default) → um fluxo OAuth2 iniciado numa instância não terminava na outra.
3. **Sessão do form-login em memória (servlet)** → o navegador logado numa instância era anônimo
   na outra (exigiria sticky session).

O restante do sistema **já era multi-instance-safe por construção**: jobs com advisory lock por
janela (DL-0075), idempotência por UNIQUE em todos os consumidores/webhooks, lockout de login em
tabela (V38), retries do crawler confinados à execução e dead-letters gravados no banco.

## Decision

Tornar o app **stateless por instância** (o estado de autenticação vive no Postgres/configuração),
mantendo o deploy single-instance como default operacional:

1. **Chave de assinatura persistida** (`PersistedJwk`): `OIDC_JWK_PRIVATE_KEY` (base64 DER PKCS#8)
   + `OIDC_JWK_KEY_ID` estável — todas as instâncias publicam o MESMO JWKS; restart não desloga.
   Sem a config, gera efêmera (conveniência dev/test); **produção exige a persistida**
   (`ProdReadinessValidator`).
2. **Estado do AS em JDBC** (V39): `JdbcRegisteredClientRepository` (client SPA bootstrapado
   idempotentemente) + `JdbcOAuth2AuthorizationService` — qualquer instância completa um fluxo que
   outra iniciou.
3. **Sessão do form-login via Spring Session JDBC** (V39: `spring_session*`) — sem sticky session;
   `spring.session.jdbc.initialize-schema=never` (schema é do Flyway).

**Continuam por instância, por decisão:** circuit breakers de saída (proteção local por processo é
o comportamento correto), caches locais inexistentes, e o dispatcher do mock de pagamento (corrida
entre instâncias é inofensiva — o receiver é idempotente por UNIQUE).

## Consequences

- Um restart deixa de derrubar sessões/tokens; duas instâncias atrás de um load balancer passam a
  ser possíveis (a prova operacional com reverse proxy/replicas fica na fatia 19l, junto do
  compose de produção).
- Nova responsabilidade operacional: custodiar `OIDC_JWK_PRIVATE_KEY` como segredo (mesma
  disciplina da `PLATFORM_SECRET_KEY`); rotação de chave = publicar a nova + manter a antiga no
  JWKS por uma janela (seam documentado — hoje 1 chave).
- O login/logout ganha I/O de banco (sessão JDBC) — irrelevante no volume atual.
- Migração V39 (tabelas SAS + Spring Session), aditiva.

## Alternatives Considered

- **Manter single-instance estrito (status quo):** rejeitado — mesmo sem réplica, a chave efêmera
  derrubava todos os usuários a cada deploy/restart (dor real hoje, não especulação).
- **Redis para sessão/estado:** infra nova sem necessidade — o Postgres já é o único stateful do
  sistema (Regra Zero).
- **Sticky sessions no LB:** esconderia o problema, não o resolveria (restart continuaria
  deslogando; failover perderia sessões).
- **JWKS com rotação automática de múltiplas chaves agora:** especulativo; 1 chave persistida +
  seam de rotação documentado cobre o requisito atual.
