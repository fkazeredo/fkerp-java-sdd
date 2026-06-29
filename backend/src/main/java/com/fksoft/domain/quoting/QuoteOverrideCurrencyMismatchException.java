package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an override's applied amount is in a different currency than the suggested amount
 * (BR7). The presentation layer maps it to {@code 400 Bad Request}.
 */
public class QuoteOverrideCurrencyMismatchException extends DomainException {

  public QuoteOverrideCurrencyMismatchException() {
    super("quoting.override.currency-mismatch");
  }
}
