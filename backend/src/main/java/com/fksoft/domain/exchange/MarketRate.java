package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only market-rate observation (SPEC-0011 BR1): the rate the market showed for a pair at a
 * point in time. Rows are never updated or deleted; "market now" is the most recent observation not
 * in the future. Positivity and scale are guaranteed by the service before the entity is built.
 * Module-internal: other modules read the market rate through the {@code MarketRateProvider} port.
 */
@Entity
@Table(name = "market_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class MarketRate {

  @Id private UUID id;

  private String currencyPair;

  private BigDecimal rate;

  private Instant observedAt;

  @Enumerated(EnumType.STRING)
  private MarketRateSource source;

  private Instant createdAt;

  @Column(name = "created_by")
  private String recordedBy;

  /**
   * Records a market-rate observation. The caller guarantees {@code rate > 0} and the correct scale
   * (BR1).
   *
   * @param pair the currency pair
   * @param rate the positive market rate (scale 6)
   * @param observedAt when the market showed this rate
   * @param source where the observation came from
   * @param recordedBy who recorded it (audit), or {@code null} for a feed
   * @param createdAt creation instant (UTC)
   * @return a new, persistable observation
   */
  public static MarketRate record(
      CurrencyPair pair,
      BigDecimal rate,
      Instant observedAt,
      MarketRateSource source,
      String recordedBy,
      Instant createdAt) {
    MarketRate observation = new MarketRate();
    observation.id = UUID.randomUUID();
    observation.currencyPair = pair.asText();
    observation.rate = rate;
    observation.observedAt = observedAt;
    observation.source = source;
    observation.recordedBy = recordedBy;
    observation.createdAt = createdAt;
    return observation;
  }

  /** Projects this entity to its public read view. */
  public MarketRateView toView() {
    return new MarketRateView(id, CurrencyPair.parse(currencyPair), rate, observedAt, source);
  }
}
