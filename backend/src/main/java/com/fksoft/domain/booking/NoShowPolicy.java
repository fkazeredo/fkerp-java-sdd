package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;

/**
 * No-show policy (SPEC-0010 BR6): the fee charged on a no-show and whether it is waived with proof
 * of a cancelled flight. A {@code null} {@link #fee()} means no no-show fee is configured (no
 * charge).
 *
 * @param fee the no-show fee, or {@code null} when none is configured
 * @param waivedIfFlightCancelled whether the fee is waived when proof of a cancelled flight is
 *     given
 */
public record NoShowPolicy(Money fee, boolean waivedIfFlightCancelled) {

  /**
   * The no-show charge to apply, given whether proof of a cancelled flight was provided (BR6).
   *
   * @param flightCancelledProof whether proof of a cancelled flight was provided
   * @return the fee to charge, or {@code null} when there is nothing to charge (no fee configured,
   *     or waived by valid proof)
   */
  public Money chargeFor(boolean flightCancelledProof) {
    if (fee == null) {
      return null;
    }
    if (waivedIfFlightCancelled && flightCancelledProof) {
      return null;
    }
    return fee;
  }

  /** Whether the fee would be waived for the given proof (used to report {@code waived}). */
  public boolean isWaived(boolean flightCancelledProof) {
    return fee != null && waivedIfFlightCancelled && flightCancelledProof;
  }

  /** A policy with no no-show fee configured. */
  public static NoShowPolicy none() {
    return new NoShowPolicy(null, false);
  }
}
