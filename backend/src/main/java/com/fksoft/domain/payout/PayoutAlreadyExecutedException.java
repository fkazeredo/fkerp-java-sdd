package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an execution is attempted on a payout that has no executable installment left — every
 * installment is already EXECUTED (SPEC-0017 BR3 idempotency / {@code payout.already-executed}).
 * Mapped to {@code 409 Conflict}.
 */
public class PayoutAlreadyExecutedException extends DomainException {

  public PayoutAlreadyExecutedException() {
    super("payout.already-executed");
  }
}
