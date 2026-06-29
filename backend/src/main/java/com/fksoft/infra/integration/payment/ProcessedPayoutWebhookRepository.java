package com.fksoft.infra.integration.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the {@link ProcessedPayoutWebhook} idempotency ledger. Infra-only. */
interface ProcessedPayoutWebhookRepository extends JpaRepository<ProcessedPayoutWebhook, UUID> {

  /** Whether this exact webhook was already processed (the idempotency pre-check, BR3). */
  boolean existsByPayoutIdAndInstallmentSeqAndProviderRef(
      UUID payoutId, int installmentSeq, String providerRef);
}
