package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.IntegrationLevel;
import com.fksoft.domain.sourcing.OfferOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/sourcing/offers}. Delivery-level validation guards presence;
 * the non-empty product text invariant (BR1) is also enforced by the domain {@code SourcedOffer}.
 *
 * @param productText free-text product description (required, non-blank)
 * @param basePrice the base price {@code {amount, currency}} (required)
 * @param origin where the offer comes from (required)
 * @param integrationLevel how integrated the source is (required)
 * @param externalRef optional external reference (e.g. an external quotation id)
 */
public record RegisterSourcedOfferRequest(
    @NotBlank String productText,
    @NotNull Money basePrice,
    @NotNull OfferOrigin origin,
    @NotNull IntegrationLevel integrationLevel,
    String externalRef) {}
