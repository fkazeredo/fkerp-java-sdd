package com.fksoft.domain.finance.internal;

import com.fksoft.domain.billing.CommissionInvoiceIssued;
import com.fksoft.domain.billing.Withholding;
import com.fksoft.domain.finance.EntryType;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, in-process consumer of the Billing {@link CommissionInvoiceIssued} event
 * (SPEC-0016 Events; DL-0047), turning the issued invoice's taxes into PAYABLE ledger entries (the
 * ISS to remit and any withholdings). Finance is a leaf consumer of Billing: it only reads the
 * EXPOSED event and NEVER calls back into Billing (no cycle — Modulith; analogous to {@code finance
 * → booking} of DL-0041). Posting is idempotent per {@code (invoiceId, chargeKind)} via {@link
 * FinanceService#postFromCharge} (state-check + UNIQUE), so a re-delivered event does not
 * double-post.
 *
 * <p>The commission <em>receivable</em> is NOT posted here — that revenue belongs to the commercial
 * flow (Reconciliation/Booking); the invoice only adds the <em>tax</em> payable, avoiding double
 * counting (DL-0047). The tax authority is recorded as an {@link PartyType#OTHER} party keyed by
 * the municipality code (value reference, never an FK).
 */
@Component
@RequiredArgsConstructor
class CommissionInvoiceEventsListener {

  private final FinanceService financeService;

  /**
   * Posts the ISS (and any withholdings) of an issued commission invoice as PAYABLE entries (BR5),
   * idempotently. The period is derived from the issuance instant (UTC) by {@link
   * FinanceService#postFromCharge}.
   */
  @EventListener
  void onCommissionInvoiceIssued(CommissionInvoiceIssued event) {
    String sourceRef = event.invoiceId().toString();
    Party fisco = new Party(municipalityRef(event.municipality()), PartyType.OTHER);

    financeService.postFromCharge(
        sourceRef,
        "ISS",
        LedgerDirection.PAYABLE,
        fisco,
        event.iss(),
        EntryType.TAX_PAYABLE,
        event.occurredAt());

    for (Withholding withholding : event.withholdings()) {
      financeService.postFromCharge(
          sourceRef,
          "WHT_" + withholding.kind().name(),
          LedgerDirection.PAYABLE,
          fisco,
          withholding.amount(),
          EntryType.TAX_PAYABLE,
          event.occurredAt());
    }
  }

  private static String municipalityRef(String municipality) {
    return municipality == null || municipality.isBlank() ? "MUNICIPALITY" : municipality;
  }
}
