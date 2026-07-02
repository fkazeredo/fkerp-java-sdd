package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/sourcing/offers}. Delivery-level validation guards presence;
 * the non-empty product text invariant (BR1) is also enforced by the domain {@code SourcedOffer}.
 * The {@code origin}/{@code integrationLevel} are cadastro codes (were {@code OfferOrigin}/{@code
 * IntegrationLevel}; SPEC-0031/DL-0117) — the wire stays a string, validated against the cadastro
 * by the service.
 *
 * @param productText free-text product description (required, non-blank)
 * @param basePrice the base price {@code {amount, currency}} (required)
 * @param origin the offer-origin cadastro code (required)
 * @param integrationLevel the integration-level cadastro code (required)
 * @param externalRef optional external reference (e.g. an external quotation id)
 */
public record RegisterSourcedOfferRequest(
    @NotBlank String productText,
    @NotNull Money basePrice,
    @NotBlank String origin,
    @NotBlank String integrationLevel,
    String externalRef) {}
