package com.fksoft.domain.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic {@link RuleBasedInsightNarrator} after the verdict/subject-kind
 * became cadastro codes (SPEC-0031/DL-0116): the narrator still branches on the verdict code
 * (CONVERTE → keep, QUEIMA_MARGEM → tighten) and renders the subject-kind code, with no external
 * dependency. Proves the conversion preserved the wired branching.
 */
class RuleBasedInsightNarratorTest {

  private static final List<String> SOURCES = List.of("BookingConfirmed");
  private final RuleBasedInsightNarrator narrator = new RuleBasedInsightNarrator();

  @Test
  void narratesKeepForTheConverteVerdictCode() {
    PromoFxAssessment advice =
        new PromoFxAssessment(IntelligenceCodes.CONVERTE, brl("100.00"), null, null, SOURCES);

    String text = narrator.narratePromoFx(IntelligenceCodes.AGENCY, "acc-1", advice);

    assertThat(text).contains("manter").contains("agency acc-1");
  }

  @Test
  void narratesTightenForTheQueimaMargemVerdictCode() {
    PromoFxAssessment advice =
        new PromoFxAssessment(
            IntelligenceCodes.QUEIMA_MARGEM,
            Money.zero("BRL"),
            brl("50.00"),
            brl("10.00"),
            SOURCES);

    String text = narrator.narratePromoFx(IntelligenceCodes.AGENCY, "acc-2", advice);

    assertThat(text).contains("apertar").contains("agency acc-2");
  }

  private static Money brl(String amount) {
    return Money.of(new java.math.BigDecimal(amount), "BRL");
  }
}
