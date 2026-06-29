package com.fksoft.domain.booking;

import java.math.BigDecimal;

/**
 * A penalty window of a {@link CancellationPolicy} (BR2): if the service starts within {@code
 * hoursBefore} hours, the penalty is {@code penaltyPct} of the reference amount. Windows are
 * validated on construction; a malformed window raises {@link CancellationPolicyInvalidException}.
 *
 * @param hoursBefore the upper bound (in hours) of "hours until service" this window applies to
 *     (non-negative)
 * @param penaltyPct the penalty fraction in [0, 1] (e.g. {@code 0.50} for 50%)
 */
public record PenaltyWindow(int hoursBefore, BigDecimal penaltyPct) {

  public PenaltyWindow {
    if (hoursBefore < 0) {
      throw new CancellationPolicyInvalidException();
    }
    if (penaltyPct == null || penaltyPct.signum() < 0 || penaltyPct.compareTo(BigDecimal.ONE) > 0) {
      throw new CancellationPolicyInvalidException();
    }
  }
}
