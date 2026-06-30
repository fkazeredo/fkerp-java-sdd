package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a processed journey is requested for an (employee, period) that was never processed
 * (SPEC-0022). Mapped to {@code 404 Not Found}.
 */
public class JourneyNotFoundException extends DomainException {

  public JourneyNotFoundException() {
    super("people.journey.not-found");
  }
}
