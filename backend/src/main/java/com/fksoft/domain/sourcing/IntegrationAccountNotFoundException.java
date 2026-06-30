package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when no registered account matches the inbound payload's document (SPEC-0009, DL-0017):
 * the inbound quotation is rejected and nothing is created. The presentation layer maps it to
 * {@code 422 Unprocessable Content}.
 */
public class IntegrationAccountNotFoundException extends DomainException {

  public IntegrationAccountNotFoundException() {
    super("integration.account.not-found");
  }
}
