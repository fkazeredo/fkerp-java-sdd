package com.fksoft.infra.integration.payment;

import com.fksoft.domain.payout.PayoutWebhookSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies the payment webhook (ADR 0006; DL-0048): an HMAC-SHA256 of the raw callback
 * body keyed by a shared secret, sent in the {@code X-Payment-Signature} header (hex, optionally
 * prefixed {@code sha256=}). The mock signs with the same scheme a real provider will use, so the
 * verification code is not specific to the mock. Comparison is constant-time. A missing/mismatched
 * signature raises {@link PayoutWebhookSignatureInvalidException} (401) and nothing is processed.
 * Infra-only; the secret lives in configuration, never in the domain.
 */
@Slf4j
@Component
public class PayoutWebhookSignature {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public PayoutWebhookSignature(
      @Value("${integration.payment.webhook-secret:dev-payment-webhook-secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Verifies the signature of the raw body. Raises {@link PayoutWebhookSignatureInvalidException}
   * when the header is absent/blank or the computed HMAC does not match.
   *
   * @param rawBody the exact bytes received
   * @param signatureHeader the {@code X-Payment-Signature} header value (nullable)
   */
  public void verify(byte[] rawBody, String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new PayoutWebhookSignatureInvalidException();
    }
    String provided = stripPrefix(signatureHeader.trim());
    byte[] expected = hmac(rawBody);
    byte[] providedBytes = decodeHexOrReject(provided);
    if (!MessageDigest.isEqual(expected, providedBytes)) {
      throw new PayoutWebhookSignatureInvalidException();
    }
  }

  /**
   * Computes the HMAC-SHA256 of a body with the shared secret — used by the mock/clients to sign.
   */
  public String sign(byte[] rawBody) {
    return HexFormat.of().formatHex(hmac(rawBody));
  }

  private byte[] hmac(byte[] rawBody) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(rawBody);
    } catch (java.security.GeneralSecurityException e) {
      // Misconfiguration of the algorithm/key is not a client error; do not leak details.
      log.error("Failed to compute payment webhook HMAC", e);
      throw new IllegalStateException("cannot compute HMAC");
    }
  }

  private static String stripPrefix(String header) {
    int eq = header.indexOf('=');
    if (header.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
      return header.substring(eq + 1);
    }
    return header;
  }

  private static byte[] decodeHexOrReject(String hex) {
    try {
      return HexFormat.of().parseHex(hex);
    } catch (IllegalArgumentException invalidHex) {
      throw new PayoutWebhookSignatureInvalidException();
    }
  }
}
