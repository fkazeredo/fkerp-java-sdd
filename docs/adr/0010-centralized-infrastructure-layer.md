# ADR 0010: Centralized Infrastructure Layer (`com.fksoft.infra` by concern)

## Status

Accepted (owner decision; refines ADR 0001). Layering refined by **ADR 0012** (domain /
application / infra; `shared` deleted; `application → infra` allowed).

## Context

ADR 0001 placed module-specific technical adapters inside each business module
(e.g. the SMTP/outbox sender in `notification`, the JWT issuer in `auth`, the
mock payment gateway in `payment`). The owner wants a single, **centralized
infrastructure layer** organized by concern, so all non-business technical code
lives in one obvious place and the domain modules hold only business rules.

The owner also fixed the governing dependency rule:

> The **application** layer and **infra** may depend on the **domain**; the
> **domain** must NOT depend on controllers/endpoints (`api`) or on `infra`.

Two existing ArchUnit rules constrain this: `applicationMustNotDependOnGlobalInfra`
(domain ↛ infra) and the per-module `otherModulesMustNotTouch<Module>Persistence`
rules (nothing outside a module touches its `@Entity`/`Repository`).

## Decision

Introduce `com.fksoft.infra` as the centralized technical layer, split by concern:

```
com.fksoft.infra
  security/      Spring Security config, JWT encode/decode, entry/denied handlers, JwtProperties,
                 JwtAccessTokenIssuer (impl of the auth AccessTokens port)
  email/         MailConfig/Properties, EmailTemplateConfig, SpringMailEmailSender, EmailRenderer,
                 OutboxDispatcher, OutboxSendWorker
  integration/   PaymentProperties, MockPaymentGateway (impl of the payment PaymentGateway port),
                 MockPaymentDispatcher, MockPaymentDeliveryWorker, WebhookUrlResolver
  time/          ClockConfig
  i18n/          MessageSourceConfig
  socket/        WebSocketConfig, StompAuthChannelInterceptor, StompPrincipal
  observability/ CorrelationIdFilter
```

Rules that make this coherent:

1. **Domain ↛ infra is preserved.** Where the domain core invokes a technical
   adapter, the adapter implements a **port defined in the module** and the core
   depends on the port (e.g. auth `AccessTokens`, notification `EmailSender`,
   payment `PaymentGateway`). The domain never imports `com.fksoft.infra`.
2. **Infra may access the domain.** The persistence-bound adapters (outbox
   dispatch worker, mock-gateway integration) read/write their module's
   `@Entity`/`Repository`. The two per-module persistence ArchUnit rules are
   **relaxed to exempt `com.fksoft.infra..`** — other business modules are still
   forbidden from touching each other's persistence (Spring Modulith still
   enforces inter-module boundaries).
3. **Business rules stay in the module.** Only code with no business rule moves.
   E.g. the JWT *encoder* moved (pure technical), but auth's `RefreshTokenService`
   (single-use rotation, theft detection) and `SessionIssuer` stayed — they are
   security domain logic. The `notification` module keeps its outbox aggregate
   + enqueue listeners; the `payment` module keeps its ledger, webhook handler,
   signer and JSON codec (webhook acceptance is payment's domain).
4. **`shared` remains the kernel.** `com.fksoft.shared` (error contracts,
   pagination envelope, `UserContext`/`UserContextProvider`) holds cross-cutting
   types the **domain imports directly**; by rule (1) these cannot live in infra.
   *(Superseded by ADR 0012: `shared` was deleted — the error kernel moved to
   `com.fksoft.domain.error`, and identity + `PageResponse` moved into `com.fksoft.infra`
   once `application → infra` became allowed.)*

## Consequences

Positive: one obvious home for technical config and adapters by concern; domain
packages contain only business code; "where does X live?" is answerable from the
concern name; swapping a real provider stays a new infra adapter.

Negative: to let infra operate a module's technical tables, some module
`@Entity`/`Repository`/accessors became `public` (e.g. `MockPaymentJob`,
`PaymentSigner`, `WebhookJson`, `EmailTemplate` accessors), widening those
modules' surface. Infra now depends on several modules' internals (one-way:
infra → domain), and infra emits some modules' domain events (outbox). This is
an accepted trade for centralization; the inter-module boundary and domain ↛
infra/api boundaries remain enforced by ArchUnit + Modulith.

## Alternatives Considered

- **Keep adapters inside each module (ADR 0001 as-is).** Rejected by the owner:
  they want a single centralized infra layer.
- **Two-tier (global infra + module-local `infra/`).** Rejected: the owner
  prefers everything technical in the global infra.
- **Move `shared` into infra too.** Rejected here — at the time, the domain imported
  `BusinessException`/`ApiErrorResponse`/`PageResponse`/`UserContextProvider`, so they had to
  stay in a domain-accessible kernel. **Revisited in ADR 0012**: the error kernel went to
  `com.fksoft.domain.error` (the domain still imports it), while identity and `PageResponse`
  — which only the delivery layer imports — went to `com.fksoft.infra`, and `shared` was
  deleted.
