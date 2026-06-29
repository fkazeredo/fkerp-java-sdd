package com.fksoft.infra.integration.payment;

import com.fksoft.domain.payout.PaymentGateway;
import com.fksoft.domain.payout.PaymentInstruction;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutService.InstallmentToExecute;
import com.fksoft.domain.payout.PayoutView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the execution of a payout (SPEC-0017 BR2/BR3/BR4; ADR 0006; DL-0048). It lives in
 * {@code infra.integration.payment} — not in the Payout domain module — so it can wire multiple
 * facades (Payout, the payment ACL and, in slice 8d-3, Compliance) without coupling the leaf Payout
 * module to them (infra → domain is allowed; same pattern as the Phase-8c {@code
 * BillingIssuanceService}).
 *
 * <p>Execution is asynchronous (ADR 0006): {@link #execute} moves the next installment to EXECUTING
 * (under the domain's pessimistic lock) and asks the {@link PaymentGateway} to pay — there is no
 * synchronous "paid". The provider confirms/fails later by webhook, which {@link
 * #onWebhook(WebhookConfirmation)} processes idempotently: success → confirm the installment (and,
 * 8d-3, archive the receipt); failure → fail the installment (explicit FAILED, never a false paid).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutExecutionService {

  private final PayoutService payoutService;
  private final PaymentGateway paymentGateway;

  /**
   * Starts executing the next installment of a payout (BR2/BR3): claims it (PENDING/FAILED →
   * EXECUTING, pessimistic lock) and requests the payment from the gateway. Returns the payout view
   * (still EXECUTING — the final outcome arrives by webhook). Idempotent at the boundary: when
   * every installment is already EXECUTED the domain raises {@code payout.already-executed} (409).
   *
   * @param payoutId the payout id
   * @param outcomeHint the desired mock outcome (test/staging only), or {@code null} for SUCCEEDED
   * @return the payout view after the installment moved to EXECUTING
   */
  @Transactional
  public PayoutView execute(UUID payoutId, PaymentOutcome outcomeHint) {
    InstallmentToExecute toExecute = payoutService.beginInstallmentExecution(payoutId);
    PayoutView payout = payoutService.getById(payoutId);
    paymentGateway.request(
        new PaymentInstruction(
            payoutId, toExecute.seq(), toExecute.amount(), payout.payee().type(), outcomeHint));
    log.info(
        "PayoutExecutionRequested payoutId={} seq={} amount={}",
        payoutId,
        toExecute.seq(),
        toExecute.amount().amount());
    return payoutService.getById(payoutId);
  }

  /**
   * Processes a payment webhook outcome idempotently (BR3): on SUCCEEDED, confirms the installment
   * (8d-3 will archive the receipt first and pass its id); on FAILED, fails the installment. The
   * caller ({@link PaymentWebhookReceiver}) has already verified the signature and recorded the
   * idempotency row, so a re-delivered callback never reaches here twice.
   *
   * @param confirmation the verified webhook outcome
   */
  @Transactional
  public void onWebhook(WebhookConfirmation confirmation) {
    if (confirmation.outcome() == PaymentOutcome.SUCCEEDED) {
      payoutService.confirmInstallment(
          confirmation.payoutId(), confirmation.installmentSeq(), confirmation.proofDocumentId());
    } else {
      payoutService.failInstallment(confirmation.payoutId(), confirmation.installmentSeq());
    }
  }

  /**
   * A verified payment outcome to apply to an installment.
   *
   * @param payoutId the payout id
   * @param installmentSeq the installment sequence
   * @param outcome the terminal outcome
   * @param proofDocumentId the archived receipt id (8d-3), or {@code null}
   */
  public record WebhookConfirmation(
      UUID payoutId, int installmentSeq, PaymentOutcome outcome, UUID proofDocumentId) {}
}
