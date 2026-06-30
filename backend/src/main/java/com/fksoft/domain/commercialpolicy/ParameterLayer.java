package com.fksoft.domain.commercialpolicy;

/**
 * Governance layer of a {@link com.fksoft.domain.commercialpolicy.ParameterRule} (SPEC-0014
 * BR1/BR2). The precedence is fixed: {@code DIRECTIVE > PROMOTION > CONTRACT > POLICY >
 * SYSTEM_DEFAULT} (redesign 7.3). The {@link #rank()} encodes that order — a lower rank wins — so
 * the resolution engine can sort deterministically (BR2, DL-0037). The order of the enum constants
 * is the precedence order, so {@code rank()} is just the ordinal; keeping a named accessor makes
 * the intent explicit at the call sites and survives a future reorder being caught by tests.
 */
public enum ParameterLayer {

  /** The director's order — top of precedence; audited with a mandatory justification (BR5). */
  DIRECTIVE,

  /** A promotion (e.g. an FX-freeze promo) overriding contracts and policies. */
  PROMOTION,

  /** A negotiated contract clause for an account/product. */
  CONTRACT,

  /** A standing commercial policy. */
  POLICY,

  /** The system default — always present for every key in use (BR4); lowest precedence. */
  SYSTEM_DEFAULT;

  /** Precedence rank: lower wins ({@code DIRECTIVE} = 0 … {@code SYSTEM_DEFAULT} = 4). */
  public int rank() {
    return ordinal();
  }

  /** Whether this is the top (director) layer, which requires reinforced audit (BR5). */
  public boolean isDirective() {
    return this == DIRECTIVE;
  }
}
