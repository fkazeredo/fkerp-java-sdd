package com.fksoft.domain.assets;

/**
 * The small set of {@code ASSET_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0115). After {@code AssetType} became an editable cadastro, the one wired rule — a {@code
 * SOFTWARE_LICENSE} requires an expiry date and drives the license-expiry alert (SPEC-0021 BR1/BR3)
 * — is preserved here as a code constant. The cadastro owns the extensible set + labels; this class
 * owns the wired behavior.
 */
public final class AssetCodes {

  /** Physical equipment (notebooks, servers, peripherals). */
  public static final String EQUIPMENT = "EQUIPMENT";

  /** A software license — the only type that requires an expiry date and gets the expiry alert. */
  public static final String SOFTWARE_LICENSE = "SOFTWARE_LICENSE";

  /** Any other internal asset that is neither equipment nor a software license. */
  public static final String OTHER = "OTHER";

  private AssetCodes() {}

  /**
   * Whether the given asset-type code is a software license (the type that requires an expiry and
   * gets the expiry alert — SPEC-0021 BR1/BR3).
   *
   * @param code the {@code ASSET_TYPE} cadastro code
   * @return whether it is the software-license code
   */
  public static boolean isSoftwareLicense(String code) {
    return SOFTWARE_LICENSE.equals(code);
  }
}
