package com.fksoft.domain.admin.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command/query repository for the {@link AdminContract} aggregate (SPEC-0025). Module-internal:
 * other modules never touch it (Spring Modulith).
 */
public interface AdminContractRepository extends JpaRepository<AdminContract, UUID> {

  /** The contracts of a supplier, newest first. */
  List<AdminContract> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);

  /**
   * Contracts not yet signaled whose {@code validUntil} is on/before the threshold — the candidates
   * for the expiry alert sweep (DL-0087).
   *
   * @param threshold the inclusive upper bound on {@code validUntil}
   * @return the candidate contracts
   */
  @Query(
      "SELECT c FROM AdminContract c WHERE c.expirySignaledAt IS NULL "
          + "AND c.validUntil IS NOT NULL AND c.validUntil <= :threshold")
  List<AdminContract> findExpiringCandidates(@Param("threshold") LocalDate threshold);
}
