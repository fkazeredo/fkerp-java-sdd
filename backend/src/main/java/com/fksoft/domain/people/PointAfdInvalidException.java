package com.fksoft.domain.people;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an AFD/AEJ upload fails signature/integrity verification on ingestion (SPEC-0012 BR4;
 * DL-0032) — a malformed CAdES/PKCS#7 envelope, a missing signature, or a content hash that does
 * not match the declared one (tampered). Mapped to {@code 400 Bad Request} ({@code
 * point.afd.invalid}). A tampered/invalid legal artifact must never enter the vault. The message
 * never exposes file content or credentials (security.md).
 */
public class PointAfdInvalidException extends DomainException {

  public PointAfdInvalidException() {
    super("point.afd.invalid");
  }
}
