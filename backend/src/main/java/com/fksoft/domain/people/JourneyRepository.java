package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal repository for the {@link Journey} aggregate (SPEC-0022). */
@ModuleInternal
public interface JourneyRepository extends JpaRepository<Journey, UUID> {

  /** The processed journey for an (employee, period), if any (idempotency lookup, BR2). */
  Optional<Journey> findByEmployeeIdAndPeriod(UUID employeeId, String period);
}
