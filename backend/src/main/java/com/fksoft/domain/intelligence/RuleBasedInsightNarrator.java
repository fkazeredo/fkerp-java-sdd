package com.fksoft.domain.intelligence;

import org.springframework.stereotype.Component;

/**
 * Deterministic default {@link InsightNarrator} (DL-0036): builds the recommendation action text by
 * rule, with NO external dependency. This is the seam where a real LLM adapter could plug in later
 * (behind the same port, with a stub in tests) — but the v1 advice is fully deterministic, so the
 * build never depends on a live model.
 */
@Component
class RuleBasedInsightNarrator implements InsightNarrator {

  @Override
  public String narratePromoFx(
      String subjectKind, String subjectRef, PromoFxAssessment assessment) {
    String subject = subjectKind.toLowerCase(java.util.Locale.ROOT) + " " + subjectRef;
    if (IntelligenceCodes.CONVERTE.equals(assessment.verdict())) {
      return "manter o congelamento de câmbio para a " + subject + " (a promoção se paga)";
    }
    return "apertar o congelamento de câmbio para a "
        + subject
        + " (só queima margem além do limite)";
  }
}
