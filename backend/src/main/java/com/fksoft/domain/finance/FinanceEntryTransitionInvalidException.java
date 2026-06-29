package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a ledger entry status transition is not allowed (SPEC-0015 BR2:
 * PROVISIONAL→CONFIRMED→SETTLED only). Mapped to {@code 409 Conflict}.
 */
public class FinanceEntryTransitionInvalidException extends DomainException {

  public FinanceEntryTransitionInvalidException() {
    super("finance.entry.transition.invalid");
  }
}
