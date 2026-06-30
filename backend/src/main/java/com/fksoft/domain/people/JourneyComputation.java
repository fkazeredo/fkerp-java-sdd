package com.fksoft.domain.people;

import java.util.List;

/**
 * The pure result of computing a period journey (SPEC-0022 BR3/BR4; DL-0070/DL-0071): the worked
 * and contracted minutes, the time-bank balance and the detected discrepancy kinds (alerts). A
 * value object with no persistence — the output of {@link JourneyCalculator}, which the application
 * service turns into stored journey/discrepancy rows and events.
 *
 * @param workedMinutes the operational worked minutes
 * @param contractedMinutes the contracted minutes for the period
 * @param balanceMinutes the balance ({@code worked - contracted}; positive = extras, negative =
 *     faltas)
 * @param discrepancies the discrepancy kinds detected (possibly empty; never null)
 */
public record JourneyComputation(
    int workedMinutes,
    int contractedMinutes,
    int balanceMinutes,
    List<DiscrepancyKind> discrepancies) {

  /** Whether any discrepancy was detected (an alert is due). */
  public boolean hasDiscrepancies() {
    return !discrepancies.isEmpty();
  }
}
