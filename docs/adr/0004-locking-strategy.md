# ADR 0004: Locking Strategy for Seat Reservations

## Status

Accepted

## Context

The critical race condition in this system is two users selecting the same
seat at the same time and both succeeding (double booking). The expected
concurrent volume is not yet known, so the design must work for low/medium
load and expose metrics for the team to detect when stronger control is
needed.

The transition `FREE → HELD` on `ScreeningSeat` is the only place where the
race matters. Other state transitions are caused by single owners
(`HELD → SOLD` by the reservation owner, `HELD → FREE` by the expiration job
or cancellation).

## Decision

Three defenses in depth:

1. **Database-level uniqueness**: `screening_seats` has a UNIQUE constraint on
   `(screening_id, seat_id)`. Any attempt to create a duplicate row fails at
   the database. (Note: this protects creation. The status update race is
   handled by the next two defenses.)

2. **Pessimistic row lock at the critical moment**: when the reservation
   service reserves seats, it loads the target `ScreeningSeat` rows with
   `SELECT ... FOR UPDATE` (Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`).
   Concurrent transactions targeting the same row serialize. After locking,
   the service checks the row is `FREE` before transitioning to `HELD`.
   Pessimism is scoped to this one transition only.

3. **Optimistic locking on `Reservation`**: `Reservation` carries `@Version`
   to protect against lost updates on its status transitions
   (`PENDING → AWAITING_PAYMENT`, `→ CONFIRMED`, `→ CANCELLED`, `→ EXPIRED`).

Observability: emit metrics for pessimistic lock wait time, optimistic lock
failures, and reservation-create p99 latency. Revision triggers documented
below.

## Consequences

Positive: double booking is structurally impossible. Lock contention is
narrow (only `FREE → HELD`). Other concurrent operations (catalog reads,
seat-map reads, expiration job, confirmation) suffer no extra locking cost.

Negative: under very high contention on hot screenings (e.g., blockbuster
premiere with thousands of users hitting the same row), pessimistic locking
can serialize reservations and increase p99. The team will see this in
metrics before it becomes a user-visible problem.

## Revision Triggers

Revisit this ADR if any of the following is observed in production:
- p99 of reservation-create endpoint exceeds 2 seconds for sustained periods;
- pessimistic-lock wait time p95 exceeds 500 ms;
- error rate on reservation-create exceeds 1% on hot screenings.

Likely next steps if triggered: introduce queued reservation, in-memory
seat-hold pre-check, or partition load with multiple application instances.

## Alternatives Considered

- **Optimistic locking everywhere**: rejected because the race is on row
  creation/update where retry semantics on conflict cost user-visible
  failures during contention.
- **Database serializable isolation**: rejected as too coarse — would
  serialize unrelated work.
- **In-memory distributed cache lock (Redis)**: rejected for v1 — introduces
  new infrastructure for a problem the database already solves.
