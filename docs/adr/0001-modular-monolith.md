# ADR 0001: Modular Monolith with Spring Modulith

## Status

Accepted

## Context

The system has clear bounded contexts (authentication, cinema, screening, pricing,
booking, payment, notification) but operates as a single product with a single
team. Splitting into microservices upfront would distribute complexity without
solving a real problem (single deployment target, single database, no
independent scalability requirement yet).

The team needs strong module boundaries to keep the codebase maintainable and
to preserve the option of extracting microservices later if scalability,
ownership or fault isolation demand it.

## Decision

Build the backend as a single Maven project, single Spring Boot application,
single Postgres database, with internal modules defined by business domain:

```
com.fksoft.domain       <- DOMAIN core, one package per module (ADR 0012)
  auth            users, roles, JWT, login
  cinema          rooms, seats with types
  screening       movies, screenings
  pricing         price catalog and calculation
  booking         reservation, screening_seats, tickets, expiration
  payment         mock gateway port, webhook handler, payment requests
  notification    email port and SMTP/transactional implementations
  error           kernel: DomainException, ErrorDetails, RateLimited
com.fksoft.application  <- DELIVERY (driving adapters): api, api.dto, realtime, realtime.dto
com.fksoft.infra        <- centralized technical layer, by concern (ADR 0010)
  security (UserContext) email integration time i18n socket observability web persistence
```

> Updated by **ADR 0010**: technical adapters (SMTP/outbox, JWT encoder, mock
> payment gateway, STOMP, correlation filter) are centralized under
> `com.fksoft.infra.<concern>`, implementing module-owned ports.
>
> Updated by **ADR 0012**: the business modules moved to `com.fksoft.domain.<module>`;
> `com.fksoft.application` now holds only the delivery layer (REST/realtime); the `shared`
> kernel was deleted (error kernel → `domain.error`, identity + `PageResponse` → `infra`).
> The domain never depends on `application` or `infra`; both may depend on the domain, and
> delivery may depend on infra. Spring Modulith modules are now `domain.auth` … `domain.screening`.

Enforce boundaries with **Spring Modulith** (`ModularityTests.verifiesModularStructure()`)
and **ArchUnit** rules. Cross-module synchronous calls go through public
application-level APIs only; cross-module asynchronous reactions go through
domain events.

## Consequences

Positive: simple deployment, simple debugging, single transaction across the
module if business demands, fast iteration. Boundary violations fail the build
deterministically.

Negative: a single module's bad code can affect the whole application's startup
time. Build time grows with codebase size.

## Alternatives Considered

- **Microservices from day one.** Rejected: no concrete scalability,
  organizational or fault-isolation reason. Would multiply infrastructure cost
  and operational complexity without business return.
- **Plain monolith without enforced module boundaries.** Rejected: experience
  shows boundaries silently erode without tooling. Modulith + ArchUnit make
  enforcement deterministic.
