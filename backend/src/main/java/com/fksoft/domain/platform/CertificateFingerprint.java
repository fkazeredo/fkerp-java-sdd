package com.fksoft.domain.platform;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes a certificate thumbprint and masks the holder document for safe logging (SPEC-0023 BR1,
 * security.md). The fingerprint is a SHA-256 over the certificate bytes — an
 * <strong>identifier</strong>, not a secret (it cannot be reversed to the material). The CNPJ
 * masking keeps personal/identifying data out of plain logs.
 */
public final class CertificateFingerprint {

  private CertificateFingerprint() {}

  /**
   * The SHA-256 thumbprint (lowercase hex) of the certificate material — an identifier, not secret.
   */
  public static String of(byte[] material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(material));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  /**
   * Masks a holder document (CNPJ) for logging, keeping only the last 4 characters visible (e.g.
   * {@code ****0191}). A {@code null}/short value yields a constant mask.
   */
  public static String maskCnpj(String document) {
    if (document == null || document.length() < 4) {
      return "****";
    }
    return "****" + document.substring(document.length() - 4);
  }
}
