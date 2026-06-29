package com.fksoft.domain.quoting;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when composing a quote for an account that does not exist (BR2), checked via the Accounts
 * facade. The presentation layer maps it to {@code 404 Not Found}.
 */
public class QuoteAccountNotFoundException extends DomainException {

  public QuoteAccountNotFoundException() {
    super("quoting.account.not-found");
  }
}
