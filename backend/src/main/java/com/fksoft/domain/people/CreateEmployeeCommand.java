package com.fksoft.domain.people;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Use-case input to register a collaborator (SPEC-0022 BR1). The contracted journey arrives as a
 * {@code HH:mm} label and is parsed into the {@link ContractedJourney} value object by the service.
 *
 * @param identifier the unique business identifier (required)
 * @param admissionDate the admission date (required)
 * @param contractedJourney the contracted daily journey as {@code HH:mm} (required, e.g. {@code
 *     "08:00"})
 * @param contractDocumentId the Compliance contract document id (value), or {@code null}
 */
public record CreateEmployeeCommand(
    String identifier,
    LocalDate admissionDate,
    String contractedJourney,
    UUID contractDocumentId) {}
