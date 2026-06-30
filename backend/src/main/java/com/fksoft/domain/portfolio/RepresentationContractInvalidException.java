package com.fksoft.domain.portfolio;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a representation contract is registered with invalid data (SPEC-0020 BR2): a missing
 * {@code validFrom}, or a {@code validUntil} before {@code validFrom}. Mapped to {@code 400 Bad
 * Request}.
 */
public class RepresentationContractInvalidException extends DomainException {

  public RepresentationContractInvalidException() {
    super("portfolio.contract.invalid");
  }
}
