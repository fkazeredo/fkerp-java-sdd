package com.fksoft.domain.finance.internal;

import com.fksoft.domain.finance.EntryType;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyType;
import com.fksoft.domain.payout.AgentCommissionPaid;
import com.fksoft.domain.payout.RefundExecuted;
import com.fksoft.domain.payout.SupplierSettled;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, in-process consumer of the Payout events (SPEC-0017 BR4/BR5; DL-0051), turning
 * an executed payout into the matching AP ledger baixa. Finance is a leaf consumer of Payout: it
 * only reads the EXPOSED events and NEVER calls back into Payout (no cycle — Modulith; analogous to
 * {@code finance → billing} of DL-0047 and {@code finance → booking} of DL-0041). Each posting is
 * idempotent per {@code (payoutId, chargeKind)} via {@link FinanceService#postFromCharge}
 * (state-check + UNIQUE), so a re-delivered event does not double-post.
 *
 * <p>The supplier settlement posts the BRL baixa (the {@code paidBrl} the Payout computed from the
 * settlement rate, DL-0049) as a SUPPLIER_SETTLEMENT PAYABLE — posted <strong>exactly once</strong>
 * (the supervisor's hard requirement). The customer refund posts a REFUND PAYABLE keyed by the
 * payout — it does <strong>not</strong> touch the supplier obligation, so the merchant trap stays
 * intact (DL-0024/DL-0051): the supplier PAYABLE from the cancellation and the customer refund
 * baixa coexist and never net.
 */
@Component
@RequiredArgsConstructor
class PayoutEventsListener {

  private final FinanceService financeService;

  /** Posts the supplier settlement BRL baixa as a PAYABLE SUPPLIER_SETTLEMENT, once (BR4/BR5). */
  @EventListener
  void onSupplierSettled(SupplierSettled event) {
    financeService.postFromCharge(
        event.payoutId().toString(),
        "SUPPLIER_SETTLEMENT",
        LedgerDirection.PAYABLE,
        new Party(supplierRef(event), PartyType.SUPPLIER),
        event.paidBrl(),
        EntryType.SUPPLIER_SETTLEMENT,
        event.occurredAt());
  }

  /** Posts the agent commission repass as a PAYABLE COMMISSION_PAYABLE baixa, once (BR4). */
  @EventListener
  void onAgentCommissionPaid(AgentCommissionPaid event) {
    financeService.postFromCharge(
        event.payoutId().toString(),
        "AGENT_COMMISSION",
        LedgerDirection.PAYABLE,
        new Party(event.agentId(), PartyType.AGENCY),
        event.amount(),
        EntryType.COMMISSION_PAYABLE,
        event.occurredAt());
  }

  /**
   * Posts the customer refund as a PAYABLE REFUND baixa, once (BR4/BR7). This does NOT cancel or
   * net the supplier obligation — the merchant trap is preserved (DL-0024/DL-0051).
   */
  @EventListener
  void onRefundExecuted(RefundExecuted event) {
    financeService.postFromCharge(
        event.payoutId().toString(),
        "REFUND",
        LedgerDirection.PAYABLE,
        new Party(refundRef(event), PartyType.AGENCY),
        event.amount(),
        EntryType.REFUND,
        event.occurredAt());
  }

  private static String supplierRef(SupplierSettled event) {
    return event.bookingId() != null ? event.bookingId() : event.payoutId().toString();
  }

  private static String refundRef(RefundExecuted event) {
    return event.originRef() != null ? event.originRef() : event.payoutId().toString();
  }
}
