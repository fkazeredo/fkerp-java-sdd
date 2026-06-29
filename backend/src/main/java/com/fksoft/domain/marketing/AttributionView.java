package com.fksoft.domain.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a campaign→booking attribution (SPEC-0019 BR5). A row links a campaign code
 * to a booking; {@code converted} flips to {@code true} when the booking is confirmed (DL-0057).
 *
 * @param id the attribution id
 * @param campaignCode the campaign's public code (value)
 * @param bookingId the booking (value)
 * @param converted whether the booking was confirmed (a measured conversion)
 * @param attributedAt when the link was registered
 * @param convertedAt when the conversion was confirmed, or {@code null}
 */
public record AttributionView(
    UUID id,
    String campaignCode,
    UUID bookingId,
    boolean converted,
    Instant attributedAt,
    Instant convertedAt) {}
