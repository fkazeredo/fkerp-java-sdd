package com.fksoft.application.api.dto;

import com.fksoft.domain.portfolio.RegisterBrandCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/portfolio/brands} (SPEC-0020 BR1).
 *
 * @param brandRef the unique brand/supplier identifier
 * @param displayName the human-readable name
 */
public record RegisterBrandRequest(@NotBlank String brandRef, @NotBlank String displayName) {

  /** Translates this request to the domain command. */
  public RegisterBrandCommand toCommand() {
    return new RegisterBrandCommand(brandRef, displayName);
  }
}
