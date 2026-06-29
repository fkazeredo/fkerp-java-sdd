package com.fksoft.domain.aftersales;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an after-sales case is looked up by an id that does not exist (SPEC-0018). Mapped to
 * {@code 404 Not Found}.
 */
public class SupportCaseNotFoundException extends DomainException {

  public SupportCaseNotFoundException() {
    super("aftersales.case.not-found");
  }
}
