package com.fksoft.domain.payout.internal;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.InstallmentPlan;
import com.fksoft.domain.payout.InstallmentView;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeType;
import com.fksoft.domain.payout.PayoutAmountInvalidException;
import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutRefundOriginRequiredException;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Payout aggregate root (SPEC-0017): a financial outflow — an agent commission repass, a supplier
 * settlement (possibly in foreign currency, with a {@code settlementRate} and a BRL baixa) or a
 * customer refund (which must reference its origin obligation, BR7). It owns its installment plan
 * (BR6); the payout is only {@code EXECUTED} when every installment is EXECUTED, and {@code FAILED}
 * if any installment fails (BR2 — no false "paid"). Money is scale-2 HALF_UP per currency; the
 * settlement rate is scale 6 (BR1, DL-0049). Module-internal.
 *
 * <p>The aggregate references other contexts only by value ({@code bookingId}, {@code originRef},
 * the {@link Payee} id) — never an FK (Modulith). Execution is driven by the application service
 * under a pessimistic lock (BR2); the webhook outcome confirms/fails an installment idempotently.
 */
@Entity
@Table(name = "payouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payout {

  private static final int RATE_SCALE = 6;

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private PayoutKind kind;

  private String payeeId;

  @Enumerated(EnumType.STRING)
  private PayeeType payeeType;

  private String bookingId;
  private String originRef;

  private BigDecimal amount;
  private String currency;

  private BigDecimal settlementRate;
  private BigDecimal settledBrl;

  @Enumerated(EnumType.STRING)
  private PayoutStatus status;

  private UUID proofDocumentId;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "payout_id")
  @OrderBy("seq ASC")
  private List<PayoutInstallment> installments = new ArrayList<>();

  /**
   * Opens a PENDING payout from a create command (BR1/BR6/BR7). Validates the amount, the foreign
   * settlement rate, the refund origin and the installment plan, then builds the installments (an
   * implicit single one when no plan is given, DL-0050).
   *
   * @param command the create command
   * @param now creation instant (UTC)
   * @param actor who creates it (audit)
   * @return a new, persistable PENDING payout
   * @throws PayoutAmountInvalidException when the amount/rate/plan is invalid (BR1/BR6)
   * @throws PayoutRefundOriginRequiredException when a REFUND has no origin (BR7)
   */
  public static Payout open(CreatePayoutCommand command, Instant now, String actor) {
    Money amount = command.amount();
    if (amount == null || amount.amount().signum() <= 0) {
      throw new PayoutAmountInvalidException();
    }
    if (command.kind() == PayoutKind.REFUND
        && (command.originRef() == null || command.originRef().isBlank())) {
      throw new PayoutRefundOriginRequiredException();
    }

    Payout payout = new Payout();
    payout.id = UUID.randomUUID();
    payout.kind = command.kind();
    Payee payee = command.payee();
    payout.payeeId = payee.id();
    payout.payeeType = payee.type();
    payout.bookingId = blankToNull(command.bookingId());
    payout.originRef = blankToNull(command.originRef());
    payout.amount = amount.amount();
    payout.currency = amount.currency();
    payout.applySettlementRate(command.settlementRate(), amount);
    payout.status = PayoutStatus.PENDING;
    payout.createdAt = now;
    payout.updatedAt = now;
    payout.createdBy = actor;
    payout.updatedBy = actor;

    InstallmentPlan plan = resolvePlan(command, amount, now);
    int seq = 1;
    for (int i = 0; i < plan.amounts().size(); i++) {
      payout.installments.add(
          PayoutInstallment.create(
              payout.id, seq++, plan.dueDates().get(i), plan.amounts().get(i)));
    }
    return payout;
  }

  private void applySettlementRate(BigDecimal rate, Money amount) {
    if (rate == null) {
      return;
    }
    if (rate.signum() <= 0) {
      throw new PayoutAmountInvalidException();
    }
    this.settlementRate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    // BRL baixa = amount × rate, normalized to scale 2 HALF_UP (DL-0049).
    this.settledBrl =
        amount.amount().multiply(this.settlementRate).setScale(2, RoundingMode.HALF_UP);
  }

  private static InstallmentPlan resolvePlan(
      CreatePayoutCommand command, Money amount, Instant now) {
    List<LocalDate> dueDates = command.installmentDueDates();
    if (command.installmentAmounts() != null && !command.installmentAmounts().isEmpty()) {
      // Explicit plan: validate it sums exactly to the total (DL-0050).
      List<LocalDate> dates = ensureDueDates(dueDates, command.installmentAmounts().size(), now);
      return InstallmentPlan.explicit(amount, dates, command.installmentAmounts());
    }
    int count =
        command.installmentCount() == null || command.installmentCount() < 1
            ? 1
            : command.installmentCount();
    List<LocalDate> dates = ensureDueDates(dueDates, count, now);
    return InstallmentPlan.split(amount, dates);
  }

  private static List<LocalDate> ensureDueDates(List<LocalDate> dueDates, int count, Instant now) {
    if (dueDates != null && !dueDates.isEmpty()) {
      if (dueDates.size() != count) {
        throw new PayoutAmountInvalidException();
      }
      return dueDates;
    }
    // Default schedule: monthly, first due today (DL-0050 — schedule is configuration, not policy).
    LocalDate base = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    List<LocalDate> generated = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      generated.add(base.plusMonths(i));
    }
    return generated;
  }

  /** The id of the payout. */
  public UUID id() {
    return id;
  }

  /** The current aggregate status. */
  public PayoutStatus status() {
    return status;
  }

  /** The payout kind. */
  public PayoutKind kind() {
    return kind;
  }

  /** The related booking id (value), or {@code null}. */
  public String bookingId() {
    return bookingId;
  }

  /** The origin obligation reference (value), or {@code null}. */
  public String originRef() {
    return originRef;
  }

  /** The payee id (value). */
  public String payeeId() {
    return payeeId;
  }

  /** The settlement rate applied (scale 6), or {@code null} when BRL-native. */
  public BigDecimal settlementRate() {
    return settlementRate;
  }

  /** The BRL settled amount (the baixa Finance posts), or {@code null} when BRL-native. */
  public Money settledBrl() {
    return settledBrl == null ? null : Money.of(settledBrl, "BRL");
  }

  /** The amount in its original currency. */
  public Money amount() {
    return Money.of(amount, currency);
  }

  private Optional<PayoutInstallment> nextExecutable() {
    return installments.stream()
        .sorted(Comparator.comparingInt(PayoutInstallment::seq))
        .filter(i -> i.status() == PayoutStatus.PENDING || i.status() == PayoutStatus.FAILED)
        .findFirst();
  }

  private Optional<PayoutInstallment> installmentBySeq(int seq) {
    return installments.stream().filter(i -> i.seq() == seq).findFirst();
  }

  /** Whether every installment is EXECUTED (so the payout as a whole is settled, BR6). */
  public boolean allInstallmentsExecuted() {
    return installments.stream().allMatch(PayoutInstallment::isExecuted);
  }

  /**
   * Moves the next executable installment to EXECUTING (PENDING/FAILED → EXECUTING, BR2) and
   * returns what the gateway must pay (its sequence and amount). The aggregate status is
   * recomputed.
   *
   * @param now the transition instant (UTC)
   * @param actor who triggers it (audit)
   * @return the installment to execute (sequence + amount)
   * @throws com.fksoft.domain.payout.PayoutAlreadyExecutedException when none is executable (BR3)
   */
  public com.fksoft.domain.payout.PayoutService.InstallmentToExecute beginNextExecution(
      Instant now, String actor) {
    PayoutInstallment installment =
        nextExecutable().orElseThrow(com.fksoft.domain.payout.PayoutAlreadyExecutedException::new);
    installment.beginExecuting();
    recomputeStatus(now, actor);
    return new com.fksoft.domain.payout.PayoutService.InstallmentToExecute(
        installment.seq(), installment.amount());
  }

  /**
   * Confirms an installment EXECUTED with its receipt (EXECUTING → EXECUTED, BR2/BR4),
   * idempotently: an already-EXECUTED installment is a no-op (BR3). Recomputes the aggregate status
   * and returns it.
   *
   * @param seq the installment sequence
   * @param proofDocumentId the archived receipt document id, or {@code null}
   * @param now the transition instant (UTC)
   * @param actor who triggers it (audit)
   * @return the recomputed aggregate status, or empty when the confirmation was a no-op
   *     (idempotent)
   * @throws com.fksoft.domain.payout.PayoutNotFoundException when the sequence does not exist
   */
  public Optional<PayoutStatus> confirmInstallment(
      int seq, UUID proofDocumentId, Instant now, String actor) {
    PayoutInstallment installment =
        installmentBySeq(seq).orElseThrow(com.fksoft.domain.payout.PayoutNotFoundException::new);
    if (installment.isExecuted()) {
      return Optional.empty(); // idempotent no-op (BR3)
    }
    installment.markExecuted(proofDocumentId, now);
    return Optional.of(recomputeStatus(now, actor));
  }

  /**
   * Fails an installment (EXECUTING → FAILED, BR2 — explicit failure, never a false "paid"),
   * idempotently: only an EXECUTING installment fails. Recomputes the aggregate status.
   *
   * @param seq the installment sequence
   * @param now the transition instant (UTC)
   * @param actor who triggers it (audit)
   * @return {@code true} when the installment was failed, {@code false} when it was a no-op
   * @throws com.fksoft.domain.payout.PayoutNotFoundException when the sequence does not exist
   */
  public boolean failInstallment(int seq, Instant now, String actor) {
    PayoutInstallment installment =
        installmentBySeq(seq).orElseThrow(com.fksoft.domain.payout.PayoutNotFoundException::new);
    if (installment.status() != PayoutStatus.EXECUTING) {
      return false; // idempotent — only an EXECUTING installment can fail
    }
    installment.markFailed();
    recomputeStatus(now, actor);
    return true;
  }

  /**
   * Recomputes the aggregate status from the installments (BR2/BR6): EXECUTED only when ALL are
   * EXECUTED (BR6); FAILED if any failed; EXECUTING once execution has started (at least one
   * installment is EXECUTING or already EXECUTED) but is not yet complete; else PENDING (nothing
   * started). Records the single-payout receipt when it is the trivial one-installment plan.
   * Returns the new status.
   *
   * @param now the update instant (UTC)
   * @param actor who triggered the change (audit)
   * @return the recomputed status
   */
  public PayoutStatus recomputeStatus(Instant now, String actor) {
    PayoutStatus next;
    if (allInstallmentsExecuted()) {
      next = PayoutStatus.EXECUTED;
      if (installments.size() == 1) {
        this.proofDocumentId = installments.get(0).toView().proofDocumentId();
      }
    } else if (installments.stream().anyMatch(i -> i.status() == PayoutStatus.FAILED)) {
      next = PayoutStatus.FAILED;
    } else if (installments.stream()
        .anyMatch(
            i -> i.status() == PayoutStatus.EXECUTING || i.status() == PayoutStatus.EXECUTED)) {
      // Execution has started (an installment is in flight or already settled) but not all are
      // done — the payout as a whole is EXECUTING (BR6: EXECUTED needs every installment).
      next = PayoutStatus.EXECUTING;
    } else {
      next = PayoutStatus.PENDING;
    }
    this.status = next;
    this.updatedAt = now;
    this.updatedBy = actor;
    return next;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Projects the aggregate to its public read view. */
  public PayoutView toView() {
    List<InstallmentView> installmentViews =
        installments.stream()
            .sorted(Comparator.comparingInt(PayoutInstallment::seq))
            .map(PayoutInstallment::toView)
            .toList();
    return new PayoutView(
        id,
        kind,
        new Payee(payeeId, payeeType),
        bookingId,
        originRef,
        Money.of(amount, currency),
        settlementRate,
        settledBrl(),
        status,
        proofDocumentId,
        installmentViews,
        createdAt);
  }
}
