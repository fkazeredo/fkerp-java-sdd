package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.portfolio.DefineGoalCommand;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/portfolio/brands/{brandRef}/goals} (SPEC-0020 BR3). A REVENUE
 * goal carries a {@code target} money (BRL); a VOLUME goal carries a {@code targetCount}. The
 * {@code metric} is a goal-metric cadastro code (was {@code GoalMetric}; SPEC-0031/DL-0116) — the
 * wire stays a string, validated against the cadastro by the service.
 *
 * @param period the period (YYYY or YYYY-MM)
 * @param metric the goal-metric cadastro code (VOLUME or REVENUE)
 * @param target the REVENUE target money (amount + currency), or {@code null} for VOLUME
 * @param targetCount the VOLUME target count, or {@code null} for REVENUE
 */
public record DefineGoalRequest(
    @NotBlank String period, @NotBlank String metric, MoneyValue target, Integer targetCount) {

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
