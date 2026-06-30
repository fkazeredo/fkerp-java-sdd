package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single installment of a payout (SPEC-0017 BR6). Each installment executes and is receipted
 * individually; the parent {@link Payout} is only EXECUTED when every installment is EXECUTED.
 * Module-internal. The execution transition is guarded by the {@link PayoutStatus} state machine
 * (BR2) and the whole aggregate is loaded under a pessimistic lock by the service (BR2).
 */
@Entity
@Table(name = "payout_installments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PayoutInstallment {

  @Id private UUID id;

  @Column(name = "payout_id")
  private UUID payoutId;

  private int seq;
  private LocalDate dueDate;
  private BigDecimal amount;
  private String currency;

  @Enumerated(EnumType.STRING)
  private PayoutStatus status;

  private Instant executedAt;
  private UUID proofDocumentId;

  static PayoutInstallment create(UUID payoutId, int seq, LocalDate dueDate, Money amount) {
    PayoutInstallment installment = new PayoutInstallment();
    installment.id = UUID.randomUUID();
    installment.payoutId = payoutId;
    installment.seq = seq;
    installment.dueDate = dueDate;
    installment.amount = amount.amount();
    installment.currency = amount.currency();
    installment.status = PayoutStatus.PENDING;
    return installment;
  }

  UUID id() {
    return id;
  }

  int seq() {
    return seq;
  }

  PayoutStatus status() {
    return status;
  }

  Money amount() {
    return Money.of(amount, currency);
  }

  boolean isExecuted() {
    return status == PayoutStatus.EXECUTED;
  }

  /** Marks this installment EXECUTING (PENDING/FAILED → EXECUTING, BR2). */
  void beginExecuting() {
    transitionTo(PayoutStatus.EXECUTING);
  }

  /** Marks this installment EXECUTED with its receipt (EXECUTING → EXECUTED, BR2/BR4). */
  void markExecuted(UUID proofDocumentId, Instant now) {
    transitionTo(PayoutStatus.EXECUTED);
    this.executedAt = now;
    this.proofDocumentId = proofDocumentId;
  }

  /** Marks this installment FAILED (EXECUTING → FAILED, BR2 — explicit failure, no false paid). */
  void markFailed() {
    transitionTo(PayoutStatus.FAILED);
  }

  private void transitionTo(PayoutStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new com.fksoft.domain.payout.PayoutTransitionInvalidException();
    }
    this.status = target;
  }

  InstallmentView toView() {
    return new InstallmentView(
        id, seq, dueDate, Money.of(amount, currency), status, executedAt, proofDocumentId);
  }
}
