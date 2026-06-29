# Caderno de testes — Slice 8f-1 (Marketing: Consent log append-only)

- **Spec:** SPEC-0019 (Marketing — Campanha, Segmentação, Newsletter, Consentimento LGPD).
- **Decisões:** DL-0055 (single opt-in v1), DL-0056 (consent append-only; estado = última linha).

## Escopo

Primeira fatia do módulo `marketing` (16º módulo Modulith). Entrega o **consentimento LGPD como
cidadão de primeira classe** (BR1): conceder e revogar **apendam linhas imutáveis** ao log de
consentimento; o **estado atual** é a última linha por `(titular, finalidade)`; o **histórico** é
preservado; reconsentir após revogar volta a `GRANTED`. Endpoints REST: `POST /consents`,
`DELETE /consents/{id}` (revoga), `GET /consents?subject=&subjectType=&purpose=` (estado + histórico).
Migração `V24__create_marketing.sql` (tabela `consents` + índice por `(subject, purpose, created_at
DESC)`; as demais tabelas do módulo entram nas fatias 8f-2/8f-3).

Acceptance Criteria cobertos nesta fatia: a base de consentimento que governa o envio (pré-condição
da BR2, exercida na 8f-2). Erros não vazam PII (LGPD): `marketing.consent.*` sem id do titular.

## Casos de teste

### Integração (Testcontainers + Postgres real) — `ConsentApiIntegrationTest`
| Caso | O que verifica | Regra |
|---|---|---|
| `grantingAppendsAGrantedRowAndCurrentStateIsGranted` | Conceder grava 1 linha GRANTED; estado atual = GRANTED | BR1/DL-0056 |
| `revokingAppendsANewRowAndPreservesHistory` | Revogar cria **nova** linha (id ≠), estado vira REVOKED, histórico tem as 2 linhas (mais nova primeiro) | BR1 (append/history) |
| `reConsentAfterRevokeResolvesBackToGranted` | Conceder de novo após revogar → estado GRANTED; 3 linhas no histórico | DL-0056 (última linha) |
| `unknownSubjectIsNotConsented` | Titular sem nenhuma linha → `isGranted()=false` (não consentido por padrão) | BR2 (filtro trata desconhecido como suprimido) |
| `revokingAMissingConsentIsNotFound` | Revogar id inexistente → `ConsentNotFoundException` (404) | Error Behavior |

### Arquitetura / Modulith (gates) — verdes
| Caso | O que verifica |
|---|---|
| `ModularityTests.verifiesModularStructure` | Grafo Modulith acíclico com o novo módulo `marketing` (16º); sem violação de fronteira |
| `ArchitectureTest` (12 regras) | Domínio não depende de application/infra; sem `*Impl`; construtor-injection; entidades sem setter |
| `HttpErrorMappingCompletenessTest` | Toda `DomainException` mapeada — inclui `ConsentInvalidException` (400) e `ConsentNotFoundException` (404) |

## Resultado

`./mvnw test -Dtest=ConsentApiIntegrationTest,ModularityTests,ArchitectureTest,HttpErrorMappingCompletenessTest`:

```
Tests run: 1  HttpErrorMappingCompletenessTest   OK
Tests run: 1  ModularityTests                    OK
Tests run: 5  ConsentApiIntegrationTest          OK
Tests run: 12 ArchitectureTest                   OK
BUILD SUCCESS
```

Total acumulado do backend após a fatia: **324 testes** (319 da 8e + 5 desta fatia). O `./mvnw verify`
completo da fase roda ao final, na branch de integração.

## Cobertura / o que NÃO está coberto (e por quê)

- **Envio/supressão (BR2), segmentação (BR3), campanha (BR4):** fatia 8f-2.
- **Atribuição (BR5) e erasure LGPD (BR6):** fatia 8f-3.
- **Double opt-in:** fora do v1 (DL-0055); o modelo já comporta o estado `PENDING` sem refator.
- **Autorização por papel (OIDC):** o stub de identidade vigora (SPEC-0024/Fase 13 adiada); o ator
  vem do `UserContextProvider`.

## Como reproduzir

```bash
cd backend
./mvnw test -Dtest=ConsentApiIntegrationTest          # fatia (integração)
./mvnw test -Dtest=ModularityTests,ArchitectureTest   # portões de arquitetura
./mvnw verify                                          # tudo (Docker no ar p/ Testcontainers)
```
