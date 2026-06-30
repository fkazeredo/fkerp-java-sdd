package com.fksoft.people;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.compliance.DocumentType;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.people.EmployeeView;
import com.fksoft.infra.integration.payroll.PayslipArchivingService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for archiving a collaborator payslip into the Compliance vault (SPEC-0022 BR5,
 * slice 8i-3; DL-0072) against real Postgres. Covers: archiving creates a PAYROLL document with the
 * legal 5-year retention and {@code hasPersonalData=true} (LGPD); archiving for an unknown
 * collaborator is rejected (the orchestrator validates existence first). Drives the {@link
 * PayslipArchivingService} orchestrator (the infra seam People uses, BR6 — People never becomes a
 * vault).
 */
class PayslipArchivingIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PayslipArchivingService payslipArchivingService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM employees");
  }

  private UUID registerEmployee(String identifier) {
    EmployeeView created =
        restTemplate
            .postForEntity(
                "/api/people/employees",
                Map.of(
                    "identifier", identifier,
                    "admissionDate", "2025-01-10",
                    "contractedJourney", "08:00"),
                EmployeeView.class)
            .getBody();
    return created.id();
  }

  @Test
  void archivesAPayslipAsPayrollWithFiveYearRetentionAndPersonalData() {
    UUID employeeId = registerEmployee("col-pay1");
    LocalDate issuedAt = LocalDate.of(2026, 6, 30);

    DocumentView document =
        payslipArchivingService.archive(
            employeeId,
            "HOLERITE 06/2026".getBytes(),
            "holerite-2026-06.pdf",
            "application/pdf",
            issuedAt,
            "2026-06",
            "rh-user");

    // PAYROLL → 5-year retention (RetentionPolicy), personal data flagged (LGPD).
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT type, retention_until, has_personal_data FROM documents WHERE id = ?",
            document.id());
    assertThat(row.get("type")).isEqualTo(DocumentType.PAYROLL.name());
    assertThat(row.get("has_personal_data")).isEqualTo(true);
    LocalDate retentionUntil = ((java.sql.Date) row.get("retention_until")).toLocalDate();
    assertThat(ChronoUnit.YEARS.between(issuedAt, retentionUntil)).isEqualTo(5);
  }

  @Test
  void archivingForAnUnknownEmployeeIsRejected() {
    org.junit.jupiter.api.Assertions.assertThrows(
        com.fksoft.domain.people.EmployeeNotFoundException.class,
        () ->
            payslipArchivingService.archive(
                UUID.randomUUID(),
                "x".getBytes(),
                "x.pdf",
                "application/pdf",
                LocalDate.of(2026, 6, 30),
                "2026-06",
                "rh-user"));
  }
}
