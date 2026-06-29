package com.fksoft.domain.commercialpolicy;

import java.util.regex.Pattern;

/**
 * A governed-parameter key (SPEC-0014 BR1), e.g. {@code MARKUP_PCT}, {@code FX_DRIFT_LIMIT}, {@code
 * RECON_DISCREPANCY_TOL}. A small typed value object so a raw string never flows through the
 * engine. The format is upper snake-case ({@code A-Z} and {@code _}); an invalid key is rejected at
 * the boundary ({@code policy.rule.invalid}).
 *
 * @param value the canonical key text (upper snake-case)
 */
public record ParameterKey(String value) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

  /** Markup rate consumed by Quoting through {@link MarkupProvider} (SPEC-0005, graduated here). */
  public static final ParameterKey MARKUP_PCT = new ParameterKey("MARKUP_PCT");

  public ParameterKey {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid parameter key: " + value);
    }
  }

  /**
   * Parses a key from text, normalizing to upper-case and trimming.
   *
   * @throws IllegalArgumentException when the text is null/blank or malformed
   */
  public static ParameterKey parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("parameter key is required");
    }
    return new ParameterKey(text.trim().toUpperCase(java.util.Locale.ROOT));
  }

  @Override
  public String toString() {
    return value;
  }
}
