package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeType;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.infra.integration.payment.MockPayoutJobDispatcher;
import com.fksoft.infra.integration.payment.PayoutExecutionService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the asynchronous payment webhook (SPEC-0017 BR2/BR3; ADR 0006; DL-0048;
 * V22): executing requests the mock gateway (PENDING, no synchronous paid); the mock dispatcher
 * then delivers a <strong>signed</strong> webhook that confirms or fails the installment. Proves
 * the supervisor's hard requirements: the webhook confirms idempotently (a re-delivered callback
 * does NOT double-confirm) and a failed payment leaves an explicit FAILED state (no false "paid").
 */
class PayoutExecutionWebhookIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PayoutService payoutService;
  @Autowired private PayoutExecutionService executionService;
  @Autowired private MockPayoutJobDispatcher dispatcher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM processed_payout_webhooks");
    jdbcTemplate.execute("DELETE FROM mock_payout_jobs");
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
  }

  @Test
  void executingRequestsTheGatewayAndStaysExecutingUntilTheWebhookConfirms() {
    PayoutView created = createAgentCommission(new BigDecimal("405.00"));

    // execute → installment EXECUTING, a mock job queued, NOTHING paid synchronously (ADR 0006).
    PayoutView executing = executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    assertThat(executing.status()).isEqualTo(PayoutStatus.EXECUTING);
    Integer jobs =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM mock_payout_jobs WHERE payout_id = ?",
            Integer.class,
            created.id());
    assertThat(jobs).isEqualTo(1);

    // The async webhook arrives (signed) → installment EXECUTED → payout EXECUTED.
    int delivered = dispatcher.deliverDue();
    assertThat(delivered).isEqualTo(1);
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);
  }

  @Test
  void aReDeliveredWebhookDoesNotDoubleConfirm() {
    // BR3: idempotency — delivering the same signed callback twice confirms exactly once.
    PayoutView created = createAgentCommission(new BigDecimal("405.00"));
    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);

    dispatcher.deliverDue(); // first delivery confirms
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);

    // Reset the job's delivered flag to force a re-delivery of the very same webhook.
    jdbcTemplate.execute("UPDATE mock_payout_jobs SET delivered = false");
    int redelivered = dispatcher.deliverDue();
    assertThat(redelivered).isEqualTo(1); // the dispatcher re-sent it

    // …but the receiver treated it as a duplicate: still exactly ONE processed-webhook row, and the
    // payout is still EXECUTED (not double-confirmed, not double-paid).
    Integer processed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_payout_webhooks WHERE payout_id = ?",
            Integer.class,
            created.id());
    assertThat(processed).isEqualTo(1);
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);
  }

  @Test
  void aFailedPaymentLeavesAnExplicitFailedStateNeverAFalsePaid() {
    // BR2: a FAILED outcome lands FAILED — no false "executed".
    PayoutView created = createAgentCommission(new BigDecimal("405.00"));
    executionService.execute(created.id(), PaymentOutcome.FAILED);

    dispatcher.deliverDue();

    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.FAILED);
  }

  @Test
  void aFailedInstallmentCanBeRetriedAndThenSucceed() {
    // BR3: retries are safe — a FAILED installment can be re-executed and confirmed.
    PayoutView created = createAgentCommission(new BigDecimal("405.00"));
    executionService.execute(created.id(), PaymentOutcome.FAILED);
    dispatcher.deliverDue();
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.FAILED);

    // Retry with a successful outcome.
    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    dispatcher.deliverDue();
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);
  }

  private PayoutView createAgentCommission(BigDecimal amount) {
    return payoutService.create(
        new CreatePayoutCommand(
            PayoutKind.AGENT_COMMISSION,
            new Payee("ag-1", PayeeType.AGENT),
            null,
            null,
            Money.of(amount, "BRL"),
            null,
            null,
            null,
            null),
        "dev");
  }
}
