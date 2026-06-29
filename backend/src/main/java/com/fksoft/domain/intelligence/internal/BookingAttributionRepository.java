package com.fksoft.domain.intelligence.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-booking {@link BookingAttribution}. Module-internal. */
public interface BookingAttributionRepository extends JpaRepository<BookingAttribution, UUID> {}
