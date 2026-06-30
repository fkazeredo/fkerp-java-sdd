package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal repository for the {@link Employee} aggregate (SPEC-0022). */
@ModuleInternal
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  /** Whether a collaborator already exists with the given unique identifier (BR1). */
  boolean existsByIdentifier(String identifier);

  /** Looks up a collaborator by its unique identifier. */
  Optional<Employee> findByIdentifier(String identifier);

  /** Lists collaborators, newest first, optionally filtered by status. */
  Page<Employee> findAllByOrderByCreatedAtDesc(Pageable pageable);

  /** Lists collaborators of a given status, newest first. */
  Page<Employee> findByStatusOrderByCreatedAtDesc(EmployeeStatus status, Pageable pageable);
}
