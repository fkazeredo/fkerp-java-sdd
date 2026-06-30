package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/portfolio/brands/{brandRef}/sales} (SPEC-0020 BR4; DL-0062):
 * the sale→brand attribution intake. Declares that a booking belongs to the path's brand.
 *
 * @param bookingId the booking to attribute to the brand (value)
 */
public record AttributeSaleRequest(@NotNull UUID bookingId) {}
