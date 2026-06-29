package com.fksoft.domain.compliance;

/**
 * Signed-document format (SPEC-0008 BR3): for signed artifacts (e.g. AFD/AEJ) the original signed
 * file must be preserved as-is and its format recorded. {@code null} means the document is not
 * signed.
 */
public enum SignedFormat {
  CAdES_P7S,
  XADES,
  PADES
}
