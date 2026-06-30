package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link CancellationCharge}. Module-internal. Charges are read back per
 * booking (for the cancel response and audit).
 */
@ModuleInternal
public interface CancellationChargeRepository extends JpaRepository<CancellationCharge, UUID> {

  /** All charges recorded for a booking, oldest first. */
  List<CancellationCharge> findByBookingIdOrderByCreatedAt(UUID bookingId);
}
