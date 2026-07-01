package com.fksoft.application.api.dto;

import com.fksoft.domain.booking.CancellationPolicy;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.NoShowPolicy;
import com.fksoft.domain.booking.PenaltyWindow;
import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for {@code PUT /api/products/{ref}/cancellation-policy} (SPEC-0010): the
 * cancellation policy and the no-show policy to administer for a product/supplier scope.
 * Window/percentage validation lives in the domain value objects (a malformed window surfaces as
 * {@code cancellation.policy.invalid}). The {@code type} is a cancellation-type cadastro code (was
 * {@code CancellationType}; SPEC-0031/DL-0117) — the wire stays a string, validated against the
 * cadastro by the service.
 *
 * @param type the cancellation-type cadastro code (required)
 * @param windows the penalty windows (may be empty/null)
 * @param refundable whether the sale is refundable from the supplier's point of view (required)
 * @param costBearer who bears a STANDARD/CUSTOM penalty (required)
 * @param merchantOfRecord whether the seller is the merchant of record (BR8; defaults to false)
 * @param noShowFee the no-show fee, or {@code null} when none
 * @param waivedIfFlightCancelled whether the no-show fee is waived with proof of a cancelled flight
 */
public record CancellationPolicyRequest(
    @NotBlank String type,
    List<WindowRequest> windows,
    @NotNull Boolean refundable,
    @NotNull CostBearer costBearer,
    Boolean merchantOfRecord,
    Money noShowFee,
    Boolean waivedIfFlightCancelled) {

  /** A single penalty window in the request. */
  public record WindowRequest(@NotNull Integer hoursBefore, @NotNull BigDecimal penaltyPct) {}

  /** The cancellation policy value object built from this request. */
  public CancellationPolicy toPolicy() {
    List<PenaltyWindow> mapped =
        windows == null
            ? List.of()
            : windows.stream()
                .map(w -> new PenaltyWindow(w.hoursBefore(), w.penaltyPct()))
                .toList();
    return new CancellationPolicy(
        type, mapped, refundable, costBearer, Boolean.TRUE.equals(merchantOfRecord));
  }

  /** The no-show policy value object built from this request. */
  public NoShowPolicy toNoShowPolicy() {
    return new NoShowPolicy(noShowFee, Boolean.TRUE.equals(waivedIfFlightCancelled));
  }
}
