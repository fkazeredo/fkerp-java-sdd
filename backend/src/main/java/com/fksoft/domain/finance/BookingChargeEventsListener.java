package com.fksoft.domain.finance;

import com.fksoft.domain.booking.CancellationCharged;
import com.fksoft.domain.booking.Charge;
import com.fksoft.domain.booking.ChargeKindCodes;
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
      // charge.kind() is a charge-kind cadastro code (SPEC-0031/DL-0117); the wired posting
      // branches
      // on the ChargeKindCodes constants. A default guards against an unknown code (dado puro): it
      // posts nothing (no wired ledger effect), preserving the merchant-trap invariant.
      switch (charge.kind()) {
        case ChargeKindCodes.PENALTY ->
            financeService.postFromCharge(
                sourceRef(event.bookingId()),
                charge.kind(),
                LedgerDirection.RECEIVABLE,
                agency(event.bookingId()),
                charge.amount(),
                EntryTypeCodes.PENALTY,
                event.occurredAt());
        case ChargeKindCodes.CUSTOMER_REFUND ->
            financeService.postFromCharge(
                sourceRef(event.bookingId()),
                charge.kind(),
                LedgerDirection.PAYABLE,
                agency(event.bookingId()),
                charge.amount(),
                EntryTypeCodes.REFUND,
                event.occurredAt());
        case ChargeKindCodes.SUPPLIER -> {
          // Posted from MerchantObligationIncurred (single source of truth) — skip here.
        }
        case ChargeKindCodes.NO_SHOW -> {
          // No-show charges arrive via NoShowCharged, never inside a cancellation.
        }
        default -> {
          // An unknown charge-kind code has no wired posting (dado puro) — nothing to post.
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
        ChargeKindCodes.SUPPLIER,
        LedgerDirection.PAYABLE,
        supplier(event.bookingId()),
        event.supplierCharge().amount(),
        EntryTypeCodes.SUPPLIER_SETTLEMENT,
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
        ChargeKindCodes.NO_SHOW,
        LedgerDirection.RECEIVABLE,
        agency(event.bookingId()),
        event.fee(),
        EntryTypeCodes.PENALTY,
        event.occurredAt());
  }

  private static String sourceRef(UUID bookingId) {
    return bookingId.toString();
  }

  private static Party agency(UUID bookingId) {
    return new Party(bookingId.toString(), PartyTypeCodes.AGENCY);
  }

  private static Party supplier(UUID bookingId) {
    return new Party(bookingId.toString(), PartyTypeCodes.SUPPLIER);
  }
}
