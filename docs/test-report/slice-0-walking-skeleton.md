# Caderno de testes — Slice 0 (Walking Skeleton) · SPEC-0001

## Escopo

Spec: **SPEC-0001** (fundação / walking skeleton + Event Storming). Acceptance Criteria cobertos:

- AC1: `cd backend && ./mvnw verify` verde com Docker no ar (inclui ArchUnit e Modulith).
- AC2: `docker-compose up` sobe app + banco; `GET /api/system/health` responde `UP`.
- AC3: a tela Angular mostra o health OK (e o estado de erro quando o backend está fora).
- AC4: CI mínimo verde (build/testes back e front, lint, `flyway validate`).
- AC5: existe `docs/event-storming.md` com o fluxo da venda Portal de Experiências.

## Casos de teste

### Unitários / Arquitetura (backend — `src/test/java`)

| Caso | O que verifica | AC / regra |
|---|---|---|
| `ArchitectureTest.DOMAIN_MUST_NOT_DEPEND_ON_DELIVERY_OR_INFRA` | domínio não importa application/infra | AC1 · ADR 0012 |
| `ArchitectureTest.INFRA_MUST_NOT_DEPEND_ON_DELIVERY` | infra não importa application | AC1 · ADR 0012 |
| `ArchitectureTest.ENTITIES_MUST_NOT_EXPOSE_SETTERS` | `@Entity` sem setters JavaBean | AC1 · ADR 0013 |
| `ArchitectureTest.ENTITIES_MUST_NOT_USE_LOMBOK_DATA_OR_SETTER` | `@Entity` sem `@Data`/`@Setter` (via `@lombok.Generated`) | AC1 · ADR 0013 |
| `ArchitectureTest.NO_IMPL_SUFFIX` | sem classes `*Impl` | AC1 · CLAUDE.md |
| `ArchitectureTest.CONSTRUCTOR_INJECTION_ONLY` | sem field `@Autowired` | AC1 · backend.md |
| `ArchitectureRulesHaveTeethTest.domainRuleFailsWhenDomainDependsOnInfra` | **planta** `domain→infra` e exige que a regra **falhe** (gate tem dentes) | AC1 · SPEC-0001 "teste negativo" |
| `ModularityTests.verifiesModularStructure` | `ApplicationModules.of(...).verify()` (explicitly-annotated) | AC1 · ADR 0001/0012 |
| `HttpErrorMappingCompletenessTest.everyConcreteDomainExceptionHasAnHttpStatusMapping` | todo `DomainException` concreto tem status mapeado | ADR 0011 |
| `GlobalExceptionHandlerTest.mapsDomainExceptionToUnprocessableEntityWithStableBody` | `DomainException` → 422 + `{code,message,fields}` (fallback msg = code) | ADR 0011 |
| `GlobalExceptionHandlerTest.mapsUnexpectedExceptionToInternalServerError` | exceção inesperada → 500 `error.internal`, sem vazar interno | ADR 0011 · security.md |

### Integração (backend — Testcontainers + Postgres real)

| Caso | O que verifica | AC |
|---|---|---|
| `SystemHealthIntegrationTest.healthReturnsUpWhenDatabaseIsReachable` | sobe o contexto, Flyway aplica `V1`, `GET /api/system/health` → 200 `{status:UP, db:UP}` (HTTP→domínio→banco→resposta) | AC1, AC2 |

### Unitários (frontend — Vitest + jsdom)

| Caso | O que verifica | AC |
|---|---|---|
| `HealthPage › shows the success state when the backend reports UP` | estado `success` + dados (status/db = UP) | AC3 |
| `HealthPage › shows the error state with the error code when the request fails` | estado `error` + `errorCode` | AC3 |
| `HealthPage › starts in the loading state until the response resolves` | estado `loading` antes da resposta | AC3 |
| `App › creates the app shell` | bootstrap do shell (router-outlet) | AC3 |

### Smoke / E2E (stack real via docker-compose)

| Caso | O que verifica | AC |
|---|---|---|
| `docker compose up --build` + `curl /api/system/health` | app + banco sobem; readiness real | AC2 |
| header `X-Correlation-Id` na resposta | correlation id ativo (observabilidade) | observability.md |

## Resultado

- **Backend `./mvnw verify`:** ✅ `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.
  `Spotless.Java ... keeping 28 files clean` (0 needs changes). `You have 0 Checkstyle violations`.
  `BUILD SUCCESS`.
- **Frontend:** ✅ `ng build` → bundle gerado; `ng lint` → "All files pass linting";
  `ng test --watch=false` (Vitest) → `Test Files 2 passed (2)`, `Tests 4 passed (4)`.
- **Smoke (docker-compose):** ✅ `GET /api/system/health` → `HTTP/1.1 200` com
  `{"status":"UP","db":"UP"}` e header `X-Correlation-Id`. Stack derrubada com `docker compose down -v`.
- **CI:** workflow `.github/workflows/ci.yml` criado (3 jobs); roda em push/PR. Localmente cada
  passo equivalente foi executado e passou (verify, lint, test, build, migrate+validate via stack).

## Cobertura — o que NÃO está coberto (e por quê)

- **Handler de validação** (`MethodArgumentNotValidException`): implementado, mas sem endpoint com
  validação na Fase 0 para acioná-lo num teste. Será coberto na SPEC-0002 (Accounts tem validação).
- **Caminho 503 do health** (banco fora): a lógica (`DatabaseHealthProbe` → `DOWN` → 503) existe;
  não há teste de integração derrubando o banco no meio (custo desproporcional na Fase 0). O estado
  de erro equivalente no front é testado (`HealthPage` error).
- **Interceptors HTTP (frontend)**: exercitados em runtime (o `X-Correlation-Id` foi confirmado no
  smoke); sem teste unitário dedicado nesta fatia. Coberto indiretamente pelo fluxo do `HealthPage`.
- **E2E de navegador (Playwright)**: não incluído; a jornada crítica da fase (round-trip de health)
  é coberta pelos testes de componente + o smoke real via docker-compose. SPEC-0001 não exige E2E de
  browser na fundação.

## Como reproduzir

```bash
# Backend (precisa de Docker no ar p/ Testcontainers)
cd backend && ./mvnw verify
cd backend && ./mvnw spotless:apply   # formatar antes, se necessário

# Frontend
cd frontend && npm ci
cd frontend && npx ng lint
cd frontend && npx ng test --watch=false
cd frontend && npx ng build

# Stack completa (smoke)
docker compose up --build -d
curl -i http://localhost:8080/api/system/health   # -> 200 {"status":"UP","db":"UP"}
docker compose down -v
```
