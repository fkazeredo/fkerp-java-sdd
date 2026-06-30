package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a collaborator's period journey was processed (SPEC-0022). Published in-process
 * for HR/governance read models. Carries no PII — only the employee id, the period and the
 * time-bank balance in minutes.
 *
 * @param employeeId the collaborator id
 * @param period the period ({@code YYYY-MM})
 * @param balanceMinutes the time-bank balance in minutes (positive = extras; negative = faltas)
 * @param occurredAt when it was processed
 */
public record JourneyProcessed(
    UUID employeeId, String period, int balanceMinutes, Instant occurredAt) {}
