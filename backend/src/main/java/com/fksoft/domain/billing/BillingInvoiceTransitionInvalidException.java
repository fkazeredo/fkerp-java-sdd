package com.fksoft.domain.billing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a commission-invoice status transition is not allowed (SPEC-0016: RASCUNHO→EMITIDA→
 * CANCELADA only), e.g. issuing an already-issued invoice or cancelling a draft. Mapped to {@code
 * 409 Conflict} (it expresses "already issued"/"not in a cancellable state").
 */
public class BillingInvoiceTransitionInvalidException extends DomainException {

  public BillingInvoiceTransitionInvalidException() {
    super("billing.invoice.already-issued");
  }
}
