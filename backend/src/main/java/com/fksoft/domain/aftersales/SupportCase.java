package com.fksoft.domain.aftersales;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SupportCase aggregate root (SPEC-0018): an after-sales case (complaint, change, cancellation,
 * refund or info request) referencing a booking by value (never an FK — Modulith). It owns the
 * lifecycle state machine ({@link SupportCaseStatus}), the SLA deadlines (first response and
 * resolution, frozen at open from the governed policy — DL-0052), the SLA breach flag (an alert
 * that never blocks — BR4/DL-0053), the resolution and the linked Payout REFUND id (idempotency
 * guard, BR3/DL-0054), and the accumulated cost-to-serve (BR5/DL-0053). Module-internal.
 *
 * <p>Cost-to-serve is stored as fixed columns rather than a jsonb blob, matching the project
 * posture (Insight/PenaltyWindows — Rule Zero: the shape is small and known, so a jsonb dependency
 * would be overengineering).
 */
@Entity
@Table(name = "support_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class SupportCase {

  private static final String CURRENCY = "BRL";

  @Id private UUID id;

  private String bookingId;

  @Enumerated(EnumType.STRING)
  private SupportCaseType type;

  @Enumerated(EnumType.STRING)
  private SupportCaseStatus status;

  private String summary;

  private Instant openedAt;
  private Instant firstResponseDueAt;
  private Instant dueAt;
  private boolean breached;

  private Instant resolvedAt;

  @Enumerated(EnumType.STRING)
  private CaseResolution resolution;

  private UUID linkedPayoutId;
  private int reopenCount;

  private BigDecimal costHandling;
  private BigDecimal costRefund;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Opens a new case in {@link SupportCaseStatus#OPEN} (BR1). The SLA deadlines are computed by the
   * service from the governed policy and passed in (DL-0052), so the aggregate stays free of the
   * policy lookup.
   *
   * @param bookingId the referenced booking (value, required)
   * @param type the case type (required)
   * @param summary an optional human summary
   * @param firstResponseDueAt the first-response SLA deadline
   * @param dueAt the resolution SLA deadline
   * @param now the open instant (UTC)
   * @param actor who opens it (audit)
   * @return a new, persistable OPEN case
   * @throws SupportCaseInvalidException when the booking reference or type is missing (BR1)
   */
  public static SupportCase open(
      String bookingId,
      SupportCaseType type,
      String summary,
      Instant firstResponseDueAt,
      Instant dueAt,
      Instant now,
      String actor) {
    if (bookingId == null || bookingId.isBlank() || type == null) {
      throw new SupportCaseInvalidException();
    }
    SupportCase supportCase = new SupportCase();
    supportCase.id = UUID.randomUUID();
    supportCase.bookingId = bookingId.trim();
    supportCase.type = type;
    supportCase.status = SupportCaseStatus.OPEN;
    supportCase.summary = blankToNull(summary);
    supportCase.openedAt = now;
    supportCase.firstResponseDueAt = firstResponseDueAt;
    supportCase.dueAt = dueAt;
    supportCase.breached = false;
    supportCase.reopenCount = 0;
    supportCase.costHandling = BigDecimal.ZERO.setScale(2);
    supportCase.costRefund = BigDecimal.ZERO.setScale(2);
    supportCase.createdAt = now;
    supportCase.updatedAt = now;
    supportCase.createdBy = actor;
    supportCase.updatedBy = actor;
    return supportCase;
  }

  /**
   * Applies a lifecycle transition (BR4 Validation Rules). A move back into {@link
   * SupportCaseStatus#IN_PROGRESS} from {@link SupportCaseStatus#RESOLVED} is a reopening: it
   * increments the reopen count (a cost-to-serve signal, BR5).
   *
   * @param target the target status
   * @param now the transition instant (UTC)
   * @param actor who performs it (audit)
   * @throws SupportCaseTransitionInvalidException when the transition is not allowed
   */
  public void transitionTo(SupportCaseStatus target, Instant now, String actor) {
    if (target == null || !status.canTransitionTo(target)) {
      throw new SupportCaseTransitionInvalidException();
    }
    if (status == SupportCaseStatus.RESOLVED && target == SupportCaseStatus.IN_PROGRESS) {
      this.reopenCount++;
      this.resolvedAt = null;
      this.resolution = null;
    }
    this.status = target;
    touch(now, actor);
  }

  /**
   * Resolves the case (→ RESOLVED), recording the resolution and the optional handling cost and
   * linked refund (BR5/DL-0053/DL-0054). The Payout REFUND itself is created by the application
   * service before this is called; this only records the link and freezes the resolution.
   *
   * @param resolution the resolution outcome
   * @param handlingCost the handling effort cost to accrue, or {@code null}
   * @param refundAmount the linked refund amount, or {@code null}
   * @param linkedPayoutId the linked Payout REFUND id, or {@code null}
   * @param now the resolution instant (UTC)
   * @param actor who resolves it (audit)
   * @throws SupportCaseTransitionInvalidException when the case cannot move to RESOLVED
   */
  public void resolve(
      CaseResolution resolution,
      Money handlingCost,
      Money refundAmount,
      UUID linkedPayoutId,
      Instant now,
      String actor) {
    if (resolution == null || !status.canTransitionTo(SupportCaseStatus.RESOLVED)) {
      throw new SupportCaseTransitionInvalidException();
    }
    CostToServe cost = costToServe().accrue(handlingCost);
    if (refundAmount != null) {
      cost = cost.withRefund(refundAmount);
    }
    this.costHandling = cost.handling().amount();
    this.costRefund = cost.refund().amount();
    if (linkedPayoutId != null) {
      this.linkedPayoutId = linkedPayoutId;
    }
    this.resolution = resolution;
    this.resolvedAt = now;
    this.status = SupportCaseStatus.RESOLVED;
    touch(now, actor);
  }

  /**
   * Marks this case as having breached its SLA (BR4/DL-0053): sets the alert flag. Idempotent and
   * non-blocking — it never changes the workflow status. A non-terminal, not-yet-breached case
   * breaches when either of its SLA deadlines has passed:
   *
   * <ul>
   *   <li>the <strong>first-response</strong> deadline, while the case is still {@link
   *       SupportCaseStatus#OPEN} (no agent has picked it up — first response missed);
   *   <li>the <strong>resolution</strong> deadline, for any non-terminal case.
   * </ul>
   *
   * @param now the evaluation instant (UTC)
   * @return {@code true} when this call newly marked the breach (so the event should be published)
   */
  public boolean markBreachedIfDue(Instant now) {
    if (breached || status.isTerminal()) {
      return false;
    }
    Instant effectiveDeadline = effectiveBreachDeadline();
    if (effectiveDeadline == null || !now.isAfter(effectiveDeadline)) {
      return false;
    }
    this.breached = true;
    this.updatedAt = now;
    this.updatedBy = "system";
    return true;
  }

  /**
   * The deadline whose breach matters now (BR4/DL-0053): while the case is still {@code OPEN} the
   * earlier of the first-response and resolution deadlines; once it has been picked up, the
   * resolution deadline. This is also the {@code dueAt} reported on the {@code SlaBreached} alert.
   */
  public Instant effectiveBreachDeadline() {
    if (status == SupportCaseStatus.OPEN
        && firstResponseDueAt != null
        && (dueAt == null || firstResponseDueAt.isBefore(dueAt))) {
      return firstResponseDueAt;
    }
    return dueAt;
  }

  /** Whether this case already has a linked Payout REFUND (idempotency guard, BR3). */
  public boolean hasLinkedRefund() {
    return linkedPayoutId != null;
  }

  /** Records the handling effort cost without resolving (BR5). */
  public void accrueCost(Money handlingCost, Instant now, String actor) {
    CostToServe cost = costToServe().accrue(handlingCost);
    this.costHandling = cost.handling().amount();
    touch(now, actor);
  }

  /** The id of the case. */
  public UUID id() {
    return id;
  }

  /** The current workflow status. */
  public SupportCaseStatus status() {
    return status;
  }

  /** The referenced booking (value). */
  public String bookingId() {
    return bookingId;
  }

  /** The case type. */
  public SupportCaseType type() {
    return type;
  }

  /** The resolution SLA deadline. */
  public Instant dueAt() {
    return dueAt;
  }

  /** Whether the case has breached its SLA (alert flag). */
  public boolean isBreached() {
    return breached;
  }

  /** The current accumulated cost-to-serve. */
  public CostToServe costToServe() {
    return new CostToServe(
        Money.of(costHandling == null ? BigDecimal.ZERO : costHandling, CURRENCY),
        Money.of(costRefund == null ? BigDecimal.ZERO : costRefund, CURRENCY),
        reopenCount);
  }

  private void touch(Instant now, String actor) {
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Projects the aggregate to its public read view. */
  public SupportCaseView toView() {
    return new SupportCaseView(
        id,
        bookingId,
        type,
        status,
        summary,
        openedAt,
        firstResponseDueAt,
        dueAt,
        breached,
        resolvedAt,
        resolution,
        linkedPayoutId,
        reopenCount,
        costToServe().total());
  }
}
