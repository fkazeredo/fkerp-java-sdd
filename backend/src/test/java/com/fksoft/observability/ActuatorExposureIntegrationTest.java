package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for the Actuator exposure and security (SPEC-0027 AC2–AC6, BR1/BR2/BR3) against
 * a real Postgres with the real Spring Security chain mounted.
 *
 * <p>Public probes (health) are reachable without a token; operational telemetry (prometheus,
 * metrics) requires ROLE_IT — anonymous is 401, a token without ROLE_IT is 403, a ROLE_IT token is
 * 200 in Prometheus exposition format. Endpoints that leak internals (env, beans) are not in the
 * include set, so they 404 (not exposed). These tests SEND a token, so they run the genuine
 * security chain — the test-actor shortcut applies only when there is no Authorization header.
 *
 * <p>{@code @AutoConfigureObservability} re-enables metrics export under {@code @SpringBootTest}
 * (Spring Boot disables it by default in tests), so the real {@code /actuator/prometheus} scrape
 * endpoint is registered exactly as it is at runtime.
 */
@AutoConfigureObservability
class ActuatorExposureIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void healthIsPublic() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\"").contains("UP");
  }

  @Test
  void prometheusWithoutTokenIsUnauthorized() {
    // No Authorization header → the test-actor shortcut would authenticate it, so force a clearly
    // invalid bearer token to exercise the genuine 401 path of the real security chain.
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("not-a-valid-token");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void prometheusWithNonItRoleIsForbidden() {
    String viewer = login("viewer"); // ROLE_VIEWER, not ROLE_IT

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(bearer(viewer)), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void prometheusWithItRoleReturnsExpositionFormat() {
    String it = login("it"); // ROLE_IT

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(bearer(it)), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    // Prometheus exposition format with the technical series Micrometer registers out of the box.
    assertThat(body).contains("jvm_memory_used_bytes");
    assertThat(body).contains("http_server_requests");
    // AC8: the common application tag is attached to every meter.
    assertThat(body).contains("application=\"acme-travel-erp\"");
  }

  @Test
  void envAndBeansAreNotExposed() {
    String it = login("it"); // even ROLE_IT cannot reach what is not in the include set

    ResponseEntity<String> env =
        restTemplate.exchange(
            "/actuator/env", HttpMethod.GET, new HttpEntity<>(bearer(it)), String.class);
    ResponseEntity<String> beans =
        restTemplate.exchange(
            "/actuator/beans", HttpMethod.GET, new HttpEntity<>(bearer(it)), String.class);

    assertThat(env.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(beans.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String login(String username) {
    LoginResponse body =
        restTemplate
            .postForEntity(
                "/api/identity/login", new LoginRequest(username, "dev12345"), LoginResponse.class)
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
