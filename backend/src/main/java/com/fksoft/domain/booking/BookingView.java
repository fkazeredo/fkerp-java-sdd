package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a booking returned to the delivery layer (entity never leaves the module).
 *
 * @param id booking id
 * @param quoteId the originating quote id
 * @param accountId the account id (copied from the quote)
 * @param status current lifecycle status
 * @param locator the locator (origin + code)
 * @param pendingSince when it entered PENDING (timeout base), or {@code null}
 * @param confirmedAt when it was confirmed, or {@code null}
 * @param cancelReason the cancellation reason, or {@code null}
 * @param createdAt creation instant
 */
public record BookingView(
    UUID id,
    UUID quoteId,
    UUID accountId,
    BookingStatus status,
    Locator locator,
    Instant pendingSince,
    Instant confirmedAt,
    String cancelReason,
    Instant createdAt) {}
