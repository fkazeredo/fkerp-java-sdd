package com.fksoft.domain.billing;

/**
 * Domain result of a successful NFS-e issuance (SPEC-0016 BR3): the municipal number and
 * verification code, plus the signed document payload (bytes) and its content type, which the
 * issuance flow archives in the Compliance vault. A failed transmission never produces this — it
 * throws a classified {@link NfseTransmissionException} instead (BR7), so an "issued" result always
 * carries a real number/code.
 *
 * @param number the municipal NFS-e number (non-blank)
 * @param verificationCode the municipal verification code (non-blank)
 * @param signedDocument the signed NFS-e document bytes (preserved as-is in the vault)
 * @param contentType the document content type (e.g. {@code application/xml})
 */
public record NfseIssuance(
    String number, String verificationCode, byte[] signedDocument, String contentType) {

  public NfseIssuance {
    if (number == null || number.isBlank()) {
      throw new IllegalArgumentException("nfse number is required");
    }
    if (verificationCode == null || verificationCode.isBlank()) {
      throw new IllegalArgumentException("nfse verification code is required");
    }
    if (signedDocument == null || signedDocument.length == 0) {
      throw new IllegalArgumentException("signed document is required");
    }
  }
}
