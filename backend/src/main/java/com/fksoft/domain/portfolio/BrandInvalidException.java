package com.fksoft.domain.portfolio;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a represented brand is created with missing required data (SPEC-0020 BR1: brandRef
 * and displayName are required). Mapped to {@code 400 Bad Request}.
 */
public class BrandInvalidException extends DomainException {

  public BrandInvalidException() {
    super("portfolio.brand.invalid");
  }
}
