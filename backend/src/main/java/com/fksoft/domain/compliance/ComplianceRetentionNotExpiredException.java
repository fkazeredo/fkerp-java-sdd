package com.fksoft.domain.compliance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a document purge is attempted before its legal retention deadline (SPEC-0008 BR7).
 * Mapped to {@code 409 Conflict}.
 */
public class ComplianceRetentionNotExpiredException extends DomainException {

  public ComplianceRetentionNotExpiredException() {
    super("compliance.retention.not-expired");
  }
}
