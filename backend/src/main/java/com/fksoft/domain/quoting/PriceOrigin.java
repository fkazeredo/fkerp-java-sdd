package com.fksoft.domain.quoting;

/**
 * Origin of a quote's price. Phase 1 composes only {@link #MANUAL} quotes; {@link #INTEGRATED}
 * (trusted external price, no recomposition) is owned by SPEC-0009. External value is the name.
 */
public enum PriceOrigin {
  MANUAL,
  INTEGRATED
}
