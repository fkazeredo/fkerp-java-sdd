package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;
import java.util.List;

/**
 * Result of computing the taxes over a commission invoice's base (SPEC-0016 BR2; DL-0044): the ISS
 * due and the list of withholdings, plus the regime that produced them. The taxable base is always
 * the commission, never the gross package (BR1) — that invariant lives in {@link
 * com.fksoft.domain.billing.CommissionInvoice} and is proven by a regression test.
 *
 * @param iss the ISS due (rate × commission base, scale 2 HALF_UP)
 * @param withholdings the withholdings (empty under Simples Nacional)
 * @param regime the regime applied (a {@code TAX_REGIME} cadastro code; was {@code TaxRegime})
 */
public record TaxAssessment(Money iss, List<Withholding> withholdings, String regime) {

  public TaxAssessment {
    if (iss == null) {
      throw new IllegalArgumentException("iss is required");
    }
    if (regime == null || regime.isBlank()) {
      throw new IllegalArgumentException("regime is required");
    }
    withholdings = withholdings == null ? List.of() : List.copyOf(withholdings);
  }
}
