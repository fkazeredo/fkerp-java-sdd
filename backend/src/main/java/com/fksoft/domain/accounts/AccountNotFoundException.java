package com.fksoft.domain.accounts;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an account is looked up by an id that does not exist (BR7). The presentation layer
 * maps it to {@code 404 Not Found}.
 */
public class AccountNotFoundException extends DomainException {

  public AccountNotFoundException() {
    super("account.not-found");
  }
}
