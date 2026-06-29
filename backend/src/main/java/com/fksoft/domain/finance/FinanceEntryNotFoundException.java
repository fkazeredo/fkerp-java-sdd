package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a ledger entry is looked up by an id that does not exist (SPEC-0015). Mapped to
 * {@code 404 Not Found}.
 */
public class FinanceEntryNotFoundException extends DomainException {

  public FinanceEntryNotFoundException() {
    super("finance.entry.not-found");
  }
}
