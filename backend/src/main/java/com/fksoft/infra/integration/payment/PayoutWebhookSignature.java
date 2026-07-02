package com.fksoft.infra.integration.payment;

import com.fksoft.domain.payout.PayoutWebhookSignatureInvalidException;
import com.fksoft.infra.integration.WebhookSignatures;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies the payment webhook (ADR 0006; DL-0048; anti-replay in Fase 19c, DL-0122): an
 * HMAC-SHA256 of {@code timestamp + "." + rawBody} keyed by a shared secret, in the {@code
 * X-Payment-Signature} header (hex, optional {@code sha256=} prefix); the timestamp travels in
 * {@code X-Payment-Signature-Timestamp} (ISO-8601). The mock signs with the same scheme a real
 * provider will use, so the verification code is not specific to the mock. A missing/mismatched
 * signature — or a stale timestamp (anti-replay) — raises {@link
 * PayoutWebhookSignatureInvalidException} (401) and nothing is processed. Delegates the crypto to
 * the shared {@link WebhookSignatures}; the secret lives in configuration, never in the domain.
 */
@Slf4j
@Component
public class PayoutWebhookSignature {

  private final WebhookSignatures signatures;
  private final Clock clock;

  public PayoutWebhookSignature(
      @Value("${integration.payment.webhook-secret:dev-payment-webhook-secret}") String secret,
      @Value("${integration.payment.replay-tolerance-seconds:300}") long toleranceSeconds,
      Clock clock) {
    this.signatures = new WebhookSignatures(secret, Duration.ofSeconds(toleranceSeconds));
    this.clock = clock;
  }

  /**
   * Verifies the signature and the anti-replay window.
   *
   * @param rawBody the exact bytes received
   * @param timestampHeader the {@code X-Payment-Signature-Timestamp} header (ISO-8601; nullable)
   * @param signatureHeader the {@code X-Payment-Signature} header (nullable)
   * @throws PayoutWebhookSignatureInvalidException when the signature is absent/invalid or the
   *     timestamp is missing/stale (replay)
   */
  public void verify(byte[] rawBody, String timestampHeader, String signatureHeader) {
    WebhookSignatures.Result result =
        signatures.verify(rawBody, timestampHeader, signatureHeader, clock.instant());
    if (result != WebhookSignatures.Result.OK) {
      if (result == WebhookSignatures.Result.REPLAY) {
        log.warn("PaymentWebhook rejected: replay/stale timestamp");
      }
      throw new PayoutWebhookSignatureInvalidException();
    }
  }

  /** Signs {@code timestamp + "." + rawBody} — used by the mock/clients to sign. */
  public String sign(String timestamp, byte[] rawBody) {
    return signatures.sign(timestamp, rawBody);
  }

  /** The current instant, so the mock signs with a fresh (in-window) timestamp. */
  public String now() {
    return clock.instant().toString();
  }
}
