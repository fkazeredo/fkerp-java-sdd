package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a collaborator's data is invalid — a missing identifier or admission date, or a
 * malformed/out-of-range contracted journey (SPEC-0022 BR1). Mapped to {@code 400 Bad Request}.
 */
public class EmployeeInvalidException extends DomainException {

  public EmployeeInvalidException() {
    super("people.employee.invalid");
  }
}
