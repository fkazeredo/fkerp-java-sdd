package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/bookings/{id}/cancel}: the cancellation reason (required).
 *
 * @param reason the reason for cancelling (required, non-blank)
 */
public record CancelBookingRequest(@NotBlank String reason) {}
