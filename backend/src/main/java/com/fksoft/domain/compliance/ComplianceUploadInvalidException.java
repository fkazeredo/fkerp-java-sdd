package com.fksoft.domain.compliance;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an upload fails validation — empty content, missing type, or rejected
 * size/extension/content-type/filename (SPEC-0008; never trust the extension alone). Mapped to
 * {@code 400 Bad Request}. The message never exposes a filesystem path or sensitive data
 * (security.md).
 */
public class ComplianceUploadInvalidException extends DomainException {

  public ComplianceUploadInvalidException() {
    super("compliance.upload.invalid");
  }
}
