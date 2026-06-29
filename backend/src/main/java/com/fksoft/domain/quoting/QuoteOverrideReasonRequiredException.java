package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.error.ErrorDetails;
import java.util.Map;

/**
 * Raised when an override is requested without a non-empty reason (BR6). Carries the {@code reason}
 * field; the presentation layer maps it to {@code 400 Bad Request}.
 */
public class QuoteOverrideReasonRequiredException extends DomainException implements ErrorDetails {

  public QuoteOverrideReasonRequiredException() {
    super("quoting.override.reason-required");
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("reason", "required");
  }
}
