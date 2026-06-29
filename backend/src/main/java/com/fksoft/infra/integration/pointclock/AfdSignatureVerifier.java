package com.fksoft.infra.integration.pointclock;

/**
 * Port that verifies the signature/integrity of a signed AFD/AEJ artifact on ingestion (SPEC-0012
 * BR4; DL-0032). The internal domain depends only on this contract; the concrete verification (the
 * CAdES/PKCS#7 structure check) lives in the adapter. A full ICP-Brasil chain validation
 * (certificate against the root CA, CRL/OCSP, timestamp) is custodied by the Platform (SPEC-0023)
 * and is out of scope of this phase — this port can be strengthened there without changing callers
 * (DL-0032).
 */
public interface AfdSignatureVerifier {

  /**
   * Verifies that {@code signedFile} is a well-formed CAdES/PKCS#7 signed envelope carrying a
   * signature, and that its signed content matches {@code expectedContentHash} (tamper check).
   *
   * @param signedFile the signed file bytes (the original {@code .p7s} — preserved as-is, BR4)
   * @param expectedContentHash the expected SHA-256 of the signed content ({@code sha256:hex} or
   *     raw hex), as declared by the official export/operator
   * @return {@code true} when the envelope is valid, signed and untampered; {@code false} otherwise
   */
  boolean verify(byte[] signedFile, String expectedContentHash);
}
