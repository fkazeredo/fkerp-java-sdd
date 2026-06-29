package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a booking was confirmed — the trigger for commission accrual and for opening a
 * reconciliation case (BR5). Published in-process; consumed by Reconciliation (SPEC-0007).
 *
 * @param bookingId the confirmed booking id
 * @param quoteId the originating quote id
 * @param accountId the account id
 * @param occurredAt when confirmation happened
 */
public record BookingConfirmed(UUID bookingId, UUID quoteId, UUID accountId, Instant occurredAt) {}
