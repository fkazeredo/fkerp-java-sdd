package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;

/**
 * A markup decision: the rate to apply and the governance source it came from. In Phase 1 the
 * source is always {@code SYSTEM_DEFAULT}; SPEC-0014 introduces the other sources (Directive,
 * Promotion, Contract, Policy) and the precedence among them.
 *
 * @param pct the markup rate (decimal; default {@code 0})
 * @param source the governance source, e.g. {@code SYSTEM_DEFAULT}
 */
public record MarkupDecision(BigDecimal pct, String source) {

  /** The Phase-1 governed default: zero markup, system default source (DL-0009). */
  public static final String SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
}
