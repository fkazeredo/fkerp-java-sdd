package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a period identifier is not a valid {@code YYYY-MM} value (SPEC-0015). Mapped to
 * {@code 400 Bad Request}.
 */
public class FinancePeriodInvalidException extends DomainException {

  public FinancePeriodInvalidException() {
    super("finance.period.invalid");
  }
}
