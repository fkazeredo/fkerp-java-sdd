package com.fksoft.domain.payout;

/**
 * The asynchronous outcome of a payment, delivered by the webhook (SPEC-0017 BR2; ADR 0006): the
 * provider confirms ({@code SUCCEEDED}) or declines/fails ({@code FAILED}). A {@code FAILED}
 * outcome lands an explicit failure state — never a false "executed".
 */
public enum PaymentOutcome {
  SUCCEEDED,
  FAILED
}
