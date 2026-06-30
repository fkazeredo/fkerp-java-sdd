package com.fksoft.domain.admin;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when registering an administrative expense with missing/invalid mandatory data — supplier,
 * period, amount or kind (SPEC-0025 BR3). Mapped to {@code 400 Bad Request}.
 */
public class AdminExpenseInvalidException extends DomainException {

  public AdminExpenseInvalidException() {
    super("admin.expense.invalid");
  }
}
