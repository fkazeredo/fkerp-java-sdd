package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when no pinned rate is in effect for a pair at the requested instant (BR3). The
 * presentation layer maps it to {@code 404 Not Found}.
 */
public class ExchangeRateNotFoundException extends DomainException {

  public ExchangeRateNotFoundException() {
    super("exchange.rate.not-found");
  }
}
