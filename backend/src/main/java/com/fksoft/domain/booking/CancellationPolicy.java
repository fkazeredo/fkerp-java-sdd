package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Cancellation policy as an object (SPEC-0010): the type, the penalty windows, whether it is
 * refundable, the cost bearer and whether the seller is the merchant of record. This value object
 * owns the penalty math (a domain method, timezone-sensitive at the caller) and the cost-bearer
 * resolution for the merchant trap (BR8/DL-0021). It carries no entity and is frozen onto the
 * booking at confirmation (BR1).
 *
 * @param type the policy type cadastro code (drives behavior — BR2/BR3/BR4; was {@code
 *     CancellationType}, SPEC-0031/DL-0117)
 * @param windows the penalty windows (used by STANDARD/CUSTOM; ignored by ALL_SALES_FINAL)
 * @param refundable whether the sale is refundable from the supplier's point of view
 * @param costBearer who bears a STANDARD/CUSTOM penalty (∈ AGENCY, ACME, SUPPLIER)
 * @param merchantOfRecord whether the seller (Acme/portal) is the merchant of record for this
 *     brand/contract (BR8): when {@code true}, Acme assumes the ALL_SALES_FINAL supplier cost and
 *     any customer refund (default {@code false} = affiliate, supplier bears it)
 */
public record CancellationPolicy(
    String type,
    List<PenaltyWindow> windows,
    boolean refundable,
    CostBearer costBearer,
    boolean merchantOfRecord) {

  public CancellationPolicy {
    if (type == null || type.isBlank()) {
      throw new CancellationPolicyInvalidException();
    }
    if (costBearer == null) {
      throw new CancellationPolicyInvalidException();
    }
    windows = windows == null ? List.of() : List.copyOf(windows);
  }

  /**
   * The penalty for cancelling when the service starts in {@code hoursUntilService} hours, applied
   * to {@code paidAmount} (BR2/BR4). Selects the window whose {@code hoursBefore} is the
   * <em>smallest</em> bound that is still {@code >= hoursUntilService} (the tightest window that
   * still covers the cancellation). With no applicable window (or no windows, or a type that does
   * not use windows) the penalty is zero. Uses {@link Money} arithmetic (scale 2, HALF_UP).
   *
   * @param hoursUntilService whole hours from now until the service starts (non-negative)
   * @param paidAmount the reference amount the penalty is a fraction of
   * @return the penalty amount in {@code paidAmount}'s currency (possibly zero)
   */
  public Money penaltyFor(long hoursUntilService, Money paidAmount) {
    if (!CancellationTypeCodes.usesWindows(type)) {
      return Money.zero(paidAmount.currency());
    }
    return windows.stream()
        .filter(w -> w.hoursBefore() >= hoursUntilService)
        .min(Comparator.comparingInt(PenaltyWindow::hoursBefore))
        .map(w -> paidAmount.multiply(w.penaltyPct()))
        .orElse(Money.zero(paidAmount.currency()));
  }

  /**
   * Who bears the irrecoverable supplier cost and the customer refund under ALL_SALES_FINAL
   * (BR3/BR8). When the seller is the merchant of record, Acme assumes it ({@link
   * CostBearer#ACME}); otherwise (affiliate, the default) the supplier bears it ({@link
   * CostBearer#SUPPLIER}).
   */
  public CostBearer allSalesFinalCostBearer() {
    return merchantOfRecord ? CostBearer.ACME : CostBearer.SUPPLIER;
  }

  /** A frozen, affiliate STANDARD policy with no windows (penalty 0) — the safe default. */
  public static CancellationPolicy standardNoWindows() {
    return new CancellationPolicy(
        CancellationTypeCodes.STANDARD, List.of(), true, CostBearer.AGENCY, false);
  }

  /** Convenience for a single-window STANDARD policy. */
  public static CancellationPolicy standardWindow(
      int hoursBefore, BigDecimal penaltyPct, CostBearer costBearer) {
    return new CancellationPolicy(
        CancellationTypeCodes.STANDARD,
        List.of(new PenaltyWindow(hoursBefore, penaltyPct)),
        true,
        costBearer,
        false);
  }
}
