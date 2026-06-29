package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a currency pair is not two three-letter codes (e.g. malformed {@code USD/BRL}). The
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class ExchangeCurrencyPairInvalidException extends DomainException {

  public ExchangeCurrencyPairInvalidException() {
    super("exchange.pair.invalid");
  }
}
