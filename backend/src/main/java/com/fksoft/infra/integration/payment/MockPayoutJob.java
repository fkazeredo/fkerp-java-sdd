package com.fksoft.infra.integration.payment;

import com.fksoft.domain.payout.PaymentOutcome;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A pending payment the {@link MockPaymentGateway} will later confirm/fail by POSTing a signed
 * webhook (ADR 0006; DL-0048) — the asynchronous leg of the mock. It records which
 * payout/installment to settle, the chosen outcome (default SUCCEEDED, configurable per request to
 * exercise the failure path) and when the webhook may be delivered (the async delay). Lives
 * entirely in {@code infra.integration.payment} — never crosses into the domain.
 */
@Entity
@Table(name = "mock_payout_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MockPayoutJob {

  @Id private UUID id;

  private UUID payoutId;
  private int installmentSeq;
  private String providerRef;

  @Enumerated(EnumType.STRING)
  private PaymentOutcome outcome;

  private Instant deliverAfter;
  private boolean delivered;
  private Instant createdAt;

  static MockPayoutJob of(
      UUID payoutId,
      int installmentSeq,
      String providerRef,
      PaymentOutcome outcome,
      Instant deliverAfter,
      Instant now) {
    MockPayoutJob job = new MockPayoutJob();
    job.id = UUID.randomUUID();
    job.payoutId = payoutId;
    job.installmentSeq = installmentSeq;
    job.providerRef = providerRef;
    job.outcome = outcome;
    job.deliverAfter = deliverAfter;
    job.delivered = false;
    job.createdAt = now;
    return job;
  }

  UUID payoutId() {
    return payoutId;
  }

  int installmentSeq() {
    return installmentSeq;
  }

  String providerRef() {
    return providerRef;
  }

  PaymentOutcome outcome() {
    return outcome;
  }

  void markDelivered() {
    this.delivered = true;
  }
}
