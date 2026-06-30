package com.fksoft.domain.assets;

/**
 * The lifecycle status of an internal asset (SPEC-0021). The machine is minimal: an asset is born
 * {@link #ACTIVE} and may be retired once into {@link #RETIRED}, which is terminal (no reactivation
 * in v1 — DL-0068).
 */
public enum AssetStatus {

  /** The asset is in use / on the books. */
  ACTIVE,

  /** The asset has been written off (retired), with audit (who/when/reason — BR4). Terminal. */
  RETIRED
}
