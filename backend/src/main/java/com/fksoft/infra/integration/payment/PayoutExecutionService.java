package com.fksoft.infra.integration.payment;

import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentTypeCodes;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.PaymentGateway;
import com.fksoft.domain.payout.PaymentInstruction;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutService.InstallmentToExecute;
import com.fksoft.domain.payout.PayoutView;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
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
  private final ComplianceService complianceService;
  private final Clock clock;

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
   * Processes a payment webhook outcome idempotently (BR3/BR4): on SUCCEEDED, archives the receipt
   * (comprovante) in the Compliance vault — PAYMENT_PROOF for a commission/settlement, REFUND_PROOF
   * for a refund (BR4) — then confirms the installment with that document id (which, on the last
   * installment, publishes the kind's business fact so Finance baixar). On FAILED, fails the
   * installment (explicit FAILED, never a false paid). The caller ({@link PaymentWebhookReceiver})
   * has already verified the signature and recorded the idempotency row, so a re-delivered callback
   * never reaches here twice — the receipt is archived once and the payout confirmed once.
   *
   * @param confirmation the verified webhook outcome
   */
  @Transactional
  public void onWebhook(WebhookConfirmation confirmation) {
    if (confirmation.outcome() != PaymentOutcome.SUCCEEDED) {
      payoutService.failInstallment(confirmation.payoutId(), confirmation.installmentSeq());
      return;
    }
    PayoutView payout = payoutService.getById(confirmation.payoutId());
    UUID proofDocumentId = archiveReceipt(payout, confirmation.installmentSeq());
    payoutService.confirmInstallment(
        confirmation.payoutId(), confirmation.installmentSeq(), proofDocumentId);
  }

  /**
   * Archives the payment receipt in the Compliance vault (BR4) via the public facade (infra →
   * compliance is allowed; the leaf Payout module never imports Compliance). The document type is
   * REFUND_PROOF for a refund, else PAYMENT_PROOF. It carries the booking/origin reference so the
   * receipt is traceable; sensitive payment data is never written (SPEC-0017 Error Behavior).
   */
  private UUID archiveReceipt(PayoutView payout, int installmentSeq) {
    String type =
        payout.kind() == PayoutKind.REFUND
            ? DocumentTypeCodes.REFUND_PROOF
            : DocumentTypeCodes.PAYMENT_PROOF;
    Money paid = installmentAmount(payout, installmentSeq);
    String receipt = receiptText(payout, installmentSeq, paid);
    DocumentView document =
        complianceService.upload(
            type,
            receipt.getBytes(StandardCharsets.UTF_8),
            "comprovante-payout-" + payout.id() + "-" + installmentSeq + ".txt",
            "text/plain",
            LocalDate.now(clock),
            null, // a payment receipt is not a signed fiscal artifact
            false, // no personal data in the receipt body (only ids/amounts)
            null, // not attached to a Finance entry here — Finance posts via the event
            null,
            "payout-gateway");
    log.info(
        "PayoutReceiptArchived payoutId={} seq={} documentId={} type={}",
        payout.id(),
        installmentSeq,
        document.id(),
        type);
    return document.id();
  }

  private static Money installmentAmount(PayoutView payout, int installmentSeq) {
    return payout.installments().stream()
        .filter(i -> i.seq() == installmentSeq)
        .map(com.fksoft.domain.payout.InstallmentView::amount)
        .findFirst()
        .orElse(payout.amount());
  }

  private static String receiptText(PayoutView payout, int installmentSeq, Money paid) {
    return "PAYMENT RECEIPT\npayoutId="
        + payout.id()
        + "\nkind="
        + payout.kind()
        + "\ninstallmentSeq="
        + installmentSeq
        + "\namount="
        + paid.amount()
        + " "
        + paid.currency()
        + (payout.bookingId() != null ? "\nbookingId=" + payout.bookingId() : "")
        + (payout.originRef() != null ? "\noriginRef=" + payout.originRef() : "");
  }

  /**
   * A verified payment outcome to apply to an installment.
   *
   * @param payoutId the payout id
   * @param installmentSeq the installment sequence
   * @param outcome the terminal outcome
   * @param proofDocumentId the archived receipt id (filled by this service on success), or {@code
   *     null}
   */
  public record WebhookConfirmation(
      UUID payoutId, int installmentSeq, PaymentOutcome outcome, UUID proofDocumentId) {}
}
