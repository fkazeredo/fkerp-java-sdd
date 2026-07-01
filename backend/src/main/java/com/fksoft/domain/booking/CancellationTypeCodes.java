package com.fksoft.domain.booking;

/**
 * The {@code CANCELLATION_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0117). After {@code CancellationType} became an editable cadastro, the type still drives the
 * whole cancellation behavior (SPEC-0010; DL-0024/DL-0010): {@link #STANDARD}/{@link #CUSTOM}
 * compute a penalty from the applicable window (BR2/BR4), while {@link #ALL_SALES_FINAL} emits the
 * supplier cost and the customer refund as <em>separate</em> charges that never net out — the
 * merchant trap (BR3/BR5/BR11). These three codes are load-bearing: a policy created with an
 * unknown/inactive type is rejected (422) on write, so the penalty/no-netting logic only ever sees
 * a known type.
 *
 * <p>The cadastro owns the extensible set + labels; this class owns the wired behavior — including
 * {@link #usesWindows(String)}, which reproduces the old {@code CancellationType.usesWindows()} so
 * the penalty math stays identical.
 */
public final class CancellationTypeCodes {

  /** Penalty by the applicable window (BR2); no applicable window ⇒ penalty 0. */
  public static final String STANDARD = "STANDARD";

  /**
   * Refundable = false to the supplier: the supplier cost is fully due even when the customer is
   * refunded (the merchant trap — BR3/BR5).
   */
  public static final String ALL_SALES_FINAL = "ALL_SALES_FINAL";

  /** Uses the windows provided; no windows ⇒ behaves as STANDARD with penalty 0 (BR4). */
  public static final String CUSTOM = "CUSTOM";

  private CancellationTypeCodes() {}

  /**
   * Whether the type computes a penalty from windows (STANDARD and CUSTOM do; ALL_SALES_FINAL does
   * not) — preserves the old {@code CancellationType.usesWindows()} (SPEC-0010 BR2/BR3).
   *
   * @param type the cancellation-type cadastro code
   * @return whether the type uses penalty windows
   */
  public static boolean usesWindows(String type) {
    return STANDARD.equals(type) || CUSTOM.equals(type);
  }

  /**
   * Whether the type is ALL_SALES_FINAL — the merchant-trap branch (BR3/BR5): the supplier cost is
   * irrecoverable and coexists with any customer refund (they never net out — BR11/DL-0024).
   *
   * @param type the cancellation-type cadastro code
   * @return whether the type is ALL_SALES_FINAL
   */
  public static boolean isAllSalesFinal(String type) {
    return ALL_SALES_FINAL.equals(type);
  }
}
