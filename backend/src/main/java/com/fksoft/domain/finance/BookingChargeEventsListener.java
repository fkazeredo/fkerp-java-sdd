package com.fksoft.domain.finance;

import com.fksoft.domain.booking.CancellationCharged;
import com.fksoft.domain.booking.Charge;
import com.fksoft.domain.booking.ChargeKind;
import com.fksoft.domain.booking.MerchantObligationIncurred;
import com.fksoft.domain.booking.NoShowCharged;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, in-process consumer of the Booking charge events (SPEC-0010), turning them into
 * AP/AR ledger entries (SPEC-0015 BR5, DL-0041). It runs synchronously within the producer's
 * transaction, so each posting is atomic with the booking fact. Finance is a leaf consumer of
 * Booking: it only reads the EXPOSED event types and NEVER calls back into Booking (no cycle —
 * Modulith). Posting is idempotent per {@code (bookingId, chargeKind)} via {@link
 * FinanceService#postFromCharge} (state-check + UNIQUE).
 *
 * <p>The supplier obligation of an ALL_SALES_FINAL cancellation is published in two forms ({@link
 * CancellationCharged} carries a SUPPLIER charge AND a separate {@link
 * MerchantObligationIncurred}). To avoid double-posting it is posted ONCE — only from {@link
 * MerchantObligationIncurred} — and the SUPPLIER charge inside {@link CancellationCharged} is
 * skipped here (the idempotency key would also block it, but the rule is explicit). The supplier
 * PAYABLE and the customer REFUND therefore coexist — the merchant trap is preserved (DL-0024).
 */
@Component
@RequiredArgsConstructor
class BookingChargeEventsListener {

  private final FinanceService financeService;

  /**
   * Posts the AP/AR entries for a cancellation's charges (BR5). The PENALTY is RECEIVABLE; the
   * CUSTOMER_REFUND is a PAYABLE REFUND; the SUPPLIER charge is skipped here (posted from {@link
   * MerchantObligationIncurred} to avoid the double-publish).
   */
  @EventListener
  void onCancellationCharged(CancellationCharged event) {
    for (Charge charge : event.charges()) {
      switch (charge.kind()) {
        case PENALTY ->
            financeService.postFromCharge(
                sourceRef(event.bookingId()),
                charge.kind().name(),
                LedgerDirection.RECEIVABLE,
                agency(event.bookingId()),
                charge.amount(),
                EntryType.PENALTY,
                event.occurredAt());
        case CUSTOMER_REFUND ->
            financeService.postFromCharge(
                sourceRef(event.bookingId()),
                charge.kind().name(),
                LedgerDirection.PAYABLE,
                agency(event.bookingId()),
                charge.amount(),
                EntryType.REFUND,
                event.occurredAt());
        case SUPPLIER -> {
          // Posted from MerchantObligationIncurred (single source of truth) — skip here.
        }
        case NO_SHOW -> {
          // No-show charges arrive via NoShowCharged, never inside a cancellation.
        }
      }
    }
  }

  /**
   * Posts the supplier PAYABLE for the merchant obligation (the single source for the SUPPLIER).
   */
  @EventListener
  void onMerchantObligationIncurred(MerchantObligationIncurred event) {
    financeService.postFromCharge(
        sourceRef(event.bookingId()),
        ChargeKind.SUPPLIER.name(),
        LedgerDirection.PAYABLE,
        supplier(event.bookingId()),
        event.supplierCharge().amount(),
        EntryType.SUPPLIER_SETTLEMENT,
        event.occurredAt());
  }

  /**
   * Posts a RECEIVABLE PENALTY for a charged no-show (BR5). A waived no-show carries a {@code null}
   * fee and posts nothing.
   */
  @EventListener
  void onNoShowCharged(NoShowCharged event) {
    if (event.fee() == null) {
      return;
    }
    financeService.postFromCharge(
        sourceRef(event.bookingId()),
        ChargeKind.NO_SHOW.name(),
        LedgerDirection.RECEIVABLE,
        agency(event.bookingId()),
        event.fee(),
        EntryType.PENALTY,
        event.occurredAt());
  }

  private static String sourceRef(UUID bookingId) {
    return bookingId.toString();
  }

  private static Party agency(UUID bookingId) {
    return new Party(bookingId.toString(), PartyType.AGENCY);
  }

  private static Party supplier(UUID bookingId) {
    return new Party(bookingId.toString(), PartyType.SUPPLIER);
  }
}
