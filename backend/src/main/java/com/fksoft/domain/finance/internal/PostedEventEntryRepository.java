package com.fksoft.domain.finance.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PostedEventEntry}. Module-internal: only the Finance module uses it. The
 * idempotency pre-check (DL-0041) is {@link #existsBySourceRefAndChargeKind}; the UNIQUE {@code
 * (source_ref, charge_kind)} backs it against concurrent double-posts.
 */
public interface PostedEventEntryRepository extends JpaRepository<PostedEventEntry, UUID> {

  /**
   * Whether the fact {@code (sourceRef, chargeKind)} was already posted (idempotency pre-check).
   */
  boolean existsBySourceRefAndChargeKind(String sourceRef, String chargeKind);
}
