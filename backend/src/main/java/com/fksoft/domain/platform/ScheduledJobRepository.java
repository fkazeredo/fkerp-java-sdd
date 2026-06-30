package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for the job catalog (SPEC-0023). Only the {@code platform} domain
 * reaches it (Spring Modulith).
 */
@ModuleInternal
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, String> {

  /** The catalog ordered by name. */
  List<ScheduledJob> findAllByOrderByNameAsc();

  /** A job by name (the lookup the trigger endpoint validates against). */
  Optional<ScheduledJob> findByName(String name);
}
