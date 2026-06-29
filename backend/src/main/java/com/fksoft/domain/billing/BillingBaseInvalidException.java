package com.fksoft.domain.billing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the commission base of an invoice is missing, negative or not in BRL (SPEC-0016 BR1).
 * The taxable base is the commission and must be a valid non-negative amount. Mapped to {@code 400
 * Bad Request}.
 */
public class BillingBaseInvalidException extends DomainException {

  public BillingBaseInvalidException() {
    super("billing.base.invalid");
  }
}
