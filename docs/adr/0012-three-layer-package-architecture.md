# ADR 0012: Three-layer package architecture (domain / application / infra)

## Status

Accepted (owner decision; supersedes ADR 0009, refines ADR 0001 and ADR 0010)

## Context

The seven business modules lived under `com.fksoft.application.<module>`, mixing the domain
core (services, entities, events, exceptions) with their delivery mechanisms (`api`, `realtime`)
in the same package. A `com.fksoft.shared` package held cross-cutting kernel types
(`DomainException`/`ErrorDetails`/`RateLimited`, `UserContext`/`UserContextProvider`,
`PageResponse`). The owner wants the layers explicit, the `shared` package gone, and a single
clear dependency rule — while staying hexagonal and DDD.

## Decision

Three top-level layers under `com.fksoft`:

- **`com.fksoft.domain`** — the pure hexagon core. One package per module (`auth`, `booking`,
  `cinema`, `notification`, `payment`, `pricing`, `screening`) holding services, entities,
  repositories, domain events, enums, value/view records, **business exceptions**, and the
  public module facades (ports). Plus the kernel `com.fksoft.domain.error`
  (`DomainException`, `ErrorDetails`, `RateLimited`). The `@ApplicationModule` annotations
  (Spring Modulith, `detection-strategy=explicitly-annotated`) move here, so the modules are
  now `domain.auth` … `domain.screening`.
- **`com.fksoft.application`** — the **delivery** layer (driving adapters): only the entry
  mechanisms. `api` (REST controllers) + `api.dto` (request/response DTOs); `realtime`
  (WebSocket publishers) + `realtime.dto` (messages); `queue` (consumers) if any.
- **`com.fksoft.infra`** — the **driven adapters** + framework config, by concern (ADR 0010).
  Now also hosts identity (`UserContext` + `UserContextProvider` port + adapter) in
  `infra.security` and the web/presentation types (`ApiErrorResponse`, `GlobalExceptionHandler`,
  `HttpErrorMapping`, `PageResponse`) in `infra.web`.

**Dependency rule (ArchUnit-enforced):** `domain` **MUST NOT** depend on `application` or
`infra`. `application` and `infra` may depend on `domain`; `application` **MAY** depend on
`infra` (the delivery layer wires domain + infra). `infra` **MUST NOT** depend on `application`.
This replaces the old `applicationMustNotDependOnGlobalInfra`, `coreMustNotDependOnApiLayer` and
`coreMustNotDependOnAdapters` rules with `domainMustNotDependOnDeliveryOrInfra` and
`infraMustNotDependOnDelivery`.

**Delivery is entity-free.** Because controllers/DTOs now sit *outside* the modules, having them
touch a module's `@Entity` would break the `otherModulesMustNotTouch<X>Persistence` rules. So the
services for those endpoints return the domain **Response** record instead of the entity
(mapping internally); the few `Response` DTOs that map an entity (`MovieResponse`,
`ScreeningResponse`, `AdminUserResponse`, `UserSummaryResponse`, `VerifyEmailResponse`,
`SeatTypeSurchargeResponse`, `WeekdayMultiplierResponse`) **stay inside their domain module**
(the "hybrid" option chosen by the owner). All other request/response DTOs are entity-free and
live in `application.api.dto`.

The `com.fksoft.shared` package is **deleted**: its error kernel went to `domain.error` (the
domain depends on it, so it cannot be in infra without breaking `domain ↛ infra`); identity went
to `infra.security`; `PageResponse` to `infra.web`.

## Consequences

Positive: the dependency direction is one clear rule (`domain` is the only protected layer);
delivery, domain and adapters are physically separated; the domain never sees HTTP, transport
DTOs or entities crossing its boundary. Modules remain Spring-Modulith-verified (now `domain.*`);
`shared` no longer exists as an ambiguous middle ground.

Negative: large one-time move (~270 files) and the entity-coupled `Response` DTOs sit in the
domain rather than alongside the other DTOs, so the DTO layout is slightly split (entity-free in
`application.api.dto`, entity-mapping in `domain.<module>`). `application → infra` is now allowed,
a deliberate relaxation: the delivery layer may reference infra contracts (`PageResponse`, the
`UserContextProvider` port).

## Alternatives Considered

- **Move everything from `shared` into `infra`** (the owner's first instinct): impossible for the
  error kernel — the domain extends `DomainException`, so it cannot live in infra without
  violating `domain ↛ infra`. Resolved by the split above.
- **Exempt the delivery layer from the persistence rules** (let controllers/DTOs map entities):
  rejected by the owner in favor of keeping the entity-coupled DTOs inside the domain (hybrid).
- **Keep modules under `application` with delivery sub-packages**: rejected — the owner wants the
  layers explicit and `application` to mean delivery only.
