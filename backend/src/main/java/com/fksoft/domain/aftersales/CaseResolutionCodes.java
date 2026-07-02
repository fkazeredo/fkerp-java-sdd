package com.fksoft.domain.aftersales;

/**
 * The {@code CASE_RESOLUTION} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0118). After {@code CaseResolution} became an editable cadastro, the resolution still decides
 * whether resolving the case triggers a side effect on an <em>owner</em> module — a Payout REFUND
 * (SPEC-0017) and/or a Booking cancellation (SPEC-0010) — without AfterSales ever computing
 * penalties or posting financials itself (BR2/BR6; DL-0054). A case resolved with an
 * unknown/inactive resolution is rejected (422) on write, so the orchestration only ever sees a
 * known resolution. The cadastro owns the extensible set + labels; this class owns the wired
 * behavior ({@link #triggersRefund}/{@link #triggersCancellation}).
 *
 * <ul>
 *   <li>{@link #REFUND_APPROVED} — approves a refund; triggers a Payout REFUND referencing the case
 *       as its origin obligation (BR3).
 *   <li>{@link #CANCEL_APPROVED} — approves a cancellation; triggers Booking.cancel, which applies
 *       the SPEC-0010 penalty policy (BR2).
 *   <li>{@link #RESOLVED_NO_ACTION} — resolved without any external side effect (info/complaint).
 *   <li>{@link #REJECTED} — the request was declined; no side effect.
 * </ul>
 */
public final class CaseResolutionCodes {

  /** Approves a refund — triggers a Payout REFUND (BR3). */
  public static final String REFUND_APPROVED = "REFUND_APPROVED";

  /** Approves a cancellation — triggers Booking.cancel (BR2). */
  public static final String CANCEL_APPROVED = "CANCEL_APPROVED";

  /** Resolved without any external side effect. */
  public static final String RESOLVED_NO_ACTION = "RESOLVED_NO_ACTION";

  /** The request was declined; no side effect. */
  public static final String REJECTED = "REJECTED";

  private CaseResolutionCodes() {}

  /**
   * Whether resolving with this outcome must trigger a Payout REFUND (BR3).
   *
   * @param resolution the case-resolution cadastro code
   * @return whether it triggers a refund
   */
  public static boolean triggersRefund(String resolution) {
    return REFUND_APPROVED.equals(resolution);
  }

  /**
   * Whether resolving with this outcome must trigger a Booking cancellation (BR2).
   *
   * @param resolution the case-resolution cadastro code
   * @return whether it triggers a cancellation
   */
  public static boolean triggersCancellation(String resolution) {
    return CANCEL_APPROVED.equals(resolution);
  }
}
