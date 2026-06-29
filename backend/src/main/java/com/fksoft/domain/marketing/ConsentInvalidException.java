package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a consent decision is missing required data (subject, purpose or legal basis —
 * SPEC-0019 BR1). Mapped to {@code 400 Bad Request}. The message never echoes the subject id (LGPD:
 * errors do not leak personal data).
 */
public class ConsentInvalidException extends DomainException {

  public ConsentInvalidException() {
    super("marketing.consent.invalid");
  }
}
