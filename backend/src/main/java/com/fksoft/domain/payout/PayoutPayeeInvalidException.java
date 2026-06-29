package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a payout's payee is missing an id or type (SPEC-0017 BR1). Mapped to {@code 400 Bad
 * Request}.
 */
public class PayoutPayeeInvalidException extends DomainException {

  public PayoutPayeeInvalidException() {
    super("payout.payee.invalid");
  }
}
