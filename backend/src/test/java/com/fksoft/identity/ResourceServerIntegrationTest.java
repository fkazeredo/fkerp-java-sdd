package com.fksoft.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.MeResponse;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.security.TestJwtTokens;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for the graduated authentication (SPEC-0024 Phase 13, DL-0104/0105). The ERP is
 * a Resource Server validating JWTs minted by the external IdP via JWKS (here: a local test RSA
 * key, Keycloak claim shape). A token carrying {@code realm_access.roles} resolves to the matching
 * Spring authorities; the real {@code UserContextProvider} surfaces them on {@code /me}; a
 * deliberately malformed token yields a generic 401 (BR4).
 *
 * <p>These tests SEND tokens (or a bad one), so they exercise the genuine security chain — not the
 * test-actor shortcut (which only applies when there is no Authorization header).
 */
class ResourceServerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void aTokenWithRealmRolesResolvesToTheMatchingRoles() {
    String token = TestJwtTokens.mint("finance", "ROLE_FINANCE");

    ResponseEntity<MeResponse> me =
        restTemplate.exchange(
            "/api/identity/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), MeResponse.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
    assertThat(me.getBody().username()).isEqualTo("finance");
    assertThat(me.getBody().roles()).contains("ROLE_FINANCE");
  }

  @Test
  void theRealUserContextResolvesRolesFromTheToken() {
    String token = TestJwtTokens.mint("director", "ROLE_DIRECTOR");

    ResponseEntity<MeResponse> me =
        restTemplate.exchange(
            "/api/identity/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), MeResponse.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
    assertThat(me.getBody().username()).isEqualTo("director");
    assertThat(me.getBody().roles()).contains("ROLE_DIRECTOR");
  }

  @Test
  void anInvalidTokenYieldsAGeneric401() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("not-a-real-jwt");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/identity/me", HttpMethod.GET, new HttpEntity<>(headers), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("auth.unauthenticated");
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
