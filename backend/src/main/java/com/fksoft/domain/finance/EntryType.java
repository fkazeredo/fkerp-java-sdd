package com.fksoft.domain.finance;

/**
 * Business type of a ledger entry (SPEC-0015 BR1) — the key the Compliance uses to decide which
 * document is mandatory (SPEC-0008 BR4). Crossing the module boundary it travels as a value (its
 * {@code name()}), never as a shared enum reference, so Compliance stays decoupled from Finance's
 * type system.
 */
public enum EntryType {
  COMMISSION_RECEIVABLE,
  COMMISSION_PAYABLE,
  PENALTY,
  UTILITY_EXPENSE,
  AUTONOMOUS_SERVICE,
  SUPPLIER_SETTLEMENT,
  REFUND
}
