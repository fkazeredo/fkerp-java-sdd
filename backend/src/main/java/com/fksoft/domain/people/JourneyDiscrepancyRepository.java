package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal repository for the {@link JourneyDiscrepancyRecord} alerts (SPEC-0022). */
@ModuleInternal
public interface JourneyDiscrepancyRepository
    extends JpaRepository<JourneyDiscrepancyRecord, UUID> {

  /** Whether an alert of this kind already exists for the (employee, period) (no dup, DL-0071). */
  boolean existsByEmployeeIdAndPeriodAndKind(UUID employeeId, String period, String kind);

  /** Counts the open discrepancies of a collaborator's period (time-bank view). */
  int countByEmployeeIdAndPeriodAndStatus(UUID employeeId, String period, DiscrepancyStatus status);

  /** The discrepancy queue, newest first, filtered by period and/or status. */
  Page<JourneyDiscrepancyRecord> findByOrderByCreatedAtDesc(Pageable pageable);

  /** The discrepancy queue for a period, newest first. */
  Page<JourneyDiscrepancyRecord> findByPeriodOrderByCreatedAtDesc(String period, Pageable pageable);

  /** The discrepancy queue for a status, newest first. */
  Page<JourneyDiscrepancyRecord> findByStatusOrderByCreatedAtDesc(
      DiscrepancyStatus status, Pageable pageable);

  /** The discrepancy queue for a period and status, newest first. */
  Page<JourneyDiscrepancyRecord> findByPeriodAndStatusOrderByCreatedAtDesc(
      String period, DiscrepancyStatus status, Pageable pageable);
}
