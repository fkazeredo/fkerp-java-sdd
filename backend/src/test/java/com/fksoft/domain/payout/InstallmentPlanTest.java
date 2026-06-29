package com.fksoft.domain.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the installment plan (SPEC-0017 BR6; DL-0050): the cent distribution is exact (the
 * installments sum to the total to the cent — no rounding leak) and an explicit plan must sum to
 * the total. This is the "installments distribute cents exactly to the total" proof the supervisor
 * requires.
 */
class InstallmentPlanTest {

  private static final List<LocalDate> THREE_DATES =
      List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

  @Test
  void splittingThatDoesNotDivideEvenlyPutsTheRemainderOnTheFirstInstallmentAndSumsExactly() {
    // R$ 100.00 / 3 = 33.34 + 33.33 + 33.33 (the first absorbs the cent) → sum == 100.00 exact.
    InstallmentPlan plan =
        InstallmentPlan.split(Money.of(new BigDecimal("100.00"), "BRL"), THREE_DATES);

    assertThat(plan.amounts())
        .containsExactly(
            Money.of(new BigDecimal("33.34"), "BRL"),
            Money.of(new BigDecimal("33.33"), "BRL"),
            Money.of(new BigDecimal("33.33"), "BRL"));
    assertThat(sum(plan)).isEqualByComparingTo("100.00");
  }

  @Test
  void splittingThatDividesEvenlySumsExactly() {
    InstallmentPlan plan =
        InstallmentPlan.split(Money.of(new BigDecimal("90.00"), "BRL"), THREE_DATES);

    assertThat(plan.amounts())
        .containsExactly(
            Money.of(new BigDecimal("30.00"), "BRL"),
            Money.of(new BigDecimal("30.00"), "BRL"),
            Money.of(new BigDecimal("30.00"), "BRL"));
    assertThat(sum(plan)).isEqualByComparingTo("90.00");
  }

  @Test
  void aSingleInstallmentEqualsTheWholeTotal() {
    InstallmentPlan plan =
        InstallmentPlan.split(Money.of(new BigDecimal("2850.00"), "BRL"), List.of(LocalDate.now()));

    assertThat(plan.isSingle()).isTrue();
    assertThat(plan.amounts()).containsExactly(Money.of(new BigDecimal("2850.00"), "BRL"));
  }

  @Test
  void anExplicitPlanThatDoesNotSumToTheTotalIsRejected() {
    assertThatThrownBy(
            () ->
                InstallmentPlan.explicit(
                    Money.of(new BigDecimal("100.00"), "BRL"),
                    List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
                    List.of(
                        Money.of(new BigDecimal("60.00"), "BRL"),
                        Money.of(new BigDecimal("50.00"), "BRL")))) // 110 != 100
        .isInstanceOf(PayoutAmountInvalidException.class);
  }

  @Test
  void anExplicitPlanThatSumsToTheTotalIsAccepted() {
    InstallmentPlan plan =
        InstallmentPlan.explicit(
            Money.of(new BigDecimal("100.00"), "BRL"),
            List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)),
            List.of(
                Money.of(new BigDecimal("40.00"), "BRL"),
                Money.of(new BigDecimal("60.00"), "BRL")));

    assertThat(sum(plan)).isEqualByComparingTo("100.00");
  }

  private static BigDecimal sum(InstallmentPlan plan) {
    return plan.amounts().stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
