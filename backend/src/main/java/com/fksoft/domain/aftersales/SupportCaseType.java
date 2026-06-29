package com.fksoft.domain.aftersales;

/**
 * The kind of after-sales case (SPEC-0018 Scope). A {@code CANCELLATION_REQUEST} resolved as
 * "cancel" drives the Booking cancellation (SPEC-0010); a {@code REFUND_REQUEST} approved drives a
 * Payout REFUND (SPEC-0017). The type also selects which governed SLA deadline applies (DL-0052):
 * cancellation/refund cases use the tighter {@code AFTERSALES_SLA_REFUND} (48h), the others use
 * {@code AFTERSALES_SLA_RESOLUTION} (72h).
 */
public enum SupportCaseType {
  COMPLAINT,
  CHANGE_REQUEST,
  CANCELLATION_REQUEST,
  REFUND_REQUEST,
  INFO;

  /**
   * Whether this type uses the tighter cancellation/refund SLA deadline (48h) rather than the
   * standard resolution deadline (72h) — BR1/DL-0052.
   */
  public boolean usesRefundSla() {
    return this == CANCELLATION_REQUEST || this == REFUND_REQUEST;
  }
}
