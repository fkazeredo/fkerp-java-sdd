package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a cancellation policy is malformed — a negative {@code hoursBefore}, a {@code
 * penaltyPct} outside {@code [0, 1]}, or an invalid window set (SPEC-0010 Error Behavior). The
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class CancellationPolicyInvalidException extends DomainException {

  public CancellationPolicyInvalidException() {
    super("cancellation.policy.invalid");
  }
}
