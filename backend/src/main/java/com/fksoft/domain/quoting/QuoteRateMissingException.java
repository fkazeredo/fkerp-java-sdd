package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when composing a quote but no FX rate is in effect for the pair (BR3), checked via the
 * Exchange Open-Host port. The presentation layer maps it to {@code 422 Unprocessable Content}.
 */
public class QuoteRateMissingException extends DomainException {

  public QuoteRateMissingException() {
    super("quoting.rate.missing");
  }
}
