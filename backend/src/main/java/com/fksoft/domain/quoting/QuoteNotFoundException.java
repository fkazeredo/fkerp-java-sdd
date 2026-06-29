package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a quote is looked up (or overridden) by an id that does not exist. The presentation
 * layer maps it to {@code 404 Not Found}.
 */
public class QuoteNotFoundException extends DomainException {

  public QuoteNotFoundException() {
    super("quoting.not-found");
  }
}
