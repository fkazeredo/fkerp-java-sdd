package com.fksoft.domain.payout;

/**
 * The {@code PAYEE_TYPE} code constants the domain wires (SPEC-0031 BR5; DL-0118). After {@code
 * PayeeType} became an editable cadastro, the payee type still identifies the kind of party a
 * payout pays (SPEC-0017 BR1): the agent (commission repass), the supplier (settlement) or the
 * customer (refund). The cadastro owns the extensible set + labels; the type is carried by value
 * across module boundaries, never as a shared FK.
 */
public final class PayeeTypeCodes {

  /** The agent (commission repass). */
  public static final String AGENT = "AGENT";

  /** The supplier (settlement). */
  public static final String SUPPLIER = "SUPPLIER";

  /** The customer (refund). */
  public static final String CUSTOMER = "CUSTOMER";

  private PayeeTypeCodes() {}
}
