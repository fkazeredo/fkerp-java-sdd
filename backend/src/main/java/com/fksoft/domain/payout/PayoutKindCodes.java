package com.fksoft.domain.payout;

/**
 * The {@code PAYOUT_KIND} code constants whose behavior the domain wires (SPEC-0031 BR5; DL-0118).
 * After {@code PayoutKind} became an editable cadastro, the kind still drives the whole payout
 * behavior (SPEC-0017): {@link #REFUND} requires an origin obligation (BR7) and, on execution,
 * publishes {@code RefundExecuted}; {@link #SUPPLIER_SETTLEMENT} publishes {@code SupplierSettled}
 * (the BRL baixa via the settlement rate, DL-0049); {@link #AGENT_COMMISSION} publishes {@code
 * AgentCommissionPaid}. These three codes are load-bearing: the settlement/refund fact and the
 * merchant-trap invariant (a REFUND never nets the supplier obligation — DL-0024/DL-0051) branch on
 * them. A payout created with an unknown/inactive kind is rejected (422) on write, so the wired
 * logic only ever sees a known kind. The cadastro owns the extensible set + labels; this class owns
 * the wired behavior.
 */
public final class PayoutKindCodes {

  /** The agent commission repass. */
  public static final String AGENT_COMMISSION = "AGENT_COMMISSION";

  /** The supplier settlement (possibly in foreign currency, with a settlement rate). */
  public static final String SUPPLIER_SETTLEMENT = "SUPPLIER_SETTLEMENT";

  /** A customer refund — must reference its origin obligation (BR7). */
  public static final String REFUND = "REFUND";

  private PayoutKindCodes() {}

  /**
   * Whether the kind is a {@link #REFUND} — the branch that requires an origin obligation (BR7) and
   * that never nets the supplier obligation (the merchant trap — DL-0024/DL-0051).
   *
   * @param kind the payout-kind cadastro code
   * @return whether the kind is REFUND
   */
  public static boolean isRefund(String kind) {
    return REFUND.equals(kind);
  }
}
