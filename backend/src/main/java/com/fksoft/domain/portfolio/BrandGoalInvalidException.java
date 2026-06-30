package com.fksoft.domain.portfolio;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a brand goal is defined with invalid data (SPEC-0020 BR3): a missing brandRef/period/
 * metric, a malformed period, or a target inconsistent with the metric (a REVENUE goal without an
 * amount, a VOLUME goal without a count, or a non-positive target). Mapped to {@code 400 Bad
 * Request}.
 */
public class BrandGoalInvalidException extends DomainException {

  public BrandGoalInvalidException() {
    super("portfolio.goal.invalid");
  }
}
