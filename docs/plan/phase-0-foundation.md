# Plano — Fase 0: Fundação (walking skeleton + Event Storming)

> Spec: **SPEC-0001**. Fatia única: **Slice 0**. Laço do `TUTORIAL.md`
> (RED → esqueleto → GREEN → refactor → portões → DoD). Decisões em
> `docs/decision-log/` (DL-0001..0006).

## Objetivo

Entregar um monólito modular que **compila, sobe, tem testes verdes e CI**, provando
a stack ponta a ponta (HTTP → domínio → banco → resposta), com as regras de
arquitetura travadas por ArchUnit + Spring Modulith, pronto para a SPEC-0002.

## Specs e ADRs relevantes

- SPEC-0001 (esqueleto + Event Storming).
- ADRs: 0001 (modular monolith), 0010 (infra centralizada), 0011 (exceções sem
  transporte), 0012 (três camadas), 0013 (Lombok), 0008 (frontend), 0002
  (single-instance), 0003 (single-tenant). ADR 0014 adiado → [[DL-0005]].

## Módulos/camadas afetados

Nenhum módulo de negócio (Regra Zero). Apenas as camadas e o kernel:
`com.fksoft.domain.error`, `com.fksoft.application.api`, `com.fksoft.infra.*`.

## Arquivos backend (a criar)

```
backend/
  pom.xml                          (Spring Boot 3.5.16, Modulith 1.4.12, deps)
  mvnw, mvnw.cmd, .mvn/wrapper/    (wrapper -> Maven 3.9.11)  [[DL-0004]]
  lombok.config                    (accessors.fluent=true, ADR 0013)
  Dockerfile                       (multi-stage build + run)
  src/main/java/com/fksoft/
    FkErpApplication.java          (@SpringBootApplication)
    domain/error/
      DomainException.java  ErrorDetails.java  RateLimited.java
    application/api/
      SystemController.java        (GET /api/system/health)
      dto/SystemHealthResponse.java
    infra/
      web/  ApiErrorResponse.java  GlobalExceptionHandler.java
            HttpErrorMapping.java  PageResponse.java
      security/  UserContext.java  UserContextProvider.java
                 DevStubUserContextProvider.java  (stub dev -> SPEC-0024)
      i18n/  MessageSourceConfig.java
      observability/  CorrelationIdFilter.java
      time/  ClockConfig.java
      health/  DatabaseHealthProbe.java   (SELECT 1; readiness)
      openapi/ OpenApiConfig.java
  src/main/resources/
    application.yml                 (datasource, flyway, modulith strategy, actuator)
    messages.properties  messages_pt_BR.properties
    db/migration/V1__baseline.sql   (CREATE EXTENSION pgcrypto)
  src/test/java/...
    architecture/ArchitectureTest.java        (regras de camada/entidade/Impl/injeção)
    architecture/ArchitectureRulesHaveTeethTest.java  (planta violação -> rule falha)
    architecture/ModularityTests.java          (Spring Modulith verify)
    system/SystemHealthIntegrationTest.java    (Testcontainers Postgres -> /health UP)
    AbstractPostgresIntegrationTest.java       (container compartilhado)
  src/test/java/archfixture/                   (fixture de violação, fora de com.fksoft)
    domain/FaultyDomainType.java  infra/SomeInfraType.java
```

## Arquivos frontend (a criar) — [[DL-0003]]

```
frontend/  (Angular 22 standalone + signals + ngx-translate + ESLint)
  src/app/core/http/   api-base-url, correlation-id.interceptor, error.interceptor
  src/app/core/i18n/   translate config (pt-BR default, en)
  src/app/features/health/  health.service, health-page component (+ spec)
  proxy.conf.json      (/api -> http://localhost:8080)
  src/assets/i18n/     pt-BR.json, en.json
```

## Migrações

- `V1__baseline.sql`: `CREATE EXTENSION IF NOT EXISTS pgcrypto;` (idempotente).
  Tabelas de negócio só a partir da SPEC-0002.

## Contratos (OpenAPI)

- `GET /api/system/health` → 200 `{status, db}`; 503 quando o banco cai. Documentado
  via springdoc-openapi (`/v3/api-docs`, `/swagger-ui`).

## Testes (proporcionais)

- **Arquitetura (ArchUnit):** domain ⇏ application/infra; infra ⇏ application;
  sem `@Data`/`@Setter` em `@Entity`; sem `*Impl`; sem field `@Autowired`. +
  **teeth test** que planta `domain → infra` e exige violação.
- **Modulith:** `ApplicationModules.of(FkErpApplication.class).verify()`.
- **Integração (Testcontainers + Postgres):** `GET /api/system/health` → 200 `db:UP`.
- **Frontend:** componente de health (loading/erro/sucesso).
- **Smoke:** o próprio `/api/system/health` (liveness/readiness via actuator).

## Riscos arquiteturais

- Modulith tratando camadas como módulos → mitigado por `explicitly-annotated`
  ([[DL-0006]]).
- Atrito de peer-deps no Angular 22 → mitigado adiando PrimeNG/Tailwind ([[DL-0003]]).
- Build longo (download de deps + imagem Postgres) → esperado; rodar em background.

## Ordem de implementação (laço da fatia)

1. Git baseline + branches (feito). 2. Decision-log + plano + spec (feito/este).
3. Wrapper + scaffold backend. 4. **RED**: arch tests + integração health.
5. **GREEN**: kernel + infra + controller + Flyway. 6. `./mvnw verify` verde.
7. Frontend health + teste + lint + build. 8. docker-compose + Dockerfile + .env.
9. CI. 10. Event Storming. 11. Caderno de testes + release note + relatório.
12. Merge feature→develop, release/0.1.0→main+develop, tag `0.1.0`, push.

## Comandos de validação

```bash
cd backend && ./mvnw verify          # backend + ArchUnit + Modulith + Testcontainers
cd backend && ./mvnw spotless:apply  # format
cd frontend && npm run lint && npm test -- --watch=false && npm run build
docker-compose up --build            # app + db; GET /api/system/health -> UP
```

## Open Questions remanescentes

Nenhuma que bloqueie a Fase 0. As da SPEC-0001 foram resolvidas: pacote base
([[DL-0001]]) e stub de Identity (ADR 0011/0003). Perguntas de negócio (Q1–Q8,
fórmula de preço) pertencem à Fase 1+ e seguem em aberto nas specs donas.
