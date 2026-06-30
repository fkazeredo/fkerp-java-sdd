package com.fksoft.domain.billing;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command repository for the {@link CommissionInvoice} aggregate (SPEC-0016). Module-internal:
 * other modules never touch it (Spring Modulith). The "one live invoice per commission" rule (BR4)
 * is enforced by a partial UNIQUE index in the schema, not here.
 */
@ModuleInternal
public interface CommissionInvoiceRepository extends JpaRepository<CommissionInvoice, UUID> {

  /** Finds the non-cancelled invoice for a commission entry, if any (BR4 idempotency lookup). */
  Optional<CommissionInvoice> findByCommissionEntryIdAndStatusNot(
      UUID commissionEntryId, com.fksoft.domain.billing.InvoiceStatus status);
}
