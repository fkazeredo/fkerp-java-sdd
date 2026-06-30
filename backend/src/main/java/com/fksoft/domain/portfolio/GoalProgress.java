package com.fksoft.domain.portfolio;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;

/**
 * Read-model of a brand goal's progress (SPEC-0020 BR4): the target vs the realized, with the
 * attainment percentage. For a {@link GoalMetric#REVENUE} goal the amounts are {@link Money} (BRL)
 * and the counts are {@code null}; for a {@link GoalMetric#VOLUME} goal it is the other way around.
 * The {@code attainmentPct} is {@code realized / target * 100}, scale 1, HALF_UP.
 *
 * @param brandRef the targeted brand (value)
 * @param period the period (YYYY or YYYY-MM)
 * @param metric VOLUME or REVENUE
 * @param targetAmount the REVENUE target (BRL), or {@code null} for VOLUME
 * @param realizedAmount the REVENUE realized (BRL), or {@code null} for VOLUME
 * @param targetCount the VOLUME target, or {@code null} for REVENUE
 * @param realizedCount the VOLUME realized, or {@code null} for REVENUE
 * @param attainmentPct realized over target as a percentage (scale 1)
 */
public record GoalProgress(
    String brandRef,
    String period,
    GoalMetric metric,
    Money targetAmount,
    Money realizedAmount,
    Integer targetCount,
    Integer realizedCount,
    BigDecimal attainmentPct) {}
