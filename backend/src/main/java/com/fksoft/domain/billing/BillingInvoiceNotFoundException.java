package com.fksoft.domain.billing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a commission invoice is looked up by an id that does not exist (SPEC-0016). Mapped to
 * {@code 404 Not Found}.
 */
public class BillingInvoiceNotFoundException extends DomainException {

  public BillingInvoiceNotFoundException() {
    super("billing.invoice.not-found");
  }
}
