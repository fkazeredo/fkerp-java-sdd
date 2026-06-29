package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a booking was cancelled (manually or by PENDING timeout — BR4/BR5). Published
 * in-process; consumed by Reconciliation (SPEC-0007) to cancel the case.
 *
 * @param bookingId the cancelled booking id
 * @param reason the cancellation reason (e.g. {@code PENDING_TIMEOUT})
 * @param occurredAt when cancellation happened
 */
public record BookingCancelled(UUID bookingId, String reason, Instant occurredAt) {}
