package com.fksoft.domain.aftersales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link SupportCase} aggregate (SPEC-0018): the lifecycle state machine (valid
 * + invalid transitions throwing the specific exception), the reopening reopen-count signal, the
 * SLA breach flag (non-blocking, idempotent) and the cost-to-serve accumulation.
 */
class SupportCaseTest {

  private static final Instant OPENED = Instant.parse("2026-06-29T12:00:00Z");
  private static final Instant FIRST_RESPONSE_DUE = OPENED.plus(Duration.ofHours(24));
  private static final Instant DUE = OPENED.plus(Duration.ofHours(72));

  private SupportCase newCase(String type) {
    return SupportCase.open(
        UUID.randomUUID().toString(), type, "summary", FIRST_RESPONSE_DUE, DUE, OPENED, "agent");
  }

  @Test
  void opensInOpenStatusWithTheGivenDeadlines() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.COMPLAINT);
    assertThat(supportCase.status()).isEqualTo(SupportCaseStatus.OPEN);
    assertThat(supportCase.dueAt()).isEqualTo(DUE);
    assertThat(supportCase.isBreached()).isFalse();
    assertThat(supportCase.toView().firstResponseDueAt()).isEqualTo(FIRST_RESPONSE_DUE);
    assertThat(supportCase.costToServe().total()).isEqualTo(Money.zero("BRL"));
  }

  @Test
  void rejectsOpeningWithoutBookingReference() {
    assertThatThrownBy(
            () ->
                SupportCase.open(
                    "  ",
                    SupportCaseTypeCodes.INFO,
                    null,
                    FIRST_RESPONSE_DUE,
                    DUE,
                    OPENED,
                    "agent"))
        .isInstanceOf(SupportCaseInvalidException.class);
  }

  @Test
  void walksTheValidLifecycle() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.CHANGE_REQUEST);
    supportCase.transitionTo(SupportCaseStatus.IN_PROGRESS, OPENED.plusSeconds(60), "agent");
    supportCase.transitionTo(SupportCaseStatus.WAITING, OPENED.plusSeconds(120), "agent");
    supportCase.transitionTo(SupportCaseStatus.IN_PROGRESS, OPENED.plusSeconds(180), "agent");
    assertThat(supportCase.status()).isEqualTo(SupportCaseStatus.IN_PROGRESS);
  }

  @Test
  void rejectsAnInvalidTransitionWithTheSpecificException() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.INFO);
    assertThatThrownBy(
            () ->
                supportCase.transitionTo(SupportCaseStatus.CLOSED, OPENED.plusSeconds(1), "agent"))
        .isInstanceOf(SupportCaseTransitionInvalidException.class);
    assertThat(supportCase.status()).isEqualTo(SupportCaseStatus.OPEN);
  }

  @Test
  void reopeningFromResolvedIncrementsTheReopenCountAndClearsResolution() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.COMPLAINT);
    supportCase.resolve(
        CaseResolutionCodes.RESOLVED_NO_ACTION, null, null, null, OPENED.plusSeconds(60), "agent");
    assertThat(supportCase.status()).isEqualTo(SupportCaseStatus.RESOLVED);

    supportCase.transitionTo(SupportCaseStatus.IN_PROGRESS, OPENED.plusSeconds(120), "agent");
    assertThat(supportCase.status()).isEqualTo(SupportCaseStatus.IN_PROGRESS);
    assertThat(supportCase.costToServe().reopenCount()).isEqualTo(1);
    assertThat(supportCase.toView().resolution()).isNull();
  }

  @Test
  void accumulatesCostToServeFromHandlingAndRefund() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.REFUND_REQUEST);
    supportCase.accrueCost(
        Money.of(new BigDecimal("15.00"), "BRL"), OPENED.plusSeconds(30), "agent");
    supportCase.resolve(
        CaseResolutionCodes.REFUND_APPROVED,
        Money.of(new BigDecimal("5.00"), "BRL"),
        Money.of(new BigDecimal("480.00"), "BRL"),
        UUID.randomUUID(),
        OPENED.plusSeconds(60),
        "agent");

    // handling = 15 + 5 = 20; refund = 480; total = 500.
    assertThat(supportCase.costToServe().handling())
        .isEqualTo(Money.of(new BigDecimal("20.00"), "BRL"));
    assertThat(supportCase.costToServe().total())
        .isEqualTo(Money.of(new BigDecimal("500.00"), "BRL"));
    assertThat(supportCase.toView().linkedPayoutId()).isNotNull();
  }

  @Test
  void marksBreachOnlyWhenDuePassedAndNotTerminalAndIdempotently() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.REFUND_REQUEST);
    // Pick it up so the governing deadline is resolution (DUE), not first response.
    supportCase.transitionTo(SupportCaseStatus.IN_PROGRESS, OPENED.plusSeconds(1), "agent");

    // Within SLA: now before dueAt → not breached.
    assertThat(supportCase.markBreachedIfDue(DUE.minusSeconds(1))).isFalse();
    assertThat(supportCase.isBreached()).isFalse();

    // Past SLA: now after dueAt → breached (newly marked).
    assertThat(supportCase.markBreachedIfDue(DUE.plusSeconds(1))).isTrue();
    assertThat(supportCase.isBreached()).isTrue();

    // Idempotent: a second sweep does not re-mark.
    assertThat(supportCase.markBreachedIfDue(DUE.plusSeconds(2))).isFalse();
  }

  @Test
  void breachesTheFirstResponseDeadlineWhileStillOpen() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.COMPLAINT);
    // While OPEN, the effective deadline is the earlier first-response one (24h), not resolution.
    assertThat(supportCase.effectiveBreachDeadline()).isEqualTo(FIRST_RESPONSE_DUE);

    // Past the first-response deadline but before resolution → already a (first-response) breach.
    assertThat(supportCase.markBreachedIfDue(FIRST_RESPONSE_DUE.plusSeconds(1))).isTrue();
    assertThat(supportCase.isBreached()).isTrue();
  }

  @Test
  void oncePickedUpTheEffectiveDeadlineIsResolution() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.COMPLAINT);
    supportCase.transitionTo(SupportCaseStatus.IN_PROGRESS, OPENED.plusSeconds(60), "agent");
    // No longer OPEN → first response was given; the effective deadline is resolution (72h).
    assertThat(supportCase.effectiveBreachDeadline()).isEqualTo(DUE);
    // Past first-response but before resolution → NOT breached anymore (it was picked up).
    assertThat(supportCase.markBreachedIfDue(FIRST_RESPONSE_DUE.plusSeconds(1))).isFalse();
  }

  @Test
  void doesNotMarkBreachOnAResolvedCase() {
    SupportCase supportCase = newCase(SupportCaseTypeCodes.COMPLAINT);
    supportCase.resolve(
        CaseResolutionCodes.RESOLVED_NO_ACTION, null, null, null, OPENED.plusSeconds(60), "agent");
    assertThat(supportCase.markBreachedIfDue(DUE.plusSeconds(1))).isFalse();
    assertThat(supportCase.isBreached()).isFalse();
  }
}
