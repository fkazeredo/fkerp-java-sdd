package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a ledger entry is created against a period that is already CLOSED (SPEC-0015 BR4):
 * adjustments must go to an open period. Mapped to {@code 409 Conflict}.
 */
public class FinancePeriodClosedException extends DomainException {

  public FinancePeriodClosedException() {
    super("finance.period.closed");
  }
}
