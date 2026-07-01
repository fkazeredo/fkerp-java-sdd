package com.fksoft.infra.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The real Spring Security configuration (SPEC-0024 — re-graduated in Phase 17, ADR-0018/DL-0110).
 * The ERP is an OAuth2 <strong>Resource Server</strong> that validates JWTs minted by the
 * <strong>SELF-HOSTED OIDC IdP</strong> — the Spring Authorization Server embedded in this same app
 * ({@link AuthorizationServerConfig}); Keycloak was removed — <strong>via JWKS</strong> (RS256):
 * the {@code JwtDecoder} is auto-configured from {@code
 * spring.security.oauth2.resourceserver.jwt.issuer-uri}/{@code jwk-set-uri} (both pointing at this
 * app) and the public keys are fetched/cached from the app's own JWK set. The {@code
 * UserContextProvider} port survives the IdP swap.
 *
 * <p>It maps the token's <strong>{@code realm_access.roles}</strong> (the Keycloak-shaped claim the
 * embedded AS still emits — DL-0110) to Spring authorities — preserving the {@code ROLE_} prefix so
 * {@code hasRole(...)} keeps working — and also exposes the {@code scope} claim as {@code SCOPE_*}
 * authorities for future fine-grained checks; the current enforcement stays by role (the closed
 * catalogue of DL-0082). The backend is the single authorization authority (security.md) — never
 * the frontend. Denials return the stable contract (401 generic / 403 audited).
 *
 * <p>In the {@code test} profile the decoder is provided by a local test JWK set (DL-0105) so the
 * suite validates the genuine JWKS/RS256 path without an internet IdP; the {@code
 * TestSecurityConfig} still authenticates a full-access actor when no {@code Authorization} header
 * is present, keeping the pre-existing suite green with the security layer <strong>mounted, not
 * removed</strong> (DL-0081).
 */
@Configuration
public class SecurityConfig {

  /**
   * Keycloak's realm-roles claim, e.g. {@code {"realm_access":{"roles":["ROLE_FINANCE", ...]}}}.
   */
  static final String REALM_ACCESS_CLAIM = "realm_access";

  static final String ROLES_CLAIM = "roles";

  /**
   * Converts a verified JWT into Spring authorities: the realm roles from {@code
   * realm_access.roles} (kept verbatim, so {@code ROLE_FINANCE} works with both {@code hasRole} and
   * {@code hasAuthority}) plus the OAuth2 {@code scope}/{@code scp} as {@code SCOPE_*} authorities.
   * The principal name is {@code preferred_username}. Shared by production and the test chain so
   * both run the same mapping.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(SecurityPrincipals.USERNAME_CLAIM);
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>();
          // OAuth2 scopes → SCOPE_* (Spring default mapping), available for future fine-grained
          // authorization (the ROADMAP's "scopes → profiles"); enforcement today stays by role.
          Collection<GrantedAuthority> scoped = scopes.convert(jwt);
          if (scoped != null) {
            authorities.addAll(scoped);
          }
          // Keycloak realm roles → authorities kept verbatim (ROLE_* — DL-0104).
          authorities.addAll(realmRoles(jwt));
          return authorities;
        });
    return converter;
  }

  @SuppressWarnings("unchecked")
  private static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get(REALM_ACCESS_CLAIM);
    if (!(realmAccess instanceof Map<?, ?> map)) {
      return List.of();
    }
    Object roles = ((Map<String, Object>) map).get(ROLES_CLAIM);
    if (!(roles instanceof Collection<?> roleList)) {
      return List.of();
    }
    return roleList.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }

  /**
   * The Resource Server chain (Phase 17: {@code @Order(2)}, after the Authorization Server chain of
   * {@link AuthorizationServerConfig} at {@code @Order(1)} and before its form-login chain at
   * {@code @Order(3)}). It is scoped by {@link #resourceServerMatchers()} to the API/actuator/docs
   * surface, so the browser login flow ({@code /login}, OAuth2 authorize/consent) falls through to
   * the form-login chain and the AS chain — the enforcement rules and JWT validation are unchanged
   * (DL-0110). In the {@code test} profile this bean is absent; {@code TestSecurityConfig} mounts
   * the same {@link #configure} rules at the highest precedence.
   */
  @Bean
  @Order(2)
  @Profile("!test")
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    http.securityMatcher(resourceServerMatchers());
    return configure(
            http, authenticationEntryPoint, accessDeniedHandler, jwtAuthenticationConverter)
        .build();
  }

  /**
   * The paths the Resource Server chain owns: the API, the operational actuator surface and the API
   * docs. Everything else (the AS endpoints, {@code /login} and the OAuth2 browser flow) is handled
   * by the Authorization Server / form-login chains (ADR-0018).
   */
  private static String[] resourceServerMatchers() {
    return Stream.of(
            "/api/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
        .toArray(String[]::new);
  }

  /**
   * Applies the shared security configuration: stateless, public matchers, role-gated sensitive
   * actions (DL-0082), JWT resource server (validated by JWKS — DL-0104) and the audited 401/403
   * handlers. Reused by the test filter chain ({@code TestSecurityConfig}) so the test path runs
   * the <strong>same real authorization</strong> (security mounted, not removed — DL-0081).
   */
  public static HttpSecurity configure(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public: health, API docs, actuator health, version.
                    .requestMatchers(publicMatchers())
                    .permitAll()
                    // Machine-to-machine ACL webhooks/inbound — authenticated by HMAC, not by user.
                    .requestMatchers("/api/webhooks/**", "/api/integration/**")
                    .permitAll()
                    // Operational telemetry is for IT only (SPEC-0027/DL-0095): the Prometheus
                    // scrape and the metrics catalogue require ROLE_IT. health/info/version stay
                    // public (publicMatchers). Anonymous → 401; token without ROLE_IT → 403.
                    .requestMatchers(
                        "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                    .hasRole("IT")
                    // Sensitive actions require the corresponding role (DL-0082).
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/billing/invoices/*/issue")
                    .hasRole("FINANCE")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/finance/periods/*/close")
                    .hasRole("FINANCE")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/platform/jobs/*/trigger")
                    .hasRole("IT")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/platform/certificate")
                    .hasRole("IT")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/commercial-policy/directives")
                    .hasRole("DIRECTOR")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/commercial-policy/rules")
                    .hasAnyRole("DIRECTOR", "POLICY_ADMIN")
                    .requestMatchers("/api/identity/roles", "/api/identity/access-audit")
                    .hasAnyRole("DIRECTOR", "IT")
                    // Administrative writes (supplier/contract/expense registration, expiry sweep)
                    // generate AP/finance facts → require the finance role (SPEC-0025, DL-0088).
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/**")
                    .hasRole("FINANCE")
                    // Everything else under /api requires authentication.
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http;
  }

  private static String[] publicMatchers() {
    return Stream.of(
            "/api/system/health",
            "/api/version", // build metadata only — no secret (SPEC-0027/DL-0097)
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info")
        .toArray(String[]::new);
  }
}
