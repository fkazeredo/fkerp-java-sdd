package com.fksoft.domain.sourcing.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link SourcedOffer}. Module-internal: only the sourcing module uses it
 * (Spring Modulith).
 */
public interface SourcedOfferRepository extends JpaRepository<SourcedOffer, UUID> {}
