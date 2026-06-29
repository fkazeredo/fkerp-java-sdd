package com.fksoft.infra.integration.pointclock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Builds minimal but structurally valid CAdES/PKCS#7 {@code signed-data} envelopes for the AFD
 * verifier tests ({@code simulation-and-mocking.md}). A real REP export is out of scope (DL-0029);
 * these fixtures carry exactly the structure the {@link Pkcs7AfdSignatureVerifier} inspects: the
 * signed-data OID, an encapsulated content OCTET STRING (the AFD bytes) and a SignerInfo SET. They
 * are deterministic, so the signature/integrity check is provable without a crypto library.
 */
public final class AfdEnvelopeFixtures {

  /** DER of OID 1.2.840.113549.1.7.2 (pkcs7-signedData). */
  private static final byte[] SIGNED_DATA_OID = {
    0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x07, 0x02
  };

  private AfdEnvelopeFixtures() {}

  /** A valid signed envelope encapsulating {@code afdContent} and carrying a SignerInfo. */
  public static byte[] signedEnvelope(byte[] afdContent) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(SIGNED_DATA_OID); // the signed-data OID
    writeTagged(out, 0x04, afdContent); // eContent (the AFD bytes)
    writeTagged(out, 0x31, new byte[] {0x02, 0x01, 0x01}); // signerInfos SET (one SignerInfo)
    return out.toByteArray();
  }

  /** An envelope without a SignerInfo (the signature is absent) — must be rejected. */
  public static byte[] unsignedEnvelope(byte[] afdContent) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(SIGNED_DATA_OID);
    writeTagged(out, 0x04, afdContent); // content but no signerInfos SET
    return out.toByteArray();
  }

  /** A file that is not a PKCS#7 signed envelope at all (no signed-data OID). */
  public static byte[] notAnEnvelope() {
    return "this is a plain text file, not a signed AFD".getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The {@code sha256:hex} hash of the given content (the expected-content-hash declared on
   * upload).
   */
  public static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "sha256:" + HexFormat.of().formatHex(digest.digest(content));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void writeTagged(ByteArrayOutputStream out, int tag, byte[] value) {
    out.write(tag);
    out.write(value.length); // short-form length (fixtures are small)
    out.writeBytes(value);
  }
}
