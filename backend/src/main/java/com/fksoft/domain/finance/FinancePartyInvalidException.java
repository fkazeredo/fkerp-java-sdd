package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a ledger entry's party is missing its id or type (SPEC-0015 BR1). Mapped to {@code
 * 400 Bad Request}.
 */
public class FinancePartyInvalidException extends DomainException {

  public FinancePartyInvalidException() {
    super("finance.party.invalid");
  }
}
