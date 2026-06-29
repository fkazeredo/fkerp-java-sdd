# ADR 0003: Single-Tenant with Multi-Tenant Extension Preserved

## Status

Accepted

## Context

The product is launched for a single cinema operator. The owner stated
multi-tenancy is not a current requirement but may become one. Adding
multi-tenancy retroactively to a database, security context and queries is
expensive and risky (cross-tenant data leakage is critical).

## Decision

The system runs single-tenant for v1, with the following extension points
preserved:

- Tables that would normally carry tenant ownership (`users`, `movies`,
  `screenings`, `cinema_rooms`, `reservations`, `tickets`) include a
  `tenant_id` column with a single fixed value `default` (NOT NULL, indexed).
- `UserContext` includes a `tenantId` field, hardcoded to `default` for now,
  exposed by `UserContextProvider`.
- Repository queries that touch tenant-owned tables accept and filter by
  `tenantId` even though only one value exists today.
- Cache keys include the tenant id prefix.

When the multi-tenancy requirement arrives, the work becomes: enable multiple
tenant rows, resolve tenant from authentication, remove the hardcoded
`default`. No schema migration of business tables required.

## Consequences

Positive: zero cost today, low cost to enable later. Data leakage between
tenants is structurally impossible because filtering already exists.

Negative: every query and cache key carries a column with one value. Trivial
runtime cost. Reviewers may question the column until the design intent is
explained — this ADR is the explanation.

## Alternatives Considered

- **Pure single-tenant, no preservation.** Rejected: migration cost too high
  when (not if) multi-tenancy comes.
- **Full multi-tenancy implementation now.** Rejected: speculative complexity
  per Rule Zero of the architecture.
