package com.fksoft.domain.billing;

/**
 * Port that resolves the issuer's current tax regime (SPEC-0016 Q7; DL-0044). Kept behind a port so
 * the regime is a configuration value (default {@code SIMPLES_NACIONAL}) the implementation reads
 * from {@code billing.tax.regime}, without putting framework config in the domain. The aggregate
 * stamps the regime at draft time so a later config change does not retroactively alter issued
 * invoices.
 */
public interface BillingTaxRegimeConfig {

  /**
   * The configured tax-regime cadastro code (default {@link TaxRegimeCodes#SIMPLES_NACIONAL}; was
   * {@code TaxRegime} — SPEC-0031/DL-0115).
   */
  String regime();
}
