package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;

/**
 * The outcome of {@code resolve(key, scope)} (SPEC-0014 BR2): the winning value (typed text) plus
 * its {@link Provenance}. A pure read result — resolution never mutates state (BR6, Open-Host).
 *
 * @param key the resolved parameter key
 * @param value the winning value as text (interpreted per {@code type})
 * @param type the value type (NUMBER/PERCENT/MONEY/BOOL)
 * @param provenance which layer won, who defined it and when
 */
public record ResolvedParameter(
    ParameterKey key, String value, ParameterValueType type, Provenance provenance) {

  /**
   * The value as a {@link BigDecimal} for the numeric types.
   *
   * @throws IllegalStateException when the type is {@code BOOL}
   */
  public BigDecimal asDecimal() {
    return type.asDecimal(value);
  }

  /** The value as a boolean (only meaningful for {@code BOOL}). */
  public boolean asBoolean() {
    return Boolean.parseBoolean(value);
  }
}
