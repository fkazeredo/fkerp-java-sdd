package com.fksoft.domain.reconciliation;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a reconciliation case was opened for a confirmed booking (BR1). Published
 * in-process.
 *
 * @param caseId the opened case id
 * @param bookingId the booking that triggered it
 * @param occurredAt when it was opened
 */
public record ReconciliationCaseOpened(UUID caseId, UUID bookingId, Instant occurredAt) {}
