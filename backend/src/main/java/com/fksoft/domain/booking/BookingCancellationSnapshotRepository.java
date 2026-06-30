package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link BookingCancellationSnapshot}. Module-internal. Keyed by {@code
 * bookingId} (the snapshot frozen at confirmation — BR1).
 */
@ModuleInternal
public interface BookingCancellationSnapshotRepository
    extends JpaRepository<BookingCancellationSnapshot, UUID> {}
