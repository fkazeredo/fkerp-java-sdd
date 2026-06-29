package com.fksoft.infra.integration.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers the due mock payments as <strong>signed webhooks</strong> (ADR 0006; DL-0048): the async
 * leg of the mock gateway. A {@link Scheduled} job periodically picks up undelivered {@link
 * MockPayoutJob}s whose delay has elapsed, builds the external {@link PaymentWebhookPayload}, signs
 * it with the shared HMAC secret (the same scheme a real provider uses) and hands the signed bytes
 * to the {@link PaymentWebhookReceiver} — the exact path a real provider's HTTP callback would take
 * (verify signature → translate → apply idempotently). It marks the job delivered so it is not
 * re-sent; a duplicate delivery would be a harmless no-op anyway (BR3).
 *
 * <p>Tests call {@link #deliverDue()} directly to drive the async confirmation deterministically
 * (no sleep), which is why the delivery logic is a plain method, not buried in the scheduled tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPayoutJobDispatcher {

  private final MockPayoutJobRepository jobs;
  private final PayoutWebhookSignature signature;
  private final PaymentWebhookReceiver receiver;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** Scheduled delivery of due webhooks (interval configurable; default every 2s). */
  @Scheduled(
      initialDelayString = "${integration.payment.mock.dispatch.initial-delay-ms:2000}",
      fixedDelayString = "${integration.payment.mock.dispatch.interval-ms:2000}")
  public void dispatchScheduled() {
    deliverDue();
  }

  /**
   * Delivers every undelivered job whose delay has elapsed as a signed webhook. Returns how many
   * were delivered (useful for tests). Each delivery is the signed external callback the receiver
   * verifies and applies idempotently.
   *
   * @return the number of webhooks delivered
   */
  public int deliverDue() {
    List<MockPayoutJob> due =
        jobs.findByDeliveredFalseAndDeliverAfterLessThanEqual(clock.instant());
    int delivered = 0;
    for (MockPayoutJob job : due) {
      deliver(job);
      job.markDelivered();
      jobs.save(job);
      delivered++;
    }
    return delivered;
  }

  private void deliver(MockPayoutJob job) {
    PaymentWebhookPayload payload =
        new PaymentWebhookPayload(
            job.providerRef(),
            job.payoutId().toString(),
            job.installmentSeq(),
            job.outcome().name());
    byte[] rawBody = serialize(payload);
    String header = signature.sign(rawBody);
    receiver.receive(rawBody, header);
    log.info(
        "MockPaymentWebhookDelivered providerRef={} payoutId={} seq={} outcome={}",
        job.providerRef(),
        job.payoutId(),
        job.installmentSeq(),
        job.outcome());
  }

  private byte[] serialize(PaymentWebhookPayload payload) {
    try {
      return objectMapper.writeValueAsBytes(payload);
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("cannot serialize mock payment webhook", impossible);
    }
  }
}
