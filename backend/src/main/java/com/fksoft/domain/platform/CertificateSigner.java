package com.fksoft.domain.platform;

/**
 * Port for signing a payload with the custodied e-CNPJ certificate (SPEC-0023 Scope/BR1; DL-0078).
 * This is the Platform-owned signer that graduates the Billing stub: it signs WITHOUT exposing the
 * certificate material — the private key is read from the encrypted custody, used to sign, and
 * never returned, serialized or logged (BR1, security.md).
 *
 * <p>Modeling it as a port lets a real ICP-Brasil A1 (file) or A3 (token/HSM) backend be plugged
 * later without touching the domain.
 */
public interface CertificateSigner {

  /**
   * Signs the given payload bytes with the custodied e-CNPJ.
   *
   * @param payload the unsigned payload (e.g. an NFS-e XML)
   * @return the signed payload bytes
   * @throws CertificateUnavailableException when no usable certificate is custodied
   */
  byte[] sign(byte[] payload);
}
