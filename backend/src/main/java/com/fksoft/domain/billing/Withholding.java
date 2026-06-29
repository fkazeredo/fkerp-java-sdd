package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;

/**
 * A single tax withholding line of a {@link TaxAssessment} (SPEC-0016 BR2): a kind and the withheld
 * amount (Money, scale 2 HALF_UP). Empty for Simples Nacional (DL-0044).
 *
 * @param kind the withholding kind
 * @param amount the withheld amount (commission-based)
 */
public record Withholding(WithholdingKind kind, Money amount) {

  public Withholding {
    if (kind == null) {
      throw new IllegalArgumentException("withholding kind is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("withholding amount is required");
    }
  }
}
