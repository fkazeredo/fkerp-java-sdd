package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.infra.security.DevUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the <strong>self-hosted Spring Authorization Server</strong> embedded in the app (SPEC-0024
 * Phase 17 / ADR-0018 / DL-0110/0112) to prove — honestly, against a real Postgres — that the AS
 * config is valid and wired: (a) the OIDC discovery document is served with the standard endpoints;
 * (b) the AS JWK set is served (the RS256 signing key); (c) the local user store (V32) is seeded and
 * the {@link UserDetailsService} resolves the seed user with its {@code ROLE_*} authorities and a
 * BCrypt-verifiable password. This runs in the {@code dev,e2e} profiles (NOT {@code test}), so the AS
 * chains and the {@code DevUserSeeder} are active — exactly the production path minus the external
 * origin.
 *
 * <p>Its own Postgres container ({@code @Testcontainers}/{@code @Container}) is used rather than the
 * shared {@code test}-profile singleton, because this test intentionally does not run under {@code
 * test} (it needs the real security chains, not {@code TestSecurityConfig}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"dev", "e2e"})
@Testcontainers
class AuthorizationServerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private UserDetailsService userDetailsService;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void servesTheOidcDiscoveryDocumentWithTheStandardEndpoints() {
    ResponseEntity<String> discovery =
        restTemplate.getForEntity("/.well-known/openid-configuration", String.class);

    assertThat(discovery.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(discovery.getBody())
        .contains("\"issuer\"")
        .contains("/oauth2/authorize")
        .contains("/oauth2/token")
        .contains("/oauth2/jwks")
        .contains("/userinfo");
  }

  @Test
  void servesTheAuthorizationServerJwkSet() {
    ResponseEntity<String> jwks = restTemplate.getForEntity("/oauth2/jwks", String.class);

    assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
    // The RSA signing key (RS256): a single JWK with kty=RSA.
    assertThat(jwks.getBody()).contains("\"keys\"").contains("\"kty\":\"RSA\"");
  }

  @Test
  void seedsTheLocalUserStoreAndResolvesRolesAndPassword() {
    UserDetails director = userDetailsService.loadUserByUsername("director");

    assertThat(director.getUsername()).isEqualTo("director");
    assertThat(director.getAuthorities())
        .anyMatch(a -> a.getAuthority().equals("ROLE_DIRECTOR"));
    // The stored credential is a BCrypt hash of the dev password (never plaintext — BR4).
    assertThat(passwordEncoder.matches(DevUserSeeder.DEV_PASSWORD, director.getPassword())).isTrue();
  }

  @Test
  void seedsTheSuperUserWithEveryBaseRole() {
    UserDetails dev = userDetailsService.loadUserByUsername("dev");

    assertThat(dev.getAuthorities())
        .extracting(a -> a.getAuthority())
        .contains(
            "ROLE_DIRECTOR",
            "ROLE_FINANCE",
            "ROLE_OPERATIONS",
            "ROLE_IT",
            "ROLE_POLICY_ADMIN",
            "ROLE_VIEWER");
  }
}
