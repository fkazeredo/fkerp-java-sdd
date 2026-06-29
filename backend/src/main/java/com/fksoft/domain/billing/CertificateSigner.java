package com.fksoft.domain.billing;

/**
 * Port for signing a fiscal payload with the issuer's e-CNPJ digital certificate (SPEC-0016 BR3;
 * DL-0046). The certificate custody is owned by the Platform (SPEC-0023); until that exists, a
 * traceable stub adapter "signs" without holding a real ICP-Brasil key. The certificate/credentials
 * MUST NEVER be logged (security.md). Modeling it as a port lets the real Platform-backed signer be
 * plugged later without touching the domain.
 */
public interface CertificateSigner {

  /**
   * Signs the given payload bytes with the issuer's e-CNPJ, returning the signed bytes.
   *
   * @param payload the unsigned fiscal payload (e.g. the NFS-e XML)
   * @return the signed payload bytes
   */
  byte[] sign(byte[] payload);
}
