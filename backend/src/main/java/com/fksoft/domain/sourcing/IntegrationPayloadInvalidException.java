package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the inbound webhook payload is malformed or fails validation (SPEC-0009). Nothing is
 * created. The presentation layer maps it to {@code 400 Bad Request}.
 */
public class IntegrationPayloadInvalidException extends DomainException {

  public IntegrationPayloadInvalidException() {
    super("integration.payload.invalid");
  }
}
