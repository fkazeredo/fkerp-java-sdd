package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;

/**
 * A single cancellation/no-show obligation: a distinct fact (BR5) that carries its own {@link
 * Money} (its own currency — BR9/DL-0022) and its cost bearer. Charges NEVER net out against one
 * another (BR11/DL-0024): the domain only accumulates them, it never subtracts one from the other.
 * Under ALL_SALES_FINAL the {@link ChargeKindCodes#SUPPLIER} cost and the {@link
 * ChargeKindCodes#CUSTOMER_REFUND} coexist — that is the merchant trap, kept visible rather than
 * collapsed into a net amount. The {@code kind} is a charge-kind cadastro code (was {@code
 * ChargeKind}; SPEC-0031/DL-0117).
 *
 * @param kind the kind of obligation (charge-kind cadastro code)
 * @param amount the obligation amount (its own currency)
 * @param costBearer who bears it
 */
public record Charge(String kind, Money amount, CostBearer costBearer) {

  public Charge {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("charge kind is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("charge amount is required");
    }
    if (costBearer == null) {
      throw new IllegalArgumentException("charge costBearer is required");
    }
  }
}
