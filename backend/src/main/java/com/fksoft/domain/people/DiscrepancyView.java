package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a journey discrepancy in the treatment queue (SPEC-0022 BR4; DL-0071).
 *
 * @param id the discrepancy id
 * @param employeeId the collaborator id
 * @param period the period ({@code YYYY-MM})
 * @param kind the discrepancy kind
 * @param status the queue status (OPEN/RESOLVED)
 * @param detail an optional human detail
 * @param createdAt when it was raised
 */
public record DiscrepancyView(
    UUID id,
    UUID employeeId,
    String period,
    String kind,
    DiscrepancyStatus status,
    String detail,
    Instant createdAt) {}
