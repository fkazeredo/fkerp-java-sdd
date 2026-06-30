package com.fksoft.infra.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The real Spring Security configuration (SPEC-0024/DL-0079/DL-0081/DL-0082). The ERP authenticates
 * in-house and is the Resource Server of its own HS256 JWT issuer; this wires the encoder/decoder
 * from the configured secret, maps the {@code roles} claim to authorities, opens the public
 * endpoints, and requires the corresponding role on the sensitive actions the specs cite. Denials
 * return the stable error contract (401 generic / 403 audited) — security stays the final authority
 * on the backend (security.md), never the frontend.
 *
 * <p>This config is active in every profile (including {@code test}); in {@code test} the {@code
 * TestSecurityConfig} provides an authenticated actor so the existing suite stays green with the
 * security layer <strong>mounted, not removed</strong> (DL-0081).
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  private final SecurityProperties properties;

  public SecurityConfig(SecurityProperties properties) {
    this.properties = properties;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private SecretKeySpec signingKey() {
    return new SecretKeySpec(
        properties.getJwt().getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withSecretKey(signingKey()).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /**
   * Maps the JWT {@code roles} claim to Spring authorities (kept as-is, e.g. {@code ROLE_FINANCE},
   * so {@code hasRole}/{@code hasAuthority} both work), and uses {@code preferred_username} as the
   * principal name.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(SecurityPrincipals.ROLES_CLAIM);
    authorities.setAuthorityPrefix("");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(SecurityPrincipals.USERNAME_CLAIM);
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> granted = authorities.convert(jwt);
          // Also expose each role's permissions? Not needed: hasRole(...) is enough for the
          // sensitive-action gate (DL-0082). Permissions are documented in GET /roles.
          return granted == null ? List.of() : granted;
        });
    return converter;
  }

  @Bean
  @Profile("!test")
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    return configure(
            http, authenticationEntryPoint, accessDeniedHandler, jwtAuthenticationConverter)
        .build();
  }

  /**
   * Applies the shared security configuration: stateless, public matchers, role-gated sensitive
   * actions (DL-0082), JWT resource server and the audited 401/403 handlers. Reused by the test
   * filter chain ({@code TestSecurityConfig}) so the test path runs the <strong>same real
   * authorization</strong> (security mounted, not removed — DL-0081).
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
                    // Public: health, login, API docs, actuator health.
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
            "/api/identity/login",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info")
        .toArray(String[]::new);
  }
}
