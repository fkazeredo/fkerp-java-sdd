package com.fksoft.application.api.dto;

import com.fksoft.domain.assets.RegisterAssetCommand;
import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/assets} (SPEC-0021 BR1/BR2). The {@code expiresAt} is required
 * for the {@code SOFTWARE_LICENSE} type code (enforced in the domain, BR1); {@code documentId} and
 * {@code financeEntryId} reference the Compliance vault and the Finance ledger by value.
 *
 * @param type the asset-type cadastro code (required)
 * @param identifier the identification/description (required)
 * @param acquisitionDate when it was acquired (required)
 * @param acquisitionCost the acquisition cost (required, Money)
 * @param expiresAt the license expiry date (required for SOFTWARE_LICENSE), or {@code null}
 * @param supplierRef the supplier reference (value), or {@code null}
 * @param documentId the Compliance document id (value), or {@code null}
 * @param financeEntryId the Finance cost entry id (value), or {@code null}
 */
public record RegisterAssetRequest(
    @NotBlank String type,
    @NotBlank String identifier,
    @NotNull LocalDate acquisitionDate,
    @NotNull Money acquisitionCost,
    LocalDate expiresAt,
    String supplierRef,
    UUID documentId,
    UUID financeEntryId) {

  /** Translates this request to the domain command. */
  public RegisterAssetCommand toCommand() {
    return new RegisterAssetCommand(
        type,
        identifier,
        acquisitionDate,
        acquisitionCost,
        expiresAt,
        supplierRef,
        documentId,
        financeEntryId);
  }
}
