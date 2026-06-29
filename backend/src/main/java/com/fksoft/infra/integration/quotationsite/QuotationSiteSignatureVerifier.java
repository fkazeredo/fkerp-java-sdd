package com.fksoft.infra.integration.quotationsite;

import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the quotation-site webhook signature (SPEC-0009 BR3, DL-0016): an HMAC-SHA256 of the raw
 * request body keyed by a shared secret, sent in the {@code X-Signature} header (hex, optionally
 * prefixed {@code sha256=}). Comparison is constant-time to avoid timing attacks. A missing or
 * mismatched signature raises {@link IntegrationSignatureInvalidException} (401) and nothing is
 * created. This is an infra (driven-adapter) concern; the secret lives in configuration, never in
 * the domain.
 */
@Slf4j
@Component
public class QuotationSiteSignatureVerifier {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public QuotationSiteSignatureVerifier(
      @Value("${integration.quotation-site.secret:dev-quotation-site-secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Verifies the signature of the raw body. Raises {@link IntegrationSignatureInvalidException}
   * when the header is absent/blank or the computed HMAC does not match.
   *
   * @param rawBody the exact bytes received
   * @param signatureHeader the {@code X-Signature} header value (nullable)
   */
  public void verify(byte[] rawBody, String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new IntegrationSignatureInvalidException();
    }
    String provided = stripPrefix(signatureHeader.trim());
    byte[] expected = hmac(rawBody);
    byte[] providedBytes = decodeHexOrReject(provided);
    if (!MessageDigest.isEqual(expected, providedBytes)) {
      throw new IntegrationSignatureInvalidException();
    }
  }

  /** Computes the HMAC-SHA256 of a body with the shared secret — used by tests/clients to sign. */
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
      log.error("Failed to compute webhook HMAC", e);
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
      throw new IntegrationSignatureInvalidException();
    }
  }
}
