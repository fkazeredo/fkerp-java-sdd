package com.fksoft.domain.portfolio;

import com.fksoft.domain.money.Money;

/**
 * Command to define a goal for a brand (SPEC-0020 BR3). For a {@link GoalMetric#REVENUE} goal the
 * {@code targetAmount} (BRL) is required; for a {@link GoalMetric#VOLUME} goal the {@code
 * targetCount} is required.
 *
 * @param brandRef the targeted brand (value)
 * @param period the period (YYYY or YYYY-MM)
 * @param metric VOLUME or REVENUE
 * @param targetAmount the REVENUE target (BRL), or {@code null} for VOLUME
 * @param targetCount the VOLUME target (count), or {@code null} for REVENUE
 */
public record DefineGoalCommand(
    String brandRef, String period, GoalMetric metric, Money targetAmount, Integer targetCount) {}
