package com.fksoft.domain.payout;

/**
 * The kind of party a payout pays (SPEC-0017 BR1): the agent (commission repass), the supplier
 * (settlement) or the customer (refund). Carried by value across module boundaries, never as a
 * shared FK.
 */
public enum PayeeType {
  AGENT,
  SUPPLIER,
  CUSTOMER
}
