package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the inbound webhook signature is missing or does not match (SPEC-0009 BR3). Nothing
 * is created. The presentation layer maps it to {@code 401 Unauthorized}.
 */
public class IntegrationSignatureInvalidException extends DomainException {

  public IntegrationSignatureInvalidException() {
    super("integration.signature.invalid");
  }
}
