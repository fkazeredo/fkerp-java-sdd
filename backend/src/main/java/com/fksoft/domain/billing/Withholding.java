package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;

/**
 * A single tax withholding line of a {@link TaxAssessment} (SPEC-0016 BR2): a kind code and the
 * withheld amount (Money, scale 2 HALF_UP). Empty for Simples Nacional (DL-0044). The {@code kind}
 * is a {@code WITHHOLDING_KIND} cadastro code (was {@code WithholdingKind}; SPEC-0031/DL-0115).
 *
 * @param kind the withholding-kind cadastro code
 * @param amount the withheld amount (commission-based)
 */
public record Withholding(String kind, Money amount) {

  public Withholding {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("withholding kind is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("withholding amount is required");
    }
  }
}
