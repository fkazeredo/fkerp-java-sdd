package com.fksoft.domain.aftersales;

import com.fksoft.domain.money.Money;
import java.time.Instant;

/**
 * Command to resolve an after-sales case (SPEC-0018 {@code POST
 * /api/aftersales/cases/{id}/resolve}; DL-0054). Carries the {@link CaseResolution} and the data
 * the orchestration needs:
 *
 * <ul>
 *   <li>{@code amount} — the refund amount for a {@code REFUND_APPROVED}, also the commercial
 *       refund to the customer for a {@code CANCEL_APPROVED} (or {@code null}).
 *   <li>{@code handlingCost} — the handling effort cost to accrue into the cost-to-serve (BR5), or
 *       {@code null}.
 *   <li>{@code serviceStartsAt}/{@code cancellationReason} — passed through to {@link
 *       com.fksoft.domain.booking.BookingService#cancel} for a {@code CANCEL_APPROVED} (the
 *       penalty-window base and reason).
 * </ul>
 *
 * @param resolution the resolution outcome (required)
 * @param amount the refund/commercial-refund amount, or {@code null}
 * @param handlingCost the handling effort cost to accrue (BR5), or {@code null}
 * @param serviceStartsAt when the booked service starts (cancellation penalty-window base), or
 *     {@code null}
 * @param cancellationReason the cancellation reason passed to Booking, or {@code null}
 */
public record ResolveCaseCommand(
    CaseResolution resolution,
    Money amount,
    Money handlingCost,
    Instant serviceStartsAt,
    String cancellationReason) {

  public ResolveCaseCommand {
    if (resolution == null) {
      throw new SupportCaseInvalidException();
    }
  }
}
