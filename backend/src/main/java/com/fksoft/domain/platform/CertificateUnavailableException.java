package com.fksoft.domain.platform;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the certificate custody is unavailable — the secret material cannot be decrypted or
 * the cipher/key is missing (SPEC-0023 Error Behavior, {@code platform.certificate.unavailable}).
 * Mapped to {@code 503 Service Unavailable}. The error message NEVER exposes the material or the
 * key (BR1, security.md).
 */
public class CertificateUnavailableException extends DomainException {

  public CertificateUnavailableException() {
    super("platform.certificate.unavailable");
  }
}
