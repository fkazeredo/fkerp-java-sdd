package com.fksoft.domain.aftersales;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when opening an after-sales case with invalid input — a missing booking reference or type
 * (SPEC-0018 BR1/Validation Rules). Mapped to {@code 400 Bad Request}.
 */
public class SupportCaseInvalidException extends DomainException {

  public SupportCaseInvalidException() {
    super("aftersales.case.invalid");
  }
}
