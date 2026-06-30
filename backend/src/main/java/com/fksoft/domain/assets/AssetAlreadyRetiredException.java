package com.fksoft.domain.assets;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when an asset that is already {@link AssetStatus#RETIRED} is retired again (SPEC-0021 BR4
 * / DL-0068: RETIRED is terminal). Mapped to {@code 409 Conflict}, preserving the first
 * retirement's audit (who/when/reason).
 */
public class AssetAlreadyRetiredException extends DomainException {

  public AssetAlreadyRetiredException() {
    super("assets.asset.already-retired");
  }
}
