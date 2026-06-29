package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an invalid status transition is attempted on a payout or installment (SPEC-0017 BR2
 * state machine). Mapped to {@code 409 Conflict}.
 */
public class PayoutTransitionInvalidException extends DomainException {

  public PayoutTransitionInvalidException() {
    super("payout.transition.invalid");
  }
}
