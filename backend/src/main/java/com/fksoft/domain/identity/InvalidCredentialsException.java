package com.fksoft.domain.identity;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a login fails (SPEC-0024 BR4): unknown user, wrong password or disabled account all
 * map to the <strong>same generic</strong> exception, so the error message never reveals whether
 * the user exists. The presentation layer maps it to {@code 401 Unauthorized} with the generic
 * {@code identity.credentials.invalid} message.
 */
public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super("identity.credentials.invalid");
  }
}
