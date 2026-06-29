package com.fksoft.domain.billing;

/**
 * Tax regime of the issuer (SPEC-0016 Q7; DL-0044). It selects the {@link TaxRegimeStrategy} that
 * computes ISS and withholdings. The default is {@link #SIMPLES_NACIONAL} (the most common in PME
 * and the most defensible until the accountant confirms the real regime); the others are pluggable
 * without refactoring the aggregate.
 */
public enum TaxRegime {
  SIMPLES_NACIONAL,
  LUCRO_PRESUMIDO,
  LUCRO_REAL
}
