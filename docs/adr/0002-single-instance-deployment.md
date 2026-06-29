# ADR 0002: Single Instance Deployment for v1

## Status

Accepted

## Context

The product owner accepts single-instance deployment for v1 (Docker container
on a single host). The expected volume of concurrent users is not yet
estimated. Multi-instance deployment would require: distributed lock for the
expiration job (ShedLock), sticky sessions or external broker for WebSocket,
shared cache.

## Decision

For v1, the backend deploys as a single Docker container per environment. This
allows:

- `@Scheduled` for the reservation expiration job without distributed locking.
- Spring's in-memory simple broker (`/topic`, `/queue`) for STOMP WebSocket
  without external broker (RabbitMQ/ActiveMQ).
- In-memory rate limiting and caches when needed.

Migration to multi-instance becomes a future ADR triggered by measured load
(p99 latency above target, CPU saturation, throughput ceiling).

## Consequences

Positive: deployment is trivial, no operational overhead from broker or
distributed lock infrastructure, no sticky session complexity.

Negative: zero-downtime deployment is harder (rolling deploy is impossible
with one instance). Downtime windows must be planned. Single point of
failure for availability.

## Alternatives Considered

- **Multi-instance from day one.** Rejected: introduces ShedLock, external
  STOMP broker (RabbitMQ recommended), session stickiness or shared session
  storage. Premature without measured need.
- **Serverless / autoscaling.** Rejected: same complexity as multi-instance
  plus cold start latency on WebSocket-heavy workload.
