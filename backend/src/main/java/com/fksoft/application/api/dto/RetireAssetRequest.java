package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/assets/{id}/retire} (SPEC-0021 BR4): the retirement reason,
 * recorded for audit (who/when/reason).
 *
 * @param reason the retirement reason (required)
 */
public record RetireAssetRequest(@NotBlank String reason) {}
