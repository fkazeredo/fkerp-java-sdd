package com.fksoft.application.api.dto;

import com.fksoft.domain.people.CreateEmployeeCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/people/employees} (SPEC-0022 BR1). The contracted journey is a
 * {@code HH:mm} label parsed in the domain; {@code contractDocumentId} references the Compliance
 * contract document by value.
 *
 * @param identifier the unique business identifier (required)
 * @param admissionDate the admission date (required)
 * @param contractedJourney the contracted daily journey as {@code HH:mm} (required, e.g. {@code
 *     "08:00"})
 * @param contractDocumentId the Compliance contract document id (value), or {@code null}
 */
public record CreateEmployeeRequest(
    @NotBlank String identifier,
    @NotNull LocalDate admissionDate,
    @NotBlank String contractedJourney,
    UUID contractDocumentId) {

  /** Translates this request to the domain command. */
  public CreateEmployeeCommand toCommand() {
    return new CreateEmployeeCommand(
        identifier, admissionDate, contractedJourney, contractDocumentId);
  }
}
