package com.fksoft.infra.integration.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutWebhookSignatureInvalidException;
import com.fksoft.infra.integration.payment.PayoutExecutionService.WebhookConfirmation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Anti-Corruption Layer of the payment webhook (ADR 0006; DL-0048): it verifies the HMAC
 * signature over the raw body, translates the <strong>external</strong> {@link
 * PaymentWebhookPayload} into a domain outcome, and applies it <strong>idempotently</strong>. The
 * vendor shape stays here in {@code infra.integration.payment}; only domain calls leave this
 * component, so the external DTO never reaches the domain (ArchUnit boundary test).
 *
 * <p>Idempotency (SPEC-0017 BR3): a re-delivered callback for the same {@code (payoutId,
 * installmentSeq, providerRef)} is a no-op — guarded by a pre-check and the UNIQUE on {@link
 * ProcessedPayoutWebhook}. The idempotency row is committed in the same transaction as the
 * installment transition, so a duplicate never double-confirms or double-pays.
 *
 * <p>Both the inbound webhook controller and the mock dispatcher call {@link #receive} with signed
 * bytes, so the signature/idempotency path is exercised the same way whether the callback came over
 * HTTP from a real provider or from the in-process mock (the mock signs with the same scheme).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookReceiver {

  private final PayoutWebhookSignature signature;
  private final ObjectMapper objectMapper;
  private final ProcessedPayoutWebhookRepository processed;
  private final PayoutExecutionService executionService;

  /**
   * Verifies, translates and applies a payment webhook idempotently.
   *
   * @param rawBody the exact callback bytes
   * @param timestampHeader the {@code X-Payment-Signature-Timestamp} header (anti-replay)
   * @param signatureHeader the {@code X-Payment-Signature} header
   * @throws PayoutWebhookSignatureInvalidException when the signature is missing/invalid or the
   *     timestamp is stale (401)
   */
  @Transactional
  public void receive(byte[] rawBody, String timestampHeader, String signatureHeader) {
    signature.verify(rawBody, timestampHeader, signatureHeader);
    PaymentWebhookPayload payload = parse(rawBody);
    UUID payoutId = parsePayoutId(payload);
    PaymentOutcome outcome = parseOutcome(payload);

    // Idempotency pre-check (BR3): a re-delivered callback is a no-op.
    if (processed.existsByPayoutIdAndInstallmentSeqAndProviderRef(
        payoutId, payload.installmentSeq(), payload.providerRef())) {
      log.info(
          "PaymentWebhookDuplicate payoutId={} seq={} providerRef={} (no-op)",
          payoutId,
          payload.installmentSeq(),
          payload.providerRef());
      return;
    }

    try {
      processed.saveAndFlush(
          ProcessedPayoutWebhook.of(
              payoutId,
              payload.installmentSeq(),
              payload.providerRef(),
              outcome.name(),
              java.time.Instant.now()));
    } catch (DataIntegrityViolationException raced) {
      // A concurrent delivery won the race and inserted the idempotency row first — no-op (BR3).
      log.info(
          "PaymentWebhookRaced payoutId={} seq={} providerRef={} (already processed concurrently)",
          payoutId,
          payload.installmentSeq(),
          payload.providerRef());
      return;
    }

    executionService.onWebhook(
        new WebhookConfirmation(payoutId, payload.installmentSeq(), outcome, null));
    log.info(
        "PaymentWebhookProcessed payoutId={} seq={} outcome={}",
        payoutId,
        payload.installmentSeq(),
        outcome);
  }

  private PaymentWebhookPayload parse(byte[] rawBody) {
    try {
      return objectMapper.readValue(rawBody, PaymentWebhookPayload.class);
    } catch (java.io.IOException malformed) {
      // A malformed body that passed the signature is a provider contract error; reject as
      // 401-class
      // since we cannot trust it. (The mock always sends a well-formed body.)
      throw new PayoutWebhookSignatureInvalidException();
    }
  }

  private static UUID parsePayoutId(PaymentWebhookPayload payload) {
    try {
      return UUID.fromString(payload.payoutId());
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw new PayoutWebhookSignatureInvalidException();
    }
  }

  private static PaymentOutcome parseOutcome(PaymentWebhookPayload payload) {
    try {
      return PaymentOutcome.valueOf(payload.status());
    } catch (IllegalArgumentException | NullPointerException invalid) {
      throw new PayoutWebhookSignatureInvalidException();
    }
  }
}
