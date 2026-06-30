package com.fksoft.domain.assets;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an asset is looked up by an id that does not exist (SPEC-0021 Error Behavior). Mapped
 * to {@code 404 Not Found}.
 */
public class AssetNotFoundException extends DomainException {

  public AssetNotFoundException() {
    super("assets.asset.not-found");
  }
}
