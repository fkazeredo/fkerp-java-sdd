package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Deterministic, rule-based advisor that decides whether the FX freeze promotion paid for itself
 * for a subject (SPEC-0013 BR5, DL-0035). It is a pure function over the accumulated {@link
 * PromoFxSignal} facts: testable, reproducible, with no external dependency (Rule Zero / phase
 * DESIGN GUIDANCE — no LLM/ML here, the "intelligence" is prescriptive arithmetic over observed
 * facts). It ADVISES, it does not command (BR2/BR3).
 *
 * <p>Verdict (DL-0035), thresholds are governed constants (future SPEC-0014):
 *
 * <ul>
 *   <li><b>CONVERTE</b> (keep): {@code volumeAttracted ≥ MIN_VOLUME} and {@code realizedGap ≥ 0} —
 *       the promo paid for itself with enough attracted volume. Estimated gain = the realized gap
 *       to keep (or the recovered subsidy when the gap is exactly zero but volume is high).
 *   <li><b>QUEIMA_MARGEM</b> (tighten): {@code realizedGap < 0} and {@code |realizedGap| >
 *       BURN_THRESHOLD} — it only burned margin beyond tolerance. Estimated risk = the margin
 *       burned; the guardrail (the crossed threshold) is attached as an ALERT (BR3), never a block.
 *   <li>Otherwise: no verdict ({@link Optional#empty()}) — the advisor stays silent rather than
 *       producing noise.
 * </ul>
 */
public final class PromoFxAdvisor {

  /** Minimum attracted volume for a CONVERTE verdict (governed default, DL-0035). */
  static final long MIN_VOLUME = 5L;

  /** Burn threshold above which a negative gap is QUEIMA_MARGEM (governed default, DL-0035). */
  static final BigDecimal BURN_THRESHOLD = new BigDecimal("1000.00");

  private static final String CURRENCY = "BRL";

  private PromoFxAdvisor() {}

  /**
   * Assesses the signal, returning advice only when the rule yields a verdict.
   *
   * @param signal the accumulated facts for one subject
   * @return the advice, or empty when neither verdict applies (stay silent)
   */
  public static Optional<PromoFxAssessment> assess(PromoFxSignal signal) {
    Money gap = signal.realizedGap();
    if (gap.isNonNegative() && signal.volumeAttracted() >= MIN_VOLUME) {
      Money gain = gap.amount().signum() == 0 ? signal.accruedSubsidy() : gap;
      return Optional.of(
          new PromoFxAssessment(IntelligenceCodes.CONVERTE, gain, null, null, signal.sources()));
    }
    if (gap.isNegative()) {
      Money burned = Money.of(gap.amount().abs(), CURRENCY);
      if (burned.amount().compareTo(BURN_THRESHOLD) > 0) {
        Money threshold = Money.of(BURN_THRESHOLD, CURRENCY);
        return Optional.of(
            new PromoFxAssessment(
                IntelligenceCodes.QUEIMA_MARGEM,
                Money.zero(CURRENCY),
                burned,
                threshold,
                signal.sources()));
      }
    }
    return Optional.empty();
  }
}
