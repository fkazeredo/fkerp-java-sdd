package com.fksoft.domain.payout;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the payment gateway request itself fails synchronously (SPEC-0017 BR2 — classified
 * gateway failure, "falha de gateway → 502 classificado"). The asynchronous outcome
 * (SUCCEEDED/FAILED) arrives via webhook and is NOT this exception; this covers the request leg
 * failing (timeout/unavailable/invalid response) so the caller never sees a false "executed".
 * Mapped to {@code 502 Bad Gateway}.
 */
public class PayoutGatewayException extends DomainException {

  public PayoutGatewayException() {
    super("payout.gateway.failure");
  }
}
