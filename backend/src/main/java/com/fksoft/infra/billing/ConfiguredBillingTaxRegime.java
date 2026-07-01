package com.fksoft.infra.billing;

import com.fksoft.domain.billing.BillingTaxRegimeConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the issuer tax regime from {@code billing.tax.regime} (default {@code SIMPLES_NACIONAL}),
 * implementing the {@link BillingTaxRegimeConfig} port (SPEC-0016 Q7; DL-0044). Keeping the
 * framework config here (infra) leaves the Billing domain pure. Changing the regime is a config
 * change; the accountant's real regime is plugged by setting this property and wiring the matching
 * {@link com.fksoft.domain.billing.TaxRegimeStrategy} bean — no domain refactor.
 */
@Component
public class ConfiguredBillingTaxRegime implements BillingTaxRegimeConfig {

  private final String regime;

  public ConfiguredBillingTaxRegime(
      @Value("${billing.tax.regime:SIMPLES_NACIONAL}") String regime) {
    this.regime = regime;
  }

  @Override
  public String regime() {
    return regime;
  }
}
