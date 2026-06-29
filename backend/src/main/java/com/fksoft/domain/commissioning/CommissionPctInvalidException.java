package com.fksoft.domain.commissioning;

import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.error.ErrorDetails;
import java.util.Map;

/**
 * Raised when a commission percentage is outside {@code [0,1]} (BR2). Carries the offending field;
 * the presentation layer maps it to {@code 400 Bad Request}.
 */
public class CommissionPctInvalidException extends DomainException implements ErrorDetails {

  private final transient String field;

  public CommissionPctInvalidException(String field) {
    super("commissioning.pct.invalid");
    this.field = field;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of(field, "must be in [0,1]");
  }
}
