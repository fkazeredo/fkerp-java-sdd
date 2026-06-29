package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a payout is looked up by an id that does not exist (SPEC-0017). Mapped to {@code 404
 * Not Found}.
 */
public class PayoutNotFoundException extends DomainException {

  public PayoutNotFoundException() {
    super("payout.not-found");
  }
}
