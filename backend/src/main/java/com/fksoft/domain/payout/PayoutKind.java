package com.fksoft.domain.payout;

/**
 * The kind of payout (SPEC-0017 BR1): the agent commission repass, the supplier settlement
 * (possibly in foreign currency), or a customer refund (which must reference its origin obligation,
 * BR7).
 */
public enum PayoutKind {
  AGENT_COMMISSION,
  SUPPLIER_SETTLEMENT,
  REFUND
}
