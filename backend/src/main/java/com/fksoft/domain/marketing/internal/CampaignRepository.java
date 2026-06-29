package com.fksoft.domain.marketing.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command repository for the {@link Campaign} aggregate (SPEC-0019). Module-internal: other modules
 * never touch it (Spring Modulith).
 */
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

  /** Finds a campaign by its public attribution code (used by attribution, DL-0057). */
  Optional<Campaign> findByCode(String code);
}
