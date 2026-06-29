package com.fksoft.domain.reconciliation.internal;

import com.fksoft.domain.reconciliation.CaseStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link ReconciliationCase}. Module-internal. The {@code booking_id} unique index
 * makes case opening idempotent (BR1); settlement loads with a pessimistic write lock (a financial
 * transition, persistence.md); listing is ordered by discrepancy (the prioritization read-model).
 */
public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCase, UUID> {

  /** Whether a case already exists for the booking (idempotent open). */
  boolean existsByBookingId(UUID bookingId);

  /** The case for a booking, if any. */
  Optional<ReconciliationCase> findByBookingId(UUID bookingId);

  /** Loads a case for update with a pessimistic write lock (settlement). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from ReconciliationCase c where c.id = :id")
  Optional<ReconciliationCase> findByIdForUpdate(@Param("id") UUID id);

  /** Paginated search ordered by discrepancy desc, with optional status and min-discrepancy. */
  @Query(
      "select c from ReconciliationCase c where (:status is null or c.status = :status) "
          + "and (:minDiscrepancy is null or c.discrepancyBrl >= :minDiscrepancy) "
          + "order by c.discrepancyBrl desc")
  Page<ReconciliationCase> search(
      @Param("status") CaseStatus status,
      @Param("minDiscrepancy") BigDecimal minDiscrepancy,
      Pageable pageable);
}
