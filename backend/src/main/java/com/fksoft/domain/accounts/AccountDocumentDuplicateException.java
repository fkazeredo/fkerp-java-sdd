package com.fksoft.domain.accounts;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when registering an account whose {@code (legalType, documentNumber)} already exists
 * (BR3). The presentation layer maps it to {@code 409 Conflict}. Translated from the unique-index
 * violation so a raw database exception never leaks (persistence.md).
 */
public class AccountDocumentDuplicateException extends DomainException {

  public AccountDocumentDuplicateException() {
    super("account.document.duplicate");
  }
}
