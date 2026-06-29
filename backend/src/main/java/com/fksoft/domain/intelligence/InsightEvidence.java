package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;
import java.util.List;

/**
 * The evidence behind an insight (SPEC-0013 BR1): the numbers AND their provenance (which event
 * types sustain them). For the {@code PromoFxAdvisor} these are the accrued subsidy, the realized
 * gap and the attracted volume, with the {@code sources} that produced them.
 *
 * @param accruedSubsidy the intentional promotion cost attributed to the subject (BRL)
 * @param realizedGap subsidy + realized drift attributed to the subject (BRL)
 * @param volumeAttracted the number of confirmed positions/bookings for the subject
 * @param sources the event types backing the numbers (provenance)
 */
public record InsightEvidence(
    Money accruedSubsidy, Money realizedGap, long volumeAttracted, List<String> sources) {

  public InsightEvidence {
    sources = sources == null ? List.of() : List.copyOf(sources);
  }
}
