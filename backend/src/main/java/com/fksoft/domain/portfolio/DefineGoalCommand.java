package com.fksoft.domain.portfolio;

import com.fksoft.domain.money.Money;

/**
 * Command to define a goal for a brand (SPEC-0020 BR3). For a {@code REVENUE} goal the {@code
 * targetAmount} (BRL) is required; for a {@code VOLUME} goal the {@code targetCount} is required.
 * The {@code metric} is a goal-metric cadastro code (was {@code GoalMetric}; SPEC-0031/DL-0116),
 * validated by the service against the cadastro before the goal is stored.
 *
 * @param brandRef the targeted brand (value)
 * @param period the period (YYYY or YYYY-MM)
 * @param metric the goal-metric cadastro code (VOLUME or REVENUE)
 * @param targetAmount the REVENUE target (BRL), or {@code null} for VOLUME
 * @param targetCount the VOLUME target (count), or {@code null} for REVENUE
 */
public record DefineGoalCommand(
    String brandRef, String period, String metric, Money targetAmount, Integer targetCount) {}
