package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain calculator for the charges a cancellation produces (SPEC-0010 BR3/BR5/BR11). This is
 * the one place where the <strong>merchant trap</strong> is modeled: under {@link
 * CancellationType#ALL_SALES_FINAL} the supplier cost and the customer refund are emitted as
 * <em>separate</em> charges and are <strong>never netted</strong> against each other — this class
 * only ever <em>adds</em> charges to a list; it never subtracts one from another nor derives a net
 * amount. Keeping it pure makes the non-netting invariant easy to prove with a unit/regression
 * test.
 */
public final class CancellationCharges {

  private CancellationCharges() {}

  /**
   * Computes the charges for cancelling a booking governed by {@code policy}.
   *
   * <ul>
   *   <li><b>STANDARD/CUSTOM:</b> a single {@link ChargeKind#PENALTY} from the applicable window
   *       (BR2/BR4), on {@code saleAmount}, borne by the policy's {@code costBearer}; a zero
   *       penalty produces no charge.
   *   <li><b>ALL_SALES_FINAL:</b> a {@link ChargeKind#SUPPLIER} charge for the full supplier cost
   *       (irrecoverable — BR3), borne per the merchant-of-record rule (BR8); plus, when {@code
   *       refundAmount} is present, a <em>separate</em> {@link ChargeKind#CUSTOMER_REFUND} charge —
   *       the two coexist (the trap, BR5).
   * </ul>
   *
   * @param policy the frozen cancellation policy
   * @param hoursUntilService whole hours from cancellation until the service starts (non-negative)
   * @param saleAmount the customer-paid reference (penalty/refund base)
   * @param supplierCost the supplier cost reference (ALL_SALES_FINAL supplier charge base)
   * @param refundAmount the commercial refund to the customer, or {@code null} when none
   * @return the resulting charges (possibly empty), in deterministic order
   */
  public static List<Charge> compute(
      CancellationPolicy policy,
      long hoursUntilService,
      Money saleAmount,
      Money supplierCost,
      Money refundAmount) {
    List<Charge> charges = new ArrayList<>();
    if (policy.type() == CancellationType.ALL_SALES_FINAL) {
      // The supplier cost is fully due even though the sale is being cancelled (BR3): a distinct
      // fact.
      CostBearer bearer = policy.allSalesFinalCostBearer();
      charges.add(new Charge(ChargeKind.SUPPLIER, supplierCost, bearer));
      // A commercial refund to the customer is a SEPARATE obligation — it does NOT cancel the
      // supplier charge out (BR5/BR11 — the merchant trap).
      if (refundAmount != null) {
        charges.add(new Charge(ChargeKind.CUSTOMER_REFUND, refundAmount, bearer));
      }
      return charges;
    }
    // STANDARD / CUSTOM: penalty by the applicable window (BR2/BR4).
    Money penalty = policy.penaltyFor(hoursUntilService, saleAmount);
    if (penalty.amount().signum() > 0) {
      charges.add(new Charge(ChargeKind.PENALTY, penalty, policy.costBearer()));
    }
    return charges;
  }
}
