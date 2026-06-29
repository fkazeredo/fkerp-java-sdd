package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an {@code FxPosition} closed when the supplier settlement was recorded (SPEC-0011
 * BR5). {@code realizedDrift = (settlementRate − marketAtFreeze) × foreignAmount} and {@code
 * totalGap = subsidy + realizedDrift}. Published in-process by {@code exchange}; consumers: {@code
 * intelligence}, {@code reconciliation} (cross-checks the per-case gain/loss).
 *
 * @param bookingId the booking whose position closed
 * @param subsidy the subsidy accrued at opening (BRL)
 * @param realizedDrift the realized market drift (BRL)
 * @param totalGap the total gap = subsidy + realizedDrift (BRL)
 * @param occurredAt when the closing happened
 */
public record FxPositionClosed(
    UUID bookingId, Money subsidy, Money realizedDrift, Money totalGap, Instant occurredAt) {}
