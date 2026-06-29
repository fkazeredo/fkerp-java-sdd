package com.fksoft.domain.intelligence;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an insight is looked up or decided by an id that does not exist (SPEC-0013 Error
 * Behavior). The presentation layer maps it to {@code 404 Not Found}.
 */
public class InsightNotFoundException extends DomainException {

  public InsightNotFoundException() {
    super("intelligence.insight.not-found");
  }
}
