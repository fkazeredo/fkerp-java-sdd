package com.fksoft.application.api.dto;

import com.fksoft.domain.people.ProcessJourneyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/**
 * Request body for {@code POST /api/people/employees/{id}/journey} (SPEC-0022 BR2/BR3). The worked
 * minutes, working days and punch counts are operational inputs derived from the period snapshot
 * (non-legal, BR6); the {@code sourceRef} selects which operational snapshot is consumed. The
 * contracted period total is the collaborator daily journey times {@code workingDays}.
 *
 * @param period the period ({@code YYYY-MM}, required)
 * @param sourceRef the REP/branch reference whose snapshot is consumed (required)
 * @param workedMinutes the operational worked minutes in the period (>= 0)
 * @param workingDays the number of working days in the period (>= 0)
 * @param expectedPunches the punches expected for the period (>= 0)
 * @param actualPunches the punches actually captured (>= 0)
 */
public record ProcessJourneyRequest(
    @NotBlank String period,
    @NotBlank String sourceRef,
    @PositiveOrZero int workedMinutes,
    @PositiveOrZero int workingDays,
    @PositiveOrZero int expectedPunches,
    @PositiveOrZero int actualPunches) {

  /** Translates this request to the domain command for the given employee. */
  public ProcessJourneyCommand toCommand(UUID employeeId) {
    return new ProcessJourneyCommand(
        employeeId, period, sourceRef, workedMinutes, workingDays, expectedPunches, actualPunches);
  }
}
