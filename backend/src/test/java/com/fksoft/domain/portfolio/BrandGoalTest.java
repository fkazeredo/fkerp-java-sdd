package com.fksoft.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link BrandGoal} aggregate (SPEC-0020 BR3): the period format and the
 * target's consistency with the metric (REVENUE needs a positive BRL amount; VOLUME needs a
 * positive count).
 */
class BrandGoalTest {

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");

  @Test
  void definesARevenueGoalInBrl() {
    BrandGoal goal =
        BrandGoal.define(
            "ALAMO",
            "2026",
            GoalMetricCodes.REVENUE,
            Money.of(new BigDecimal("1200000.00"), "BRL"),
            null,
            NOW,
            "admin");
    assertThat(goal.metric()).isEqualTo(GoalMetricCodes.REVENUE);
    assertThat(goal.targetMoney()).isEqualTo(Money.of(new BigDecimal("1200000.00"), "BRL"));
    assertThat(goal.targetCount()).isNull();
  }

  @Test
  void definesAVolumeGoal() {
    BrandGoal goal =
        BrandGoal.define("ALAMO", "2026-06", GoalMetricCodes.VOLUME, null, 120, NOW, "admin");
    assertThat(goal.metric()).isEqualTo(GoalMetricCodes.VOLUME);
    assertThat(goal.targetCount()).isEqualTo(120);
    assertThat(goal.targetMoney()).isNull();
  }

  @Test
  void rejectsAMalformedPeriod() {
    assertThatThrownBy(
            () ->
                BrandGoal.define(
                    "ALAMO", "2026/06", GoalMetricCodes.VOLUME, null, 10, NOW, "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
    assertThatThrownBy(
            () ->
                BrandGoal.define(
                    "ALAMO", "2026-13", GoalMetricCodes.VOLUME, null, 10, NOW, "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
  }

  @Test
  void rejectsARevenueGoalWithoutAPositiveBrlTarget() {
    assertThatThrownBy(
            () ->
                BrandGoal.define(
                    "ALAMO", "2026", GoalMetricCodes.REVENUE, null, null, NOW, "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
    assertThatThrownBy(
            () ->
                BrandGoal.define(
                    "ALAMO",
                    "2026",
                    GoalMetricCodes.REVENUE,
                    Money.of(BigDecimal.ZERO, "BRL"),
                    null,
                    NOW,
                    "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
    // A non-BRL revenue target is rejected (the revenue goal is denominated in BRL — BR6/DL-0062).
    assertThatThrownBy(
            () ->
                BrandGoal.define(
                    "ALAMO",
                    "2026",
                    GoalMetricCodes.REVENUE,
                    Money.of(new BigDecimal("1000.00"), "USD"),
                    null,
                    NOW,
                    "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
  }

  @Test
  void rejectsAVolumeGoalWithoutAPositiveCount() {
    assertThatThrownBy(
            () -> BrandGoal.define("ALAMO", "2026", GoalMetricCodes.VOLUME, null, 0, NOW, "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
  }
}
