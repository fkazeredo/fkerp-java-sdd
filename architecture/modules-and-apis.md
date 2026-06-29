# Modules, Boundaries, APIs and Repositories Layout

> Read when: defining module boundaries, calling across modules, designing/changing
> endpoints or JSON contracts, considering microservices, BFF or repository split.

## Package layers (hexagonal / DDD — ADR 0012)

Three top-level layers under `com.fksoft`:

| Layer | Package | Contents |
|---|---|---|
| **Domain** (hexagon core) | `com.fksoft.domain.<module>` | Services, entities, repositories, domain events, enums, value/view records, **business exceptions**, module facades (ports). Plus the kernel `com.fksoft.domain.error` (`DomainException`, `ErrorDetails`, `RateLimited`). |
| **Delivery** (driving adapters) | `com.fksoft.application` | Only the entry mechanisms: `api` (REST controllers) + `api.dto` (request/response DTOs), `realtime` (WebSocket publishers) + `realtime.dto` (messages), `queue` (consumers) if any. |
| **Infra** (driven adapters) | `com.fksoft.infra.<concern>` | Email, integration, security (incl. `UserContext`/`UserContextProvider`), web (`ApiErrorResponse`, `GlobalExceptionHandler`, `HttpErrorMapping`, `PageResponse`), i18n, time, observability, socket config. |

**Dependency rule (ArchUnit-enforced):** `domain` is pure — it **MUST NOT** depend on
`application` or `infra`. `application` and `infra` may depend on `domain`; `application`
**MAY** depend on `infra` (delivery wires domain + infra). Entities never leave the domain:
the delivery layer is entity-free — services return view/response records, never `@Entity`
(the few Response DTOs that map an entity stay inside their domain module).

## Modules and bounded contexts

Modules are defined primarily by business domain: own language, rules, workflows, state
transitions, lifecycle, data ownership and reasons to change. Independent evolution is a
secondary criterion. Do not create modules only because a folder seems organized.

Cross-module rules (Spring Modulith / ArchUnit enforced):

- Synchronous collaboration through a public application-level API/facade only.
- A module **MUST NOT** depend on another **module's** repositories, internal entities,
  persistence details or implementation classes.
- Asynchronous reactions through domain events.
- Boundaries **SHOULD** preserve future microservice extraction.

Module ↔ infra (ADR 0010): a module exposes a **port** (interface in the module) for each
technical adapter; the implementation lives in `com.fksoft.infra.<concern>` and depends on
the module (infra → domain is allowed; domain → infra is forbidden). The centralized
`infra` layer **MAY** read/write a module's own persistence to operate that module's
technical adapter (e.g. the outbox dispatch worker, the mock payment gateway) — the
per-module persistence rule exempts `com.fksoft.infra`, but **other business modules are
still forbidden** from touching it.

Data ownership: in a monolith, a shared database is acceptable — do not pretend to be
distributed. Modules may read shared data for reports/projections; for commands, respect
business ownership. In microservices, each service owns its data; never write into another
service's database.

Extract a microservice only with a clear bounded context plus a concrete reason: independent
deployment/scalability, isolated load, separate team ownership, different runtime, fault or
security isolation. Microservices do not fix bad modularity — they distribute it.

## API design

APIs are external contracts, not accidental exposure of entities or framework structures.
Every endpoint defines: purpose, URL, method, request/response bodies, status codes, error
codes, validation behavior, pagination/filtering/sorting, authn/authz expectations,
versioning and compatibility expectations.

- **MUST NOT** expose JPA entities; use stable DTOs.
- Pragmatic REST: domain action endpoints are fine (`POST /orders/{id}/cancel`).
- JSON is part of the contract: never casually change field names/types, enum values, date
  formats, nullability, structure, pagination, error format or status codes.
- Enums exposed in APIs **SHOULD** have explicit external values; invalid values produce
  clear validation errors. Date/time values use ISO-8601.

Versioning: prefer backward-compatible changes; breaking change => new version + deprecation
period, documented. Explicit versioning when external systems, multiple frontends, mobile or
partners depend on the API.

OpenAPI **MUST** document relevant APIs and **MUST** be updated when contracts change.
Code-first acceptable for internal APIs; contract-first when external consumers or generated
clients depend on it.

Beyond REST: GraphQL only for real data-composition needs; gRPC for service-to-service with
strong contracts/low latency; webhooks are serious external contracts (signature, retries,
idempotency, logs, versioning, docs). Do not introduce them without a justifying use case.

## BFF and API Gateway

BFF **MUST NOT** be introduced by default; consider only for web/mobile contract divergence,
heavy screen-specific aggregation, legacy APIs, token isolation or migration. A BFF **MUST
NOT** own business rules. API Gateway is infrastructure (routing, TLS, rate limiting, CORS,
observability) — never a hidden business layer.

## Monorepo vs multiple repositories

Prefer monorepo for cohesive products:

```txt
project-root
  backend/  frontend/  docs/specs  docs/adr  infra/
  docker-compose.yml  README.md  CLAUDE.md  architecture/
```

If it changes together, keep it together. Split only for real organizational, deployment,
security, lifecycle or ownership reasons.
