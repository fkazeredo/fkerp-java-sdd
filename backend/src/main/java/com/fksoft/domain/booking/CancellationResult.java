package com.fksoft.domain.booking;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of cancelling a booking (SPEC-0010 Input/Output Examples): the booking, the applied
 * policy type and the resulting charges. The charges are distinct facts that do not net out (BR5/
 * BR11) — they are returned as a list, never collapsed into a single net amount.
 *
 * @param bookingId the cancelled booking id
 * @param status the booking status after cancellation (CANCELLED)
 * @param policyType the type of the frozen policy that governed the cancellation
 * @param charges the resulting charges (PENALTY and/or SUPPLIER + CUSTOMER_REFUND)
 */
public record CancellationResult(
    UUID bookingId, BookingStatus status, CancellationType policyType, List<Charge> charges) {}
