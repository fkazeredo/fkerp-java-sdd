package com.fksoft.domain.booking;

/**
 * The type of a {@link CancellationPolicy} (SPEC-0010). Behavior differs per type:
 *
 * <ul>
 *   <li>{@link #STANDARD} — penalty by the applicable window (BR2); no applicable window ⇒ penalty
 *       0;
 *   <li>{@link #ALL_SALES_FINAL} — refundable = false to the supplier: the supplier cost is fully
 *       due even when the customer is refunded (the merchant trap — BR3/BR5);
 *   <li>{@link #CUSTOM} — uses the windows provided; no windows ⇒ behaves as STANDARD with penalty
 *       0 (BR4).
 * </ul>
 *
 * External value is the constant name.
 */
public enum CancellationType {
  STANDARD,
  ALL_SALES_FINAL,
  CUSTOM;

  /**
   * Whether this type computes a penalty from windows (STANDARD and CUSTOM do; ALL_SALES_FINAL does
   * not).
   */
  public boolean usesWindows() {
    return this == STANDARD || this == CUSTOM;
  }
}
