package com.fksoft.domain.intelligence.internal;

import com.fksoft.domain.intelligence.InsightNarrator;
import com.fksoft.domain.intelligence.PromoFxAssessment;
import com.fksoft.domain.intelligence.SubjectKind;
import com.fksoft.domain.intelligence.Verdict;
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
      SubjectKind subjectKind, String subjectRef, PromoFxAssessment assessment) {
    String subject = subjectKind.name().toLowerCase() + " " + subjectRef;
    if (assessment.verdict() == Verdict.CONVERTE) {
      return "manter o congelamento de câmbio para a " + subject + " (a promoção se paga)";
    }
    return "apertar o congelamento de câmbio para a "
        + subject
        + " (só queima margem além do limite)";
  }
}
