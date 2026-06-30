package com.fksoft.people;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.people.EmployeeView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for collaborator registration (SPEC-0022 BR1, slice 8i-1) against real Postgres.
 * Covers: a valid registration is 201 ACTIVE and readable (200); a duplicate identifier is 409
 * {@code people.employee.duplicate}; a malformed contracted journey is 400 {@code
 * people.employee.invalid}; an unknown id is 404; the listing filters by status.
 */
class EmployeeApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM employees");
  }

  @Test
  void registersAnActiveEmployeeReadableViaTheApi() {
    ResponseEntity<EmployeeView> created =
        restTemplate.postForEntity(
            "/api/people/employees",
            Map.of(
                "identifier", "col-0012",
                "admissionDate", "2025-03-01",
                "contractedJourney", "08:00"),
            EmployeeView.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().status().name()).isEqualTo("ACTIVE");
    assertThat(created.getBody().contractedJourney()).isEqualTo("08:00");

    ResponseEntity<EmployeeView> fetched =
        restTemplate.getForEntity(
            "/api/people/employees/" + created.getBody().id(), EmployeeView.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody().identifier()).isEqualTo("col-0012");
  }

  @Test
  void rejectsADuplicateIdentifierWith409() {
    Map<String, Object> body =
        Map.of(
            "identifier", "col-dup", "admissionDate", "2025-01-10", "contractedJourney", "08:00");
    restTemplate.postForEntity("/api/people/employees", body, EmployeeView.class);

    ResponseEntity<ApiErrorResponse> dup =
        restTemplate.postForEntity("/api/people/employees", body, ApiErrorResponse.class);

    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(dup.getBody()).isNotNull();
    assertThat(dup.getBody().code()).isEqualTo("people.employee.duplicate");
  }

  @Test
  void rejectsAMalformedContractedJourneyWith400() {
    ResponseEntity<ApiErrorResponse> bad =
        restTemplate.postForEntity(
            "/api/people/employees",
            Map.of(
                "identifier", "col-bad",
                "admissionDate", "2025-01-10",
                "contractedJourney", "8 horas"),
            ApiErrorResponse.class);

    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(bad.getBody()).isNotNull();
    assertThat(bad.getBody().code()).isEqualTo("people.employee.invalid");
  }

  @Test
  void unknownEmployeeIdIs404() {
    ResponseEntity<ApiErrorResponse> notFound =
        restTemplate.getForEntity(
            "/api/people/employees/" + UUID.randomUUID(), ApiErrorResponse.class);
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFound.getBody()).isNotNull();
    assertThat(notFound.getBody().code()).isEqualTo("people.employee.not-found");
  }

  @Test
  void listsFilteringByStatus() {
    restTemplate.postForEntity(
        "/api/people/employees",
        Map.of("identifier", "col-a", "admissionDate", "2025-01-10", "contractedJourney", "08:00"),
        EmployeeView.class);

    ResponseEntity<String> active =
        restTemplate.getForEntity("/api/people/employees?status=ACTIVE&size=10", String.class);
    assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(active.getBody()).contains("col-a");

    ResponseEntity<String> terminated =
        restTemplate.getForEntity("/api/people/employees?status=TERMINATED&size=10", String.class);
    assertThat(terminated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(terminated.getBody()).doesNotContain("col-a");
  }
}
