package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a collaborator is looked up by an id that does not exist (SPEC-0022). Mapped to
 * {@code 404 Not Found}.
 */
public class EmployeeNotFoundException extends DomainException {

  public EmployeeNotFoundException() {
    super("people.employee.not-found");
  }
}
