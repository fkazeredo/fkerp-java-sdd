package com.fksoft.domain.exchange;

import com.fksoft.domain.error.DomainException;

/**
 * The forward contract is not OPEN — it was already settled or cancelled (SPEC-0032); settling or
 * cancelling it again is a conflict.
 */
public class ForwardContractNotOpenException extends DomainException {

  public ForwardContractNotOpenException() {
    super("exchange.forward.not-open");
  }
}
