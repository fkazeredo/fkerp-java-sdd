package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/** No quarantine entry with the given id (SPEC-0009 BR10, DL-0120). */
public class InboundQuarantineNotFoundException extends DomainException {

  public InboundQuarantineNotFoundException() {
    super("sourcing.quarantine.not-found");
  }
}
