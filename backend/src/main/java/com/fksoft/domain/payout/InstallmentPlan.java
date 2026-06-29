package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The installment plan of a payout (SPEC-0017 BR6, DL-0050): either an explicit list of due
 * dates/amounts, or a count to split the total into. v1 charges <strong>no interest</strong>, so
 * the installments MUST sum exactly to the total — and the cent distribution is exact (the
 * remainder goes to the first installment), proven by a unit test (no rounding leak).
 *
 * <p>A payout with no plan is treated as a single implicit installment (seq 1 = the whole total),
 * so the execution path is uniform (no special "à vista" branch).
 *
 * @param dueDates one due date per installment (size == count); the schedule of the plan
 * @param amounts one money amount per installment, summing exactly to the total
 */
public record InstallmentPlan(List<LocalDate> dueDates, List<Money> amounts) {

  public InstallmentPlan {
    dueDates = List.copyOf(dueDates);
    amounts = List.copyOf(amounts);
    if (dueDates.size() != amounts.size() || dueDates.isEmpty()) {
      throw new PayoutAmountInvalidException();
    }
  }

  /** Whether this is the trivial single-installment plan (à vista / no explicit plan). */
  public boolean isSingle() {
    return amounts.size() == 1;
  }

  /**
   * Splits a {@code total} into {@code count} equal installments with no interest (DL-0050),
   * distributing cents exactly: the remainder lands on the FIRST installment so the sum equals the
   * total to the cent. Each installment is due on the matching {@code dueDates} entry.
   *
   * @param total the amount to split (the payout total)
   * @param dueDates the due date of each installment (size == count)
   * @return the exact plan
   * @throws PayoutAmountInvalidException when the count/dates are inconsistent or the total is not
   *     positive
   */
  public static InstallmentPlan split(Money total, List<LocalDate> dueDates) {
    if (total == null || !total.isNonNegative() || total.amount().signum() <= 0) {
      throw new PayoutAmountInvalidException();
    }
    int count = dueDates.size();
    if (count <= 0) {
      throw new PayoutAmountInvalidException();
    }
    BigDecimal totalCents = total.amount().movePointRight(2);
    long cents = totalCents.longValueExact();
    long base = cents / count;
    long remainder = cents - base * count;
    List<Money> amounts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      long installmentCents = base + (i == 0 ? remainder : 0);
      amounts.add(
          Money.of(BigDecimal.valueOf(installmentCents).movePointLeft(2), total.currency()));
    }
    return new InstallmentPlan(dueDates, amounts);
  }

  /**
   * Builds an explicit plan, validating it sums exactly to {@code total} with a single currency
   * (DL-0050). No interest is added: the plan MUST already equal the total.
   *
   * @param total the payout total the plan must sum to
   * @param dueDates the due date of each installment
   * @param amounts the amount of each installment
   * @return the validated explicit plan
   * @throws PayoutAmountInvalidException when the plan does not sum to the total or mixes
   *     currencies
   */
  public static InstallmentPlan explicit(
      Money total, List<LocalDate> dueDates, List<Money> amounts) {
    if (total == null || dueDates == null || amounts == null) {
      throw new PayoutAmountInvalidException();
    }
    Money sum = Money.zero(total.currency());
    for (Money amount : amounts) {
      if (amount == null || !amount.currency().equals(total.currency()) || amount.isNegative()) {
        throw new PayoutAmountInvalidException();
      }
      sum = sum.add(amount);
    }
    if (sum.amount().compareTo(total.amount()) != 0) {
      throw new PayoutAmountInvalidException();
    }
    return new InstallmentPlan(dueDates, amounts);
  }
}
