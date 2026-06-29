package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a {@code REFUND} payout is created without referencing its origin obligation
 * (SPEC-0017 BR7 — no "loose" refund). Mapped to {@code 400 Bad Request}.
 */
public class PayoutRefundOriginRequiredException extends DomainException {

  public PayoutRefundOriginRequiredException() {
    super("payout.refund.origin-required");
  }
}
