package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/quotes/{id}/override}. The reason is intentionally not
 * bean-validated: an empty reason is a domain rule (BR6) that yields {@code
 * quoting.override.reason-required}, not a generic validation error.
 *
 * @param appliedAmount the new applied amount {@code {amount, currency}} (required)
 * @param reason the reason for diverging (validated by the domain)
 */
public record OverrideQuoteRequest(@NotNull Money appliedAmount, String reason) {}
