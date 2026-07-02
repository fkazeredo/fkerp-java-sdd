package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;

/**
 * The {@code PARAMETER_VALUE_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0118). After {@code ParameterValueType} became an editable cadastro, the value type still
 * decides how a governed parameter's text is validated and parsed (SPEC-0014 BR1; DL-0037): a rule
 * stores its value as {@code value_text} plus this type, so the engine stays free of a polymorphic
 * value model while each consumer interprets the text according to the type.
 *
 * <p>The cadastro owns the extensible set + labels; this class owns the wired parse/validate
 * behavior (which the old enum carried). A new value-type code with no wired behavior is rejected
 * on write ({@link #isValid} returns {@code false} for an unknown type), so a rule is never stored
 * with an uninterpretable value type.
 *
 * <ul>
 *   <li>{@link #NUMBER} — a plain decimal (e.g. a count or absolute limit).
 *   <li>{@link #PERCENT} — a decimal rate (e.g. {@code 0.12} for 12%); same shape as markup pct.
 *   <li>{@link #MONEY} — a BRL amount (e.g. a discrepancy tolerance).
 *   <li>{@link #BOOL} — {@code true}/{@code false}.
 * </ul>
 */
public final class ParameterValueTypeCodes {

  /** A plain decimal (count or absolute limit). */
  public static final String NUMBER = "NUMBER";

  /** A decimal rate (e.g. {@code 0.12} for 12%). */
  public static final String PERCENT = "PERCENT";

  /** A BRL amount (e.g. a discrepancy tolerance). */
  public static final String MONEY = "MONEY";

  /** A boolean ({@code true}/{@code false}). */
  public static final String BOOL = "BOOL";

  private ParameterValueTypeCodes() {}

  /**
   * Validates that {@code text} is a well-formed value for the given value-type code (SPEC-0014
   * Validation Rules). Returns {@code false} for an unknown type or a malformed value.
   *
   * @param type the value-type cadastro code
   * @param text the raw value text
   * @return {@code true} when the text parses for this type
   */
  public static boolean isValid(String type, String text) {
    if (type == null || text == null || text.isBlank()) {
      return false;
    }
    String trimmed = text.trim();
    return switch (type) {
      case NUMBER, PERCENT, MONEY -> isDecimal(trimmed);
      case BOOL -> "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed);
      default -> false;
    };
  }

  /**
   * Parses {@code text} as a {@link BigDecimal} for the numeric types ({@code NUMBER}/{@code
   * PERCENT}/{@code MONEY}).
   *
   * @param type the value-type cadastro code
   * @param text the raw value text
   * @return the parsed decimal
   * @throws IllegalStateException when called on {@code BOOL} (or an unknown non-numeric type)
   * @throws NumberFormatException when the text is not a valid decimal
   */
  public static BigDecimal asDecimal(String type, String text) {
    if (BOOL.equals(type)) {
      throw new IllegalStateException("BOOL parameter has no decimal value");
    }
    if (!NUMBER.equals(type) && !PERCENT.equals(type) && !MONEY.equals(type)) {
      throw new IllegalStateException("value type has no decimal value: " + type);
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
