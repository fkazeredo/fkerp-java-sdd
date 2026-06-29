package com.fksoft.infra.integration.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The idempotency ledger of the inbound payment webhook (ADR 0006; DL-0048; SPEC-0017 BR3): one row
 * per processed {@code (payoutId, installmentSeq, providerRef)}. The UNIQUE constraint (plus a
 * pre-check) makes a re-delivered callback a no-op — a duplicate webhook never double-confirms or
 * double-pays. Infra-only.
 */
@Entity
@Table(name = "processed_payout_webhooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessedPayoutWebhook {

  @Id private UUID id;

  private UUID payoutId;
  private int installmentSeq;
  private String providerRef;
  private String outcome;
  private Instant processedAt;

  static ProcessedPayoutWebhook of(
      UUID payoutId, int installmentSeq, String providerRef, String outcome, Instant now) {
    ProcessedPayoutWebhook processed = new ProcessedPayoutWebhook();
    processed.id = UUID.randomUUID();
    processed.payoutId = payoutId;
    processed.installmentSeq = installmentSeq;
    processed.providerRef = providerRef;
    processed.outcome = outcome;
    processed.processedAt = now;
    return processed;
  }
}
