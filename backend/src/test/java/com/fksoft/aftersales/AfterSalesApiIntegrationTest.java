package com.fksoft.aftersales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.aftersales.OpenCaseCommand;
import com.fksoft.domain.aftersales.SupportCaseNotFoundException;
import com.fksoft.domain.aftersales.SupportCaseStatus;
import com.fksoft.domain.aftersales.SupportCaseTransitionInvalidException;
import com.fksoft.domain.aftersales.SupportCaseTypeCodes;
import com.fksoft.domain.aftersales.SupportCaseView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for AfterSales case persistence and lifecycle (SPEC-0018 BR1/BR4; V23): opening
 * a case persists it OPEN with the governed SLA deadlines (24h first response / 72h resolution by
 * default, resolved from CommercialPolicy — DL-0052), the state machine drives the valid
 * transitions and rejects invalid ones, and a missing case is a 404. Drives the {@link
 * AfterSalesService} facade against a real Postgres (Testcontainers), proving the schema and the
 * policy-resolution seam.
 */
class AfterSalesApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AfterSalesService afterSalesService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM support_cases");
  }

  @Test
  void openingACasePersistsItOpenWithGovernedSlaDeadlines() {
    SupportCaseView view =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseTypeCodes.COMPLAINT, "voo atrasado"), "agent");

    assertThat(view.status()).isEqualTo(SupportCaseStatus.OPEN);
    assertThat(view.breached()).isFalse();
    // Default SLA from the V23 seed: first response 24h, resolution 72h (COMPLAINT is standard).
    assertThat(Duration.between(view.openedAt(), view.firstResponseDueAt()))
        .isEqualTo(Duration.ofHours(24));
    assertThat(Duration.between(view.openedAt(), view.dueAt())).isEqualTo(Duration.ofHours(72));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT type, status, booking_id FROM support_cases WHERE id = ?", view.id());
    assertThat(row.get("type")).isEqualTo("COMPLAINT");
    assertThat(row.get("status")).isEqualTo("OPEN");
    assertThat(row.get("booking_id")).isEqualTo("b71");
  }

  @Test
  void cancellationAndRefundCasesUseTheTighter48hResolutionSla() {
    SupportCaseView refundCase =
        afterSalesService.open(
            new OpenCaseCommand("b80", SupportCaseTypeCodes.REFUND_REQUEST, "reembolso"), "agent");
    assertThat(Duration.between(refundCase.openedAt(), refundCase.dueAt()))
        .isEqualTo(Duration.ofHours(48));

    SupportCaseView cancelCase =
        afterSalesService.open(
            new OpenCaseCommand("b81", SupportCaseTypeCodes.CANCELLATION_REQUEST, "cancelar"),
            "agent");
    assertThat(Duration.between(cancelCase.openedAt(), cancelCase.dueAt()))
        .isEqualTo(Duration.ofHours(48));
  }

  @Test
  void drivesTheValidLifecycleAndRejectsInvalidTransitions() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseTypeCodes.CHANGE_REQUEST, null), "agent");

    SupportCaseView inProgress =
        afterSalesService.transition(opened.id(), SupportCaseStatus.IN_PROGRESS, "agent");
    assertThat(inProgress.status()).isEqualTo(SupportCaseStatus.IN_PROGRESS);

    SupportCaseView waiting =
        afterSalesService.transition(opened.id(), SupportCaseStatus.WAITING, "agent");
    assertThat(waiting.status()).isEqualTo(SupportCaseStatus.WAITING);

    // Invalid: WAITING cannot jump straight to CLOSED.
    assertThatThrownBy(
            () -> afterSalesService.transition(opened.id(), SupportCaseStatus.CLOSED, "agent"))
        .isInstanceOf(SupportCaseTransitionInvalidException.class);
  }

  @Test
  void aMissingCaseIsNotFound() {
    assertThatThrownBy(() -> afterSalesService.getById(UUID.randomUUID()))
        .isInstanceOf(SupportCaseNotFoundException.class);
  }

  @Test
  void listsCasesFilteredByTypeAndStatus() {
    afterSalesService.open(
        new OpenCaseCommand("b71", SupportCaseTypeCodes.COMPLAINT, null), "agent");
    afterSalesService.open(
        new OpenCaseCommand("b72", SupportCaseTypeCodes.REFUND_REQUEST, null), "agent");

    var page =
        afterSalesService.list(
            SupportCaseTypeCodes.COMPLAINT,
            SupportCaseStatus.OPEN,
            null,
            null,
            org.springframework.data.domain.PageRequest.of(0, 20));
    assertThat(page.getTotalElements()).isEqualTo(1);
  }
}
