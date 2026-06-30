package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a journey-processing command is invalid — a missing/malformed period ({@code
 * YYYY-MM}), a missing snapshot source reference or a negative worked time (SPEC-0022 BR2). Mapped
 * to {@code 400 Bad Request}.
 */
public class JourneyInvalidException extends DomainException {

  public JourneyInvalidException() {
    super("people.journey.invalid");
  }
}
