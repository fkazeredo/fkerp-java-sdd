package com.fksoft.domain.assets;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a {@code SOFTWARE_LICENSE} asset is registered without an {@code expiresAt} (SPEC-0021
 * BR1 / Error Behavior). Mapped to {@code 400 Bad Request}.
 */
public class LicenseExpiryRequiredException extends DomainException {

  public LicenseExpiryRequiredException() {
    super("assets.license.expiry-required");
  }
}
