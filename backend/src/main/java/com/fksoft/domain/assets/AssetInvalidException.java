package com.fksoft.domain.assets;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an asset is registered with missing/invalid mandatory data — type, identifier,
 * acquisition date or acquisition cost (SPEC-0021 BR1). Mapped to {@code 400 Bad Request}.
 */
public class AssetInvalidException extends DomainException {

  public AssetInvalidException() {
    super("assets.asset.invalid");
  }
}
