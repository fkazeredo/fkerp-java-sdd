package com.fksoft.domain.compliance;

/**
 * The {@code SIGNED_FORMAT} code constants the domain wires (SPEC-0031 BR5; DL-0117). After {@code
 * SignedFormat} became an editable cadastro, the signed format is <strong>produced by the ingesting
 * adapter</strong>, not chosen from a payload: the NFS-e archive records {@link #XADES}, the
 * AFD/AEJ archive records {@link #CAdES_P7S} (SPEC-0008 BR3; SPEC-0012 BR4). A {@code null} format
 * means the document is not signed. These constants keep those wired values stable. The cadastro
 * owns the labels the screens render.
 */
public final class SignedFormatCodes {

  /** CAdES detached PKCS#7 signature (.p7s) — the AFD/AEJ legal artifact format. */
  public static final String CAdES_P7S = "CAdES_P7S";

  /** XAdES XML signature — the signed NFS-e format. */
  public static final String XADES = "XADES";

  /** PAdES PDF signature. */
  public static final String PADES = "PADES";

  private SignedFormatCodes() {}
}
