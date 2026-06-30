package com.fksoft.application.api.dto;

import com.fksoft.domain.admin.AdminRecurrence;
import com.fksoft.domain.admin.RegisterContractCommand;
import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/admin/suppliers/{id}/contracts} (SPEC-0025 BR2). The {@code
 * documentId} references the contract document already stored in the Compliance vault (value).
 *
 * @param validFrom the start of validity (required)
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param recurrence the recurring-charge cadence, or {@code null}
 * @param amount the recurring amount (Money), or {@code null}
 * @param documentId the Compliance document id (value), or {@code null}
 */
public record RegisterAdminContractRequest(
    @NotNull LocalDate validFrom,
    LocalDate validUntil,
    AdminRecurrence recurrence,
    Money amount,
    UUID documentId) {

  /** Translates this request to the domain command. */
  public RegisterContractCommand toCommand() {
    return new RegisterContractCommand(validFrom, validUntil, recurrence, amount, documentId);
  }
}
