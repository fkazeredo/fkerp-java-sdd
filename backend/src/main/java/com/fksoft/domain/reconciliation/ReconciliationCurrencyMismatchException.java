package com.fksoft.domain.reconciliation;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a realized settlement value is in a different currency than the case's sale currency
 * (where BRL is expected). The presentation layer maps it to {@code 400 Bad Request}.
 */
public class ReconciliationCurrencyMismatchException extends DomainException {

  public ReconciliationCurrencyMismatchException() {
    super("reconciliation.currency.mismatch");
  }
}
