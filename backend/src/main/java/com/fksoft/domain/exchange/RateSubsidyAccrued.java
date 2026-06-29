package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: the rate subsidy was accrued when an {@code FxPosition} opened (SPEC-0011 BR3).
 * {@code subsidy = (marketAtFreeze − pinnedRate) × foreignAmount}; positive means a subsidy given
 * (promotion), negative means the sale was above market. Published in-process by {@code exchange};
 * consumer: {@code intelligence} (promo ROI, 8.2-C).
 *
 * @param bookingId the booking whose position opened
 * @param subsidy the accrued subsidy in BRL (may be negative)
 * @param marketAtFreeze the market rate at the freeze instant (scale 6)
 * @param pinnedRate the frozen sell rate (scale 6)
 * @param occurredAt when the accrual happened
 */
public record RateSubsidyAccrued(
    UUID bookingId,
    Money subsidy,
    BigDecimal marketAtFreeze,
    BigDecimal pinnedRate,
    Instant occurredAt) {}
