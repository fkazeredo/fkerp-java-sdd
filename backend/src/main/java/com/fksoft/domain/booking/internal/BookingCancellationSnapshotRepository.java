package com.fksoft.domain.booking.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link BookingCancellationSnapshot}. Module-internal. Keyed by {@code
 * bookingId} (the snapshot frozen at confirmation — BR1).
 */
public interface BookingCancellationSnapshotRepository
    extends JpaRepository<BookingCancellationSnapshot, UUID> {}
