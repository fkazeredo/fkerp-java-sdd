package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a pinned rate is not strictly positive (BR4). The presentation layer maps it to
 * {@code 400 Bad Request}.
 */
public class ExchangeRateInvalidException extends DomainException {

  public ExchangeRateInvalidException() {
    super("exchange.rate.invalid");
  }
}
