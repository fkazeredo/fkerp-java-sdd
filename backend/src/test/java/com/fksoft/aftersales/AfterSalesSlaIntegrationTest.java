package com.fksoft.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.aftersales.OpenCaseCommand;
import com.fksoft.domain.aftersales.SupportCaseStatus;
import com.fksoft.domain.aftersales.SupportCaseType;
import com.fksoft.domain.aftersales.SupportCaseView;
import com.fksoft.domain.commercialpolicy.CommercialPolicyService;
import com.fksoft.domain.commercialpolicy.DefineRuleCommand;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueType;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the SLA tracking of AfterSales (SPEC-0018 BR4; DL-0052/DL-0053): the breach
 * sweep with a <strong>controlled clock</strong> (the evaluation instant is a parameter, like
 * {@code BookingService.expirePendingBookings}) — within-SLA vs breached for the
 * first-response/resolution/ refund deadlines — and the proof that a <strong>Directive override
 * changes the effective SLA</strong> resolved from the CommercialPolicy precedence engine. Runs
 * against a real Postgres (Testcontainers).
 */
class AfterSalesSlaIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AfterSalesService afterSalesService;
  @Autowired private CommercialPolicyService policyService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM support_cases");
    jdbcTemplate.execute("DELETE FROM parameter_rules WHERE defined_by <> 'system-seed'");
  }

  @Test
  void doesNotBreachAStandardCaseWhileWithinTheResolutionSla() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.COMPLAINT, "dentro do SLA"), "agent");
    // Pick it up (first response given) so the governing deadline is now resolution (72h).
    afterSalesService.transition(opened.id(), SupportCaseStatus.IN_PROGRESS, "agent");

    // Evaluate one second BEFORE the resolution deadline → within SLA, no breach.
    int breached = afterSalesService.markBreaches(opened.dueAt().minusSeconds(1));

    assertThat(breached).isZero();
    assertThat(afterSalesService.getById(opened.id()).breached()).isFalse();
  }

  @Test
  void breachesAStandardResolutionSlaWhenTheDeadlinePassed() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.COMPLAINT, "estourou"), "agent");
    afterSalesService.transition(opened.id(), SupportCaseStatus.IN_PROGRESS, "agent");

    // Evaluate one second AFTER the resolution deadline → breached, alert flag set.
    int breached = afterSalesService.markBreaches(opened.dueAt().plusSeconds(1));

    assertThat(breached).isEqualTo(1);
    SupportCaseView after = afterSalesService.getById(opened.id());
    assertThat(after.breached()).isTrue();
    // Non-blocking: the workflow status is untouched (BR4 — alert, not block).
    assertThat(after.status()).isEqualTo(SupportCaseStatus.IN_PROGRESS);
  }

  @Test
  void breachesTheTighterRefundSlaForACancellationOrRefundCase() {
    SupportCaseView refundCase =
        afterSalesService.open(
            new OpenCaseCommand("b80", SupportCaseType.REFUND_REQUEST, "reembolso"), "agent");
    afterSalesService.transition(refundCase.id(), SupportCaseStatus.IN_PROGRESS, "agent");
    // The refund SLA is 48h (vs 72h standard): a case is breached past its own (tighter) deadline.
    assertThat(Duration.between(refundCase.openedAt(), refundCase.dueAt()))
        .isEqualTo(Duration.ofHours(48));

    assertThat(afterSalesService.markBreaches(refundCase.dueAt().minusSeconds(1))).isZero();
    assertThat(afterSalesService.markBreaches(refundCase.dueAt().plusSeconds(1))).isEqualTo(1);
  }

  @Test
  void firstResponseDeadlineIsTighterThanResolution() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.CHANGE_REQUEST, null), "agent");
    // First response (24h) is due well before resolution (72h) — the SLA exposes both deadlines.
    assertThat(opened.firstResponseDueAt()).isBefore(opened.dueAt());
    assertThat(Duration.between(opened.openedAt(), opened.firstResponseDueAt()))
        .isEqualTo(Duration.ofHours(24));
  }

  @Test
  void breachesTheFirstResponseSlaWhileStillOpenBeforeResolutionIsDue() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.COMPLAINT, "sem 1a resposta"), "agent");

    // Evaluate AFTER the 24h first-response deadline but BEFORE the 72h resolution deadline.
    int breached = afterSalesService.markBreaches(opened.firstResponseDueAt().plusSeconds(1));

    assertThat(breached).isEqualTo(1);
    assertThat(afterSalesService.getById(opened.id()).breached()).isTrue();
  }

  @Test
  void theSweepIsIdempotentAndSkipsResolvedCases() {
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.COMPLAINT, null), "agent");

    assertThat(afterSalesService.markBreaches(opened.dueAt().plusSeconds(1))).isEqualTo(1);
    // A second sweep does not re-flag the same case (idempotent — no duplicate alert).
    assertThat(afterSalesService.markBreaches(opened.dueAt().plusSeconds(2))).isZero();
  }

  @Test
  void aDirectiveOverrideChangesTheEffectiveResolutionSla() {
    // A director issues a Directive shortening the standard resolution SLA from 72h to 1h.
    policyService.defineRule(
        new DefineRuleCommand(
            AfterSalesService.SLA_RESOLUTION_KEY,
            ParameterLayer.DIRECTIVE,
            ParameterScope.global(),
            "1",
            ParameterValueType.NUMBER,
            null,
            null,
            "tighten after-sales resolution SLA for the quarter"),
        Set.of("ROLE_DIRECTOR"),
        "director");

    // A new standard case now uses the OVERRIDDEN 1h deadline, not the 72h system default.
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand("b71", SupportCaseType.COMPLAINT, "sob diretiva"), "agent");

    assertThat(Duration.between(opened.openedAt(), opened.dueAt())).isEqualTo(Duration.ofHours(1));

    // Within the (tightened) 1h: not breached. Past it: breached — the policy drives the SLA.
    assertThat(afterSalesService.markBreaches(opened.dueAt().minusSeconds(1))).isZero();
    assertThat(afterSalesService.markBreaches(opened.dueAt().plusSeconds(1))).isEqualTo(1);
  }
}
