package com.fksoft.infra.integration.payment;

import com.fksoft.domain.payout.PaymentGateway;
import com.fksoft.domain.payout.PaymentInstruction;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PaymentRequestResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The traceable mock payment gateway (ADR 0006; DL-0048): the default {@link PaymentGateway} in
 * dev/test/staging. {@link #request} returns immediately with a {@code providerRef} and PENDING; it
 * persists a {@link MockPayoutJob} that the {@link MockPayoutJobDispatcher} will later turn into a
 * signed webhook (the async confirmation) — exactly the shape a real provider has, so swapping in a
 * real adapter is a configuration change, not an architecture change.
 *
 * <p>The async outcome is taken from the instruction's {@code outcomeHint} (default SUCCEEDED), so
 * a test can deterministically exercise the failure path. Sensitive payment data is never logged
 * (SPEC-0017 Error Behavior) — only the payout/installment/providerRef.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

  private final MockPayoutJobRepository jobs;
  private final Clock clock;

  @Value("${integration.payment.mock.delay-ms:3000}")
  private long deliveryDelayMs;

  @Override
  public PaymentRequestResult request(PaymentInstruction instruction) {
    String providerRef = "mock-" + UUID.randomUUID();
    PaymentOutcome outcome =
        instruction.outcomeHint() == null ? PaymentOutcome.SUCCEEDED : instruction.outcomeHint();
    Instant now = clock.instant();
    jobs.save(
        MockPayoutJob.of(
            instruction.payoutId(),
            instruction.installmentSeq(),
            providerRef,
            outcome,
            now.plus(Duration.ofMillis(deliveryDelayMs)),
            now));
    log.info(
        "PaymentRequested provider=mock providerRef={} payoutId={} seq={} plannedOutcome={}",
        providerRef,
        instruction.payoutId(),
        instruction.installmentSeq(),
        outcome);
    return PaymentRequestResult.pending(providerRef);
  }
}
