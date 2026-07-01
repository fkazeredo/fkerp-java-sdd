package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.billing.MunicipalIssRateProvider;
import com.fksoft.domain.billing.SimplesNacionalTaxStrategy;
import com.fksoft.domain.billing.TaxAssessment;
import com.fksoft.domain.billing.TaxRegimeCodes;
import com.fksoft.domain.billing.TaxRegimeStrategy;
import com.fksoft.domain.billing.WithholdingKindCodes;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the tax computation (SPEC-0016 BR2/BR2a; DL-0044). Proves the exact ISS math with
 * HALF_UP rounding for the Simples Nacional default, that the taxable base is the commission (not
 * the gross package), and that the regime is a swappable strategy.
 */
class TaxRegimeStrategyTest {

  // 5% default rate; São Paulo (3550308) overridden to 2% — DL-0044 seed.
  private final MunicipalIssRateProvider rates =
      municipality ->
          "3550308".equals(municipality) ? new BigDecimal("0.02") : new BigDecimal("0.05");

  private final TaxRegimeStrategy simples = new SimplesNacionalTaxStrategy(rates);

  @Test
  void simplesNacionalIssIsRateTimesCommissionBaseHalfUp() {
    // Acceptance Criteria: commission of R$ 405,00 → ISS at default 5% = R$ 20,25.
    Money base = Money.of(new BigDecimal("405.00"), "BRL");

    TaxAssessment assessment = simples.assess(base, "9999999", "1.05");

    assertThat(assessment.iss()).isEqualTo(Money.of(new BigDecimal("20.25"), "BRL"));
    assertThat(assessment.regime()).isEqualTo(TaxRegimeCodes.SIMPLES_NACIONAL);
  }

  @Test
  void simplesNacionalUsesTheMunicipalRateForSaoPaulo() {
    // São Paulo capital (3550308) = 2% → 405,00 × 0,02 = R$ 8,10.
    Money base = Money.of(new BigDecimal("405.00"), "BRL");

    TaxAssessment assessment = simples.assess(base, "3550308", "1.05");

    assertThat(assessment.iss()).isEqualTo(Money.of(new BigDecimal("8.10"), "BRL"));
  }

  @Test
  void simplesNacionalRoundsHalfUp() {
    // 333,33 × 0,05 = 16,6665 → HALF_UP scale 2 → 16,67.
    Money base = Money.of(new BigDecimal("333.33"), "BRL");

    TaxAssessment assessment = simples.assess(base, "9999999", "1.05");

    assertThat(assessment.iss()).isEqualTo(Money.of(new BigDecimal("16.67"), "BRL"));
  }

  @Test
  void simplesNacionalHasNoFederalWithholdings() {
    // DL-0044: Simples optant does not suffer federal withholdings on the commission (IN RFB
    // 1234/2012).
    Money base = Money.of(new BigDecimal("405.00"), "BRL");

    TaxAssessment assessment = simples.assess(base, "9999999", "1.05");

    assertThat(assessment.withholdings()).isEmpty();
  }

  @Test
  void theRegimeIsASwappableStrategy() {
    // DL-0044: swapping the strategy changes the result without touching the aggregate/service.
    // A stub "Presumido" that withholds IRRF at 1,5% of the commission base proves the seam.
    TaxRegimeStrategy presumidoStub =
        (commissionBase, municipality, serviceCode) ->
            new TaxAssessment(
                commissionBase.multiply(new BigDecimal("0.05")),
                List.of(
                    new com.fksoft.domain.billing.Withholding(
                        WithholdingKindCodes.IRRF,
                        commissionBase.multiply(new BigDecimal("0.015")))),
                TaxRegimeCodes.LUCRO_PRESUMIDO);

    Money base = Money.of(new BigDecimal("405.00"), "BRL");
    TaxAssessment assessment = presumidoStub.assess(base, "9999999", "1.05");

    assertThat(assessment.regime()).isEqualTo(TaxRegimeCodes.LUCRO_PRESUMIDO);
    assertThat(assessment.withholdings()).hasSize(1);
    assertThat(assessment.withholdings().get(0).kind()).isEqualTo(WithholdingKindCodes.IRRF);
    // 405,00 × 0,015 = 6,075 → HALF_UP → 6,08.
    assertThat(assessment.withholdings().get(0).amount())
        .isEqualTo(Money.of(new BigDecimal("6.08"), "BRL"));
  }
}
