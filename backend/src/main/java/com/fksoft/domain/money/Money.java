package com.fksoft.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Money value object: a decimal amount in a currency. Amounts are normalized to scale 2 with {@code
 * HALF_UP} (the project money convention); the currency is a three-letter code. Arithmetic
 * preserves the currency and requires operands to share it.
 *
 * @param amount the monetary amount (normalized to scale 2)
 * @param currency the three-letter currency code (e.g. {@code BRL}, {@code USD})
 */
public record Money(BigDecimal amount, String currency) {

  private static final int SCALE = 2;
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

  public Money {
    if (amount == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (currency == null || !CURRENCY.matcher(currency).matches()) {
      throw new IllegalArgumentException("invalid currency: " + currency);
    }
    amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** Builds a money value, normalizing the amount to scale 2. */
  public static Money of(BigDecimal amount, String currency) {
    return new Money(amount, currency);
  }

  /** Zero in the given currency. */
  public static Money zero(String currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  /** Multiplies the amount by a dimensionless factor, keeping the currency (scale 2, HALF_UP). */
  public Money multiply(BigDecimal factor) {
    return new Money(amount.multiply(factor), currency);
  }

  /** Adds another money of the same currency. */
  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  /** Subtracts another money of the same currency (result may be negative). */
  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  /** Whether the amount is strictly negative. */
  public boolean isNegative() {
    return amount.signum() < 0;
  }

  /** Whether the amount is zero or positive. */
  public boolean isNonNegative() {
    return amount.signum() >= 0;
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
  }
}
