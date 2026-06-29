# ADR 0005: JWT-Based Authentication with Refresh Tokens

## Status

Accepted

## Context

The system has three roles (`CUSTOMER`, `OPERATOR`, `ADMIN`) and a SPA
frontend (Angular). Authentication must support: browser SPA today, possible
mobile clients in the future, and the WebSocket handshake.

## Decision

Token-based authentication using JSON Web Tokens with the two-token pattern:

- **Access token**: short-lived (15 minutes), signed with HS256 using a
  rotated secret stored as env var. Carries `userId` (as `sub`), a single
  `role` claim (spec 0003: one active role per user, roles do not stack in v1),
  `tenantId`, `iat` and `exp`. Sent on every API call as
  `Authorization: Bearer <token>` and on the STOMP CONNECT frame for WebSocket.
- **Refresh token**: long-lived (7 days), opaque random string, stored
  server-side in the `refresh_tokens` table with `userId`, `expiresAt`,
  `revokedAt`. Persisted client-side in `httpOnly`+`secure`+`SameSite=Strict`
  cookie. Single-use: refresh issues a new access AND a new refresh, and
  revokes the old refresh.
- Spring Security configured with `oauth2ResourceServer.jwt(...)` for access
  token validation. Custom filter for refresh endpoint.

Passwords stored with **bcrypt** (cost 12, Spring Security default for the
selected version). Login enforces rate limiting per IP/email.

## Consequences

Positive: stateless API serving, WebSocket auth works via token in CONNECT
frame, mobile-friendly, refresh token rotation detects token theft (reuse
of a revoked refresh triggers full session revocation for that user).

Negative: revoking access tokens before their `exp` requires either a
denylist (re-introduces state) or short TTL (chosen here: 15 min). User who
loses access (deleted account, role change) keeps the old access for at most
15 min.

## Alternatives Considered

- **Server-side sessions (HttpSession)**: rejected because WebSocket auth
  with sessions requires sticky sessions or shared session store, and
  multi-instance migration becomes harder. Mobile clients also become
  second-class.
- **OAuth2 with external IdP (Keycloak, Auth0)**: rejected for v1 — adds
  infrastructure for a small user base. Revisit when SSO with a corporate
  IdP is required.
- **Long-lived access token (no refresh)**: rejected because revocation
  becomes impossible without a denylist.
