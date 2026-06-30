package com.fksoft.domain.portfolio;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a represented brand is looked up by an id or brandRef that does not exist
 * (SPEC-0020). Mapped to {@code 404 Not Found}.
 */
public class BrandNotFoundException extends DomainException {

  public BrandNotFoundException() {
    super("portfolio.brand.not-found");
  }
}
