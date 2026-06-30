package com.fksoft.domain.admin;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a recurring administrative expense already exists for the same supplier, period and
 * kind (SPEC-0025; DL-0086 idempotency by {@code (supplier, period, kind)}) — registering it again
 * would double-post the Finance ledger entry. Mapped to {@code 409 Conflict}.
 */
public class AdminExpenseDuplicateException extends DomainException {

  public AdminExpenseDuplicateException() {
    super("admin.expense.duplicate");
  }
}
