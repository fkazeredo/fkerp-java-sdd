package com.fksoft.domain.people;

import java.util.UUID;

/**
 * Use-case input to process a collaborator period journey from the operational snapshot (SPEC-0022
 * BR2/BR3; DL-0069). The worked time, the working days and the punch counts are
 * <strong>operational</strong> inputs derived from the period snapshot (the snapshot is non-legal —
 * BR6); the service resolves and records the consumed {@code snapshotRef} by value. The contracted
 * period total is the collaborator daily journey times {@code workingDays} (e.g. 22 days x 08:00 =
 * 176:00). Re-processing the same {@code (employeeId, period)} is idempotent.
 *
 * @param employeeId the collaborator id (required)
 * @param period the period ({@code YYYY-MM}, required)
 * @param sourceRef the REP/branch reference whose operational snapshot is consumed (required)
 * @param workedMinutes the operational worked minutes in the period (>= 0)
 * @param workingDays the number of working days in the period (>= 0; contracted = daily x this)
 * @param expectedPunches the number of punches expected for the period (for discrepancy detection)
 * @param actualPunches the number of punches actually captured (for discrepancy detection)
 */
public record ProcessJourneyCommand(
    UUID employeeId,
    String period,
    String sourceRef,
    int workedMinutes,
    int workingDays,
    int expectedPunches,
    int actualPunches) {}
