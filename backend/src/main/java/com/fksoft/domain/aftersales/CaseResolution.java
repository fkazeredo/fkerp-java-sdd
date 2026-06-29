package com.fksoft.domain.aftersales;

/**
 * The resolution of an after-sales case (SPEC-0018 API {@code /resolve}; DL-0054). An enum with
 * behavior: it decides whether resolving the case triggers a side effect on a <em>owner</em> module
 * — a Payout REFUND (SPEC-0017) and/or a Booking cancellation (SPEC-0010) — without AfterSales ever
 * computing penalties or posting financials itself (BR2/BR6).
 *
 * <ul>
 *   <li>{@code REFUND_APPROVED} — approves a refund; triggers a Payout REFUND referencing the case
 *       as its origin obligation (BR3).
 *   <li>{@code CANCEL_APPROVED} — approves a cancellation; triggers Booking.cancel, which applies
 *       the SPEC-0010 penalty policy (BR2).
 *   <li>{@code RESOLVED_NO_ACTION} — resolved without any external side effect (info/complaint).
 *   <li>{@code REJECTED} — the request was declined; no side effect.
 * </ul>
 */
public enum CaseResolution {
  REFUND_APPROVED,
  CANCEL_APPROVED,
  RESOLVED_NO_ACTION,
  REJECTED;

  /** Whether resolving with this outcome must trigger a Payout REFUND (BR3). */
  public boolean triggersRefund() {
    return this == REFUND_APPROVED;
  }

  /** Whether resolving with this outcome must trigger a Booking cancellation (BR2). */
  public boolean triggersCancellation() {
    return this == CANCEL_APPROVED;
  }
}
