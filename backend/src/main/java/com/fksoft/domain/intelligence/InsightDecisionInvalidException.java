package com.fksoft.domain.intelligence;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a human decision is recorded with a value outside the decision enum (SPEC-0013 Error
 * Behavior, BR4): only {@code ACCEPTED}, {@code REJECTED} or {@code DISMISSED} are valid. The
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class InsightDecisionInvalidException extends DomainException {

  public InsightDecisionInvalidException() {
    super("intelligence.decision.invalid");
  }
}
