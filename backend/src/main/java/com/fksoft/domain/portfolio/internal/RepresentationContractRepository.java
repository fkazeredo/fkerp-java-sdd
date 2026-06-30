package com.fksoft.domain.portfolio.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command/query repository for the {@link RepresentationContract} aggregate (SPEC-0020).
 * Module-internal.
 */
public interface RepresentationContractRepository
    extends JpaRepository<RepresentationContract, UUID> {

  /** All contracts of a brand, newest first. */
  List<RepresentationContract> findByBrandRefOrderByCreatedAtDesc(String brandRef);

  /**
   * Contracts that should be evaluated for the expiry alert (DL-0063): not yet signaled and with a
   * {@code validUntil} on or before the warning threshold. The final idempotency/window check lives
   * in {@link RepresentationContract#signalExpiringIfDue}.
   */
  @Query(
      "select c from RepresentationContract c where c.expiringSignaledAt is null "
          + "and c.validUntil is not null and c.validUntil <= :threshold")
  List<RepresentationContract> findExpiringCandidates(@Param("threshold") LocalDate threshold);
}
