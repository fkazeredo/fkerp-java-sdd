package com.fksoft.domain.assets;

/**
 * The kind of internal asset (SPEC-0021 BR1). Distinct external values are explicit so the API
 * contract is stable (modules-and-apis.md): an unknown value produces a clear validation error
 * rather than silently mapping.
 */
public enum AssetType {

  /** Physical equipment (notebooks, servers, peripherals). */
  EQUIPMENT,

  /** A software license — the only type that requires an expiry date (BR1). */
  SOFTWARE_LICENSE,

  /** Any other internal asset that is neither equipment nor a software license. */
  OTHER
}
