# Security, User Context, Privacy and Multi-Tenancy

> Read when: touching authentication, authorization, user context, sensitive data, LGPD
> concerns, or anything tenant-related.

## Security baseline

Spring Security is the default for authentication, authorization, endpoint protection,
filters, security context, CORS and IdP integration. Do not reinvent auth mechanisms.

## Business authorization

Spring Security handles general access; business authorization depends on domain state,
tenant, ownership, workflow or permissions. Simple checks **MAY** stay in the Application
Service; complex/reusable/critical rules go into policy classes (`OrderAccessPolicy`,
`CancellationPolicy`). The backend is the final authority — never trust frontend checks.

## Current user context

Application Services **SHOULD NOT** access `SecurityContextHolder` directly across the
codebase. Use a centralized `UserContextProvider` exposing userId, username,
roles/authorities and tenantId when applicable.

## Privacy and LGPD

Compliance is driven by project context, customer requirements, contracts and regulation —
do not introduce heavy compliance processes by default. Always apply low-cost hygiene:

- never log passwords, tokens, credentials, secrets;
- never expose sensitive internal data in API errors;
- never send secrets to the frontend;
- avoid unnecessary personal data in logs; mask sensitive values;
- do not store sensitive data without reason.

## Multi-tenancy

Only when the product requires it. If a real tenant/customer/organization concept exists, it
is an architectural boundary: tenant context propagated consistently through HTTP requests,
security context, services, repositories, queries, cache keys, logs, metrics, audit, events,
jobs, integrations and real-time connections. Data leakage between tenants is a critical
bug. The isolation strategy **MUST** be explicit and documented in an ADR.
