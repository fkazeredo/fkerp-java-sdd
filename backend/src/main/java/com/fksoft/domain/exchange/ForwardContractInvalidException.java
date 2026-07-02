package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/** Invalid forward-contract input (SPEC-0032): notional/rate/dates/counterparty invariants. */
public class ForwardContractInvalidException extends DomainException {

  public ForwardContractInvalidException() {
    super("exchange.forward.invalid");
  }
}
