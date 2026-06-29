package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;
import java.util.List;

/**
 * The accumulated facts about one subject (an agency in v1, DL-0034) that feed the {@link
 * PromoFxAdvisor} (SPEC-0013 BR5). All numbers are projected from consumed events — they carry
 * their own provenance ({@link #sources()}) so the advice cites where each number came from
 * (BR1/BR5).
 *
 * @param accruedSubsidy Σ {@code RateSubsidyAccrued.subsidy} attributed to the subject — the
 *     intentional promotion cost (BRL)
 * @param realizedGap Σ {@code FxPositionClosed.totalGap} attributed to the subject — subsidy +
 *     realized drift (BRL); non-negative means the promo paid for itself
 * @param volumeAttracted the number of confirmed positions/bookings for the subject
 * @param sources the event types that back these numbers (provenance)
 */
public record PromoFxSignal(
    Money accruedSubsidy, Money realizedGap, long volumeAttracted, List<String> sources) {

  public PromoFxSignal {
    if (accruedSubsidy == null || realizedGap == null) {
      throw new IllegalArgumentException("accruedSubsidy and realizedGap are required");
    }
    if (volumeAttracted < 0) {
      throw new IllegalArgumentException("volumeAttracted cannot be negative");
    }
    sources = sources == null ? List.of() : List.copyOf(sources);
  }
}
