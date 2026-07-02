package com.fksoft.infra.security;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * The single, ordered authorization matrix of the API (SPEC-0024 — Phase 19a, DL-0119). Every write
 * endpoint ({@code POST/PUT/PATCH/DELETE} under {@code /api/**}) MUST be covered by a rule here;
 * anything not covered is <strong>denied by default</strong> by {@link SecurityConfig}
 * (default-deny), closing the Phase-8k gap where unmapped writes fell through to "any authenticated
 * user". Sensitive reads (personal data, vault content, platform/IT surface) are gated here too;
 * unlisted reads remain "any authenticated user".
 *
 * <p>Completeness is enforced by a build-time fitness function ({@code
 * ApiAuthorizationMatrixCompletenessTest}): every write endpoint must match a rule, and every rule
 * must match at least one real endpoint (no stale entries) — the same
 * registry-plus-completeness-test pattern as {@code HttpErrorMapping}.
 *
 * <p>Role assignments follow the sensitive-action catalogue of DL-0082 extended to the whole
 * surface (DL-0119), aligned with the role-gated navigation the operator already sees (frontend
 * {@code nav.ts}): Finance/Billing/Payout/Admin → {@code FINANCE}; the commercial operation
 * (Accounts/Sourcing/Quoting/Booking/AfterSales/Marketing/Portfolio/FX desk) → {@code OPERATIONS};
 * People/Ponto/Assets/Platform → {@code IT}; the director's levers (pinned rate, directives, LGPD
 * erasure) → {@code DIRECTOR}; reference data → {@code POLICY_ADMIN}. {@code VIEWER} writes
 * nothing.
 */
public final class ApiAuthorizationMatrix {

  /** How a matched request is authorized. */
  public enum Access {
    /** Machine-to-machine endpoint authenticated by HMAC signature in the adapter, not by user. */
    PERMIT,
    /** Any authenticated user (e.g. a stateless calculation preview). */
    AUTHENTICATED,
    /** Requires any of the listed roles. */
    ROLES
  }

  /**
   * One ordered rule of the matrix. Order matters (first match wins in Spring Security): more
   * specific patterns must come before broader ones (e.g. marketing erasure before {@code
   * /api/marketing/**}).
   */
  public record Rule(HttpMethod method, String pattern, Access access, List<String> roles) {

    static Rule roles(HttpMethod method, String pattern, String... roles) {
      return new Rule(method, pattern, Access.ROLES, List.of(roles));
    }

    static Rule permit(HttpMethod method, String pattern) {
      return new Rule(method, pattern, Access.PERMIT, List.of());
    }

    static Rule authenticated(HttpMethod method, String pattern) {
      return new Rule(method, pattern, Access.AUTHENTICATED, List.of());
    }

    /** Whether this rule authorizes a write verb (used by the completeness fitness function). */
    public boolean isWriteRule() {
      return method == HttpMethod.POST
          || method == HttpMethod.PUT
          || method == HttpMethod.PATCH
          || method == HttpMethod.DELETE;
    }
  }

  /** The ordered rules. First match wins; keep specific before broad. */
  public static final List<Rule> RULES =
      List.of(
          // --- M2M inbound authenticated by HMAC signature (DL-0016/DL-0048), never by a user
          // token. ONLY these two are open: the Phase-8k blanket permitAll on /api/integration/**
          // also exposed the point AFD/crawl operator endpoints unauthenticated — closed here
          // (they are IT actions below).
          Rule.permit(HttpMethod.POST, "/api/webhooks/payouts/**"),
          Rule.permit(HttpMethod.POST, "/api/integration/quotation-site/inbound"),

          // --- Director levers (DL-0082; OVERVIEW 3.4). The pinned sell rate is THE director
          // decision; the LGPD erasure is destructive (DL-0058) and equally directorial.
          Rule.roles(HttpMethod.POST, "/api/exchange/pinned-rates", "DIRECTOR"),
          // FX forwards are treasury commitments (SPEC-0032/DL-0130): director or finance.
          Rule.roles(HttpMethod.POST, "/api/exchange/forwards/**", "DIRECTOR", "FINANCE"),
          Rule.roles(HttpMethod.POST, "/api/commercial-policy/directives", "DIRECTOR"),
          Rule.roles(HttpMethod.POST, "/api/marketing/erasure", "DIRECTOR"),
          Rule.roles(HttpMethod.POST, "/api/commercial-policy/rules", "DIRECTOR", "POLICY_ADMIN"),

          // --- Reference data (SPEC-0031/DL-0115).
          Rule.roles(HttpMethod.POST, "/api/cadastro/items", "POLICY_ADMIN"),
          Rule.roles(HttpMethod.PUT, "/api/cadastro/items/*", "POLICY_ADMIN"),
          Rule.roles(HttpMethod.DELETE, "/api/cadastro/items/*", "POLICY_ADMIN"),

          // --- Finance desk: everything that creates/settles/closes financial facts.
          Rule.roles(HttpMethod.POST, "/api/finance/**", "FINANCE"),
          Rule.roles(HttpMethod.POST, "/api/billing/**", "FINANCE"),
          Rule.roles(HttpMethod.POST, "/api/payouts/**", "FINANCE"),
          Rule.roles(HttpMethod.POST, "/api/admin/**", "FINANCE"),
          Rule.roles(HttpMethod.POST, "/api/reconciliation/*/settlement", "FINANCE"),
          // Vault purge is governance of the retention rule (retention still checked in domain).
          Rule.roles(HttpMethod.DELETE, "/api/compliance/documents/*", "FINANCE"),

          // --- Vault writes shared by the finance and operations desks (both attach documents).
          Rule.roles(HttpMethod.POST, "/api/compliance/documents", "FINANCE", "OPERATIONS"),
          Rule.roles(
              HttpMethod.POST, "/api/compliance/documents/*/attach", "FINANCE", "OPERATIONS"),

          // --- Operations desk: the commercial cycle.
          Rule.roles(HttpMethod.POST, "/api/accounts", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/sourcing/**", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/quotes/**", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/bookings/**", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/aftersales/**", "OPERATIONS"),
          Rule.roles(HttpMethod.PUT, "/api/products/*/cancellation-policy", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/exchange/market-rates", "OPERATIONS"),
          // The DSS advises, the human decides — the deciding human may be the ops desk or the
          // director (OVERVIEW Parte 8).
          Rule.roles(
              HttpMethod.POST, "/api/intelligence/insights/*/decision", "OPERATIONS", "DIRECTOR"),
          Rule.roles(HttpMethod.POST, "/api/marketing/**", "OPERATIONS"),
          Rule.roles(HttpMethod.DELETE, "/api/marketing/consents/*", "OPERATIONS"),
          Rule.roles(HttpMethod.POST, "/api/portfolio/**", "OPERATIONS"),
          Rule.roles(HttpMethod.DELETE, "/api/portfolio/brands/*", "OPERATIONS"),

          // --- IT desk: platform, patrimony, HR data custody and the point-clock operator
          // actions (the AFD upload and crawl trigger were unauthenticated before 19a).
          Rule.roles(HttpMethod.POST, "/api/platform/**", "IT"),
          Rule.roles(HttpMethod.POST, "/api/assets/**", "IT"),
          Rule.roles(HttpMethod.POST, "/api/people/**", "IT"),
          Rule.roles(HttpMethod.POST, "/api/integration/point/**", "IT"),

          // --- Stateless calculation previews: no state is written; any authenticated user.
          Rule.authenticated(HttpMethod.POST, "/api/commissioning/preview"),

          // --- Sensitive reads. Personal data (People/Ponto — LGPD) is IT-only; vault CONTENT
          // download may carry personal data (payslips) so VIEWER is excluded; the platform/IT
          // surface mirrors the identity reads rule (DIRECTOR or IT).
          Rule.roles(HttpMethod.GET, "/api/people/**", "IT"),
          Rule.roles(HttpMethod.GET, "/api/integration/point/**", "IT"),
          Rule.roles(
              HttpMethod.GET,
              "/api/compliance/documents/*/content",
              "FINANCE",
              "OPERATIONS",
              "IT",
              "DIRECTOR"),
          Rule.roles(HttpMethod.GET, "/api/platform/**", "IT", "DIRECTOR"),
          Rule.roles(HttpMethod.GET, "/api/identity/roles", "DIRECTOR", "IT"),
          Rule.roles(HttpMethod.GET, "/api/identity/access-audit", "DIRECTOR", "IT"));

  private ApiAuthorizationMatrix() {}
}
