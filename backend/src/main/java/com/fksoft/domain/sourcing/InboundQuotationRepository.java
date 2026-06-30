package com.fksoft.domain.sourcing;

import com.fksoft.domain.ModuleInternal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link InboundQuotation} (idempotency by {@code externalQuotationId}, BR4).
 * Module-internal: only the sourcing module uses it (Spring Modulith).
 */
@ModuleInternal
public interface InboundQuotationRepository extends JpaRepository<InboundQuotation, String> {

  /** The most recent {@code receivedAt} across all inbound quotations (connector health). */
  @Query("select max(i.receivedAt) from InboundQuotation i")
  Instant findLastReceivedAt();
}
