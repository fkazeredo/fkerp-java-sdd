package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a payout amount is missing or not positive, a foreign settlement is missing a
 * positive {@code settlementRate}, or an explicit installment plan does not sum exactly to the
 * total (SPEC-0017 BR1/BR6). Mapped to {@code 400 Bad Request}.
 */
public class PayoutAmountInvalidException extends DomainException {

  public PayoutAmountInvalidException() {
    super("payout.amount.invalid");
  }
}
