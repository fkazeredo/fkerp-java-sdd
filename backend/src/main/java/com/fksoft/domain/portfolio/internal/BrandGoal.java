package com.fksoft.domain.portfolio.internal;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.portfolio.BrandGoalInvalidException;
import com.fksoft.domain.portfolio.GoalMetric;
import com.fksoft.domain.portfolio.GoalView;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Brand goal aggregate root (SPEC-0020 BR3): a VOLUME or REVENUE target for a brand over a period
 * (YYYY or YYYY-MM). The target is consistent with the metric — REVENUE carries a BRL amount,
 * VOLUME a count. A goal is unique per (brand, period, metric). The realized side is a separate
 * read-model projection (BR4/DL-0062), never stored here. Module-internal.
 */
@Entity
@Table(name = "brand_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandGoal {

  /** Goal revenue is denominated in BRL (the Acme's reporting currency, OVERVIEW Part 3.2). */
  public static final String REVENUE_CURRENCY = "BRL";

  private static final Pattern PERIOD = Pattern.compile("\\d{4}(-(0[1-9]|1[0-2]))?");

  @Id private UUID id;

  private String brandRef;
  private String period;

  @Enumerated(EnumType.STRING)
  private GoalMetric metric;

  private BigDecimal targetAmount;
  private Integer targetCount;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Defines a brand goal (BR3), validating the period format and the target's consistency with the
   * metric.
   *
   * @param brandRef the targeted brand (value, required)
   * @param period the period (YYYY or YYYY-MM, required)
   * @param metric VOLUME or REVENUE (required)
   * @param targetAmount the REVENUE target (BRL), required for REVENUE, ignored for VOLUME
   * @param targetCount the VOLUME target, required for VOLUME, ignored for REVENUE
   * @param now the creation instant (UTC)
   * @param actor who defines it (audit)
   * @return a new, persistable goal
   * @throws BrandGoalInvalidException when any required data is missing or inconsistent (BR3)
   */
  public static BrandGoal define(
      String brandRef,
      String period,
      GoalMetric metric,
      Money targetAmount,
      Integer targetCount,
      Instant now,
      String actor) {
    if (brandRef == null || brandRef.isBlank() || period == null || metric == null) {
      throw new BrandGoalInvalidException();
    }
    if (!PERIOD.matcher(period).matches()) {
      throw new BrandGoalInvalidException();
    }
    BrandGoal goal = new BrandGoal();
    goal.id = UUID.randomUUID();
    goal.brandRef = brandRef.trim();
    goal.period = period;
    goal.metric = metric;
    if (metric == GoalMetric.REVENUE) {
      if (targetAmount == null
          || targetAmount.amount().signum() <= 0
          || !REVENUE_CURRENCY.equals(targetAmount.currency())) {
        throw new BrandGoalInvalidException();
      }
      goal.targetAmount = targetAmount.amount();
    } else {
      if (targetCount == null || targetCount <= 0) {
        throw new BrandGoalInvalidException();
      }
      goal.targetCount = targetCount;
    }
    goal.createdAt = now;
    goal.updatedAt = now;
    goal.createdBy = actor;
    goal.updatedBy = actor;
    return goal;
  }

  /** The goal id. */
  public UUID id() {
    return id;
  }

  /** The targeted brand (value). */
  public String brandRef() {
    return brandRef;
  }

  /** The period (YYYY or YYYY-MM). */
  public String period() {
    return period;
  }

  /** The metric. */
  public GoalMetric metric() {
    return metric;
  }

  /** The REVENUE target as money (BRL), or {@code null} for VOLUME. */
  public Money targetMoney() {
    return targetAmount == null ? null : Money.of(targetAmount, REVENUE_CURRENCY);
  }

  /** The VOLUME target count, or {@code null} for REVENUE. */
  public Integer targetCount() {
    return targetCount;
  }

  /** Projects the aggregate to its public read view. */
  public GoalView toView() {
    return new GoalView(id, brandRef, period, metric, targetMoney(), targetCount, createdAt);
  }
}
