package com.fksoft.domain.aftersales;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;

/**
 * The cost-to-serve accumulated by an after-sales case (SPEC-0018 BR5; DL-0053): the effort and the
 * money the operation spent handling a case, the signal the Intelligence (SPEC-0013) uses to
 * compute "real margin" (spread − cost to serve). A pure value object in BRL, scale 2 HALF_UP (the
 * project {@link Money} convention).
 *
 * <p>It splits the cost into a {@code handling} part (effort logged on the case) and a {@code
 * refund} part (the amount of a linked Payout REFUND), and tracks {@code reopenCount} (reopenings
 * are a cost-to-serve signal). The total is {@code handling + refund}. The structure is accumulable
 * so the exact "which costs count" rule can evolve without a schema change — "which costs count" is
 * still to be confirmed with the owner (DL-0053, Confiança=Média).
 *
 * @param handling the handling effort cost (BRL)
 * @param refund the linked refund cost (BRL)
 * @param reopenCount how many times the case was reopened
 */
public record CostToServe(Money handling, Money refund, int reopenCount) {

  private static final String CURRENCY = "BRL";

  public CostToServe {
    if (handling == null) {
      handling = Money.zero(CURRENCY);
    }
    if (refund == null) {
      refund = Money.zero(CURRENCY);
    }
    if (reopenCount < 0) {
      throw new SupportCaseInvalidException();
    }
  }

  /** An empty cost-to-serve (zero handling, zero refund, no reopenings). */
  public static CostToServe empty() {
    return new CostToServe(Money.zero(CURRENCY), Money.zero(CURRENCY), 0);
  }

  /** Adds handling effort cost (BR5). */
  public CostToServe accrue(Money handlingCost) {
    if (handlingCost == null || handlingCost.amount().signum() == 0) {
      return this;
    }
    return new CostToServe(handling.add(handlingCost), refund, reopenCount);
  }

  /** Records the linked refund amount (the cost of a Payout REFUND, BR5). */
  public CostToServe withRefund(Money refundAmount) {
    if (refundAmount == null) {
      return this;
    }
    return new CostToServe(handling, refundAmount, reopenCount);
  }

  /** Records one reopening (a cost-to-serve signal, BR5). */
  public CostToServe reopened() {
    return new CostToServe(handling, refund, reopenCount + 1);
  }

  /** The total cost to serve = handling + refund (BRL). */
  public Money total() {
    return handling.add(refund);
  }

  /** Whether any cost has been accumulated (used to decide whether to persist it). */
  public boolean isEmpty() {
    return total().amount().compareTo(BigDecimal.ZERO) == 0 && reopenCount == 0;
  }
}
