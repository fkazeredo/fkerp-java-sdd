package com.fksoft.domain.aftersales;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an after-sales case is asked to perform a status transition its state machine does
 * not allow (SPEC-0018 Validation Rules; {@link SupportCaseStatus}). Mapped to {@code 409
 * Conflict}.
 */
public class SupportCaseTransitionInvalidException extends DomainException {

  public SupportCaseTransitionInvalidException() {
    super("aftersales.case.transition.invalid");
  }
}
