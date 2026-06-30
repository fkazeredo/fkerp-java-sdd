package com.fksoft.people;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.people.CollectSnapshotCommand;
import com.fksoft.domain.people.EmployeeView;
import com.fksoft.domain.people.JourneyView;
import com.fksoft.domain.people.PointSnapshotService;
import com.fksoft.domain.people.TimeBankView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for journey/time-bank processing over the operational snapshot (SPEC-0022
 * BR2/BR3/BR4, slice 8i-2) against real Postgres. Covers: processing a journey from a period
 * snapshot persists it and yields the time-bank balance; re-processing the same (employee, period)
 * is idempotent (no duplicate journey, DL-0069); an odd/missing punch raises a discrepancy alert
 * that surfaces in the queue (BR4/DL-0071) without auto-correcting; processing with no snapshot for
 * the period is rejected; and the REGRESSION that processing creates NO legal document — the
 * snapshot is operational only (BR6).
 */
class JourneyApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PointSnapshotService pointSnapshotService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM journey_discrepancies");
    jdbcTemplate.execute("DELETE FROM journeys");
    jdbcTemplate.execute("DELETE FROM employees");
    jdbcTemplate.execute("DELETE FROM point_snapshots");
  }

  private UUID registerEmployee(String identifier, String journey) {
    ResponseEntity<EmployeeView> created =
        restTemplate.postForEntity(
            "/api/people/employees",
            Map.of(
                "identifier",
                identifier,
                "admissionDate",
                "2025-01-10",
                "contractedJourney",
                journey),
            EmployeeView.class);
    return created.getBody().id();
  }

  @Test
  void processesAJourneyFromTheSnapshotAndYieldsTheTimeBank() {
    UUID employeeId = registerEmployee("col-j1", "08:00");
    // 22 working days * 8h = 176h contracted; snapshot exists for the period (operational).
    pointSnapshotService.collect(new CollectSnapshotCommand("REP-SP", "2026-06", "mirror", 40));

    ResponseEntity<JourneyView> processed =
        restTemplate.postForEntity(
            "/api/people/employees/" + employeeId + "/journey",
            Map.of(
                "period",
                "2026-06",
                "sourceRef",
                "REP-SP",
                "workingDays",
                22,
                "workedMinutes",
                176 * 60 + 20,
                "expectedPunches",
                40,
                "actualPunches",
                40),
            JourneyView.class);

    assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(processed.getBody().balance()).isEqualTo("+00:20");

    ResponseEntity<TimeBankView> bank =
        restTemplate.getForEntity(
            "/api/people/employees/" + employeeId + "/timebank?period=2026-06", TimeBankView.class);
    assertThat(bank.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(bank.getBody().workedHours()).isEqualTo("176:20");
    assertThat(bank.getBody().contractedHours()).isEqualTo("176:00");
    assertThat(bank.getBody().balance()).isEqualTo("+00:20");
    assertThat(bank.getBody().discrepancies()).isZero();
  }

  @Test
  void reProcessingTheSamePeriodIsIdempotent() {
    UUID employeeId = registerEmployee("col-j2", "08:00");
    pointSnapshotService.collect(new CollectSnapshotCommand("REP-SP", "2026-06", "mirror", 40));

    restTemplate.postForEntity(
        "/api/people/employees/" + employeeId + "/journey",
        Map.of(
            "period",
            "2026-06",
            "sourceRef",
            "REP-SP",
            "workingDays",
            22,
            "workedMinutes",
            10000,
            "expectedPunches",
            40,
            "actualPunches",
            40),
        JourneyView.class);
    restTemplate.postForEntity(
        "/api/people/employees/" + employeeId + "/journey",
        Map.of(
            "period",
            "2026-06",
            "sourceRef",
            "REP-SP",
            "workingDays",
            22,
            "workedMinutes",
            10560,
            "expectedPunches",
            40,
            "actualPunches",
            40),
        JourneyView.class);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM journeys WHERE employee_id = ?", Integer.class, employeeId);
    assertThat(count).isEqualTo(1); // refreshed in place (DL-0069)
    Integer worked =
        jdbcTemplate.queryForObject(
            "SELECT worked_minutes FROM journeys WHERE employee_id = ?", Integer.class, employeeId);
    assertThat(worked).isEqualTo(10560);
  }

  @Test
  void anOddPunchRaisesADiscrepancyAlertWithoutAutoCorrecting() {
    UUID employeeId = registerEmployee("col-j3", "08:00");
    pointSnapshotService.collect(new CollectSnapshotCommand("REP-SP", "2026-06", "mirror", 39));

    restTemplate.postForEntity(
        "/api/people/employees/" + employeeId + "/journey",
        Map.of(
            "period",
            "2026-06",
            "sourceRef",
            "REP-SP",
            "workingDays",
            22,
            "workedMinutes",
            176 * 60,
            "expectedPunches",
            40,
            "actualPunches",
            39),
        JourneyView.class);

    // The discrepancy is in the queue (BR4) and counted in the time-bank.
    ResponseEntity<String> queue =
        restTemplate.getForEntity(
            "/api/people/discrepancies?period=2026-06&status=OPEN&size=10", String.class);
    assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(queue.getBody()).contains("ODD_PUNCH");

    ResponseEntity<TimeBankView> bank =
        restTemplate.getForEntity(
            "/api/people/employees/" + employeeId + "/timebank?period=2026-06", TimeBankView.class);
    assertThat(bank.getBody().discrepancies()).isGreaterThanOrEqualTo(1);

    // Re-processing does not duplicate the alert (idempotent, DL-0071).
    restTemplate.postForEntity(
        "/api/people/employees/" + employeeId + "/journey",
        Map.of(
            "period",
            "2026-06",
            "sourceRef",
            "REP-SP",
            "workingDays",
            22,
            "workedMinutes",
            176 * 60,
            "expectedPunches",
            40,
            "actualPunches",
            39),
        JourneyView.class);
    Integer odd =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM journey_discrepancies WHERE employee_id = ? AND kind = 'ODD_PUNCH'",
            Integer.class,
            employeeId);
    assertThat(odd).isEqualTo(1);
  }

  @Test
  void processingWithNoSnapshotForThePeriodIsRejected() {
    UUID employeeId = registerEmployee("col-j4", "08:00");
    // No snapshot collected for 2026-06.
    ResponseEntity<ApiErrorResponse> bad =
        restTemplate.postForEntity(
            "/api/people/employees/" + employeeId + "/journey",
            Map.of(
                "period",
                "2026-06",
                "sourceRef",
                "REP-SP",
                "workingDays",
                22,
                "workedMinutes",
                100,
                "expectedPunches",
                2,
                "actualPunches",
                2),
            ApiErrorResponse.class);
    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(bad.getBody().code()).isEqualTo("people.journey.invalid");
  }

  @Test
  void unknownJourneyIs404() {
    UUID employeeId = registerEmployee("col-j5", "08:00");
    ResponseEntity<ApiErrorResponse> notFound =
        restTemplate.getForEntity(
            "/api/people/employees/" + employeeId + "/journey?period=2099-01",
            ApiErrorResponse.class);
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFound.getBody().code()).isEqualTo("people.journey.not-found");
  }

  @Test
  void regressionProcessingCreatesNoLegalDocument() {
    UUID employeeId = registerEmployee("col-j6", "08:00");
    pointSnapshotService.collect(new CollectSnapshotCommand("REP-SP", "2026-06", "mirror", 40));

    restTemplate.postForEntity(
        "/api/people/employees/" + employeeId + "/journey",
        Map.of(
            "period",
            "2026-06",
            "sourceRef",
            "REP-SP",
            "workingDays",
            22,
            "workedMinutes",
            176 * 60,
            "expectedPunches",
            40,
            "actualPunches",
            40),
        JourneyView.class);

    // BR6 regression: the snapshot is operational only — processing the journey must NOT create any
    // legal/retention document in the Compliance vault (that path is the official AFD/AEJ,
    // SPEC-0012,
    // and the payslip archive of slice 8i-3, never the journey).
    Integer docs = jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
    assertThat(docs).isZero();
  }
}
