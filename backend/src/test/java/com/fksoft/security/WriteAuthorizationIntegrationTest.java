package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression tests for the default-deny authorization matrix (SPEC-0024 Phase 19a, DL-0119). Before
 * 19a, every write under {@code /api/**} not in the small DL-0082 list fell through to "any
 * authenticated user": a VIEWER could pin the sell rate, create and execute payouts, purge vault
 * documents — and the point AFD/crawl endpoints were reachable with NO credentials at all (blanket
 * permitAll on {@code /api/integration/**}). These tests fail on the pre-19a chain.
 *
 * <p>Tokens are minted with specific roles so the genuine JWT chain runs (the no-header test-actor
 * shortcut only applies to requests without an {@code Authorization} header — DL-0081/DL-0105).
 */
class WriteAuthorizationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  // --- VIEWER writes nothing (DL-0119) ---

  @Test
  void viewerCannotPinTheSellRate() {
    assertForbidden(post("/api/exchange/pinned-rates", viewer(), "{}"));
  }

  @Test
  void viewerCannotCreateOrExecutePayouts() {
    assertForbidden(post("/api/payouts", viewer(), "{}"));
    assertForbidden(
        post("/api/payouts/00000000-0000-0000-0000-000000000000/execute", viewer(), "{}"));
  }

  @Test
  void viewerCannotPostFinanceEntriesNorPurgeVaultDocuments() {
    assertForbidden(post("/api/finance/entries", viewer(), "{}"));
    ResponseEntity<ApiErrorResponse> purge =
        restTemplate.exchange(
            "/api/compliance/documents/00000000-0000-0000-0000-000000000000",
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(viewer())),
            ApiErrorResponse.class);
    assertThat(purge.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void viewerCannotDriveTheBookingLifecycle() {
    assertForbidden(post("/api/bookings", viewer(), "{}"));
    assertForbidden(
        post("/api/bookings/00000000-0000-0000-0000-000000000000/cancel", viewer(), "{}"));
  }

  // --- The director lever stays with the director ---

  @Test
  void operationsCannotPinTheSellRateButDirectorPassesTheGate() {
    assertForbidden(post("/api/exchange/pinned-rates", ops(), "{}"));

    // Director passes the security gate: an empty body is a 400/422 validation error (reached the
    // controller), never a 401/403.
    ResponseEntity<String> director =
        restTemplate.postForEntity(
            "/api/exchange/pinned-rates",
            new HttpEntity<>("{}", jsonWith(director())),
            String.class);
    assertThat(director.getStatusCode().value()).isNotIn(401, 403);
  }

  // --- Point endpoints are no longer anonymous (pre-19a: permitAll without HMAC) ---

  @Test
  void pointCrawlAndAfdRequireAuthenticationAndTheItRole() {
    // An invalid bearer → 401 from the resource server. (A header-less request cannot stand in
    // for "anonymous" here: the test-actor filter authenticates it — DL-0081.)
    ResponseEntity<String> invalidToken =
        restTemplate.postForEntity(
            "/api/integration/point/crawl",
            new HttpEntity<>("{}", jsonWith("not-a-jwt")),
            String.class);
    assertThat(invalidToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // THE 19a regression: a valid token without ROLE_IT → 403. Under the pre-19a blanket
    // permitAll on /api/integration/** this returned 202 for anyone.
    assertForbidden(post("/api/integration/point/crawl", ops(), "{}"));

    // ROLE_IT passes the security gate (any downstream error is a business/validation one).
    ResponseEntity<String> it =
        restTemplate.postForEntity(
            "/api/integration/point/crawl", new HttpEntity<>("{}", jsonWith(it())), String.class);
    assertThat(it.getStatusCode().value()).isNotIn(401, 403);
  }

  // --- Personal data reads are IT-only (LGPD) ---

  @Test
  void peopleReadsRequireTheItRole() {
    ResponseEntity<String> denied =
        restTemplate.exchange(
            "/api/people/employees",
            HttpMethod.GET,
            new HttpEntity<>(bearer(viewer())),
            String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> allowed =
        restTemplate.exchange(
            "/api/people/employees", HttpMethod.GET, new HttpEntity<>(bearer(it())), String.class);
    assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // --- The right desk still passes the gate ---

  @Test
  void operationsPassesTheBookingGateAndFinancePassesThePayoutGate() {
    ResponseEntity<String> booking =
        restTemplate.postForEntity(
            "/api/bookings", new HttpEntity<>("{}", jsonWith(ops())), String.class);
    assertThat(booking.getStatusCode().value()).isNotIn(401, 403);

    ResponseEntity<String> payout =
        restTemplate.postForEntity(
            "/api/payouts", new HttpEntity<>("{}", jsonWith(finance())), String.class);
    assertThat(payout.getStatusCode().value()).isNotIn(401, 403);
  }

  // --- helpers ---

  private static String viewer() {
    return TestJwtTokens.mint("viewer", "ROLE_VIEWER");
  }

  private static String ops() {
    return TestJwtTokens.mint("ops", "ROLE_OPERATIONS");
  }

  private static String finance() {
    return TestJwtTokens.mint("finance", "ROLE_FINANCE");
  }

  private static String director() {
    return TestJwtTokens.mint("director", "ROLE_DIRECTOR");
  }

  private static String it() {
    return TestJwtTokens.mint("it", "ROLE_IT");
  }

  private ResponseEntity<ApiErrorResponse> post(String url, String token, String body) {
    return restTemplate.postForEntity(
        url, new HttpEntity<>(body, jsonWith(token)), ApiErrorResponse.class);
  }

  private static void assertForbidden(ResponseEntity<ApiErrorResponse> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("access.denied");
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static HttpHeaders json() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders jsonWith(String token) {
    HttpHeaders headers = json();
    headers.setBearerAuth(token);
    return headers;
  }
}
