package com.fksoft.domain.people;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain service that computes a period journey and detects discrepancies from operational
 * point data (SPEC-0022 BR3/BR4; DL-0070/DL-0071). It is the testable core the spec asks for
 * (datas/timezone), free of persistence and framework: given the operational worked minutes, the
 * contracted minutes for the period and the expected/actual punch counts, it returns the time-bank
 * balance and the discrepancy kinds.
 *
 * <p>Time-bank (DL-0070): {@code balance = worked - contracted}; positive is extras, negative is
 * faltas (a negative bank is allowed by the CLT). The calculator does <strong>not</strong>
 * liquidate extras nor pay the +50% premium — that is folha (out of scope); it only measures the
 * balance.
 *
 * <p>Discrepancies (DL-0071), an alert never a correction (BR4):
 *
 * <ul>
 *   <li>{@link DiscrepancyKindCodes#ODD_PUNCH} — an odd number of captured punches (an entry
 *       without its matching exit);
 *   <li>{@link DiscrepancyKindCodes#MISSING_PUNCH} — fewer captured punches than expected for the
 *       period;
 *   <li>{@link DiscrepancyKindCodes#INCOHERENT_JOURNAL} — a journey that cannot be true: punches
 *       present but no worked time, or worked time beyond a sane per-period ceiling.
 * </ul>
 */
public final class JourneyCalculator {

  /** A sane per-day ceiling (minutes); a period beyond {@code days * this} is incoherent. */
  private static final int MAX_MINUTES_PER_DAY = 24 * 60;

  /** A conservative upper bound on a month's calendar days, used for the sanity ceiling. */
  private static final int MAX_DAYS_IN_PERIOD = 31;

  private JourneyCalculator() {}

  /**
   * Computes the journey and discrepancies for a period.
   *
   * @param workedMinutes the operational worked minutes (>= 0)
   * @param contractedMinutes the contracted minutes for the period (>= 0)
   * @param expectedPunches the number of punches expected for the period (>= 0)
   * @param actualPunches the number of punches actually captured (>= 0)
   * @return the computation (balance + discrepancy kinds)
   * @throws JourneyInvalidException when a count is negative
   */
  public static JourneyComputation compute(
      int workedMinutes, int contractedMinutes, int expectedPunches, int actualPunches) {
    if (workedMinutes < 0 || contractedMinutes < 0 || expectedPunches < 0 || actualPunches < 0) {
      throw new JourneyInvalidException();
    }
    int balance = workedMinutes - contractedMinutes;

    List<String> discrepancies = new ArrayList<>();
    if (actualPunches % 2 != 0) {
      discrepancies.add(DiscrepancyKindCodes.ODD_PUNCH);
    }
    if (actualPunches < expectedPunches) {
      discrepancies.add(DiscrepancyKindCodes.MISSING_PUNCH);
    }
    if (isIncoherent(workedMinutes, actualPunches)) {
      discrepancies.add(DiscrepancyKindCodes.INCOHERENT_JOURNAL);
    }
    return new JourneyComputation(workedMinutes, contractedMinutes, balance, discrepancies);
  }

  /**
   * A journey is incoherent when there are punches but no worked time at all, or the worked time
   * exceeds a sane per-period ceiling (a typo/parse fault in the operational data).
   */
  private static boolean isIncoherent(int workedMinutes, int actualPunches) {
    boolean punchesButNoWork = actualPunches > 0 && workedMinutes == 0;
    boolean beyondCeiling = workedMinutes > MAX_DAYS_IN_PERIOD * MAX_MINUTES_PER_DAY;
    return punchesButNoWork || beyondCeiling;
  }
}
