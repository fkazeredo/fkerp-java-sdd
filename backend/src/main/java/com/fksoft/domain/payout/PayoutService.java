package com.fksoft.domain.payout;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Payout module (SPEC-0017): creates payouts (with their installment
 * plan), drives the execution lifecycle under a pessimistic lock (BR2), and confirms/fails an
 * installment idempotently when the payment webhook arrives (BR3). When a payout's installments all
 * execute, it publishes the business fact for the kind ({@link SupplierSettled}/{@link
 * AgentCommissionPaid}/{@link RefundExecuted}) so downstream modules (Finance, …) post idempotently
 * — Payout is a <strong>leaf</strong>: it never calls those modules, the event is the only coupling
 * (DL-0051, acyclic).
 *
 * <p>The cross-module orchestration of execution (requesting the gateway, archiving the receipt in
 * Compliance) lives in {@code infra.integration.payment} (the {@code PayoutExecutionService}),
 * which calls {@link #beginInstallmentExecution} / {@link #confirmInstallment} / {@link
 * #failInstallment} here. This module depends only on the {@code money}/{@code error} kernels and
 * its {@link PaymentGateway} port — never Finance or Compliance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

  private final PayoutRepository payouts;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final CadastroValidator cadastroValidator;

  /**
   * Creates a PENDING payout with its installment plan (BR1/BR6/BR7). For a foreign settlement the
   * BRL baixa is derived from the {@code settlementRate} (DL-0049). Validates the refund origin
   * (BR7) and that an explicit plan sums exactly to the total (DL-0050).
   *
   * @param command the create command
   * @param actor who creates it (audit)
   * @return the created payout view (PENDING)
   * @throws PayoutAmountInvalidException when the amount/rate/plan is invalid (BR1/BR6)
   * @throws PayoutRefundOriginRequiredException when a REFUND has no origin (BR7)
   * @throws com.fksoft.domain.cadastro.CadastroCodeInvalidException when the kind/payee-type code
   *     is unknown/inactive (SPEC-0031 BR3/DL-0118)
   */
  @Transactional
  public PayoutView create(CreatePayoutCommand command, String actor) {
    // Validate the reference codes against the cadastro (SPEC-0031 BR3/DL-0118) before building the
    // aggregate — an unknown/inactive kind or payee type is rejected (422). The wired branching
    // (PayoutKindCodes) then only ever sees a known kind.
    cadastroValidator.validate(CadastroType.PAYOUT_KIND, command.kind());
    cadastroValidator.validate(CadastroType.PAYEE_TYPE, command.payee().type());
    Payout payout = Payout.open(command, clock.instant(), actor);
    payouts.save(payout);
    log.info(
        "PayoutCreated payoutId={} kind={} payee={} amount={} installments={}",
        payout.id(),
        payout.kind(),
        payout.payeeId(),
        payout.amount().amount(),
        payout.toView().installments().size());
    return payout.toView();
  }

  /**
   * Begins executing the next executable installment (PENDING/FAILED → EXECUTING), under a
   * pessimistic lock (BR2). The actual payment request to the gateway is done by the infra
   * orchestrator; this only moves the state so a concurrent execute cannot double-request.
   *
   * @param payoutId the payout id
   * @return the installment's 1-based sequence and amount to pay
   * @throws PayoutNotFoundException when the payout does not exist
   * @throws PayoutAlreadyExecutedException when every installment is already EXECUTED (BR3)
   */
  @Transactional
  public InstallmentToExecute beginInstallmentExecution(UUID payoutId) {
    Payout payout = payouts.findByIdForUpdate(payoutId).orElseThrow(PayoutNotFoundException::new);
    InstallmentToExecute toExecute = payout.beginNextExecution(clock.instant(), "system");
    payouts.save(payout);
    log.info(
        "PayoutInstallmentExecuting payoutId={} seq={} amount={}",
        payoutId,
        toExecute.seq(),
        toExecute.amount().amount());
    return toExecute;
  }

  /**
   * Confirms an installment as EXECUTED with its receipt (EXECUTING → EXECUTED, BR2/BR4),
   * idempotently: a re-delivered confirmation for an already-EXECUTED installment is a no-op (no
   * double event, no double receipt — BR3). Under a pessimistic lock. When this completes the last
   * installment, publishes the kind's business fact (BR5).
   *
   * @param payoutId the payout id
   * @param seq the installment sequence
   * @param proofDocumentId the archived receipt document id (Compliance)
   * @return the updated payout view
   * @throws PayoutNotFoundException when the payout does not exist
   */
  @Transactional
  public PayoutView confirmInstallment(UUID payoutId, int seq, UUID proofDocumentId) {
    Payout payout = payouts.findByIdForUpdate(payoutId).orElseThrow(PayoutNotFoundException::new);
    Instant now = clock.instant();
    var status = payout.confirmInstallment(seq, proofDocumentId, now, "system");
    if (status.isEmpty()) {
      return payout.toView(); // idempotent no-op (BR3) — already confirmed
    }
    payouts.save(payout);
    log.info(
        "PayoutInstallmentExecuted payoutId={} seq={} payoutStatus={}",
        payoutId,
        seq,
        status.get());
    if (status.get() == PayoutStatus.EXECUTED) {
      publishExecuted(payout, now);
    }
    return payout.toView();
  }

  /**
   * Fails an installment (EXECUTING → FAILED, BR2 — explicit failure, never a false "paid"),
   * idempotently. Under a pessimistic lock.
   *
   * @param payoutId the payout id
   * @param seq the installment sequence
   * @return the updated payout view
   * @throws PayoutNotFoundException when the payout does not exist
   */
  @Transactional
  public PayoutView failInstallment(UUID payoutId, int seq) {
    Payout payout = payouts.findByIdForUpdate(payoutId).orElseThrow(PayoutNotFoundException::new);
    boolean failed = payout.failInstallment(seq, clock.instant(), "system");
    if (failed) {
      payouts.save(payout);
      log.info("PayoutInstallmentFailed payoutId={} seq={}", payoutId, seq);
    }
    return payout.toView();
  }

  /** Fetches a payout by id. */
  @Transactional(readOnly = true)
  public PayoutView getById(UUID payoutId) {
    return payouts.findById(payoutId).map(Payout::toView).orElseThrow(PayoutNotFoundException::new);
  }

  /** Lists payouts with optional kind, status and payee filters. */
  @Transactional(readOnly = true)
  public Page<PayoutView> list(String kind, PayoutStatus status, String payee, Pageable pageable) {
    return payouts.search(normalize(kind), status, normalize(payee), pageable).map(Payout::toView);
  }

  private void publishExecuted(Payout payout, Instant now) {
    // Branches on the payout-kind cadastro codes (SPEC-0031 BR5/DL-0118): the wired settlement /
    // repass / refund facts. A kind with no wired fact publishes nothing (dado puro).
    switch (payout.kind()) {
      case PayoutKindCodes.SUPPLIER_SETTLEMENT ->
          events.publishEvent(
              new SupplierSettled(
                  payout.id(),
                  payout.bookingId(),
                  payout.settlementRate(),
                  settledOrAmount(payout),
                  now));
      case PayoutKindCodes.AGENT_COMMISSION ->
          events.publishEvent(
              new AgentCommissionPaid(payout.id(), payout.payeeId(), payout.amount(), now));
      case PayoutKindCodes.REFUND ->
          events.publishEvent(
              new RefundExecuted(payout.id(), payout.originRef(), payout.amount(), now));
      default -> {
        // A new payout kind with no wired business fact — nothing to publish (dado puro).
      }
    }
  }

  /**
   * The BRL baixa for the Finance posting: the settled BRL for a foreign settlement, else the
   * amount.
   */
  private static Money settledOrAmount(Payout payout) {
    return payout.settledBrl() != null ? payout.settledBrl() : payout.amount();
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * The installment the gateway must pay next (its sequence and amount), returned by {@link
   * #beginInstallmentExecution}.
   *
   * @param seq the 1-based installment sequence
   * @param amount the amount to pay
   */
  public record InstallmentToExecute(int seq, Money amount) {}
}
