# ADR 0009: Realtime Publishing Scoped to the Booking Module

## Status

**Superseded by ADR 0012.** The decision that realtime publishing belongs with the events it
reacts to (not in a generic module) still holds, but the publishers are now part of the
**delivery layer** at `com.fksoft.application.realtime` (driving adapters), not inside the
booking module. They remain `@TransactionalEventListener(AFTER_COMMIT)` consumers of the
booking domain events; transport config stays in `com.fksoft.infra.socket`. The historical
decision below referenced `com.fksoft.application.booking.realtime`.

Accepted (original)

## Context

The system has two concerns that both touch WebSocket/STOMP:

- **Transport configuration**: the `/ws` endpoint, broker prefixes
  (`/topic`, `/queue`, `/app`), authentication on CONNECT frame, message
  converters. This is generic infrastructure.
- **Business publishing**: when seats change status (HELD, SOLD, FREE) and
  when a reservation's payment confirms, the appropriate clients must be
  notified on the right topic.

It is tempting to create a top-level `realtime` module that owns both
concerns. This concentrates WebSocket knowledge but couples booking business
events to a module that does not own those events.

## Decision

- Transport configuration lives in `com.fksoft.infra.socket`
  (`WebSocketConfig`, channel interceptor for JWT auth on CONNECT). This is
  shared infrastructure, owned by no business module.
- Business publishing lives **inside the module that owns the business
  event**. In v1 that is the `booking` module:
  `com.fksoft.application.booking.realtime.SeatUpdatePublisher`,
  `ReservationStatusPublisher`.
- Publishing happens **only after transaction commit** via Spring's
  `@TransactionalEventListener(phase = AFTER_COMMIT)`. Booking services
  publish a domain event inside the transaction; the listener picks it up
  after commit and sends the STOMP message. This prevents the user from
  seeing a `HELD` status that the database rolled back.
- A top-level `realtime` module is **not** created.

## Consequences

Positive: each module owns its events end-to-end. No "realtime" module
becomes a dumping ground. Boundary rule "modules do not depend on each
other's internals" is preserved (booking depends on the generic transport
config, not on another module's realtime code). After-commit semantics are
explicit and consistent.

Negative: when a future module also needs to publish realtime updates (e.g.,
a chat module), it will own its own publisher rather than reusing one. This
is intentional — the cost of a small extra publisher class is lower than the
cost of cross-module coupling.

## Alternatives Considered

- **Top-level `realtime` module owning all publishers**: rejected because it
  would import booking entities and events, creating an inverted dependency
  (realtime knowing about booking instead of booking owning its publishing).
- **Publish from inside the transaction**: rejected. A rollback after the
  message goes out would deceive clients. After-commit is mandatory.
