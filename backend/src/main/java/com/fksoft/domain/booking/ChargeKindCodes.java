package com.fksoft.domain.booking;

/**
 * The {@code CHARGE_KIND} code constants whose behavior the domain wires (SPEC-0031 BR5; DL-0117).
 * After {@code ChargeKind} became an editable cadastro, the kind still identifies the obligation a
 * cancellation/no-show produces (SPEC-0010 BR5/BR6) and drives the AP/AR posting in Finance
 * (SPEC-0015 BR5, DL-0041). These are distinct facts that NEVER net out against each other
 * (BR11/DL-0024): under ALL_SALES_FINAL the {@link #SUPPLIER} cost and the {@link #CUSTOMER_REFUND}
 * coexist — the merchant trap. The cadastro owns the extensible set + labels; this class owns the
 * wired behavior (the Finance posting branches on these four codes).
 */
public final class ChargeKindCodes {

  /** The cancellation fee computed from the applicable window. */
  public static final String PENALTY = "PENALTY";

  /** What is owed to the supplier/marketplace (irrecoverable under ALL_SALES_FINAL). */
  public static final String SUPPLIER = "SUPPLIER";

  /** What is refunded to the customer (a commercial decision). */
  public static final String CUSTOMER_REFUND = "CUSTOMER_REFUND";

  /** The no-show fee. */
  public static final String NO_SHOW = "NO_SHOW";

  private ChargeKindCodes() {}
}
