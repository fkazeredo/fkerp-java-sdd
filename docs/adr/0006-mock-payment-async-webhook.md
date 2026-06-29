# ADR 0006: Mock Payment Gateway with Async Webhook

## Status

Accepted

## Context

The product requires payment but the real gateway (Stripe, PagSeguro,
Mercado Pago, etc.) has not yet been selected. We need a working payment
flow that exercises the eventual integration's semantics (async confirmation
via webhook, idempotency, retries, signature validation) so that swapping
to a real provider later is a configuration change, not an architecture
change.

## Decision

Define a `PaymentGateway` port in the `payment` module:

```java
PaymentRequestResult request(PaymentRequest request);
```

Returns immediately with a `paymentId` and status `PENDING`. The gateway
implementation is responsible for delivering a final status (`SUCCEEDED` or
`FAILED`) asynchronously via webhook.

Implement two adapters:

- **`MockPaymentGateway`** (default in dev/test/staging): persists a
  `MockPaymentJob` in the database, scheduled to be processed after a
  configurable delay (default 3 seconds, configurable per request for tests).
  When the delay elapses, the mock POSTs a signed webhook to
  `POST /api/webhooks/payments/mock` with the chosen outcome. Outcome is
  configurable via request metadata (default `SUCCEEDED`; on staging there is
  a deterministic rule to occasionally fail to exercise the failure path).
- **(Future) Real gateway adapter** (e.g., `StripePaymentGateway`). Out of
  scope of this ADR.

Webhook handler validates HMAC-SHA256 signature using a secret shared with
the gateway (env var), processes idempotently keyed on `paymentId +
eventType`, and transitions the related `Reservation` (`AWAITING_PAYMENT →
CONFIRMED` on success; `AWAITING_PAYMENT → CANCELLED` on failure, with
held seats released to `FREE`).

The mock signs webhooks with the same scheme the real gateway will use, so
the signature verification code is not specific to the mock.

## Consequences

Positive: production-shaped flow today (async, webhook, idempotency, signature
verification, failure path). Switching to a real gateway requires adding a
new adapter — no domain change, no UI change beyond the polling/WebSocket
states which already exist.

Negative: mock implementation has nontrivial complexity (scheduled job to
deliver webhook). Worth it because the alternative — synchronous mock that
returns success immediately — produces a fundamentally different flow that
would have to be rewritten when the real gateway lands.

## Alternatives Considered

- **Synchronous mock**: rejected. The 0008 flow would assume sync confirmation
  and require rewriting when the real gateway (async) lands. We would also
  miss the WebSocket notification for late confirmation, which is a real UX
  requirement.
- **No abstraction, mock embedded in confirmation service**: rejected.
  Violates anti-corruption layer principle (`messaging-and-integrations.md`).
