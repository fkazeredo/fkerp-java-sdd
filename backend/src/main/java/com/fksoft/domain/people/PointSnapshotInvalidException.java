package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a collect command is invalid — missing source/period reference, a malformed period
 * ({@code YYYY-MM}), a missing payload reference or a negative mark count (SPEC-0012). Mapped to
 * {@code 400 Bad Request}.
 */
public class PointSnapshotInvalidException extends DomainException {

  public PointSnapshotInvalidException() {
    super("point.snapshot.invalid");
  }
}
