package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link Booking}. Module-internal. The unique index on {@code
 * (locator_origin, locator_code)} is the authoritative duplicate guard (BR3). The PENDING-timeout
 * sweep selects bookings that entered PENDING before a cutoff (BR4).
 */
@ModuleInternal
public interface BookingRepository extends JpaRepository<Booking, UUID> {

  /** Whether a booking already exists with the given locator. */
  boolean existsByLocatorOriginAndLocatorCode(LocatorOrigin origin, String code);

  /** Bookings still PENDING since at or before the cutoff (timeout candidates). */
  List<Booking> findByStatusAndPendingSinceLessThanEqual(BookingStatus status, Instant cutoff);

  /** Paginated listing with optional status and account filters. */
  @org.springframework.data.jpa.repository.Query(
      "select b from Booking b where (:status is null or b.status = :status) "
          + "and (:accountId is null or b.accountId = :accountId)")
  Page<Booking> search(
      @org.springframework.data.repository.query.Param("status") BookingStatus status,
      @org.springframework.data.repository.query.Param("accountId") UUID accountId,
      Pageable pageable);
}
