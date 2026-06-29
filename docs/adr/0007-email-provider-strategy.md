# ADR 0007: Email Provider in Layers (Gmail SMTP in Dev, Transactional in Prod)

## Status

Accepted

## Context

Email is needed for: account verification, password reset, purchase
confirmation, cancellation/refund notice, ticket delivery. The owner accepts
using a personal Gmail SMTP account for development. Using personal Gmail
SMTP in production has known problems: ~500 emails/day limit, poor
deliverability without SPF/DKIM/DMARC on a proper domain, credential exposure
risk for the personal account, automated-use account bans.

## Decision

Define an `EmailSender` port in the `notification` module:

```java
void send(EmailMessage message);
```

Configure Spring Mail via env vars. The implementation is always the same
Spring Mail-based class. What changes per environment is configuration:

- **Dev / Local**: SMTP host `smtp.gmail.com:587`, STARTTLS, auth with the
  developer's Gmail account and an app password (env vars).
  `MAIL_FROM=dev@<localhost>`.
- **Staging**: same Spring Mail, pointed at a transactional provider's SMTP
  bridge (Resend, AWS SES, SendGrid, Postmark) with `MAIL_FROM` on a
  controlled domain. SPF/DKIM/DMARC configured on that domain by infra.
- **Production**: identical configuration shape as staging, with production
  credentials.

The application code does not branch on environment. Templates rendered with
Spring's `MessageSource` for i18n (PT-BR default, EN fallback).

Failure mode: email sending is best-effort and **must not** fail the business
transaction that triggered it. Sending is asynchronous via a dedicated outbox
table polled by a scheduled worker (chosen over `@Async` for crash safety and
restartability; see spec 0006). Failed sends are retried with exponential
backoff and a dead-letter status (`FAILED_PERMANENT`) for permanently failed
messages.

## Consequences

Positive: zero code change to migrate from Gmail to a transactional provider.
Async delivery keeps purchase confirmation from being held hostage by email
infrastructure. Dead-letter visibility for ops.

Negative: dev email visibility depends on the developer's personal inbox.
Recommend setting `MAIL_TO_OVERRIDE` env var in dev to redirect all outbound
mail to the developer's address regardless of recipient (avoids accidentally
spamming testers with real emails).

## Alternatives Considered

- **Transactional provider from day one**: rejected because the owner is not
  ready to commit to a provider and configure a domain.
- **No abstraction, Spring Mail injected directly into services**: rejected.
  Loses the async/outbox/retry envelope and the test seam (test doubles for
  `EmailSender`).
