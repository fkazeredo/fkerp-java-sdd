package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a consent row is looked up by an id that does not exist (SPEC-0019; revocation by
 * id). Mapped to {@code 404 Not Found}.
 */
public class ConsentNotFoundException extends DomainException {

  public ConsentNotFoundException() {
    super("marketing.consent.not-found");
  }
}
