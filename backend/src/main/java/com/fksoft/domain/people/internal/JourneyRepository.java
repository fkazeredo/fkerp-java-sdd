package com.fksoft.domain.people.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal repository for the {@link Journey} aggregate (SPEC-0022). */
public interface JourneyRepository extends JpaRepository<Journey, UUID> {

  /** The processed journey for an (employee, period), if any (idempotency lookup, BR2). */
  Optional<Journey> findByEmployeeIdAndPeriod(UUID employeeId, String period);
}
