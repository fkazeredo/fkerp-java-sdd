package com.fksoft.domain.exchange.internal;

import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.exchange.PinnedSellRateView;
import jakarta.persistence.Column;
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
 * Append-only record of a pinned sell rate (BR2): pinning a new rate never mutates an earlier one;
 * rows are never updated or deleted. The rate's positivity (BR4) is guaranteed before the entity is
 * built. Module-internal: other modules read the rate through the {@code exchange} Open-Host port.
 */
@Entity
@Table(name = "pinned_sell_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinnedSellRate {

  @Id private UUID id;

  private String currencyPair;

  private BigDecimal rate;

  private Instant effectiveFrom;

  // Stored as set_by (SPEC-0003); the Java field avoids a "setBy" getter that would look like a
  // JavaBean setter to the ArchUnit entity-mutation rule.
  @Column(name = "set_by")
  private String pinnedBy;

  private String note;

  private Instant createdAt;

  /**
   * Pins a new rate. The caller guarantees {@code rate > 0} (BR4) and the correct scale; {@code
   * effectiveFrom} may be past, now or future (BR5).
   *
   * @param pair the currency pair
   * @param rate the positive sell rate (scale 6)
   * @param effectiveFrom when the rate begins to prevail
   * @param setBy who pinned it (audit)
   * @param note optional note, or {@code null}
   * @param createdAt creation instant (UTC)
   * @return a new, persistable pinned rate
   */
  public static PinnedSellRate pin(
      CurrencyPair pair,
      BigDecimal rate,
      Instant effectiveFrom,
      String setBy,
      String note,
      Instant createdAt) {
    PinnedSellRate pinned = new PinnedSellRate();
    pinned.id = UUID.randomUUID();
    pinned.currencyPair = pair.asText();
    pinned.rate = rate;
    pinned.effectiveFrom = effectiveFrom;
    pinned.pinnedBy = setBy;
    pinned.note = note;
    pinned.createdAt = createdAt;
    return pinned;
  }

  /** Projects this entity to its public read view. */
  public PinnedSellRateView toView() {
    return new PinnedSellRateView(
        id, CurrencyPair.parse(currencyPair), rate, effectiveFrom, pinnedBy, note);
  }
}
