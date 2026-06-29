package com.fksoft.domain.exchange.internal;

import com.fksoft.domain.exchange.FxPositionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link FxPosition}. Module-internal. The {@code booking_id} unique index makes
 * opening idempotent per booking (BR2); the queries feed the exposure read-models (BR6): the open
 * book and the positions opened within a reporting period.
 */
public interface FxPositionRepository extends JpaRepository<FxPosition, UUID> {

  /** Whether a position already exists for the booking (idempotent open). */
  boolean existsByBookingId(UUID bookingId);

  /** The position for a booking, if any. */
  Optional<FxPosition> findByBookingId(UUID bookingId);

  /** All positions in a status (the open book for {@code LiveExposure}). */
  List<FxPosition> findByStatus(FxPositionStatus status);

  /** Positions opened within {@code [from, to)} — the period for {@code PromoFxResult}. */
  @Query("select p from FxPosition p where p.openedAt >= :from and p.openedAt < :to")
  List<FxPosition> findOpenedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
