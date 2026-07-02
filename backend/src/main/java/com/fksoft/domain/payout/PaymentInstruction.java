package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.util.UUID;

/**
 * What the {@link PaymentGateway} must pay (SPEC-0017): one installment of a payout. The {@code
 * outcomeHint} lets a test/staging mock choose the asynchronous outcome ({@code SUCCEEDED}/{@code
 * FAILED}) deterministically (ADR 0006); a real provider ignores it. Sensitive payment data is
 * never carried here or logged (SPEC-0017 Error Behavior).
 *
 * @param payoutId the payout id
 * @param installmentSeq the 1-based installment sequence
 * @param amount the amount to pay
 * @param payeeType the payee kind (drives the receipt document type)
 * @param outcomeHint the desired mock outcome, or {@code null} for the default (SUCCEEDED)
 */
public record PaymentInstruction(
    UUID payoutId,
    int installmentSeq,
    Money amount,
    String payeeType,
    PaymentOutcome outcomeHint) {}
