package com.fksoft.infra.platform;

import com.fksoft.domain.platform.CertificateCustodyService;
import com.fksoft.domain.platform.CertificateSigner;
import com.fksoft.domain.platform.CertificateUnavailableException;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custody-backed adapter for the Platform {@link CertificateSigner} port (SPEC-0023 BR1; DL-0078).
 * It loads the active certificate's secret material from the custody (decrypted in-process, never
 * exposed), derives a detached signature over the payload and appends a marker — proving the
 * signing seam reaches the real custodied material end to end, <strong>without the material ever
 * leaving custody or being logged</strong> (BR1, security.md).
 *
 * <p>The real ICP-Brasil A1/A3 signing (CAdES/XAdES with the actual private key) is the owner's
 * infra decision (Open Question); when chosen, a new adapter implements this same port — the domain
 * does not change. Here the material is used as an HMAC key so the signature genuinely depends on
 * the custodied secret, while the secret itself is never written anywhere.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustodyCertificateSigner implements CertificateSigner {

  private final CertificateCustodyService custodyService;

  @Override
  public byte[] sign(byte[] payload) {
    byte[] material = custodyService.loadActiveMaterial(); // decrypted in custody; never logged
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(material, "HmacSHA256"));
      byte[] signature = mac.doFinal(payload);
      String marker = "\n<!--e-CNPJ-sig:" + java.util.HexFormat.of().formatHex(signature) + "-->";
      byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
      byte[] signed = new byte[payload.length + markerBytes.length];
      System.arraycopy(payload, 0, signed, 0, payload.length);
      System.arraycopy(markerBytes, 0, signed, payload.length, markerBytes.length);
      log.info("CustodySignedPayload bytes={} (e-CNPJ custody — SPEC-0023)", signed.length);
      return signed;
    } catch (java.security.GeneralSecurityException cannotSign) {
      // Never include the material in the message.
      throw new CertificateUnavailableException();
    } finally {
      java.util.Arrays.fill(material, (byte) 0); // wipe the plaintext material from memory
    }
  }
}
