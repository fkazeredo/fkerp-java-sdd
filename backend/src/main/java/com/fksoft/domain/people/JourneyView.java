package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a processed period journey (SPEC-0022). Durations are rendered as {@code
 * HH:mm} (worked/contracted) and {@code ±HH:mm} (balance). The {@code snapshotRef} is the
 * operational snapshot consumed (by value, DL-0069) — never a legal document.
 *
 * @param employeeId the collaborator id
 * @param period the period ({@code YYYY-MM})
 * @param workedHours the worked time as {@code HH:mm}
 * @param contractedHours the contracted time for the period as {@code HH:mm}
 * @param balance the time-bank balance as {@code ±HH:mm}
 * @param snapshotRef the operational snapshot consumed (value)
 * @param processedAt when the journey was processed
 */
public record JourneyView(
    UUID employeeId,
    String period,
    String workedHours,
    String contractedHours,
    String balance,
    UUID snapshotRef,
    Instant processedAt) {}
