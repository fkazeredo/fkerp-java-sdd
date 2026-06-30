package com.fksoft.domain.intelligence;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-booking {@link BookingAttribution}. Module-internal. */
@ModuleInternal
public interface BookingAttributionRepository extends JpaRepository<BookingAttribution, UUID> {}
