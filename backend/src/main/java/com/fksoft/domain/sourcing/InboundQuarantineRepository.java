package com.fksoft.domain.sourcing;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Repository of quarantined inbound quotations (SPEC-0009 BR10, DL-0120). Module-internal. */
@ModuleInternal
public interface InboundQuarantineRepository extends Repository<InboundQuarantineEntry, UUID> {

  InboundQuarantineEntry save(InboundQuarantineEntry entry);

  Optional<InboundQuarantineEntry> findById(UUID id);

  /** Whether a pending entry already holds this external id (re-delivery of a rejected payload). */
  boolean existsByExternalQuotationIdAndStatus(
      String externalQuotationId, InboundQuarantineStatus status);

  List<InboundQuarantineEntry> findAllByOrderByReceivedAtDesc();

  List<InboundQuarantineEntry> findByStatusOrderByReceivedAtDesc(InboundQuarantineStatus status);
}
