package com.fksoft.domain.commissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the two-sided commission calculation (SPEC-0004): correctness, HALF_UP rounding,
 * exposed negative spread, percentage bounds and base validation.
 */
class CommissionServiceTest {

  private final CommissionService calculator = new CommissionService();

  private static Money usd(String amount) {
    return Money.of(new BigDecimal(amount), "USD");
  }

  @Test
  void computesSupplierAgentAndSpread() {
    CommissionStatement statement =
        calculator.compute(
            new CommissionInput(usd("500.00"), new BigDecimal("0.15"), new BigDecimal("0.10")));

    assertThat(statement.supplierCommission()).isEqualTo(usd("75.00"));
    assertThat(statement.agentCommission()).isEqualTo(usd("50.00"));
    assertThat(statement.spread()).isEqualTo(usd("25.00"));
    assertThat(statement.spreadNegative()).isFalse();
  }

  @Test
  void exposesNegativeSpreadWhenAgentRateExceedsSupplier() {
    CommissionStatement statement =
        calculator.compute(
            new CommissionInput(usd("500.00"), new BigDecimal("0.15"), new BigDecimal("0.20")));

    assertThat(statement.spread()).isEqualTo(usd("-25.00"));
    assertThat(statement.spreadNegative()).isTrue();
  }

  @Test
  void roundsHalfUp() {
    CommissionStatement statement =
        calculator.compute(
            new CommissionInput(usd("1.00"), new BigDecimal("0.125"), BigDecimal.ZERO));

    assertThat(statement.supplierCommission()).isEqualTo(usd("0.13"));
  }

  @Test
  void acceptsBoundaryPercentages() {
    CommissionStatement statement =
        calculator.compute(new CommissionInput(usd("500.00"), BigDecimal.ZERO, BigDecimal.ONE));

    assertThat(statement.supplierCommission()).isEqualTo(usd("0.00"));
    assertThat(statement.agentCommission()).isEqualTo(usd("500.00"));
    assertThat(statement.spreadNegative()).isTrue();
  }

  @Test
  void rejectsPercentageOutOfRangePointingToField() {
    assertThatThrownBy(
            () ->
                calculator.compute(
                    new CommissionInput(
                        usd("500.00"), new BigDecimal("1.5"), new BigDecimal("0.1"))))
        .isInstanceOf(CommissionPctInvalidException.class)
        .satisfies(
            ex ->
                assertThat(((CommissionPctInvalidException) ex).details())
                    .containsKey("supplierCommissionPct"));
  }

  @Test
  void rejectsNegativeBase() {
    assertThatThrownBy(
            () ->
                calculator.compute(
                    new CommissionInput(
                        usd("-1.00"), new BigDecimal("0.1"), new BigDecimal("0.1"))))
        .isInstanceOf(CommissionBaseInvalidException.class);
  }
}
