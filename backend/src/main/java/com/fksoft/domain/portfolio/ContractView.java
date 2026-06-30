package com.fksoft.domain.portfolio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Public read view of a representation contract (SPEC-0020).
 *
 * @param id the contract id
 * @param brandRef the covered brand (value)
 * @param validFrom the start of validity
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param documentId the contract document id in the Compliance vault (value), or {@code null}
 * @param terms the reference commercial terms (free map), or empty
 * @param createdAt when it was registered
 */
public record ContractView(
    UUID id,
    String brandRef,
    LocalDate validFrom,
    LocalDate validUntil,
    UUID documentId,
    Map<String, String> terms,
    Instant createdAt) {}
