package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.portfolio.DefineGoalCommand;
import com.fksoft.domain.portfolio.GoalMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/portfolio/brands/{brandRef}/goals} (SPEC-0020 BR3). A REVENUE
 * goal carries a {@code target} money (BRL); a VOLUME goal carries a {@code targetCount}.
 *
 * @param period the period (YYYY or YYYY-MM)
 * @param metric VOLUME or REVENUE
 * @param target the REVENUE target money (amount + currency), or {@code null} for VOLUME
 * @param targetCount the VOLUME target count, or {@code null} for REVENUE
 */
public record DefineGoalRequest(
    @NotBlank String period, @NotNull GoalMetric metric, MoneyValue target, Integer targetCount) {

  /**
   * A money value in the request.
   *
   * @param amount the decimal amount
   * @param currency the three-letter currency code
   */
  public record MoneyValue(BigDecimal amount, String currency) {}

  /** Translates this request to the domain command for the given brand. */
  public DefineGoalCommand toCommand(String brandRef) {
    Money money = target == null ? null : Money.of(target.amount(), target.currency());
    return new DefineGoalCommand(brandRef, period, metric, money, targetCount);
  }
}
