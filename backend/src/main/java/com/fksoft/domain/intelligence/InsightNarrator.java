package com.fksoft.domain.intelligence;

/**
 * Domain port that turns an already-computed assessment into the human-readable action text of a
 * recommendation (SPEC-0013 BR1 "recommendation: action"). It is the SEAM for a future predictive/
 * LLM narrator (DL-0036): the default {@code RuleBasedInsightNarrator} is fully deterministic with
 * NO external dependency.
 *
 * <p>If a real LLM ever sits behind this port it MUST (messaging-and-integrations.md, AI):
 *
 * <ul>
 *   <li>live behind this port as an ACL adapter in {@code infra/integration}, with a deterministic
 *       stub in tests (never block the gate on an external call / API key);
 *   <li>validate its output before it affects state, with a fallback to the rule-based text;
 *   <li>version provider/model/prompt and mask personal data, logging it as an AI event;
 *   <li>use the latest Claude model id {@code claude-opus-4-8} for any real wiring.
 * </ul>
 *
 * <p>Crucially, the narrator only produces TEXT for an advice whose numbers/verdict were already
 * decided deterministically by {@link PromoFxAdvisor} — it never decides the verdict, so it can
 * never make the DSS command anything.
 */
public interface InsightNarrator {

  /**
   * Narrates the action text for a promo-FX assessment about a subject.
   *
   * @param subjectKind the axis the insight is about
   * @param subjectRef the subject reference
   * @param assessment the already-computed assessment (verdict + gain/risk)
   * @return the human-readable action text
   */
  String narratePromoFx(String subjectKind, String subjectRef, PromoFxAssessment assessment);
}
