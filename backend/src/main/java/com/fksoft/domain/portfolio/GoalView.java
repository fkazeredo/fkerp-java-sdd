package com.fksoft.domain.portfolio;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a brand goal (SPEC-0020 BR3).
 *
 * @param id the goal id
 * @param brandRef the targeted brand (value)
 * @param period the period (YYYY or YYYY-MM)
 * @param metric VOLUME or REVENUE
 * @param targetAmount the REVENUE target (BRL), or {@code null} for VOLUME
 * @param targetCount the VOLUME target (count), or {@code null} for REVENUE
 * @param createdAt when it was defined
 */
public record GoalView(
    UUID id,
    String brandRef,
    String period,
    String metric,
    Money targetAmount,
    Integer targetCount,
    Instant createdAt) {}
