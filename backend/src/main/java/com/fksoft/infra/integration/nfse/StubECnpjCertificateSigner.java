package com.fksoft.infra.integration.nfse;

import com.fksoft.domain.billing.CertificateSigner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Traceable stub of the e-CNPJ {@link CertificateSigner} (SPEC-0016 BR3; DL-0046). The real
 * ICP-Brasil certificate custody belongs to the Platform (SPEC-0023); until then, this stub "signs"
 * by appending a deterministic detached marker (a hash) to the payload — enough to prove the
 * ACL/issuance seam end to end without holding a real key. It is the explicit, traceable stand-in
 * for the live signer ({@code simulation-and-mocking.md}), not fake business logic shipped to
 * users. It NEVER logs the certificate/credentials (security.md) — there is no key here to leak.
 */
@Slf4j
@Component
public class StubECnpjCertificateSigner implements CertificateSigner {

  @Override
  public byte[] sign(byte[] payload) {
    // Deterministic, traceable "signature": payload + a hash marker. The real signer is SPEC-0023.
    String marker = "\n<!--e-CNPJ-sig:" + sha256(payload) + "-->";
    byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
    byte[] signed = new byte[payload.length + markerBytes.length];
    System.arraycopy(payload, 0, signed, 0, payload.length);
    System.arraycopy(markerBytes, 0, signed, payload.length, markerBytes.length);
    log.info(
        "NfsePayloadSigned bytes={} (stub e-CNPJ — SPEC-0023 owns real custody)", signed.length);
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
