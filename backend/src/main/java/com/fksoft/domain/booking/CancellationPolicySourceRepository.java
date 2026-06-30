package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link CancellationPolicySource}. Module-internal. One row per {@code
 * scopeRef} (unique index), which is the lookup the booking uses to freeze its snapshot (BR1).
 */
@ModuleInternal
public interface CancellationPolicySourceRepository
    extends JpaRepository<CancellationPolicySource, UUID> {

  /** The administered policy source for a scope, if one exists. */
  Optional<CancellationPolicySource> findByScopeRef(String scopeRef);
}
