package com.fksoft.domain.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based proof of the exact cent distribution (SPEC-0017 BR6/DL-0050, Fase 19i/DL-0132):
 * for <em>any</em> total (1 centavo to R$ 100 million) split into <em>any</em> count (1–48), the
 * installments sum back to the total to the cent, the remainder concentrates on the first
 * installment (spread bounded by count−1 centavos) and no installment is negative — no rounding
 * leak for any input, not just the picked examples.
 */
class InstallmentPlanPropertyTest {

  @Property
  void splitAlwaysSumsBackToTheTotalToTheCent(
      @ForAll @LongRange(min = 1, max = 10_000_000_000L) long totalCents,
      @ForAll @IntRange(min = 1, max = 48) int count) {
    Money total = Money.of(BigDecimal.valueOf(totalCents).movePointLeft(2), "BRL");

    InstallmentPlan plan = InstallmentPlan.split(total, dueDates(count));

    Money sum = plan.amounts().stream().reduce(Money.zero("BRL"), Money::add);
    assertThat(sum).isEqualTo(total);
    assertThat(plan.amounts()).hasSize(count);
  }

  @Property
  void theRemainderConcentratesOnTheFirstInstallmentWithoutGoingNegative(
      @ForAll @LongRange(min = 1, max = 10_000_000_000L) long totalCents,
      @ForAll @IntRange(min = 1, max = 48) int count) {
    Money total = Money.of(BigDecimal.valueOf(totalCents).movePointLeft(2), "BRL");

    InstallmentPlan plan = InstallmentPlan.split(total, dueDates(count));

    BigDecimal min =
        plan.amounts().stream().map(Money::amount).min(BigDecimal::compareTo).orElseThrow();
    BigDecimal max =
        plan.amounts().stream().map(Money::amount).max(BigDecimal::compareTo).orElseThrow();
    // The remainder (< count centavos) lands whole on the first installment; every other
    // installment is the same base value.
    BigDecimal maxSpread = BigDecimal.valueOf(count - 1L).movePointLeft(2);
    assertThat(max.subtract(min)).isLessThanOrEqualTo(maxSpread);
    assertThat(min.signum()).isGreaterThanOrEqualTo(0);
    assertThat(plan.amounts().getFirst()).isEqualTo(Money.of(max, "BRL"));
  }

  private static List<LocalDate> dueDates(int count) {
    List<LocalDate> dates = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      dates.add(LocalDate.of(2031, 1, 1).plusMonths(i));
    }
    return dates;
  }
}
