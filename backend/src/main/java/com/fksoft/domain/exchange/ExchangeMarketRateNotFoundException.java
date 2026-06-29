package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when no market-rate observation exists for a pair up to the requested instant (SPEC-0011
 * BR1). The presentation layer maps it to {@code 404 Not Found}.
 */
public class ExchangeMarketRateNotFoundException extends DomainException {

  public ExchangeMarketRateNotFoundException() {
    super("exchange.market-rate.not-found");
  }
}
