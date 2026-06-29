package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a price override is attempted on an {@code INTEGRATED} quote (SPEC-0009 BR2): the
 * external price is trusted and there is no suggestion to diverge from, so there is nothing to
 * override. The presentation layer maps it to {@code 409 Conflict}.
 */
public class QuoteOverrideNotApplicableException extends DomainException {

  public QuoteOverrideNotApplicableException() {
    super("quoting.override.not-applicable");
  }
}
