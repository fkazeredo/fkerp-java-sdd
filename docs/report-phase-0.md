# Relatório — Fase 0 (Fundação / Walking Skeleton)

- **Data:** 2026-06-29 · **Spec:** SPEC-0001 · **Tag:** `0.1.0` · **Branch da fatia:**
  `feature/slice-0-walking-skeleton` → `develop` → `release/0.1.0` → `main`.

## 1. Fatias entregues

| Fatia | Spec | Entregável | Status |
|---|---|---|---|
| Slice 0 — Walking Skeleton + Event Storming | SPEC-0001 | Monólito modular que sobe/testa/CI + portões + tela de health + Event Storming | ✅ verde |

A Fase 0 tem uma única fatia (a fundação). Não há fase anterior pendente (o repositório só tinha
documentação ao iniciar).

## 2. Arquivos criados/alterados (resumo)

**Backend** (`backend/`): `pom.xml`, `mvnw`/`mvnw.cmd`/`.mvn/` (wrapper + `jvm.config`),
`lombok.config`, `config/checkstyle/checkstyle.xml`, `Dockerfile`, `.dockerignore`;
`com.fksoft.FkErpApplication`; `domain/error/{DomainException,ErrorDetails,RateLimited}`;
`infra/web/{ApiErrorResponse,GlobalExceptionHandler,HttpErrorMapping,PageResponse}`;
`infra/security/{UserContext,UserContextProvider,DevStubUserContextProvider}`;
`infra/i18n/MessageSourceConfig`, `infra/observability/CorrelationIdFilter`,
`infra/time/ClockConfig`, `infra/health/DatabaseHealthProbe`, `infra/openapi/OpenApiConfig`;
`application/api/SystemController` + `dto/SystemHealthResponse`;
`resources/{application.yml, messages*.properties, db/migration/V1__baseline.sql}`;
testes: `architecture/{ArchitectureTest,ArchitectureRulesHaveTeethTest,ModularityTests,
HttpErrorMappingCompletenessTest}`, `infra/web/GlobalExceptionHandlerTest`,
`system/{AbstractPostgresIntegrationTest,SystemHealthIntegrationTest}`,
`ExplicitlyAnnotatedModuleDetection` + `test/resources/META-INF/spring.factories`,
fixture `archfixture/{domain,infra}`.

**Frontend** (`frontend/`): Angular 22 (`ng new`) + `core/config/api`, `core/http/{api-error,
correlation-id.interceptor,error.interceptor}`, `core/i18n/{translations,in-memory-translate.loader}`,
`features/health/{health.models,health.service,health-page.ts/html/scss,health-page.spec}`,
`app.{config,routes,html,ts,spec}`, `proxy.conf.json`, `eslint.config.js`.

**Raiz / infra:** `.gitignore`, `.gitattributes`, `docker-compose.yml`, `.env.example`,
`.github/workflows/ci.yml`.

**Docs:** `docs/event-storming.md`, `docs/plan/phase-0-foundation.md`, `docs/decision-log/*`,
`docs/test-report/*`, `docs/release-notes/{_TEMPLATE,0.1.0}.md`, `docs/ROADMAP-STATUS.md`,
`docs/report-phase-0.md` (este).

## 3. Specs / ADRs atualizados

- **SPEC-0001:** Open Questions → Business Rules (ASSUMIDO): pacote base `com.fksoft` (DL-0001),
  stub de Identity (ADR 0011), Modulith `explicitly-annotated` (DL-0006); nota sobre o ADR 0014.
- **ADR 0014** (conjunto inicial de módulos e ordem): **criado pelo dono** durante a fase
  (`docs/adr/0014-initial-modules-and-slice-order.md`); DL-0005 superseded.

## 4. Migrações

- `V1__baseline.sql` (`CREATE EXTENSION IF NOT EXISTS pgcrypto;`). Aplicada e validada via
  Testcontainers no `verify` e via `flyway migrate/validate` na stack/CI. Idempotente.

## 5. Testes por tipo e resultado

| Tipo | Casos | Resultado |
|---|---|---|
| Arquitetura (ArchUnit) + teeth | 7 | ✅ |
| Spring Modulith verify | 1 | ✅ |
| Completude de erros (ADR 0011) | 1 | ✅ |
| Handler de erros (MockMvc) | 2 | ✅ |
| Integração (Testcontainers/Postgres) | 1 | ✅ |
| Frontend (Vitest) | 4 | ✅ |
| Smoke (docker-compose + curl) | 1 | ✅ 200 `{status:UP,db:UP}` |

**Saída resumida do `./mvnw verify`:**

```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
spotless:check — keeping 28 files clean (0 needs changes)
checkstyle:check — You have 0 Checkstyle violations.
BUILD SUCCESS
```

**Frontend:** `ng build` OK · `ng lint` "All files pass linting" · Vitest `Tests 4 passed (4)`.

Detalhe completo em `docs/test-report/slice-0-walking-skeleton.md`.

## 6. Impacto em OpenAPI

- Novo: `GET /api/system/health` (200 `{status,db}`; 503 quando o banco cai). Exposto via springdoc
  (`/v3/api-docs`, `/swagger-ui.html`). Sem contratos de negócio ainda.

## 7. Decisões (decision-log)

> Destaques: **DL-0001** tem **Reversibilidade=Cara**. Nenhuma decisão ficou com Confiança=Baixa.

- [DL-0001](decision-log/DL-0001-pacote-base-com-fksoft.md) — pacote base `com.fksoft` ·
  Conf. **Alta** · Rev. **Cara** ⚠️
- [DL-0002](decision-log/DL-0002-stack-versoes-backend.md) — versões do stack backend ·
  Alta · Moderada
- [DL-0003](decision-log/DL-0003-stack-frontend-fase-0.md) — stack frontend; PrimeNG/Tailwind
  adiados · Alta · Barata
- [DL-0004](decision-log/DL-0004-maven-wrapper-bootstrap.md) — bootstrap do Maven Wrapper ·
  Alta · Barata
- [DL-0005](decision-log/DL-0005-adr-0014-ausente-adiar-fase-1.md) — ADR 0014 (superseded; criado
  pelo dono) · Alta · Barata
- [DL-0006](decision-log/DL-0006-modulith-detection-strategy.md) — Modulith `explicitly-annotated`
  via SPI · Alta · Barata

## 8. Riscos

- **DL-0001 (Rev. Cara):** renomear o pacote base depois fica caro conforme o código cresce — se for
  mudar, mudar cedo.
- **Cobertura adiada:** handler de validação, caminho 503 do health e testes unitários dos
  interceptors do front (ver caderno de testes). Baixo risco; cobertos nas próximas fatias.
- **Boot 3.5 vs 4.x:** linha 3.5 escolhida por estabilidade; upgrade futuro é um ADR próprio.

## 9. O que ficou para a próxima fase

- **Fase 1 — Núcleo comercial manual** (ADR 0014): SPEC-0002 Accounts → SPEC-0003 Exchange →
  SPEC-0004 Commissioning → SPEC-0005 Quoting (keystone) → SPEC-0006 Booking → SPEC-0007
  Reconciliation. Primeiro evento de negócio: `AccountRegistered`.
- **Frontend:** adicionar **PrimeNG + Tailwind** na primeira tela real (Accounts) — DL-0003.
- **Open Questions de negócio** (fórmula de preço, Q4/Q5, merchant, REP) seguem nas specs donas até
  serem decididas pelo dono.
