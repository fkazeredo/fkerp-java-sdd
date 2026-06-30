package com.fksoft.application.api.dto;

import com.fksoft.domain.portfolio.RegisterContractCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code POST /api/portfolio/brands/{brandRef}/contracts} (SPEC-0020 BR2). The
 * {@code documentId} references the contract document already stored in the Compliance vault
 * (value).
 *
 * @param validFrom the start of validity (required)
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param documentId the Compliance document id (value), or {@code null}
 * @param terms the reference commercial terms (free map), or {@code null}
 */
public record RegisterContractRequest(
    @NotNull LocalDate validFrom,
    LocalDate validUntil,
    UUID documentId,
    Map<String, String> terms) {

  /** Translates this request to the domain command for the given brand. */
  public RegisterContractCommand toCommand(String brandRef) {
    return new RegisterContractCommand(brandRef, validFrom, validUntil, documentId, terms);
  }
}
