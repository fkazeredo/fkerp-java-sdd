package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;
import java.util.List;

/**
 * Read view of the administered cancellation/no-show policy for a product/supplier scope (SPEC-0010
 * API: {@code GET/PUT /api/products/{ref}/cancellation-policy}). Carries no entity.
 *
 * @param scopeRef the product/supplier scope reference this policy applies to
 * @param type the cancellation type
 * @param windows the penalty windows
 * @param refundable whether the sale is refundable from the supplier's point of view
 * @param costBearer who bears a STANDARD/CUSTOM penalty
 * @param merchantOfRecord whether the seller is the merchant of record (BR8)
 * @param noShowFee the no-show fee, or {@code null} when none is set
 * @param waivedIfFlightCancelled whether the no-show fee is waived with proof of a cancelled flight
 */
public record CancellationPolicyView(
    String scopeRef,
    CancellationType type,
    List<PenaltyWindow> windows,
    boolean refundable,
    CostBearer costBearer,
    boolean merchantOfRecord,
    Money noShowFee,
    boolean waivedIfFlightCancelled) {

  /** The cancellation policy value object embedded in this view. */
  public CancellationPolicy policy() {
    return new CancellationPolicy(type, windows, refundable, costBearer, merchantOfRecord);
  }
}
