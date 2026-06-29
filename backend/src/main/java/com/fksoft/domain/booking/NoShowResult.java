package com.fksoft.domain.booking;

import java.util.UUID;

/**
 * The outcome of recording a no-show (SPEC-0010 BR6): the booking, the fee charged (or {@code null}
 * when none) and whether it was waived by proof of a cancelled flight.
 *
 * @param bookingId the booking id
 * @param status the booking status after the no-show (NO_SHOW)
 * @param charge the no-show charge applied, or {@code null} when none (no fee or waived)
 * @param waived whether the fee was waived by valid proof
 */
public record NoShowResult(UUID bookingId, BookingStatus status, Charge charge, boolean waived) {}
