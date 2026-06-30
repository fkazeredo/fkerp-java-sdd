package com.fksoft.domain.portfolio;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Command to register a representation contract for a brand (SPEC-0020 BR2). The {@code documentId}
 * references the contract document already stored in the Compliance vault (value, never an FK).
 *
 * @param brandRef the brand the contract covers (value)
 * @param validFrom the start of validity (required)
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param documentId the Compliance document id (value), or {@code null}
 * @param terms the reference commercial terms (free map), or {@code null}
 */
public record RegisterContractCommand(
    String brandRef,
    LocalDate validFrom,
    LocalDate validUntil,
    UUID documentId,
    Map<String, String> terms) {}
