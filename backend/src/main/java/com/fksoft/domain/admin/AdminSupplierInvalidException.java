package com.fksoft.domain.admin;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when registering an administrative supplier with missing/invalid mandatory data — type or
 * display name (SPEC-0025 BR1). Mapped to {@code 400 Bad Request}.
 */
public class AdminSupplierInvalidException extends DomainException {

  public AdminSupplierInvalidException() {
    super("admin.supplier.invalid");
  }
}
