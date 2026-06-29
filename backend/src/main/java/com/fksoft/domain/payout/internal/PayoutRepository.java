package com.fksoft.domain.payout.internal;

import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Command repository for the {@link Payout} aggregate (SPEC-0017). Module-internal: other modules
 * never touch it (Spring Modulith). Execution is a financial transition, so it loads the aggregate
 * with a pessimistic write lock (persistence.md; SPEC-0017 BR2 "locking pessimista").
 */
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

  /** Loads a payout for update with a pessimistic write lock (the execution transition, BR2). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payout p where p.id = :id")
  Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

  /** Lists payouts with optional kind, status and payee filters, paged (newest first). */
  @Query(
      "select p from Payout p where (:kind is null or p.kind = :kind) "
          + "and (:status is null or p.status = :status) "
          + "and (:payee is null or p.payeeId = :payee) "
          + "order by p.createdAt desc")
  Page<Payout> search(
      @Param("kind") PayoutKind kind,
      @Param("status") PayoutStatus status,
      @Param("payee") String payee,
      Pageable pageable);
}
