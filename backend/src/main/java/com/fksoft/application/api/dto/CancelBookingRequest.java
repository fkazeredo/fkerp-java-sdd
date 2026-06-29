package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Request body for {@code POST /api/bookings/{id}/cancel} (SPEC-0010): the cancellation reason,
 * when the booked service starts (the penalty-window base — BR2), and an optional commercial refund
 * to the customer (which, under ALL_SALES_FINAL, becomes a SEPARATE obligation that does not net
 * out against the supplier charge — the merchant trap, BR5).
 *
 * @param reason the reason for cancelling (required, non-blank)
 * @param serviceStartsAt when the booked service starts (UTC); {@code null} is treated as "now"
 * @param refundAmount a commercial refund to the customer, or {@code null} when none
 */
public record CancelBookingRequest(
    @NotBlank String reason, Instant serviceStartsAt, Money refundAmount) {}
