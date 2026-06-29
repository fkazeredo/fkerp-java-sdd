package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a report period is not a valid {@code YYYY-MM} (SPEC-0011 promo-fx). The presentation
 * layer maps it to {@code 400 Bad Request}.
 */
public class ExchangePeriodInvalidException extends DomainException {

  public ExchangePeriodInvalidException() {
    super("exchange.period.invalid");
  }
}
