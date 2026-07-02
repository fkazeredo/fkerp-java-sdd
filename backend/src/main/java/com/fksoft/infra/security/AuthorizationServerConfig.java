package com.fksoft.infra.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The <strong>self-hosted OIDC Authorization Server</strong> (SPEC-0024 — re-graduated in Phase 17,
 * ADR-0018/DL-0110). Phase 13 delegated authentication to an external Keycloak; Phase 17 removes
 * Keycloak and serves OIDC from this same application via the <strong>Spring Authorization
 * Server</strong> (on Spring Boot 4 / Security 7 the AS is part of Spring Security). It runs
 * <strong>embedded</strong> — no extra process/Docker — signing tokens RS256 with a local RSA key
 * and exposing {@code /.well-known/openid-configuration}, {@code /oauth2/authorize}, {@code
 * /oauth2/token}, {@code /oauth2/jwks}, {@code /userinfo} and a form {@code /login}.
 *
 * <p><strong>Three filter chains</strong> ordered by precedence (this class provides #1 and #3; the
 * Resource Server chain of {@link SecurityConfig} is #2):
 *
 * <ol>
 *   <li><b>{@code @Order(1)}</b> — the Authorization Server endpoints (protocol + OIDC),
 *       redirecting unauthenticated browser requests to {@code /login}.
 *   <li><b>{@code @Order(2)}</b> — the Resource Server {@code /api/**} chain ({@link
 *       SecurityConfig}), unchanged in behavior.
 *   <li><b>{@code @Order(3)}</b> — the form-login chain ({@code /login}) that authenticates the
 *       user against the local user store (BCrypt — DL-0112) via {@link AppUserDetailsService}.
 * </ol>
 *
 * <p><strong>Roles claim preserved.</strong> The {@link OAuth2TokenCustomizer} injects {@code
 * realm_access.roles} (the Keycloak claim shape) plus {@code preferred_username} into the access
 * token, so the Resource Server's {@code JwtAuthenticationConverter} ({@link SecurityConfig}) and
 * every security test keep working with <strong>only the token issuer changed</strong> to this app.
 *
 * <p>Not active in the {@code test} profile: the suite uses a local test {@code JwtDecoder}
 * (DL-0105) and never boots the AS, so the 444 backend tests validate the genuine JWKS/RS256 path
 * without a live issuer. This config lives in {@code infra} (never {@code domain}) so
 * ArchUnit/Modulith stay green.
 */
@Configuration
@Profile("!test")
public class AuthorizationServerConfig {

  /** The public SPA client id — mirrors the Keycloak {@code acme-erp-web} client (DL-0111). */
  static final String SPA_CLIENT_ID = "acme-erp-web";

  /** Keycloak-shaped realm-roles claim so the Resource Server mapping is unchanged (DL-0110). */
  static final String REALM_ACCESS_CLAIM = "realm_access";

  static final String ROLES_CLAIM = "roles";

  /**
   * The Authorization Server protocol chain (highest precedence). Applies the {@link
   * OAuth2AuthorizationServerConfigurer} to the AS endpoints, enables OpenID Connect 1.0, and sends
   * unauthenticated browser (text/html) requests to the form {@code /login}.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
        new OAuth2AuthorizationServerConfigurer();
    http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
        .with(
            authorizationServerConfigurer,
            authorizationServer -> authorizationServer.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        // The SPA (a different origin: 4200/4201) fetches the discovery document, the JWK set and
        // exchanges the code at /oauth2/token via XHR — those need CORS (DL-0113). The browser
        // REDIRECT to /oauth2/authorize and /login is a top-level navigation and needs no CORS.
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
  }

  /**
   * The form-login chain (lowest precedence). Authenticates the local user (BCrypt, DL-0112) at
   * {@code /login} and permits the login form/assets; everything else it matches requires
   * authentication. It only sees requests not matched by the AS ({@code @Order(1)}) or the Resource
   * Server ({@code @Order(2)}) chains — i.e. the login page and the consent/authorization browser
   * flow.
   */
  @Bean
  @Order(3)
  public SecurityFilterChain loginSecurityFilterChain(
      HttpSecurity http, LoginAttemptService loginAttempts) throws Exception {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/login", "/error", "/webjars/**", "/assets/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.successHandler(loginSuccessHandler(loginAttempts))
                    .failureHandler(loginFailureHandler(loginAttempts)))
        .logout(Customizer.withDefaults());
    return http.build();
  }

  /**
   * On a successful form login, clears the failed-attempt counter (DL-0125) and forwards to the
   * saved request (the OAuth2 authorize the browser was pursuing), preserving the standard flow.
   */
  private static org.springframework.security.web.authentication.AuthenticationSuccessHandler
      loginSuccessHandler(LoginAttemptService loginAttempts) {
    org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
        delegate =
            new org.springframework.security.web.authentication
                .SavedRequestAwareAuthenticationSuccessHandler();
    return (request, response, authentication) -> {
      loginAttempts.onSuccess(authentication.getName());
      delegate.onAuthenticationSuccess(request, response, authentication);
    };
  }

  /**
   * On a failed form login, records the failure (DL-0125). When the failure crosses the threshold
   * the account is locked; the user is redirected back to {@code /login?error} with a generic
   * message (BR4 — never reveal whether the user exists or is locked).
   */
  private static org.springframework.security.web.authentication.AuthenticationFailureHandler
      loginFailureHandler(LoginAttemptService loginAttempts) {
    org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler delegate =
        new org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler(
            "/login?error");
    return (request, response, exception) -> {
      String username = request.getParameter("username");
      if (username != null && !username.isBlank()) {
        loginAttempts.onFailure(username);
      }
      delegate.onAuthenticationFailure(request, response, exception);
    };
  }

  /**
   * The registered public SPA client (DL-0111): {@code acme-erp-web}, Authorization Code + PKCE
   * (S256), public ({@code NONE} — no secret), no consent screen (internal users), redirect/logout
   * URIs for dev (4200) and the isolated E2E stack (4201). Mirrors the Keycloak client so the
   * frontend changes only its {@code issuer}. Access token lives 5 minutes (mirrors the old realm).
   */
  @Bean
  public RegisteredClientRepository registeredClientRepository(
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    // Fase 19g (DL-0129/ADR-0020): client registrations live in Postgres (V39) so every instance
    // shares them. The SPA client is bootstrapped idempotently: JdbcRegisteredClientRepository.save
    // is an upsert by client id lookup, so a restart/replica never duplicates it.
    var repository =
        new org.springframework.security.oauth2.server.authorization.client
            .JdbcRegisteredClientRepository(jdbcTemplate);
    RegisteredClient existing = repository.findByClientId(SPA_CLIENT_ID);
    RegisteredClient spa =
        RegisteredClient.withId(existing != null ? existing.getId() : UUID.randomUUID().toString())
            .clientId(SPA_CLIENT_ID)
            .clientName("Acme Travel ERP — Web SPA")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:4200")
            .redirectUri("http://localhost:4200/")
            .redirectUri("http://localhost:4201")
            .redirectUri("http://localhost:4201/")
            .postLogoutRedirectUri("http://localhost:4200")
            .postLogoutRedirectUri("http://localhost:4201")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true) // PKCE S256 mandatory (public client)
                    .requireAuthorizationConsent(false) // internal users → no consent screen
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(java.time.Duration.ofMinutes(5))
                    .build())
            .build();
    repository.save(spa);
    return repository;
  }

  /**
   * Authorization state (codes, access/refresh tokens under issuance) in Postgres (V39) so any
   * instance can complete a flow another instance started and a restart loses nothing (Fase 19g,
   * DL-0129/ADR-0020).
   */
  @Bean
  public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
      authorizationService(
          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
          RegisteredClientRepository registeredClientRepository) {
    return new org.springframework.security.oauth2.server.authorization
        .JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  }

  /**
   * Injects the ERP's role claim into the access token so the Resource Server mapping and the tests
   * are unchanged (DL-0110): {@code realm_access.roles} (the {@code ROLE_*} authorities of the
   * authenticated user, Keycloak shape) and {@code preferred_username}. Access tokens only — never
   * the id token. No secret/token is logged.
   */
  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> accessTokenRolesCustomizer() {
    return context -> {
      if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        return;
      }
      List<String> roles =
          context.getPrincipal().getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .filter(authority -> authority.startsWith("ROLE_"))
              .toList();
      context
          .getClaims()
          .claim(REALM_ACCESS_CLAIM, java.util.Map.of(ROLES_CLAIM, roles))
          .claim(SecurityPrincipals.USERNAME_CLAIM, context.getPrincipal().getName());
    };
  }

  /**
   * The RSA key that signs the tokens (RS256), served by the AS JWK set ({@code /oauth2/jwks}).
   * Fase 19g (DL-0129/ADR-0020): the key is loaded from configuration ({@code
   * app.oidc.jwk.private-key} / {@code OIDC_JWK_PRIVATE_KEY}, base64 DER PKCS#8) with a stable
   * {@code kid}, so restarts and replicas share the SAME key — tokens survive a restart and any
   * instance validates any instance's tokens. When unset (dev/test), an ephemeral key is generated
   * (the pre-19g behavior); production requires the persisted key ({@code ProdReadinessValidator}).
   */
  @Bean
  public JWKSource<SecurityContext> jwkSource(
      @Value("${app.oidc.jwk.private-key:}") String privateKeyBase64,
      @Value("${app.oidc.jwk.key-id:}") String keyId) {
    RSAKey rsaKey = PersistedJwk.load(privateKeyBase64, keyId);
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  /**
   * The password encoder for the local user store (DL-0112): BCrypt (Spring Security default cost).
   * Used to verify the form-login password and by the dev seeder to hash the seed passwords — never
   * a plaintext password/hash is logged (BR4).
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * CORS for the SPA's cross-origin XHR to the AS endpoints (DL-0113): the SPA on dev (4200) / E2E
   * (4201) fetches the discovery document, the JWK set and exchanges the authorization code at
   * {@code /oauth2/token}. Only the two known SPA origins are allowed. The {@code /api/**} calls go
   * through the frontend proxy (same-origin) and are not covered here.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:4201}")
          List<String> allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /**
   * The AS-side {@link JwtDecoder} (used by the AS chain to validate its own tokens, e.g. at {@code
   * /userinfo}). The Resource Server {@code /api/**} chain uses the auto-configured decoder pointed
   * at {@code issuer-uri} (same app).
   */
  @Bean
  public JwtDecoder authorizationServerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  /**
   * The AS settings. The {@code issuer} is derived from the incoming request (default), so the same
   * build serves dev (8080) and the isolated E2E stack (8081) without a hardcoded host — matching
   * the Resource Server's {@code issuer-uri} resolution.
   */
  @Bean
  public AuthorizationServerSettings authorizationServerSettings(
      @Value("${app.oidc.issuer:}") String issuer) {
    AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
    if (issuer != null && !issuer.isBlank()) {
      builder.issuer(issuer);
    }
    return builder.build();
  }
}
