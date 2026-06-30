package com.fksoft.domain.platform;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the certificate status is requested but no certificate is custodied (SPEC-0023).
 * Mapped to {@code 404 Not Found}. Carries no secret.
 */
public class CertificateNotFoundException extends DomainException {

  public CertificateNotFoundException() {
    super("platform.certificate.not-found");
  }
}
