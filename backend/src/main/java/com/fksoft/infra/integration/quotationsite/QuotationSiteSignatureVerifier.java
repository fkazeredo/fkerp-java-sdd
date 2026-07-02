package com.fksoft.infra.integration.quotationsite;

import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import com.fksoft.infra.integration.WebhookSignatures;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the quotation-site webhook signature (SPEC-0009 BR3, DL-0016; anti-replay in Fase 19c,
 * DL-0122). The signature is an HMAC-SHA256 of {@code timestamp + "." + rawBody} keyed by a shared
 * secret, in the {@code X-Signature} header (hex, optional {@code sha256=} prefix); the request
 * timestamp travels in {@code X-Signature-Timestamp} (ISO-8601). A missing/mismatched signature —
 * or a timestamp outside the tolerance window (a captured, validly-signed body cannot be replayed
 * forever) — raises {@link IntegrationSignatureInvalidException} (401) and nothing is created.
 * Delegates the crypto to the shared {@link WebhookSignatures}; the secret lives in configuration,
 * never in the domain.
 */
@Slf4j
@Component
public class QuotationSiteSignatureVerifier {

  private final WebhookSignatures signatures;
  private final Clock clock;

  public QuotationSiteSignatureVerifier(
      @Value("${integration.quotation-site.secret:dev-quotation-site-secret}") String secret,
      @Value("${integration.quotation-site.replay-tolerance-seconds:300}") long toleranceSeconds,
      Clock clock) {
    this.signatures = new WebhookSignatures(secret, Duration.ofSeconds(toleranceSeconds));
    this.clock = clock;
  }

  /**
   * Verifies the signature and the anti-replay window.
   *
   * @param rawBody the exact bytes received
   * @param timestampHeader the {@code X-Signature-Timestamp} header (ISO-8601 instant; nullable)
   * @param signatureHeader the {@code X-Signature} header (nullable)
   * @throws IntegrationSignatureInvalidException when the signature is absent/invalid or the
   *     timestamp is missing/stale (replay)
   */
  public void verify(byte[] rawBody, String timestampHeader, String signatureHeader) {
    WebhookSignatures.Result result =
        signatures.verify(rawBody, timestampHeader, signatureHeader, clock.instant());
    if (result != WebhookSignatures.Result.OK) {
      if (result == WebhookSignatures.Result.REPLAY) {
        log.warn("QuotationSiteWebhook rejected: replay/stale timestamp");
      }
      throw new IntegrationSignatureInvalidException();
    }
  }

  /** Signs {@code timestamp + "." + rawBody} — used by tests/clients to sign. */
  public String sign(String timestamp, byte[] rawBody) {
    return signatures.sign(timestamp, rawBody);
  }
}
