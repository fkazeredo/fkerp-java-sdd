# Caderno de testes — Fatia 11-1 · Actuator + Micrometer + Prometheus + `/api/version`

## Escopo

SPEC-0027, Acceptance Criteria cobertos: **AC1** (`GET /api/version` público, payload e versão),
**AC2** (`/actuator/health` público), **AC3** (`/actuator/prometheus` 401 anônimo), **AC4**
(`/actuator/prometheus` 403 papel sem ROLE_IT), **AC5** (`/actuator/prometheus` 200 ROLE_IT + formato
de exposição + tag `application`), **AC6** (`/actuator/env`/`/actuator/beans` não expostos → 404),
**AC8** (tag comum `application=acme-travel-erp`). BR1/BR2/BR3/BR4. (DL-0095, DL-0097)

## Casos de teste

### Integração (Testcontainers Postgres + segurança real)

`VersionEndpointIntegrationTest`:
- **versionIsPublicAndReportsTheConfiguredSemVer** — `GET /api/version` sem token → 200; `version`
  == `app.version` filtrado do pom (ADR 0015); `gitCommit`/`buildTime` populados (BR4 degradação
  graciosa nunca devolve campo em branco). → **AC1, BR2, BR4**.

`ActuatorExposureIntegrationTest` (`@AutoConfigureObservability` reabilita o export de métricas que o
Spring Boot desliga por padrão em `@SpringBootTest`):
- **healthIsPublic** — `GET /actuator/health` sem token → 200, corpo com `status: UP`. → **AC2, BR2**.
- **prometheusWithoutTokenIsUnauthorized** — `GET /actuator/prometheus` com bearer inválido → 401
  (não vaza métrica a anônimo). → **AC3, BR3**.
- **prometheusWithNonItRoleIsForbidden** — token de `viewer` (ROLE_VIEWER) → 403 (auditado como
  `ACCESS_DENIED`). → **AC4, BR3**.
- **prometheusWithItRoleReturnsExpositionFormat** — token de `it` (ROLE_IT) → 200; corpo contém
  `jvm_memory_used_bytes`, `http_server_requests` e a tag `application="acme-travel-erp"`. →
  **AC5, AC8, BR1, BR3**.
- **envAndBeansAreNotExposed** — `GET /actuator/env` e `/actuator/beans` (mesmo com ROLE_IT) → 404
  (fora do `include`). → **AC6, BR1**.

## Resultado

`cd backend && ./mvnw clean verify` → **BUILD SUCCESS**. **474 testes** (468 anteriores + 6 novos),
0 falhas. ArchUnit 16 testes verdes; Spotless limpo (816 arquivos); Checkstyle 0 violações.

```
Tests run: 474, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Mudança colateral (correção de contrato)

Adicionado handler `NoResourceFoundException → 404` no `GlobalExceptionHandler` (i18n
`resource.not-found`): uma URL desconhecida (inclusive um endpoint do Actuator deliberadamente não
exposto) passa a responder **404** (antes o catch-all a transformava em 500). Necessário para o AC6 e
é a semântica REST correta.

## Cobertura / o que NÃO está coberto

- AC5 valida o **formato** e a presença de séries técnicas; a presença de métricas de **negócio** é
  coberta na fatia 11-4 (`BusinessMetricsIntegrationTest`).
- AC9 (`docker compose up` sobe a stack de monitoramento) é **operacional/manual** — fatia 11-3, fora
  do `./mvnw verify` por desenho (BR8).
- A regra ArchUnit que prova `domain ↛ Micrometer/Actuator` entra na fatia 11-4 (junto com o listener).

## Como reproduzir

```bash
cd backend
./mvnw -o test -Dtest='VersionEndpointIntegrationTest,ActuatorExposureIntegrationTest'
./mvnw clean verify   # suíte completa + portões
```
