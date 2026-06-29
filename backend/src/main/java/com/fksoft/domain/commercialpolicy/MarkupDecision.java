package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;

/**
 * A markup decision: the rate to apply and the governance source it came from. Graduated by
 * SPEC-0014: the {@code source} is the name of the winning {@link ParameterLayer} — {@code
 * DIRECTIVE}, {@code PROMOTION}, {@code CONTRACT}, {@code POLICY} or {@code SYSTEM_DEFAULT}. When
 * no rule above the default applies, it is the zero/{@code SYSTEM_DEFAULT} decision, identical to
 * the Phase-1 behaviour (back-compat, DL-0040).
 *
 * @param pct the markup rate (decimal; default {@code 0})
 * @param source the winning governance layer name (e.g. {@code PROMOTION}, {@code SYSTEM_DEFAULT})
 */
public record MarkupDecision(BigDecimal pct, String source) {

  /** The system default source — the layer that always exists for every key (BR4). */
  public static final String SYSTEM_DEFAULT = ParameterLayer.SYSTEM_DEFAULT.name();

  /** The zero markup at the system-default layer (the resolution-base / back-compat value). */
  public static final MarkupDecision DEFAULT = new MarkupDecision(BigDecimal.ZERO, SYSTEM_DEFAULT);

  /**
   * Builds a decision from a resolved {@code MARKUP_PCT}: pct + the winning layer as the source.
   */
  public static MarkupDecision from(ResolvedParameter resolved) {
    return new MarkupDecision(resolved.asDecimal(), resolved.provenance().layer().name());
  }
}
