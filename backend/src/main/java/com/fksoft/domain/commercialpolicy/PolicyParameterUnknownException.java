package com.fksoft.domain.commercialpolicy;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a parameter key is resolved but has no {@code SYSTEM_DEFAULT} (SPEC-0014 BR4): the
 * resolution would be empty, which must never happen for a key in use. The presentation layer maps
 * it to {@code 404 Not Found}.
 */
public class PolicyParameterUnknownException extends DomainException {

  public PolicyParameterUnknownException() {
    super("policy.parameter.unknown");
  }
}
