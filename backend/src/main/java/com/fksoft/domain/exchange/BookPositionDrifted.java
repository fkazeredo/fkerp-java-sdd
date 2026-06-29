package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.time.Instant;

/**
 * Business fact: the book's mark-to-market drift crossed the configured alert threshold (SPEC-0011
 * BR4/BR9). This is an <strong>alert</strong> evaluated on the aggregate book position — it never
 * blocks. Published in-process by {@code exchange}; consumer: {@code intelligence}
 * (LiveExposure/alert, 8.2-C).
 *
 * @param asOf the instant the exposure was evaluated
 * @param markToMarketDrift the book's mark-to-market drift (BRL)
 * @param threshold the alert threshold that was crossed (BRL)
 * @param occurredAt when the alert was raised
 */
public record BookPositionDrifted(
    Instant asOf, Money markToMarketDrift, Money threshold, Instant occurredAt) {}
