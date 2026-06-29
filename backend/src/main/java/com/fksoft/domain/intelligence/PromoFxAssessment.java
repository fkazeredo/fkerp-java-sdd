package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;
import java.util.List;

/**
 * The {@link PromoFxAdvisor}'s output for one subject (SPEC-0013 BR5, DL-0035): a verdict plus the
 * estimated gain/risk and the guardrail crossed (if any), all in Money with the provenance of the
 * numbers. It is a pure advice value — it changes no state and commands nothing (BR2/BR3).
 *
 * @param verdict CONVERTE (keep) or QUEIMA_MARGEM (tighten)
 * @param estimatedGain the gain of following the advice (BRL); the gap to keep when CONVERTE
 * @param estimatedRisk the margin burned if nothing changes (BRL); set when QUEIMA_MARGEM, else
 *     {@code null}
 * @param guardrailThresholdCrossed the burn threshold crossed (BRL) when QUEIMA_MARGEM, else {@code
 *     null} — an ALERT, never a block (BR3)
 * @param sources the event types backing the advice (provenance)
 */
public record PromoFxAssessment(
    Verdict verdict,
    Money estimatedGain,
    Money estimatedRisk,
    Money guardrailThresholdCrossed,
    List<String> sources) {

  public PromoFxAssessment {
    if (verdict == null) {
      throw new IllegalArgumentException("verdict is required");
    }
    sources = sources == null ? List.of() : List.copyOf(sources);
  }

  /** Whether a guardrail was crossed (QUEIMA_MARGEM beyond the burn threshold). */
  public boolean hasGuardrail() {
    return guardrailThresholdCrossed != null;
  }
}
