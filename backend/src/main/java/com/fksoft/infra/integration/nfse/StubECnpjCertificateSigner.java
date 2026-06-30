package com.fksoft.infra.integration.nfse;

import com.fksoft.domain.platform.CertificateCustodyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Billing's e-CNPJ signer adapter ({@code com.fksoft.domain.billing.CertificateSigner}; SPEC-0016
 * BR3, DL-0046). Since the Platform now owns the real certificate custody (SPEC-0023, DL-0078),
 * this adapter <strong>delegates</strong> to the Platform {@link
 * com.fksoft.domain.platform.CertificateSigner} when a certificate is custodied — so the NFS-e
 * signing seam reaches the real, encrypted-at-rest custody — and falls back to the deterministic
 * stub marker only when none is custodied (dev/empty custody). Keeping the Billing port lets the
 * Billing domain stay unchanged (back-compat). It NEVER logs the certificate/credentials
 * (security.md) — the material stays inside the Platform custody.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StubECnpjCertificateSigner implements com.fksoft.domain.billing.CertificateSigner {

  private final CertificateCustodyService custodyService;
  private final com.fksoft.domain.platform.CertificateSigner platformSigner;

  @Override
  public byte[] sign(byte[] payload) {
    if (custodyService.hasCertificate()) {
      // DL-0078: reach the real Platform custody (material never leaves it, never logged).
      return platformSigner.sign(payload);
    }
    // No certificate custodied (dev/empty): deterministic, traceable stub marker — no key to leak.
    String marker = "\n<!--e-CNPJ-sig:" + sha256(payload) + "-->";
    byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
    byte[] signed = new byte[payload.length + markerBytes.length];
    System.arraycopy(payload, 0, signed, 0, payload.length);
    System.arraycopy(markerBytes, 0, signed, payload.length, markerBytes.length);
    log.info(
        "NfsePayloadSigned bytes={} (stub e-CNPJ — no certificate custodied yet)", signed.length);
    return signed;
  }

  private static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
