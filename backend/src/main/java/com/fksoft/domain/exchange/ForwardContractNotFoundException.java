package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/** No forward contract with the given id (SPEC-0032). */
public class ForwardContractNotFoundException extends DomainException {

  public ForwardContractNotFoundException() {
    super("exchange.forward.not-found");
  }
}
