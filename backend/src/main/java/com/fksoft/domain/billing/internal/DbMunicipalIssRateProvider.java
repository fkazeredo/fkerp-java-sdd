package com.fksoft.domain.billing.internal;

import com.fksoft.domain.billing.MunicipalIssRateProvider;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the municipal ISS rate from the seeded {@code municipal_iss_rates} table (SPEC-0016;
 * DL-0044), falling back to the 5% legal cap (LC 116/2003) when the municipality is absent or
 * blank. This keeps the {@link com.fksoft.domain.billing.TaxRegimeStrategy} decoupled from
 * persistence.
 */
@Component
@RequiredArgsConstructor
class DbMunicipalIssRateProvider implements MunicipalIssRateProvider {

  /** The legal ISS cap (LC 116/2003), used when a municipality is not configured (DL-0044). */
  private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.05");

  private final MunicipalIssRateRepository rates;

  @Override
  @Transactional(readOnly = true)
  public BigDecimal rateFor(String municipality) {
    if (municipality == null || municipality.isBlank()) {
      return DEFAULT_RATE;
    }
    return rates.findById(municipality.trim()).map(MunicipalIssRate::issRate).orElse(DEFAULT_RATE);
  }
}
