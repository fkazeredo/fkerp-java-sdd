package com.fksoft.domain.admin;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an administrative supplier is looked up by an id that does not exist (SPEC-0025).
 * Mapped to {@code 404 Not Found}.
 */
public class AdminSupplierNotFoundException extends DomainException {

  public AdminSupplierNotFoundException() {
    super("admin.supplier.not-found");
  }
}
