package com.fksoft.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the role enforcement and access audit (SPEC-0024 slice 8k-2, DL-0082/0083).
 * Against a real Postgres: a sensitive action requires the corresponding role (the security
 * boundary regression — the dev stub used to let everyone through), denials are audited, and the
 * access-audit endpoint is itself protected.
 *
 * <p>These tests SEND a token for a specific user, so they run the genuine security chain — the
 * test-actor shortcut applies only when there is no Authorization header.
 */
class AccessControlIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  @Test
  void issuingAnInvoiceWithoutTheFinanceRoleIsForbiddenAndAudited() {
    // 'director' has ROLE_DIRECTOR but NOT ROLE_FINANCE — the sensitive NF issuance must be denied.
    String token = login("director");

    ResponseEntity<ApiErrorResponse> denied =
        restTemplate.exchange(
            "/api/billing/invoices/00000000-0000-0000-0000-000000000000/issue",
            HttpMethod.POST,
            new HttpEntity<>(bearer(token)),
            ApiErrorResponse.class);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(denied.getBody()).isNotNull();
    assertThat(denied.getBody().code()).isEqualTo("access.denied");

    // BR3/DL-0083: the denial is in the append-only system audit as ACCESS_DENIED, actor=director.
    Integer denials =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM system_audit WHERE type = 'ACCESS_DENIED' AND actor = 'director'",
            Integer.class);
    assertThat(denials).isEqualTo(1);
    String detail =
        jdbcTemplate.queryForObject(
            "SELECT detail_json::text FROM system_audit WHERE type='ACCESS_DENIED' AND actor='director'",
            String.class);
    assertThat(detail).contains("DENY").contains("/api/billing/invoices");
  }

  @Test
  void issuingAnInvoiceWithTheFinanceRolePassesTheSecurityGate() {
    // 'finance' has ROLE_FINANCE — the request passes the security gate (and then 404s on the fake
    // id, which proves it reached the controller — it was NOT blocked at 403).
    String token = login("finance");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/billing/invoices/00000000-0000-0000-0000-000000000000/issue",
            HttpMethod.POST,
            new HttpEntity<>(bearer(token)),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("billing.invoice.not-found");
  }

  @Test
  void triggeringAJobRequiresTheItRole() {
    String director = login("director"); // no ROLE_IT
    String it = login("it"); // has ROLE_IT

    ResponseEntity<ApiErrorResponse> denied =
        restTemplate.exchange(
            "/api/platform/jobs/certificate-expiry/trigger",
            HttpMethod.POST,
            new HttpEntity<>(bearer(director)),
            ApiErrorResponse.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> allowed =
        restTemplate.exchange(
            "/api/platform/jobs/certificate-expiry/trigger",
            HttpMethod.POST,
            new HttpEntity<>(bearer(it)),
            String.class);
    assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void issuingADirectiveRequiresTheDirectorRole() {
    String finance = login("finance"); // no ROLE_DIRECTOR

    ResponseEntity<ApiErrorResponse> denied =
        restTemplate.exchange(
            "/api/commercial-policy/directives",
            HttpMethod.POST,
            new HttpEntity<>(directiveBody(), jsonBearer(finance)),
            ApiErrorResponse.class);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(denied.getBody().code()).isEqualTo("access.denied");
  }

  @Test
  void accessAuditAndRolesEndpointsAreThemselvesProtected() {
    String viewer = login("viewer"); // neither DIRECTOR nor IT
    String it = login("it");

    ResponseEntity<ApiErrorResponse> deniedAudit =
        restTemplate.exchange(
            "/api/identity/access-audit",
            HttpMethod.GET,
            new HttpEntity<>(bearer(viewer)),
            ApiErrorResponse.class);
    assertThat(deniedAudit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> allowedRoles =
        restTemplate.exchange(
            "/api/identity/roles", HttpMethod.GET, new HttpEntity<>(bearer(it)), String.class);
    assertThat(allowedRoles.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(allowedRoles.getBody()).contains("ROLE_DIRECTOR").contains("billing:invoice:issue");
  }

  @Test
  void loginIsRecordedInTheAccessAudit() {
    login("finance");

    Integer logins =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM system_audit WHERE type = 'AUTH_LOGIN' AND actor = 'finance'",
            Integer.class);
    assertThat(logins).isGreaterThanOrEqualTo(1);
    // BR4: the audit never carries the password/token.
    String detail =
        jdbcTemplate.queryForObject(
            "SELECT detail_json::text FROM system_audit WHERE type='AUTH_LOGIN' AND actor='finance' LIMIT 1",
            String.class);
    assertThat(detail).doesNotContain("dev12345").contains("LOGIN_SUCCESS");
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

  private static String directiveBody() {
    return "{\"key\":\"MARKUP_PCT\",\"value\":\"0.05\",\"type\":\"PERCENT\","
        + "\"validFrom\":\"2026-06-01\",\"justification\":\"x\"}";
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static HttpHeaders jsonBearer(String token) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return headers;
  }
}
