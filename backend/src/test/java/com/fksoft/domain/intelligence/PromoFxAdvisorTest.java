package com.fksoft.domain.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic {@link PromoFxAdvisor} (SPEC-0013 BR5, DL-0035): it classifies
 * CONVERTE × QUEIMA_MARGEM from the accumulated facts, carries the provenance (sources) into the
 * advice, attaches the guardrail only when the burn threshold is crossed, and stays silent when
 * neither verdict applies. Exact fixtures — the rule is reproducible.
 */
class PromoFxAdvisorTest {

  private static final List<String> SOURCES =
      List.of("RateSubsidyAccrued", "FxPositionClosed", "BookingConfirmed");

  private static Money brl(String amount) {
    return Money.of(new BigDecimal(amount), "BRL");
  }

  @Test
  void convertsWhenGapNonNegativeWithEnoughVolume() {
    PromoFxSignal signal = new PromoFxSignal(brl("4200.00"), brl("4900.00"), 38, SOURCES);

    Optional<PromoFxAssessment> assessment = PromoFxAdvisor.assess(signal);

    assertThat(assessment).isPresent();
    PromoFxAssessment advice = assessment.get();
    assertThat(advice.verdict()).isEqualTo(Verdict.CONVERTE);
    assertThat(advice.estimatedGain()).isEqualTo(brl("4900.00"));
    assertThat(advice.estimatedRisk()).isNull();
    assertThat(advice.hasGuardrail()).isFalse();
    assertThat(advice.sources())
        .containsExactly("RateSubsidyAccrued", "FxPositionClosed", "BookingConfirmed");
  }

  @Test
  void convertsRecoveringSubsidyWhenGapIsExactlyZero() {
    PromoFxSignal signal = new PromoFxSignal(brl("4200.00"), brl("0.00"), 10, SOURCES);

    PromoFxAssessment advice = PromoFxAdvisor.assess(signal).orElseThrow();

    assertThat(advice.verdict()).isEqualTo(Verdict.CONVERTE);
    assertThat(advice.estimatedGain()).isEqualTo(brl("4200.00"));
  }

  @Test
  void burnsMarginWhenNegativeGapCrossesThresholdAndAttachesGuardrail() {
    PromoFxSignal signal = new PromoFxSignal(brl("5000.00"), brl("-1500.00"), 6, SOURCES);

    PromoFxAssessment advice = PromoFxAdvisor.assess(signal).orElseThrow();

    assertThat(advice.verdict()).isEqualTo(Verdict.QUEIMA_MARGEM);
    assertThat(advice.estimatedRisk()).isEqualTo(brl("1500.00"));
    assertThat(advice.estimatedGain()).isEqualTo(brl("0.00"));
    assertThat(advice.hasGuardrail()).isTrue();
    assertThat(advice.guardrailThresholdCrossed()).isEqualTo(brl("1000.00"));
  }

  @Test
  void staysSilentWhenNegativeGapIsWithinTolerance() {
    PromoFxSignal signal = new PromoFxSignal(brl("800.00"), brl("-900.00"), 6, SOURCES);

    assertThat(PromoFxAdvisor.assess(signal)).isEmpty();
  }

  @Test
  void staysSilentWhenGapPositiveButVolumeTooLow() {
    PromoFxSignal signal = new PromoFxSignal(brl("4200.00"), brl("4900.00"), 4, SOURCES);

    assertThat(PromoFxAdvisor.assess(signal)).isEmpty();
  }
}
