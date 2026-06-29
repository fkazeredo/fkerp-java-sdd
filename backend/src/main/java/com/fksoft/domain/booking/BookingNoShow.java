package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a confirmed booking became a no-show. Published in-process; consumer is
 * Commissioning's no-show rule (SPEC-0010).
 *
 * @param bookingId the booking id
 * @param occurredAt when the no-show was recorded
 */
public record BookingNoShow(UUID bookingId, Instant occurredAt) {}
