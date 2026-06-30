package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact (alert): a journey discrepancy was detected for a collaborator's period (SPEC-0022
 * BR4; DL-0071). Published in-process for HR/governance; it is an <strong>alert</strong> for human
 * treatment — the system never auto-corrects. Carries no PII.
 *
 * @param employeeId the collaborator id
 * @param period the period ({@code YYYY-MM})
 * @param kind the discrepancy kind
 * @param occurredAt when it was detected
 */
public record JourneyDiscrepancy(
    UUID employeeId, String period, DiscrepancyKind kind, Instant occurredAt) {}
