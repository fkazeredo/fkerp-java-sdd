package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a customer refund was executed (SPEC-0017 Events; BR7). Published in-process by
 * the payout module when a {@code REFUND} payout finishes executing. Consumed by AfterSales and
 * Finance (to baixar the REFUND payable idempotently). It carries the {@code originRef} of the
 * obligation that justified the refund (BR7). Executing this refund does <strong>not</strong> touch
 * the supplier obligation — the merchant trap stays intact (DL-0024/DL-0051).
 *
 * @param payoutId the executed payout id (the idempotency source ref)
 * @param originRef the origin obligation reference (the cancellation/aftersales charge)
 * @param amount the amount refunded
 * @param occurredAt when it was refunded
 */
public record RefundExecuted(UUID payoutId, String originRef, Money amount, Instant occurredAt) {}
