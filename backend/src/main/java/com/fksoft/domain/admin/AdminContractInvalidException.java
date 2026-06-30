package com.fksoft.domain.admin;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when registering an administrative contract with an invalid validity window ({@code
 * validUntil} before {@code validFrom}) or other inconsistent data (SPEC-0025 BR2). Mapped to
 * {@code 400 Bad Request}.
 */
public class AdminContractInvalidException extends DomainException {

  public AdminContractInvalidException() {
    super("admin.contract.invalid");
  }
}
