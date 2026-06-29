package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when no operational point snapshot has the requested id (SPEC-0012). Mapped to {@code 404
 * Not Found}.
 */
public class PointSnapshotNotFoundException extends DomainException {

  public PointSnapshotNotFoundException() {
    super("point.snapshot.not-found");
  }
}
