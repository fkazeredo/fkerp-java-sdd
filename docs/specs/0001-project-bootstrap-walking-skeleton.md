# 0001 - Esqueleto do projeto (walking skeleton) + Event Storming

Status: Approved
Related ADRs: 0010, 0011, 0012, 0013, 0014

## Goal

Entregar um monólito modular que **compila, sobe, tem testes verdes e CI**, provando a stack de
ponta a ponta (HTTP → domínio → banco → resposta), com as regras de arquitetura já travadas por
ArchUnit/Spring Modulith, pronto para receber a primeira fatia de negócio (SPEC-0002).

## Scope

**Em escopo**
- `backend/` Spring Boot (Maven wrapper `mvnw`), pacote `com.fksoft` com as três camadas
  `domain` / `application` / `infra` (ADR 0012).
- Postgres via `docker-compose`; Flyway baseline (`V1__baseline.sql`).
- Camada técnica mínima: `infra.web` (`ApiErrorResponse`, `GlobalExceptionHandler`,
  `HttpErrorMapping`, `PageResponse`); `infra.security` (`UserContext` + porta
  `UserContextProvider` + adapter **stub de dev** que devolve um usuário fixo — marcado como stub
  até a spec de Identity); `infra.i18n` (`MessageSource` + `messages.properties` +
  `messages_pt_BR.properties`); `infra.observability` (filtro de correlation id, logs estruturados).
- `domain.error` (kernel): `DomainException`, `ErrorDetails`, `RateLimited`.
- `application.api`: `SystemController` com `GET /api/system/health` (readiness real, checa o banco).
- `frontend/` Angular (`core/ shared/ features/`), `core/http` com base URL + interceptors +
  correlation id, e **uma tela** que chama `/api/system/health` e exibe o status (prova ponta a
  ponta no front).
- CI mínimo: build backend (`mvnw verify`), build/test frontend, Spotless/Checkstyle, `flyway validate`.
- **Event Storming** da "venda Portal de Experiências ponta a ponta" em `docs/event-storming.md`
  (artefato da Fatia 0 do redesenho: onde a linguagem muda, há fronteira).

**Fora de escopo:** qualquer regra de negócio; autenticação real; qualquer módulo de negócio
(eles começam na SPEC-0002).

## Business Context

Fundação técnica. É a única fatia sem valor de negócio: existe para provar a stack e **fixar as
regras de arquitetura antes do primeiro código de domínio**. Atores: a equipe de engenharia (e o
Claude Code como implementador).

## Convenções do projeto (herdadas por todas as specs seguintes)

```txt
Dinheiro          Money = value object { amount: BigDecimal scale 2, currency: ISO-4217 }
Taxa de câmbio    BigDecimal scale 6, sempre > 0
Arredondamento    HALF_UP em conversões e cálculos monetários
Instantes         Instant/OffsetDateTime em UTC; LocalDate p/ datas civis; ISO-8601 nas APIs
Erros             DomainException com code estável == chave i18n; sem HttpStatus no domínio (ADR 0011)
Auditoria         createdAt, updatedAt, createdBy, updatedBy em entidades relevantes (delivery.md)
Concorrência      @Version (optimistic) em entidades mutáveis sob risco
Paginação         PageResponse (infra.web); page/size, default sort e max page size definidos por endpoint
```

## Business Rules

```txt
O endpoint GET /api/system/health MUST checar a conectividade com o banco e refletir no readiness.
O domain MUST NOT depender de application nem de infra (ArchUnit reprova).
Entidades MUST NOT usar @Data nem @Setter (ArchUnit reprova); mudam por métodos de negócio.
Nenhuma classe *Impl para serviços internos; injeção só por construtor.
```

Decisões assumidas em modo autônomo (antes eram Open Questions):

- **ASSUMIDO (ver DL-0001):** o pacote base é mantido como **`com.fksoft`** (do template).
- **ASSUMIDO (ver ADR 0011 + DL-0003/0006):** `UserContextProvider` é um **stub de dev**
  (`DevStubUserContextProvider`, usuário fixo) até a spec de Identity (SPEC-0024); a Identity
  real (login/papéis) fica adiada.
- **ASSUMIDO (ver DL-0006):** Spring Modulith roda com `detection-strategy=explicitly-annotated`
  (as camadas `domain/application/infra` não são módulos; o gate de camadas é do ArchUnit).

## Input/Output Examples

```http
GET /api/system/health
200 OK
{ "status": "UP", "db": "UP" }
```

```http
# Exemplo de formato de erro padrão (vale para todas as specs)
{ "code": "some.error.code", "message": "mensagem i18n", "fields": [] }
```

## API Contracts

- `GET /api/system/health` — sem autenticação (readiness). 200 com `{status, db}`; 503 quando o
  banco está indisponível. Documentado na OpenAPI.

## Events

Not applicable. (Eventos de negócio começam na SPEC-0002.)

## Persistence Changes

- `V1__baseline.sql`: estabelece o schema base (extensões necessárias, ex. `pgcrypto` se uuid for
  gerado no banco). Tabelas de negócio vêm nas fatias seguintes.

## Validation Rules

- Delivery: `GlobalExceptionHandler` já ativo, mesmo sem exceções de negócio ainda; formato
  `{code, message, fields}`.
- O readiness valida a conexão com o banco antes de responder `UP`.

## Error Behavior

- Toda resposta de erro passa pelo `GlobalExceptionHandler` + `HttpErrorMapping` (tipo de exceção →
  status; teste de build garante completude do mapa — ADR 0011).

## Observability Requirements

- Correlation id em toda request (header propagado e logado).
- Logs estruturados; nunca logar segredos.
- Health distingue liveness/readiness (`observability.md`).

## Tests Required

- **ArchUnit:** regra de dependência (`domain` ⇏ `application`/`infra`), proibição de
  `@Data`/`@Setter` em entidade, proibição de `*Impl`, injeção por construtor. Inclui um **teste
  negativo documentado** que falharia se alguém fizesse `domain → infra`.
- **Spring Modulith** `verify()` no suite normal.
- **Integração (Testcontainers + Postgres):** `GET /api/system/health` → 200 com `db: UP`, provando
  conectividade real com o banco.
- **Frontend:** teste do componente de health (estado loading/erro/sucesso).

## Acceptance Criteria

- `cd backend && ./mvnw verify` verde com Docker no ar (inclui ArchUnit e Modulith).
- `docker-compose up` sobe app + banco; `GET /api/system/health` responde `UP`.
- A tela Angular mostra o health OK (e o estado de erro quando o backend está fora).
- CI mínimo verde (build/testes back e front, lint, `flyway validate`).
- Existe `docs/event-storming.md` com o fluxo da venda Portal de Experiências e as fronteiras
  identificadas.

## Open Questions

Resolvidas em modo autônomo (ver Business Rules e `docs/decision-log/`):

- ~~Pacote base `com.fksoft` — manter ou renomear?~~ → ASSUMIDO manter (DL-0001).
- ~~Identity real / stub de `UserContextProvider`?~~ → ASSUMIDO stub de dev (ADR 0011, DL-0003).

Nota: o ADR 0014 (conjunto inicial de módulos e ordem) foi **criado pelo dono** —
`docs/adr/0014-initial-modules-and-slice-order.md` (ver DL-0005, supersedida). Ele documenta o
destino dos módulos sem criar módulos vazios (os módulos nascem fatia a fatia na Fase 1).

## Out of Scope

Qualquer regra de negócio, autenticação real e qualquer módulo de negócio. A primeira capacidade de
negócio é a SPEC-0002 (Accounts).
