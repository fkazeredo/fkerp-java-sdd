package com.fksoft.application.api.dto;

import com.fksoft.domain.accounts.LegalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/accounts}. Delivery-level validation guards presence and shape;
 * the document's check-digit invariant is enforced by the domain {@code Document} value object.
 *
 * @param legalType the legal type (required)
 * @param documentNumber the document as typed; punctuation allowed (required)
 * @param displayName the commercial display name (required, non-blank)
 * @param cadastur optional CADASTUR registration
 * @param iata optional IATA registration
 */
public record CreateAccountRequest(
    @NotNull LegalType legalType,
    @NotBlank String documentNumber,
    @NotBlank String displayName,
    String cadastur,
    String iata) {}
