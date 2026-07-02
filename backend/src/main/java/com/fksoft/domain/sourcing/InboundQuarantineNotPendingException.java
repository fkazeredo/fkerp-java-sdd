package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * The quarantine entry is not pending — it was already replayed or discarded (SPEC-0009 BR10,
 * DL-0120); replaying/discarding it again is a conflict.
 */
public class InboundQuarantineNotPendingException extends DomainException {

  public InboundQuarantineNotPendingException() {
    super("sourcing.quarantine.not-pending");
  }
}
