package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the payment webhook signature is missing or invalid (SPEC-0017; ADR 0006 — the mock
 * signs with the same HMAC scheme a real provider will use). Mapped to {@code 401 Unauthorized}.
 */
public class PayoutWebhookSignatureInvalidException extends DomainException {

  public PayoutWebhookSignatureInvalidException() {
    super("payout.webhook.signature.invalid");
  }
}
