package com.fksoft.domain.people;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of a collaborator (SPEC-0022). The contracted journey is exposed as its {@code
 * HH:mm} label; the contract document is referenced by value (Compliance), never an entity.
 *
 * @param id the employee id
 * @param identifier the unique business identifier
 * @param admissionDate the admission date
 * @param contractedJourney the contracted daily journey as {@code HH:mm}
 * @param status the employment status
 * @param contractDocumentId the Compliance contract document id (value), or {@code null}
 */
public record EmployeeView(
    UUID id,
    String identifier,
    LocalDate admissionDate,
    String contractedJourney,
    EmployeeStatus status,
    UUID contractDocumentId) {}
