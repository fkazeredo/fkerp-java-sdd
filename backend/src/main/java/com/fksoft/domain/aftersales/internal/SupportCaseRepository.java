package com.fksoft.domain.aftersales.internal;

import com.fksoft.domain.aftersales.SupportCaseStatus;
import com.fksoft.domain.aftersales.SupportCaseType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command repository for the {@link SupportCase} aggregate (SPEC-0018). Module-internal: other
 * modules never touch it (Spring Modulith). Reads support the listing filters and the SLA breach
 * sweep (non-terminal cases whose deadline has passed and that are not yet flagged).
 */
public interface SupportCaseRepository extends JpaRepository<SupportCase, UUID> {

  /** Lists cases with optional type/status/booking/breached filters, paged (newest first). */
  @Query(
      "select c from SupportCase c where (:type is null or c.type = :type) "
          + "and (:status is null or c.status = :status) "
          + "and (:bookingId is null or c.bookingId = :bookingId) "
          + "and (:breached is null or c.breached = :breached) "
          + "order by c.openedAt desc")
  Page<SupportCase> search(
      @Param("type") SupportCaseType type,
      @Param("status") SupportCaseStatus status,
      @Param("bookingId") String bookingId,
      @Param("breached") Boolean breached,
      Pageable pageable);

  /**
   * The candidates for an SLA breach sweep (BR4/DL-0053): cases that are not yet flagged as
   * breached, are still open (status is not RESOLVED/CLOSED) and whose resolution deadline is
   * before {@code now}. The aggregate makes the final, idempotent decision.
   */
  @Query(
      "select c from SupportCase c where c.breached = false "
          + "and c.status not in (com.fksoft.domain.aftersales.SupportCaseStatus.RESOLVED, "
          + "com.fksoft.domain.aftersales.SupportCaseStatus.CLOSED) "
          + "and c.dueAt < :now")
  List<SupportCase> findBreachCandidates(@Param("now") Instant now);
}
