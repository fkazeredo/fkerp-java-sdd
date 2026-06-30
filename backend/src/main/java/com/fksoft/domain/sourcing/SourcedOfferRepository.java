package com.fksoft.domain.sourcing;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link SourcedOffer}. Module-internal: only the sourcing module uses it
 * (Spring Modulith).
 */
@ModuleInternal
public interface SourcedOfferRepository extends JpaRepository<SourcedOffer, UUID> {}
