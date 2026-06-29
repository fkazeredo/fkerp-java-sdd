package com.fksoft.domain.reconciliation;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a reconciliation case is looked up or settled by an id that does not exist. The
 * presentation layer maps it to {@code 404 Not Found}.
 */
public class ReconciliationCaseNotFoundException extends DomainException {

  public ReconciliationCaseNotFoundException() {
    super("reconciliation.case.not-found");
  }
}
