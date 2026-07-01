/**
 * Identity module (SPEC-0024; OVERVIEW Part 5/6, generic subdomain): the bounded context that owns
 * <strong>what an internal ERP user may do</strong> and the access audit. It graduated the
 * SPEC-0001 dev stub into real authentication and a role/permission model the other modules already
 * assume (DIRECTIVE → director, issue NF → finance, trigger job → IT).
 *
 * <p><strong>Authentication (Phase 17 — ADR-0018/DL-0110).</strong> OIDC is served by a
 * <strong>self-hosted Spring Authorization Server embedded in this app</strong> (Keycloak was
 * removed); the ERP is the Resource Server that validates the AS's JWT via JWKS (RS256) and maps
 * {@code realm_access.roles} to authorities. The in-house HS256 issuer and {@code POST /login} of
 * the 8k were retired in Phase 13 (DL-0105) and stay retired. The {@code UserContextProvider} port
 * (in {@code infra.security}) is the seam that survived the IdP swap, so the modules never changed.
 *
 * <p><strong>Role/permission model (DL-0082/DL-0107).</strong> {@link
 * com.fksoft.domain.identity.RoleEntity} ({@code roles} + {@code role_permissions}) is the single
 * source of truth of internal authorization (BR5/BR16) — the IdP says <em>which</em> roles a user
 * has, the ERP says <em>what</em> each role may do. Sensitive actions require the corresponding
 * role, enforced at the HTTP layer (Spring Security) and reaffirmed by the existing domain checks
 * (e.g. CommercialPolicy directive, DL-0038). The local user store (BCrypt hash) — dropped in Phase
 * 13 (V31) — was <strong>reintroduced in Phase 17 (V32, DL-0112)</strong> in {@code infra.security}
 * so the embedded Authorization Server can authenticate; this {@code domain.identity} module still
 * owns only the role/permission catalogue, never the user credentials.
 *
 * <p><strong>Access auditing (DL-0083).</strong> Login (first authenticated touch) and denial are
 * recorded in the Platform's append-only {@code system_audit} ({@code AUTH_LOGIN}/{@code
 * ACCESS_DENIED}) via the public {@code SystemAuditService} facade — metadata only, never a
 * token/secret (BR4). There is no separate {@code access_audit} table (Rule Zero); {@code GET
 * /api/identity/access-audit} is a focused read over that seam.
 *
 * <p>Spring Modulith application module. It depends on the Platform audit facade (a command call to
 * a consumer-leaf that never imports {@code identity}), so the graph stays acyclic. The role
 * reference data lives in this same package marked {@link com.fksoft.domain.ModuleInternal} and
 * must never be reached from other modules — encapsulation is enforced by ArchUnit (Phase 9 / ADR
 * 0016); the {@code infra} layer holds the Spring Security/Resource Server configuration in {@code
 * com.fksoft.infra.security} behind the {@code UserContextProvider} port (ADR 0010/0012).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.fksoft.domain.identity;
