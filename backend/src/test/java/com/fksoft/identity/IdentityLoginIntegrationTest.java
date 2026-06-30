package com.fksoft.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.application.api.dto.MeResponse;
import com.fksoft.infra.web.ApiErrorResponse;
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
 * End-to-end tests for the graduated authentication (SPEC-0024 slice 8k-1, DL-0079/0081). Against a
 * real Postgres: a seeded dev user logs in and gets a JWT carrying its roles; the real {@code
 * UserContextProvider} resolves that token on {@code /me}; an invalid token yields a generic 401;
 * and bad credentials yield a generic 401 that never reveals whether the user exists (BR4).
 *
 * <p>These tests SEND tokens (or a deliberately bad one), so they exercise the genuine security
 * chain — not the test-actor shortcut (which only applies when there is no Authorization header).
 */
class IdentityLoginIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void aSeededUserLogsInAndGetsATokenWithItsRoles() {
    ResponseEntity<LoginResponse> response =
        restTemplate.postForEntity(
            "/api/identity/login", new LoginRequest("finance", "dev12345"), LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    LoginResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.tokenType()).isEqualTo("Bearer");
    assertThat(body.token()).isNotBlank();
    assertThat(body.expiresIn()).isPositive();
    assertThat(body.user().username()).isEqualTo("finance");
    assertThat(body.user().roles()).contains("ROLE_FINANCE");
  }

  @Test
  void theRealUserContextResolvesRolesFromTheToken() {
    String token = login("director", "dev12345");

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

  @Test
  void badCredentialsYieldAGeneric401WithoutRevealingUserExistence() {
    ResponseEntity<ApiErrorResponse> wrongPassword =
        restTemplate.postForEntity(
            "/api/identity/login",
            new LoginRequest("finance", "wrong-password"),
            ApiErrorResponse.class);
    ResponseEntity<ApiErrorResponse> unknownUser =
        restTemplate.postForEntity(
            "/api/identity/login",
            new LoginRequest("ghost", "whatever-123"),
            ApiErrorResponse.class);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    // Same generic code for both — the message never reveals whether the user exists (BR4).
    assertThat(wrongPassword.getBody().code()).isEqualTo("identity.credentials.invalid");
    assertThat(unknownUser.getBody().code()).isEqualTo("identity.credentials.invalid");
  }

  private String login(String username, String password) {
    LoginResponse body =
        restTemplate
            .postForEntity(
                "/api/identity/login", new LoginRequest(username, password), LoginResponse.class)
            .getBody();
    assertThat(body).isNotNull();
    return body.token();
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
