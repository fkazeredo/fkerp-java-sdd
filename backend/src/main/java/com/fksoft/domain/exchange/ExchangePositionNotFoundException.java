package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when no FX position exists for a booking (SPEC-0011). The presentation layer maps it to
 * {@code 404 Not Found}.
 */
public class ExchangePositionNotFoundException extends DomainException {

  public ExchangePositionNotFoundException() {
    super("exchange.position.not-found");
  }
}
