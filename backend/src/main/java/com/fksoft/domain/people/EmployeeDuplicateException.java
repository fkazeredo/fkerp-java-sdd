package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when registering a collaborator whose {@code identifier} is already in use (SPEC-0022 BR1,
 * unique). Mapped to {@code 409 Conflict} — the unique constraint is translated into this business
 * error, never a raw database exception.
 */
public class EmployeeDuplicateException extends DomainException {

  public EmployeeDuplicateException() {
    super("people.employee.duplicate");
  }
}
