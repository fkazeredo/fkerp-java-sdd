package com.fksoft.domain.aftersales;

/**
 * The {@code SUPPORT_CASE_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0118). After {@code SupportCaseType} became an editable cadastro, the type still selects which
 * governed SLA deadline applies (DL-0052): {@link #CANCELLATION_REQUEST}/{@link #REFUND_REQUEST}
 * use the tighter refund SLA (48h), the others use the standard resolution SLA (72h). A case opened
 * with an unknown/inactive type is rejected (422) on write, so the SLA selection only ever sees a
 * known type. The cadastro owns the extensible set + labels; this class owns the wired behavior
 * ({@link #usesRefundSla}).
 */
public final class SupportCaseTypeCodes {

  /** A complaint (standard resolution SLA). */
  public static final String COMPLAINT = "COMPLAINT";

  /** A change request (standard resolution SLA). */
  public static final String CHANGE_REQUEST = "CHANGE_REQUEST";

  /** A cancellation request — uses the tighter refund SLA (48h). */
  public static final String CANCELLATION_REQUEST = "CANCELLATION_REQUEST";

  /** A refund request — uses the tighter refund SLA (48h). */
  public static final String REFUND_REQUEST = "REFUND_REQUEST";

  /** An information request (standard resolution SLA). */
  public static final String INFO = "INFO";

  private SupportCaseTypeCodes() {}

  /**
   * Whether this type uses the tighter cancellation/refund SLA deadline (48h) rather than the
   * standard resolution deadline (72h) — preserves the old {@code SupportCaseType.usesRefundSla()}
   * (BR1/DL-0052). An unknown type (a new cadastro item) uses the standard SLA (safe default).
   *
   * @param type the support-case-type cadastro code
   * @return whether the type uses the refund SLA
   */
  public static boolean usesRefundSla(String type) {
    return CANCELLATION_REQUEST.equals(type) || REFUND_REQUEST.equals(type);
  }
}
