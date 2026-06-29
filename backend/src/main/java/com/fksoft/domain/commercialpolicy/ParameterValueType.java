package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;

/**
 * The type of a governed parameter's value (SPEC-0014 BR1). A rule stores its value as text ({@code
 * value_text}) plus this type, so the engine stays free of a polymorphic value model (DL-0037)
 * while each consumer interprets the text according to the type.
 *
 * <ul>
 *   <li>{@code NUMBER} — a plain decimal (e.g. a count or absolute limit).
 *   <li>{@code PERCENT} — a decimal rate (e.g. {@code 0.12} for 12%); same shape as markup pct.
 *   <li>{@code MONEY} — a BRL amount (e.g. a discrepancy tolerance).
 *   <li>{@code BOOL} — {@code true}/{@code false}.
 * </ul>
 */
public enum ParameterValueType {
  NUMBER,
  PERCENT,
  MONEY,
  BOOL;

  /**
   * Validates that {@code text} is a well-formed value for this type (SPEC-0014 Validation Rules).
   *
   * @param text the raw value text
   * @return {@code true} when the text parses for this type
   */
  public boolean isValid(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String trimmed = text.trim();
    return switch (this) {
      case NUMBER, PERCENT, MONEY -> isDecimal(trimmed);
      case BOOL -> "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed);
    };
  }

  /**
   * Parses {@code text} as a {@link BigDecimal} for the numeric types ({@code NUMBER}/{@code
   * PERCENT}/{@code MONEY}).
   *
   * @throws IllegalStateException when called on {@code BOOL}
   * @throws NumberFormatException when the text is not a valid decimal
   */
  public BigDecimal asDecimal(String text) {
    if (this == BOOL) {
      throw new IllegalStateException("BOOL parameter has no decimal value");
    }
    return new BigDecimal(text.trim());
  }

  private static boolean isDecimal(String text) {
    try {
      new BigDecimal(text);
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
