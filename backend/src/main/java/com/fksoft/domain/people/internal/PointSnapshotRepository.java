package com.fksoft.domain.people.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PointSnapshot}. Module-internal: only the People module uses it. */
public interface PointSnapshotRepository extends JpaRepository<PointSnapshot, UUID> {

  /** The snapshot for a source/period, if any (idempotency lookup, BR5). */
  Optional<PointSnapshot> findBySourceRefAndPeriodRef(String sourceRef, String periodRef);
}
