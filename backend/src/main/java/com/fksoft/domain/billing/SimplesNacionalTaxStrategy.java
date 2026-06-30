package com.fksoft.domain.billing;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The Simples Nacional tax strategy (SPEC-0016 BR2a; DL-0044) — the v1 default regime. ISS = {@code
 * issRate(municipality) × commissionBase} (the rate from {@link MunicipalIssRateProvider}, default
 * 5% / São Paulo 2%), rounded to scale 2 HALF_UP by the {@link Money} kernel. Under the Simples
 * unified-collection regime the optant does not suffer the federal withholdings
 * (IRRF/PIS/COFINS/CSLL) on the commission (IN RFB 1.234/2012), so the withholdings list is empty.
 * A Presumido/Real strategy is plugged in (without touching this class) when the accountant
 * confirms the real regime.
 */
@Component
@RequiredArgsConstructor
@ModuleInternal
public class SimplesNacionalTaxStrategy implements TaxRegimeStrategy {

  private final MunicipalIssRateProvider rateProvider;

  @Override
  public TaxAssessment assess(Money commissionBase, String municipality, String serviceCode) {
    BigDecimal rate = rateProvider.rateFor(municipality);
    Money iss = commissionBase.multiply(rate);
    // Simples optant: no federal withholdings on the commission (DL-0044).
    return new TaxAssessment(iss, List.of(), TaxRegime.SIMPLES_NACIONAL);
  }
}
