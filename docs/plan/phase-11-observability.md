# Plano — Fase 11 · Observabilidade & monitoramento (SPEC-0027)

> Base: `origin/develop` (0.21.0, 22 módulos, 468 testes verdes). Alvo: **0.22.0** (MINOR — capability
> retro-compatível nova, ADR 0015). Branch de integração: `feature/11-integration` (gitflow worktree-safe).

## Objetivo

Trazer a stack de observabilidade do **fkerp-poc** e adaptá-la às camadas (ADR 0012) e ao modelo de
papéis (SPEC-0024) deste projeto, **sem inventar comportamento de negócio** (Regra Zero): Actuator +
Micrometer + Prometheus, logs JSON, stack `infra/` (Prometheus/Loki/Alloy/Grafana), `GET /api/version`,
e métricas de negócio sobre eventos **já publicados**.

## Decisões (registradas ANTES do código)

- **DL-0095** — Exposição/segurança do Actuator (health/info/version públicos; prometheus/metrics → ROLE_IT).
- **DL-0096** — Logs JSON nativos do Spring Boot + higiene de mascaramento.
- **DL-0097** — `/api/version` por build-info + git, degradação graciosa.
- **DL-0098** — Métricas de negócio por listener em infra (domain não conhece Micrometer).

## Fatias (uma por vez; `./mvnw verify` VERDE antes de cada merge no integration)

### 11-1 — Actuator + Micrometer + Prometheus + segurança + `GET /api/version`
- `pom.xml`: dep `micrometer-registry-prometheus`; goal `build-info` no `spring-boot-maven-plugin`;
  `git-commit-id-maven-plugin`; filtrar `application.yml` (resolver `@project.version@`).
- `application.yml`: `management.endpoints.web.exposure.include = health,info,prometheus,metrics`;
  `management.metrics.tags.application = ${spring.application.name}`; `app.version: @project.version@`.
- `SecurityConfig`: `/api/version` + `/actuator/info` públicos; `/actuator/prometheus`,
  `/actuator/metrics` → `hasRole("IT")`.
- `application/api/VersionController` + `dto/VersionResponse {version, gitCommit, buildTime}`
  (beans `BuildProperties`/`GitProperties` opcionais com fallback). `OpenApiConfig`: frase do endpoint.
- **Testes:** `VersionEndpointIntegrationTest` (AC1), `ActuatorExposureIntegrationTest` (AC2–AC6).
- **Gate:** verify verde. Caderno `docs/test-report/phase-11-1-actuator-version.md`.

### 11-2 — Logs estruturados em JSON
- `docker-compose.yml`: env `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` no `app` (JSON no container; dev
  segue console humano via `logging.pattern.console`).
- Teste de fumaça reforçando que o login não loga a senha (higiene BR5; o caminho já existe).
- **Gate:** verify verde. Caderno `docs/test-report/phase-11-2-json-logging.md`.

### 11-3 — Stack `infra/` (config + compose, fora do `./mvnw verify`)
- `infra/prometheus/prometheus.yml`, `infra/loki/loki-config.yml`, `infra/alloy/config.alloy`,
  `infra/grafana/provisioning/{datasources,dashboards}/*.yml`, `infra/grafana/dashboards/*.json`
  (espelha a POC; alvo de scrape `app:8080/actuator/prometheus`; serviço `app`, não `backend`).
- `docker-compose.yml`: serviços `prometheus`, `loki`, `alloy`, `grafana` na rede existente; volumes.
- `.env.example`: portas/credenciais do Grafana.
- **Gate:** verify verde (não muda backend). Caderno `docs/test-report/phase-11-3-infra-stack.md` (AC9 manual).

### 11-4 — Métricas de negócio (infra listener) + regra ArchUnit
- `infra/observability/BusinessMetrics.java`: `@TransactionalEventListener(AFTER_COMMIT)` para
  `BookingConfirmed`/`BookingCancelled`/`QuoteComposed`/`PriceOverridden`/`CommissionInvoiceIssued`/
  `PeriodClosed`/`UserAuthenticated` → `Counter`s `acme.*`. Falha de métrica nunca propaga.
- `ArchitectureTest`: regra `..domain..` não depende de `io.micrometer..` nem `..boot.actuate..`.
- **Testes:** `BusinessMetricsIntegrationTest` (AC7/AC8). **Gate:** verify verde. Caderno 11-4.

### 11-5 — Docs + release + bump
- `pom.xml` 0.21.0 → **0.22.0**; OpenAPI info → 0.22.0 + frase da Fase 11.
- `docs/MANUAL.md` + `docs/MANUAL.en-US.md` (operador: monitoramento/versão), em sincronia.
- `docs/release-notes/0.22.0.md` (pt-BR) + `docs/release-notes/CHANGELOG.en-US.md` (topo).
- `docs/test-report/INDEX.md`; relatório de fase em `docs/`.

## Portões inegociáveis
ArchUnit + Modulith + Spotless + Checkstyle ligados e capazes de quebrar; `./mvnw verify` verde
(esperado 468 + novos testes). Sem migração (nenhuma mudança de schema; próxima seria V31). Domain puro
(sem Micrometer/Actuator). Constructor injection; sem `*Impl`; sem TODO solto.

## Publicação (worktree-safe)
`git push origin feature/11-integration:develop` (ff) → `git tag 0.22.0` + push → atualizar `main` por
merge `--no-ff` em HEAD destacado em `origin/main` (árvore = develop) → verificar com `git ls-remote`.
