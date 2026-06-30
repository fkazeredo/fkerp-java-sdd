/**
 * Identity module (SPEC-0024; OVERVIEW Part 5/6, generic subdomain): the bounded context that owns
 * <strong>who the internal ERP user is</strong> and <strong>what they may do</strong>. It graduates
 * the SPEC-0001 dev stub into real authentication and a role/permission model that the other
 * modules already assume (DIRECTIVE → director, issue NF → finance, trigger job → IT).
 *
 * <p><strong>Authentication (DL-0079).</strong> In the 8k delivery the ERP authenticates in-house
 * and is the Resource Server of its <em>own</em> JWT issuer: {@link IdentityService#login} verifies
 * a local user's BCrypt password and the {@code infra.security} layer mints/verifies an HS256 JWT.
 * The live external OIDC IdP (JWKS/rotation, fine scopes) is Phase 13; the {@code
 * UserContextProvider} port is the seam that survives that swap, so the modules never change.
 *
 * <p><strong>Role/permission model (DL-0080/DL-0082).</strong> {@link
 * com.fksoft.domain.identity.internal.IdentityUser} (minimal local user, BCrypt hash only — never a
 * plaintext password/token, BR4), {@code roles} and {@code role_permissions} are the single source
 * of truth of internal authorization (BR5). Sensitive actions require the corresponding role,
 * enforced at the HTTP layer (Spring Security) and reaffirmed by the existing domain checks (e.g.
 * CommercialPolicy directive, DL-0038) — the modules <em>consume</em> the context, they do not
 * reimplement access.
 *
 * <p><strong>Access auditing (DL-0083).</strong> Login and denial are recorded in the Platform's
 * append-only {@code system_audit} ({@code AUTH_LOGIN}/{@code ACCESS_DENIED}) via the public {@code
 * SystemAuditService} facade — metadata only, never a token/secret (BR4). There is no separate
 * {@code access_audit} table (Rule Zero); {@code GET /api/identity/access-audit} is a focused read
 * over that seam.
 *
 * <p>Spring Modulith application module. It depends on the Platform audit facade (a command call to
 * a consumer-leaf that never imports {@code identity}), so the graph stays acyclic. The {@code
 * internal} sub-package (user aggregate + repositories) is module-private; the Spring Security/JWT
 * configuration lives in {@code com.fksoft.infra.security} behind the {@code UserContextProvider}
 * port (ADR 0010/0012).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.fksoft.domain.identity;
