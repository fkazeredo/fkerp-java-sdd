package com.fksoft.domain.admin.internal;

import com.fksoft.domain.admin.AdminExpenseKind;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link AdminExpense} aggregate (SPEC-0025). Module-internal:
 * other modules never touch it (Spring Modulith).
 */
public interface AdminExpenseRepository extends JpaRepository<AdminExpense, UUID> {

  /**
   * Whether a recurring expense already exists for the same supplier, period and kind (DL-0086 —
   * idempotency pre-check before the UNIQUE constraint).
   *
   * @param supplierId the supplier
   * @param period the period ({@code YYYY-MM})
   * @param kind the expense kind
   * @return whether a duplicate already exists
   */
  boolean existsBySupplierIdAndPeriodAndKind(UUID supplierId, String period, AdminExpenseKind kind);
}
