package com.fksoft.domain.portfolio;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One realized contribution to a brand's progress (SPEC-0020 BR4; DL-0062): a single sale event
 * projected onto a brand+metric. It is <strong>idempotent</strong> per {@code (metric, sourceRef)}
 * — the {@code sourceRef} is the originating event key (the bookingId for VOLUME, the caseId for
 * REVENUE), so a re-delivered {@code BookingConfirmed}/{@code SpreadRealized} never double-counts.
 * The realized total for a (brand, period, metric) is the aggregate of these rows. Module-internal.
 */
@Entity
@Table(name = "brand_realized")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class BrandRealized {

  @Id private UUID id;

  private String brandRef;

  /** The goal-metric cadastro code (was {@code GoalMetric}; SPEC-0031/DL-0116). */
  private String metric;

  private String sourceRef;
  private BigDecimal amount;
  private Integer countInc;
  private Instant occurredAt;

  /**
   * Records a VOLUME contribution (a confirmed sale increments the count by one).
   *
   * @param brandRef the brand (value)
   * @param sourceRef the originating event key (the bookingId)
   * @param occurredAt when the sale was confirmed
   * @return a new, persistable VOLUME contribution
   */
  public static BrandRealized volume(String brandRef, String sourceRef, Instant occurredAt) {
    return contribution(brandRef, GoalMetricCodes.VOLUME, sourceRef, null, 1, occurredAt);
  }

  /**
   * Records a REVENUE contribution (the realized spread, in BRL).
   *
   * @param brandRef the brand (value)
   * @param sourceRef the originating event key (the caseId)
   * @param amount the realized spread (BRL)
   * @param occurredAt when the spread was realized
   * @return a new, persistable REVENUE contribution
   */
  public static BrandRealized revenue(
      String brandRef, String sourceRef, BigDecimal amount, Instant occurredAt) {
    return contribution(brandRef, GoalMetricCodes.REVENUE, sourceRef, amount, null, occurredAt);
  }

  private static BrandRealized contribution(
      String brandRef,
      String metric,
      String sourceRef,
      BigDecimal amount,
      Integer countInc,
      Instant occurredAt) {
    BrandRealized realized = new BrandRealized();
    realized.id = UUID.randomUUID();
    realized.brandRef = brandRef;
    realized.metric = metric;
    realized.sourceRef = sourceRef;
    realized.amount = amount;
    realized.countInc = countInc;
    realized.occurredAt = occurredAt;
    return realized;
  }
}
