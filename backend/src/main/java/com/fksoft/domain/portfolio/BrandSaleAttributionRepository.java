package com.fksoft.domain.portfolio;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link BrandSaleAttribution} intake (SPEC-0020 BR4; DL-0062).
 * Module-internal.
 */
@ModuleInternal
public interface BrandSaleAttributionRepository extends JpaRepository<BrandSaleAttribution, UUID> {

  /** The attribution of a booking, if any (the booking→brand link). */
  Optional<BrandSaleAttribution> findByBookingId(UUID bookingId);

  /** The attribution linked to a reconciliation case, if any (for the REVENUE projection). */
  Optional<BrandSaleAttribution> findByCaseId(UUID caseId);

  /** Whether a booking is already attributed (idempotency guard). */
  boolean existsByBookingId(UUID bookingId);
}
