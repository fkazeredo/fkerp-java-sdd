# Relatório da Fase 11 — Observabilidade & monitoramento (SPEC-0027, release 0.22.0)

## Resumo

Trouxe a base de **observabilidade** do fkerp-poc para o ERP, adaptada às camadas (ADR 0012) e ao
modelo de papéis (SPEC-0024), **sem mudar nenhuma regra de negócio** (Regra Zero): instrumenta e expõe
o que já existe. Entregue em 5 fatias, todas com `./mvnw verify` **verde**.

## Fatias entregues

| Fatia | Entrega | Testes |
|---|---|---|
| 11-1 | Actuator + Micrometer + registry Prometheus; exposição segura (`health`/`info`/`version` públicos; `prometheus`/`metrics` → ROLE_IT); `GET /api/version` (build-info + git, degradação graciosa); `NoResourceFoundException`→404 | `VersionEndpointIntegrationTest`, `ActuatorExposureIntegrationTest` |
| 11-2 | Logs estruturados em JSON (ECS) no container, correlation id no JSON, higiene de não-logar segredo | `SensitiveDataNotLoggedIntegrationTest` |
| 11-3 | Stack `infra/` (Prometheus/Loki/Alloy/Grafana) via docker-compose; datasources + dashboard provisionados | operacional (compose up; AC9) |
| 11-4 | Métricas de negócio sobre eventos já publicados (`BusinessMetrics`, infra) + regra ArchUnit `domain ↛ Micrometer/Actuator` | `BusinessMetricsIntegrationTest`, `ArchitectureTest` (17ª regra) |
| 11-5 | Bump 0.22.0 (pom+OpenAPI), MANUAL pt-BR+en-US, release note + CHANGELOG.en-US, este relatório | — |

## Resultado dos testes

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, **477 testes** (eram 468; +9: 2 actuator/version,
1 logging, 2 métricas/ArchUnit, e os que cada classe adiciona). **Sem falhas.** Portões verdes:
**ArchUnit 17 regras** (com a nova `DOMAIN_MUST_NOT_DEPEND_ON_METRICS_OR_ACTUATOR` + teeth),
**Spring Modulith** (grafo acíclico, 22 módulos), **Spotless** (819 arquivos), **Checkstyle** (0
violações).

Cobertura por tipo:
- **Integração (Testcontainers + segurança real):** `/api/version` (público, payload/versão);
  `/actuator/prometheus` (401 anônimo, 403 sem ROLE_IT, 200 com TI + formato/tag); `/actuator/env` e
  `/actuator/beans` 404 (não expostos); login não loga senha/token; login incrementa
  `acme_identity_logins_total`.
- **Arquitetura (ArchUnit):** domínio não depende de Micrometer/Actuator.
- **Operacional (manual, AC9):** `docker compose up` sobe Prometheus/Loki/Alloy/Grafana; Grafana com
  datasources Prometheus+Loki e o dashboard provisionados; alvo `acme-travel-erp` no Prometheus.

## Arquivos criados/alterados (principais)

- **Actuator/Micrometer/versão:** `backend/pom.xml` (dep prometheus; goal build-info; git plugin;
  filtragem de `application.yml`); `application.yml` (exposure include; metrics tag; `app.version`);
  `infra/security/SecurityConfig.java` (matchers ROLE_IT + `/api/version` público);
  `application/api/VersionController.java` + `dto/VersionResponse.java`; `infra/openapi/OpenApiConfig.java`.
- **404 handler:** `infra/web/GlobalExceptionHandler.java` + i18n `resource.not-found` (pt-BR/en).
- **Logs JSON:** `docker-compose.yml` (env ECS), `application.yml` (comentário); teste de higiene.
- **infra/:** `prometheus/prometheus.yml` + `README.md`, `loki/loki-config.yml`, `alloy/config.alloy`,
  `grafana/provisioning/{datasources,dashboards}/*.yml`, `grafana/dashboards/acme-travel-erp.json`;
  `docker-compose.yml` (serviços + rede + volumes), `.env.example`, `.gitignore` (scrape-token).
- **Métricas de negócio:** `infra/observability/BusinessMetrics.java`; `ArchitectureTest.java` (regra nova).
- **Docs:** `docs/specs/0027-...md`, `docs/decision-log/DL-0095..0098` + INDEX, `docs/plan/phase-11-...md`,
  `docs/test-report/phase-11-{1..4}-*.md` + INDEX, `docs/MANUAL.md` + `docs/MANUAL.en-US.md`,
  `docs/release-notes/0.22.0.md` + `CHANGELOG.en-US.md`.

## Migrações / Contrato

- **Migração:** nenhuma (sem mudança de schema; próxima seria V31).
- **Contrato:** novo endpoint **aditivo** `GET /api/version`; Actuator `health`/`info` públicos,
  `prometheus`/`metrics` ROLE_IT; OpenAPI `version`→0.22.0 + frase da Fase 11. Nenhuma mudança de
  contrato de negócio.

## Decisões (decision-log)

- [DL-0095](decision-log/DL-0095-actuator-exposure-and-security-role-it.md) — exposição/segurança do
  Actuator (Média/Barata).
- [DL-0096](decision-log/DL-0096-json-logging-native-spring-boot-and-masking.md) — logs JSON nativos +
  higiene (Alta/Barata).
- [DL-0097](decision-log/DL-0097-api-version-from-build-info-and-git.md) — `/api/version` por
  build-info+git (Alta/Barata).
- [DL-0098](decision-log/DL-0098-business-metrics-via-infra-event-listener.md) — métricas de negócio em
  infra (Alta/Barata).

Nenhuma é Confiança=Baixa nem Reversibilidade=Cara: a tarefa traz a stack pronta e as fontes oficiais
(Spring Boot Actuator/Micrometer/Prometheus/Grafana) fecham as escolhas.

## Riscos e o que fica para a próxima fase

- **Token de raspagem do Prometheus expira** (TTL do JWT): em produção, service account/token de longa
  duração e/ou raspagem por rede restrita (DL-0095).
- **Alertas (Alertmanager) e tracing distribuído** ficam **fora de escopo** (Regra Zero); a stack está
  pronta para receber regras quando a operação as definir; tracing só com saltos de rede reais.
- **Fase 12** (Qualidade & E2E — Playwright/coverage) e **Fase 13** (Identity/OIDC) seguem o ROADMAP.
