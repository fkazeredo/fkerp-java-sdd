package com.fksoft.domain.compliance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a document is looked up by an id that does not exist (SPEC-0008). Mapped to {@code
 * 404 Not Found}.
 */
public class ComplianceDocumentNotFoundException extends DomainException {

  public ComplianceDocumentNotFoundException() {
    super("compliance.document.not-found");
  }
}
