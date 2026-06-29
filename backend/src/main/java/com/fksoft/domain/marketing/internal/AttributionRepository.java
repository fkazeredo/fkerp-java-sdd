package com.fksoft.domain.marketing.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/projection repository for the {@link Attribution} aggregate (SPEC-0019 BR5). The {@code
 * (campaignCode, bookingId)} pair is unique (idempotency); the {@code bookingId} lookup links a
 * confirmed booking back to its attribution. Module-internal.
 */
public interface AttributionRepository extends JpaRepository<Attribution, UUID> {

  /** Finds the attribution for a campaign code and booking (idempotency check). */
  Optional<Attribution> findByCampaignCodeAndBookingId(String campaignCode, UUID bookingId);

  /** The attributions registered for a booking (the BookingConfirmed consumer resolves these). */
  List<Attribution> findByBookingId(UUID bookingId);

  /** The attributions for a campaign code (the GET attribution report). */
  List<Attribution> findByCampaignCodeOrderByAttributedAtDesc(String campaignCode);
}
