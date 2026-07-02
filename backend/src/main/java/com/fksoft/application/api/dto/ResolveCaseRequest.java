package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Request body for {@code POST /api/aftersales/cases/{id}/resolve} (SPEC-0018; DL-0054): the
 * resolution outcome plus the data the orchestration needs — the refund/commercial-refund amount,
 * the handling effort cost (cost-to-serve, BR5) and the cancellation details passed to Booking.
 *
 * @param resolution the resolution outcome (required)
 * @param amount the refund/commercial-refund amount, or {@code null}
 * @param handlingCost the handling effort cost to accrue (BR5), or {@code null}
 * @param serviceStartsAt when the booked service starts (cancellation penalty-window base), or
 *     {@code null}
 * @param cancellationReason the cancellation reason passed to Booking, or {@code null}
 */
public record ResolveCaseRequest(
    @NotBlank String resolution,
    Money amount,
    Money handlingCost,
    Instant serviceStartsAt,
    String cancellationReason) {}
