package com.fksoft.domain.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link JourneyCalculator} (SPEC-0022 BR3/BR4; DL-0070/DL-0071): the
 * time-bank balance (extras/faltas, negative bank) and the discrepancy detection (odd/missing
 * punch, incoherent journal). No infrastructure — the testable core the spec asks for.
 */
class JourneyCalculatorTest {

  @Test
  void computesPositiveBalanceWhenWorkedExceedsContracted() {
    // 176:20 worked vs 176:00 contracted = +00:20 (the spec example).
    JourneyComputation c = JourneyCalculator.compute(176 * 60 + 20, 176 * 60, 40, 40);
    assertThat(c.balanceMinutes()).isEqualTo(20);
    assertThat(c.hasDiscrepancies()).isFalse();
  }

  @Test
  void computesNegativeBalanceAsFaltas() {
    JourneyComputation c = JourneyCalculator.compute(170 * 60, 176 * 60, 40, 40);
    assertThat(c.balanceMinutes()).isEqualTo(-6 * 60);
    assertThat(TimeFormat.signedHhmm(c.balanceMinutes())).isEqualTo("-06:00");
    assertThat(c.hasDiscrepancies()).isFalse();
  }

  @Test
  void detectsOddPunch() {
    JourneyComputation c = JourneyCalculator.compute(8 * 60, 8 * 60, 4, 3);
    assertThat(c.discrepancies())
        .contains(DiscrepancyKindCodes.ODD_PUNCH, DiscrepancyKindCodes.MISSING_PUNCH);
  }

  @Test
  void detectsMissingPunchEvenWhenEven() {
    JourneyComputation c = JourneyCalculator.compute(8 * 60, 8 * 60, 6, 4);
    assertThat(c.discrepancies()).containsExactly(DiscrepancyKindCodes.MISSING_PUNCH);
  }

  @Test
  void detectsIncoherentJournalWhenPunchesButNoWork() {
    JourneyComputation c = JourneyCalculator.compute(0, 8 * 60, 2, 2);
    assertThat(c.discrepancies()).containsExactly(DiscrepancyKindCodes.INCOHERENT_JOURNAL);
  }

  @Test
  void cleanEvenFullJourneyHasNoDiscrepancy() {
    JourneyComputation c = JourneyCalculator.compute(8 * 60, 8 * 60, 2, 2);
    assertThat(c.hasDiscrepancies()).isFalse();
    assertThat(c.balanceMinutes()).isZero();
  }

  @Test
  void rejectsNegativeCounts() {
    assertThatThrownBy(() -> JourneyCalculator.compute(-1, 480, 2, 2))
        .isInstanceOf(JourneyInvalidException.class);
    assertThatThrownBy(() -> JourneyCalculator.compute(480, 480, 2, -2))
        .isInstanceOf(JourneyInvalidException.class);
  }
}
