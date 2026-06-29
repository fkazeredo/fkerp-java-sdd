package com.fksoft.domain.commissioning;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the commissionable base amount is negative (BR2). The presentation layer maps it to
 * {@code 400 Bad Request}.
 */
public class CommissionBaseInvalidException extends DomainException {

  public CommissionBaseInvalidException() {
    super("commissioning.base.invalid");
  }
}
