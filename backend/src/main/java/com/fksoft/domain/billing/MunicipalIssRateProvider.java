package com.fksoft.domain.billing;

import java.math.BigDecimal;

/**
 * Port that resolves the municipal ISS rate for a municipality (SPEC-0016 BR2; DL-0044). The rate
 * is a parameter per municipality (the legal ISS band is 2%–5%, LC 116/2003), defaulting to 5% (the
 * cap) when the municipality is not in the table. Keeping it behind a port lets the rate table be
 * seeded data (a Flyway-managed table) without coupling the strategy to persistence.
 */
public interface MunicipalIssRateProvider {

  /**
   * The ISS rate for the given municipality as a fraction (e.g. {@code 0.05} for 5%).
   *
   * @param municipality the IBGE municipality code (may be {@code null}/blank → default rate)
   * @return the ISS rate fraction (default 5% when not configured)
   */
  BigDecimal rateFor(String municipality);
}
