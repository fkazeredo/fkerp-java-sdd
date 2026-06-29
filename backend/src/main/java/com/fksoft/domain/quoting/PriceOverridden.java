package com.fksoft.domain.quoting;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a human diverged from the suggested price. Published in-process; future consumer
 * is Intelligence (OverrideNudge). Audited as a manual override.
 *
 * @param quoteId the quote id
 * @param fromAmount the previous applied amount
 * @param toAmount the new applied amount
 * @param reason the mandatory reason for diverging
 * @param performedBy who performed the override
 * @param occurredAt when it happened
 */
public record PriceOverridden(
    UUID quoteId,
    Money fromAmount,
    Money toAmount,
    String reason,
    String performedBy,
    Instant occurredAt) {}
