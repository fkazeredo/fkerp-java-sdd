package com.fksoft.application.api.dto;

import com.fksoft.domain.booking.LocatorOrigin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/bookings}: the quote to book and the locator. For INTERNAL the
 * code is generated (the supplied code is ignored); for EXTERNAL the operator's code is used (the
 * domain requires it to be non-empty).
 *
 * @param quoteId the quote to turn into a booking (required)
 * @param locator the locator origin and optional code (required)
 */
public record CreateBookingRequest(@NotNull UUID quoteId, @NotNull @Valid LocatorRequest locator) {

  /**
   * Locator part of the request.
   *
   * @param origin the locator origin (required)
   * @param code the code (required for EXTERNAL; ignored for INTERNAL)
   */
  public record LocatorRequest(@NotNull LocatorOrigin origin, String code) {}
}
