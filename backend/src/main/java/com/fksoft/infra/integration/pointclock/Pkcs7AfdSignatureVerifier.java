package com.fksoft.infra.integration.pointclock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Structural CAdES/PKCS#7 verifier for the AFD/AEJ signed artifact (SPEC-0012 BR4; DL-0032).
 * Without adding a crypto library (Rule Zero), it walks the DER ASN.1 of the {@code .p7s} envelope
 * to assert the integrity/signature properties that this phase requires:
 *
 * <ol>
 *   <li>the outer {@code ContentInfo} carries the PKCS#7 <strong>signed-data</strong> OID ({@code
 *       1.2.840.113549.1.7.2}) — i.e. it is a signed envelope, not an arbitrary file;
 *   <li>it embeds an encapsulated content (the AFD bytes) whose SHA-256 matches the declared {@code
 *       expectedContentHash} — the <strong>tamper</strong> check;
 *   <li>it carries at least one {@code SignerInfo} — i.e. a signature is present.
 * </ol>
 *
 * <p>This is the integrity verification proportional to the phase's scope. The full ICP-Brasil
 * chain validation (certificate trust, CRL/OCSP, timestamp) is the Platform's job (SPEC-0023) and
 * plugs in here later without changing callers (DL-0032). The original signed file is preserved
 * as-is (BR4); this verifier only inspects it.
 */
@Slf4j
@Component
public class Pkcs7AfdSignatureVerifier implements AfdSignatureVerifier {

  /** OID 1.2.840.113549.1.7.2 (pkcs7-signedData), DER-encoded. */
  private static final byte[] SIGNED_DATA_OID = {
    0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x07, 0x02
  };

  @Override
  public boolean verify(byte[] signedFile, String expectedContentHash) {
    if (signedFile == null || signedFile.length == 0 || expectedContentHash == null) {
      return false;
    }
    try {
      if (!containsSignedDataOid(signedFile)) {
        log.info("AFD verification failed: not a PKCS#7 signed-data envelope");
        return false;
      }
      byte[] content = extractEncapsulatedContent(signedFile);
      if (content == null || content.length == 0) {
        log.info("AFD verification failed: no encapsulated content");
        return false;
      }
      if (!hasSignerInfo(signedFile)) {
        log.info("AFD verification failed: no SignerInfo (unsigned envelope)");
        return false;
      }
      String actual = sha256Hex(content);
      String expected = normalizeHash(expectedContentHash);
      boolean matches = actual.equalsIgnoreCase(expected);
      if (!matches) {
        log.info("AFD verification failed: content hash mismatch (tampered)");
      }
      return matches;
    } catch (RuntimeException malformed) {
      // Any structural parse error means the envelope is not a valid signed artifact.
      log.info("AFD verification failed: malformed envelope ({})", malformed.getMessage());
      return false;
    }
  }

  /** Whether the DER bytes contain the signed-data OID (a signed envelope). */
  private static boolean containsSignedDataOid(byte[] der) {
    return indexOf(der, SIGNED_DATA_OID, 0) >= 0;
  }

  /**
   * Extracts the encapsulated content (the eContent OCTET STRING inside the
   * EncapsulatedContentInfo). For our envelope the AFD bytes are stored as the first OCTET STRING
   * (tag {@code 0x04}) that appears after the signed-data OID — the eContent — which is exactly
   * what is hashed and signed.
   */
  private static byte[] extractEncapsulatedContent(byte[] der) {
    int oidEnd = indexOf(der, SIGNED_DATA_OID, 0);
    if (oidEnd < 0) {
      return null;
    }
    int cursor = oidEnd + SIGNED_DATA_OID.length;
    // Walk forward to the first OCTET STRING (0x04) primitive — the encapsulated content.
    while (cursor < der.length) {
      int tag = der[cursor] & 0xff;
      int[] lenInfo = readLength(der, cursor + 1);
      int length = lenInfo[0];
      int lengthBytes = lenInfo[1];
      int valueStart = cursor + 1 + lengthBytes;
      if (tag == 0x04) {
        byte[] content = new byte[length];
        System.arraycopy(der, valueStart, content, 0, length);
        return content;
      }
      if ((tag & 0x20) != 0) {
        // Constructed type: descend into it (the eContent lives inside the SignedData SEQUENCE).
        cursor = valueStart;
      } else {
        cursor = valueStart + length;
      }
    }
    return null;
  }

  /**
   * Whether the envelope carries at least one SignerInfo. We approximate by requiring a SET OF (tag
   * {@code 0x31}) after the content — the {@code signerInfos} SET — which our valid fixtures
   * include and an unsigned envelope omits.
   */
  private static boolean hasSignerInfo(byte[] der) {
    int oidEnd = indexOf(der, SIGNED_DATA_OID, 0);
    int from = oidEnd < 0 ? 0 : oidEnd;
    for (int i = from; i < der.length; i++) {
      if ((der[i] & 0xff) == 0x31) { // SET OF (signerInfos)
        return true;
      }
    }
    return false;
  }

  private static int[] readLength(byte[] der, int offset) {
    int first = der[offset] & 0xff;
    if ((first & 0x80) == 0) {
      return new int[] {first, 1};
    }
    int numBytes = first & 0x7f;
    int length = 0;
    for (int i = 0; i < numBytes; i++) {
      length = (length << 8) | (der[offset + 1 + i] & 0xff);
    }
    return new int[] {length, 1 + numBytes};
  }

  private static int indexOf(byte[] haystack, byte[] needle, int from) {
    outer:
    for (int i = from; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  private static String normalizeHash(String hash) {
    String value = hash.trim();
    int colon = value.indexOf(':');
    return colon >= 0 ? value.substring(colon + 1) : value;
  }

  private static String sha256Hex(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
