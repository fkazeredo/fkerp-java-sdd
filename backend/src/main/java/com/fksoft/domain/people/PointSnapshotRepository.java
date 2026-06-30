package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PointSnapshot}. Module-internal: only the People module uses it. */
@ModuleInternal
public interface PointSnapshotRepository extends JpaRepository<PointSnapshot, UUID> {

  /** The snapshot for a source/period, if any (idempotency lookup, BR5). */
  Optional<PointSnapshot> findBySourceRefAndPeriodRef(String sourceRef, String periodRef);
}
