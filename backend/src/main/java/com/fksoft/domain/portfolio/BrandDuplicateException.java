package com.fksoft.domain.portfolio;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a brand is registered with a {@code brandRef} that already exists (SPEC-0020 BR1:
 * brandRef is unique). The unique index violation is translated to this business error — a raw
 * database exception never leaks (persistence.md). Mapped to {@code 409 Conflict}.
 */
public class BrandDuplicateException extends DomainException {

  public BrandDuplicateException() {
    super("portfolio.brand.duplicate");
  }
}
