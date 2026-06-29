package com.fksoft.application.api.dto;

import com.fksoft.domain.payout.PaymentOutcome;

/**
 * Optional request body for {@code POST /api/payouts/{id}/execute} (SPEC-0017). The {@code
 * outcomeHint} lets dev/test/staging steer the mock provider's asynchronous outcome ({@code
 * SUCCEEDED}/{@code FAILED}) deterministically (ADR 0006); a real provider ignores it. When the
 * body is absent the default outcome (SUCCEEDED) applies.
 *
 * @param outcomeHint the desired mock outcome, or {@code null} for the default
 */
public record ExecutePayoutRequest(PaymentOutcome outcomeHint) {}
