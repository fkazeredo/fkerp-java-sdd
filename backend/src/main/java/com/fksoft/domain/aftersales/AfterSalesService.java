package com.fksoft.domain.aftersales;

import com.fksoft.domain.aftersales.internal.SupportCase;
import com.fksoft.domain.aftersales.internal.SupportCaseRepository;
import com.fksoft.domain.booking.BookingService;
import com.fksoft.domain.commercialpolicy.CommercialPolicyService;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ResolvedParameter;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeType;
import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the AfterSales module (SPEC-0018): opens support cases, drives the
 * lifecycle state machine, resolves a case — <strong>orchestrating</strong> the owner modules (a
 * cancellation goes to {@link BookingService#cancel}, an approved refund to {@link
 * PayoutService#create} as a {@code REFUND}) without ever computing penalties or posting financials
 * itself (BR2/BR6) — and sweeps SLA breaches.
 *
 * <p>The SLA deadlines are <strong>governed parameters</strong> resolved through the {@link
 * CommercialPolicyService} precedence engine (BR1/DL-0052): the keys {@link
 * #SLA_FIRST_RESPONSE_KEY}/{@link #SLA_RESOLUTION_KEY}/{@link #SLA_REFUND_KEY} (hours, NUMBER) so a
 * Directive can change the effective SLA without a deploy. The refund encaminhamento is idempotent
 * (a case keeps its {@code linkedPayoutId}; a second approval does not create a second Payout —
 * BR3/DL-0054), and it never touches the supplier obligation, so the merchant trap stays intact
 * (DL-0024/DL-0051).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSalesService {

  /** Governed SLA key: hours until the first response is due (default 24h, DL-0052). */
  public static final ParameterKey SLA_FIRST_RESPONSE_KEY =
      new ParameterKey("AFTERSALES_SLA_FIRST_RESPONSE");

  /** Governed SLA key: hours until resolution is due, standard cases (default 72h, DL-0052). */
  public static final ParameterKey SLA_RESOLUTION_KEY =
      new ParameterKey("AFTERSALES_SLA_RESOLUTION");

  /**
   * Governed SLA key: hours until resolution is due, cancellation/refund (default 48h, DL-0052).
   */
  public static final ParameterKey SLA_REFUND_KEY = new ParameterKey("AFTERSALES_SLA_REFUND");

  private final SupportCaseRepository repository;
  private final CommercialPolicyService commercialPolicy;
  private final PayoutService payoutService;
  private final BookingService bookingService;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Opens a new case (BR1). The SLA deadlines are derived from the type and the governed policy at
   * open time (DL-0052) and frozen on the case.
   *
   * @param command the open command (booking ref, type, summary)
   * @param actor who opens it (audit)
   * @return the opened case view
   * @throws SupportCaseInvalidException when the booking reference or type is missing (BR1)
   */
  @Transactional
  public SupportCaseView open(OpenCaseCommand command, String actor) {
    if (command == null || command.type() == null) {
      throw new SupportCaseInvalidException();
    }
    Instant now = clock.instant();
    Instant firstResponseDueAt = now.plus(resolveSlaHours(SLA_FIRST_RESPONSE_KEY));
    Instant dueAt = now.plus(resolveResolutionSla(command.type()));

    SupportCase supportCase =
        SupportCase.open(
            command.bookingId(),
            command.type(),
            command.summary(),
            firstResponseDueAt,
            dueAt,
            now,
            actor);
    repository.save(supportCase);

    events.publishEvent(
        new SupportCaseOpened(supportCase.id(), supportCase.bookingId(), supportCase.type(), now));
    log.info(
        "SupportCaseOpened caseId={} bookingId={} type={} dueAt={} openedBy={}",
        supportCase.id(),
        supportCase.bookingId(),
        supportCase.type(),
        dueAt,
        actor);
    return supportCase.toView();
  }

  /**
   * Applies a lifecycle transition (assign/progress/wait/close, BR4). Use {@link #resolve} to move
   * to RESOLVED (it may trigger the owner modules).
   *
   * @param caseId the case id
   * @param target the target status
   * @param actor who performs it (audit)
   * @return the updated case view
   * @throws SupportCaseNotFoundException when the case does not exist
   * @throws SupportCaseTransitionInvalidException when the transition is not allowed
   */
  @Transactional
  public SupportCaseView transition(UUID caseId, SupportCaseStatus target, String actor) {
    SupportCase supportCase =
        repository.findById(caseId).orElseThrow(SupportCaseNotFoundException::new);
    supportCase.transitionTo(target, clock.instant(), actor);
    repository.save(supportCase);
    log.info("SupportCaseTransition caseId={} to={} performedBy={}", caseId, target, actor);
    return supportCase.toView();
  }

  /**
   * Resolves a case (BR2/BR3/BR5/DL-0054), orchestrating the owner modules:
   *
   * <ul>
   *   <li>{@code REFUND_APPROVED} → creates a Payout {@code REFUND} referencing this case as its
   *       origin obligation (BR3), <strong>idempotently</strong>: if the case already has a linked
   *       refund, it raises {@link SupportCaseRefundDuplicateException} (no second Payout). The
   *       refund never cancels the supplier obligation — the merchant trap holds (DL-0024).
   *   <li>{@code CANCEL_APPROVED} → calls {@link BookingService#cancel}, which applies the
   *       SPEC-0010 penalty policy. AfterSales does not change the booking state itself (BR2).
   * </ul>
   *
   * The handling cost and the linked refund amount are accrued into the cost-to-serve (BR5).
   *
   * @param caseId the case id
   * @param command the resolution (outcome, optional refund amount/payee, handling cost, cancel
   *     details)
   * @param actor who resolves it (audit)
   * @return the resolved case view
   * @throws SupportCaseNotFoundException when the case does not exist
   * @throws SupportCaseRefundDuplicateException when a refund was already linked (BR3)
   * @throws SupportCaseTransitionInvalidException when the case cannot move to RESOLVED
   */
  @Transactional
  public SupportCaseView resolve(UUID caseId, ResolveCaseCommand command, String actor) {
    SupportCase supportCase =
        repository.findById(caseId).orElseThrow(SupportCaseNotFoundException::new);
    Instant now = clock.instant();

    UUID linkedPayoutId = null;
    Money refundAmount = null;

    if (command.resolution().triggersRefund()) {
      if (supportCase.hasLinkedRefund()) {
        throw new SupportCaseRefundDuplicateException();
      }
      refundAmount = command.amount();
      linkedPayoutId = triggerRefund(supportCase, refundAmount, actor);
    }

    if (command.resolution().triggersCancellation()) {
      triggerCancellation(supportCase, command, actor, now);
    }

    supportCase.resolve(
        command.resolution(), command.handlingCost(), refundAmount, linkedPayoutId, now, actor);
    repository.save(supportCase);

    events.publishEvent(
        new SupportCaseResolved(
            supportCase.id(),
            supportCase.bookingId(),
            supportCase.type(),
            command.resolution(),
            supportCase.costToServe().total(),
            now));
    log.info(
        "SupportCaseResolved caseId={} resolution={} linkedPayoutId={} costToServe={} resolvedBy={}",
        caseId,
        command.resolution(),
        linkedPayoutId,
        supportCase.costToServe().total().amount(),
        actor);
    return supportCase.toView();
  }

  /**
   * Sweeps SLA breaches (BR4/DL-0053): every non-terminal case whose resolution deadline is before
   * {@code now} and that is not yet flagged is marked breached and a {@link SlaBreached} alert is
   * published. <strong>Non-blocking</strong> — it never changes the workflow status. The evaluation
   * instant is a parameter (controlled clock, like {@code BookingService.expirePendingBookings}),
   * so the rule is deterministically testable.
   *
   * @param now the evaluation instant (UTC)
   * @return how many cases were newly marked breached
   */
  @Transactional
  public int markBreaches(Instant now) {
    List<SupportCase> candidates = repository.findBreachCandidates(now);
    int marked = 0;
    for (SupportCase supportCase : candidates) {
      java.time.Instant deadline = supportCase.effectiveBreachDeadline();
      if (supportCase.markBreachedIfDue(now)) {
        repository.save(supportCase);
        events.publishEvent(new SlaBreached(supportCase.id(), deadline, now));
        log.info("SlaBreached caseId={} dueAt={} detectedAt={}", supportCase.id(), deadline, now);
        marked++;
      }
    }
    return marked;
  }

  /**
   * Fetches a case by id.
   *
   * @throws SupportCaseNotFoundException when the case does not exist
   */
  @Transactional(readOnly = true)
  public SupportCaseView getById(UUID caseId) {
    return repository
        .findById(caseId)
        .map(SupportCase::toView)
        .orElseThrow(SupportCaseNotFoundException::new);
  }

  /** Lists cases with optional type/status/booking/breached filters, paged. */
  @Transactional(readOnly = true)
  public Page<SupportCaseView> list(
      SupportCaseType type,
      SupportCaseStatus status,
      String bookingId,
      Boolean breached,
      Pageable pageable) {
    return repository
        .search(type, status, normalize(bookingId), breached, pageable)
        .map(SupportCase::toView);
  }

  // --- internals ---

  /**
   * Triggers a Payout REFUND for the case (BR3): the case id is the origin obligation reference, so
   * there is never a loose refund. Returns the new payout id to link.
   */
  private UUID triggerRefund(SupportCase supportCase, Money refundAmount, String actor) {
    if (refundAmount == null || refundAmount.amount().signum() <= 0) {
      throw new SupportCaseInvalidException();
    }
    PayoutView payout =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKind.REFUND,
                new Payee(supportCase.bookingId(), PayeeType.CUSTOMER),
                supportCase.bookingId(),
                supportCase.id().toString(),
                refundAmount,
                null,
                null,
                null,
                null),
            actor);
    log.info(
        "AfterSalesRefundTriggered caseId={} payoutId={} amount={}",
        supportCase.id(),
        payout.id(),
        refundAmount.amount());
    return payout.id();
  }

  /**
   * Triggers the Booking cancellation (BR2): AfterSales decides, Booking applies the SPEC-0010
   * penalty policy and changes the reservation state. AfterSales never does it itself.
   */
  private void triggerCancellation(
      SupportCase supportCase, ResolveCaseCommand command, String actor, Instant now) {
    UUID bookingId = UUID.fromString(supportCase.bookingId());
    bookingService.cancel(
        bookingId,
        command.cancellationReason() != null
            ? command.cancellationReason()
            : "AFTERSALES_" + supportCase.id(),
        command.serviceStartsAt(),
        command.amount(),
        actor);
    log.info("AfterSalesCancellationTriggered caseId={} bookingId={}", supportCase.id(), bookingId);
  }

  /**
   * The resolution SLA for a case type: the tighter refund SLA for cancellation/refund (DL-0052).
   */
  private Duration resolveResolutionSla(SupportCaseType type) {
    return resolveSlaHours(type.usesRefundSla() ? SLA_REFUND_KEY : SLA_RESOLUTION_KEY);
  }

  /**
   * Resolves a governed SLA parameter (hours, NUMBER) through the precedence engine (DL-0052) and
   * converts it to a {@link Duration}. Fractional hours are honored by going through minutes, so a
   * value like {@code 1.5} means 90 minutes.
   */
  private Duration resolveSlaHours(ParameterKey key) {
    ResolvedParameter resolved = commercialPolicy.resolve(key, ParameterScope.global());
    long minutes =
        resolved
            .asDecimal()
            .multiply(java.math.BigDecimal.valueOf(60))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact();
    return Duration.ofMinutes(minutes);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
