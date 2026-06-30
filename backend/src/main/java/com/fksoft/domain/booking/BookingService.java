package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteDirectory;
import com.fksoft.domain.quoting.QuoteSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the booking module (SPEC-0006/0010): creates bookings from quotes, drives
 * the lifecycle state machine, publishes the lifecycle events, expires stale PENDING bookings
 * (BR4), freezes the cancellation/no-show policy snapshot at confirmation (SPEC-0010 BR1), and
 * materializes the distinct cancellation charges — including the merchant trap (BR5/BR11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

  /** A PENDING booking older than this is auto-cancelled (BR4). */
  public static final Duration PENDING_TIMEOUT = Duration.ofHours(72);

  private static final String PENDING_TIMEOUT_REASON = "PENDING_TIMEOUT";

  private final BookingRepository repository;
  private final CancellationPolicySourceRepository policySourceRepository;
  private final BookingCancellationSnapshotRepository snapshotRepository;
  private final CancellationChargeRepository chargeRepository;
  private final QuoteDirectory quoteDirectory;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Creates a booking in ORDERED from an existing quote (BR1) with a unique locator (BR3).
   *
   * @param quoteId the originating quote id
   * @param origin the locator origin (INTERNAL generates a code; EXTERNAL uses {@code
   *     externalCode})
   * @param externalCode the operator-typed code when EXTERNAL (non-empty)
   * @param scopeRef the product/supplier scope reference for the cancellation policy, or {@code
   *     null}
   * @param actor who creates it (audit)
   * @return the created booking view
   * @throws BookingQuoteNotFoundException when the quote does not exist (BR1)
   * @throws BookingLocatorDuplicateException when the locator already exists (BR3)
   */
  @Transactional
  public BookingView create(
      UUID quoteId, LocatorOrigin origin, String externalCode, String scopeRef, String actor) {
    QuoteSnapshot quote =
        quoteDirectory.find(quoteId).orElseThrow(BookingQuoteNotFoundException::new);
    Locator locator =
        origin == LocatorOrigin.INTERNAL
            ? Locator.internalGenerated()
            : Locator.external(externalCode);
    if (repository.existsByLocatorOriginAndLocatorCode(locator.origin(), locator.code())) {
      throw new BookingLocatorDuplicateException();
    }
    Booking booking =
        Booking.create(quoteId, quote.accountId(), scopeRef, locator, clock.instant(), actor);
    try {
      repository.saveAndFlush(booking);
    } catch (DataIntegrityViolationException duplicate) {
      throw new BookingLocatorDuplicateException();
    }
    log.info(
        "BookingCreated bookingId={} quoteId={} locator={}/{} scopeRef={}",
        booking.id(),
        quoteId,
        locator.origin(),
        locator.code(),
        booking.scopeRef());
    return booking.toView();
  }

  /**
   * Applies a lifecycle transition (BR2). On CONFIRMED it freezes the cancellation/no-show policy
   * snapshot (SPEC-0010 BR1) and publishes {@code BookingConfirmed}. Cancellation and no-show go
   * through {@link #cancel}/{@link #noShow} (they produce charges); this method is for the other
   * transitions (PENDING/CHANGED/COMPLETED/CONFIRMED).
   *
   * @param bookingId the booking id
   * @param target the target status (not CANCELLED/NO_SHOW — use {@link #cancel}/{@link #noShow})
   * @param reason the reason
   * @param actor who performs the transition (audit)
   * @return the updated booking view
   * @throws BookingNotFoundException when the booking does not exist
   * @throws BookingTransitionInvalidException when the transition is not allowed (BR2)
   */
  @Transactional
  public BookingView transition(UUID bookingId, BookingStatus target, String reason, String actor) {
    Booking booking = repository.findById(bookingId).orElseThrow(BookingNotFoundException::new);
    Instant now = clock.instant();
    booking.transitionTo(target, reason, now, actor);
    repository.save(booking);
    if (target == BookingStatus.CONFIRMED) {
      freezeSnapshot(booking, now);
      events.publishEvent(
          new BookingConfirmed(booking.id(), booking.quoteId(), booking.accountId(), now));
    }
    log.info("BookingTransition bookingId={} to={} performedBy={}", booking.id(), target, actor);
    return booking.toView();
  }

  /**
   * Records a no-show using the booking's frozen no-show policy (SPEC-0010 BR6): charges the fee
   * unless it is waived by proof of a cancelled flight (when {@code waivedIfFlightCancelled} is
   * set). Persists a {@link ChargeKind#NO_SHOW} charge when the fee applies (BR7) and publishes
   * {@code BookingNoShow} and {@code NoShowCharged}. The proof's compliance verification is out of
   * scope (DL-0023): {@code flightCancelledProof} is taken as the fact that proof was provided.
   *
   * @param bookingId the booking id
   * @param flightCancelledProof whether proof of a cancelled flight was provided
   * @param actor who records it (audit)
   * @return the no-show result (fee and whether it was waived)
   * @throws BookingNotFoundException when the booking does not exist
   * @throws BookingTransitionInvalidException when the booking cannot transition to NO_SHOW (BR2)
   */
  @Transactional
  public NoShowResult noShow(UUID bookingId, boolean flightCancelledProof, String actor) {
    Booking booking = repository.findById(bookingId).orElseThrow(BookingNotFoundException::new);
    Instant now = clock.instant();
    booking.transitionTo(BookingStatus.NO_SHOW, null, now, actor);
    repository.save(booking);

    BookingCancellationSnapshot snapshot = snapshotFor(booking, now);
    NoShowPolicy noShow = snapshot.noShowPolicy();
    Money fee = noShow.chargeFor(flightCancelledProof);
    boolean waived = noShow.isWaived(flightCancelledProof);

    Charge charge = null;
    if (fee != null) {
      charge = new Charge(ChargeKind.NO_SHOW, fee, snapshot.policy().costBearer());
      chargeRepository.save(CancellationCharge.of(booking.id(), charge, now, actor));
    }

    events.publishEvent(new BookingNoShow(booking.id(), now));
    events.publishEvent(new NoShowCharged(booking.id(), fee, waived, now));
    log.info(
        "BookingNoShow bookingId={} fee={} waived={} performedBy={}",
        booking.id(),
        fee,
        waived,
        actor);
    return new NoShowResult(booking.id(), booking.status(), charge, waived);
  }

  /**
   * Cancels a booking using its frozen policy snapshot (SPEC-0010), computing the resulting charges
   * as distinct facts that do not net out (BR5/BR11 — the merchant trap), persisting and auditing
   * them (BR7), and publishing {@code CancellationCharged} (and {@code MerchantObligationIncurred}
   * for ALL_SALES_FINAL).
   *
   * @param bookingId the booking id
   * @param reason the cancellation reason (audited)
   * @param serviceStartsAt when the booked service starts (UTC) — the penalty-window base (BR2)
   * @param refundAmount a commercial refund to the customer, or {@code null} when none
   * @param actor who cancels it (audit)
   * @return the cancellation result with the charges
   * @throws BookingNotFoundException when the booking does not exist
   * @throws BookingTransitionInvalidException when the booking cannot be cancelled (BR2)
   */
  @Transactional
  public CancellationResult cancel(
      UUID bookingId, String reason, Instant serviceStartsAt, Money refundAmount, String actor) {
    Booking booking = repository.findById(bookingId).orElseThrow(BookingNotFoundException::new);
    Instant now = clock.instant();
    booking.transitionTo(BookingStatus.CANCELLED, reason, now, actor);
    repository.save(booking);

    BookingCancellationSnapshot snapshot = snapshotFor(booking, now);
    CancellationPolicy policy = snapshot.policy();
    long hoursUntilService =
        serviceStartsAt == null ? 0 : Math.max(0, ChronoUnit.HOURS.between(now, serviceStartsAt));

    List<Charge> charges =
        CancellationCharges.compute(
            policy, hoursUntilService, snapshot.sale(), snapshot.supplierCost(), refundAmount);
    for (Charge charge : charges) {
      chargeRepository.save(CancellationCharge.of(booking.id(), charge, now, actor));
    }

    events.publishEvent(new BookingCancelled(booking.id(), reason, now));
    events.publishEvent(new CancellationCharged(booking.id(), charges, policy.type(), now));
    charges.stream()
        .filter(c -> c.kind() == ChargeKind.SUPPLIER)
        .findFirst()
        .ifPresent(
            supplier ->
                events.publishEvent(new MerchantObligationIncurred(booking.id(), supplier, now)));

    log.info(
        "BookingCancelled bookingId={} policyType={} charges={} performedBy={}",
        booking.id(),
        policy.type(),
        charges.size(),
        actor);
    return new CancellationResult(booking.id(), booking.status(), policy.type(), charges);
  }

  /**
   * Fetches a booking by id.
   *
   * @throws BookingNotFoundException when the booking does not exist
   */
  @Transactional(readOnly = true)
  public BookingView getById(UUID bookingId) {
    return repository
        .findById(bookingId)
        .map(Booking::toView)
        .orElseThrow(BookingNotFoundException::new);
  }

  /** Lists bookings with optional status and account filters. */
  @Transactional(readOnly = true)
  public Page<BookingView> list(BookingStatus status, UUID accountId, Pageable pageable) {
    return repository.search(status, accountId, pageable).map(Booking::toView);
  }

  /**
   * Cancels every booking that has been PENDING since at or before {@code cutoff} (BR4), publishing
   * {@code BookingCancelled} with reason {@code PENDING_TIMEOUT}. A PENDING booking has not been
   * confirmed, so it has no frozen policy and no charges (a free timeout cancellation). Idempotent:
   * a second run finds none (they are no longer PENDING).
   *
   * @param cutoff the timeout boundary (typically now minus 72h)
   * @return how many bookings were expired
   */
  @Transactional
  public int expirePendingBookings(Instant cutoff) {
    List<Booking> expired =
        repository.findByStatusAndPendingSinceLessThanEqual(BookingStatus.PENDING, cutoff);
    Instant now = clock.instant();
    for (Booking booking : expired) {
      booking.transitionTo(BookingStatus.CANCELLED, PENDING_TIMEOUT_REASON, now, "system");
      repository.save(booking);
      events.publishEvent(new BookingCancelled(booking.id(), PENDING_TIMEOUT_REASON, now));
      log.info("BookingPendingTimeout bookingId={}", booking.id());
    }
    return expired.size();
  }

  /**
   * Freezes the cancellation/no-show policy and the reference amounts at the FIRST confirmation
   * (BR1). Idempotent: a re-confirmation (e.g. CHANGED -> CONFIRMED) keeps the original snapshot,
   * so a later policy edit never alters an already-confirmed booking.
   */
  private void freezeSnapshot(Booking booking, Instant now) {
    if (snapshotRepository.existsById(booking.id())) {
      return;
    }
    CancellationPolicy policy = resolvePolicy(booking.scopeRef());
    NoShowPolicy noShow = resolveNoShow(booking.scopeRef());
    QuoteSnapshot quote =
        quoteDirectory.find(booking.quoteId()).orElseThrow(BookingQuoteNotFoundException::new);
    snapshotRepository.save(
        BookingCancellationSnapshot.freeze(
            booking.id(), policy, noShow, quote.baseConverted(), quote.basePrice(), now));
  }

  /**
   * The snapshot governing a cancellation. Normally it was frozen at confirmation (BR1); for a
   * booking cancelled without ever being confirmed (defensive), it falls back to the safe default.
   */
  private BookingCancellationSnapshot snapshotFor(Booking booking, Instant now) {
    return snapshotRepository
        .findById(booking.id())
        .orElseGet(
            () -> {
              QuoteSnapshot quote =
                  quoteDirectory
                      .find(booking.quoteId())
                      .orElseThrow(BookingQuoteNotFoundException::new);
              return BookingCancellationSnapshot.freeze(
                  booking.id(),
                  CancellationPolicy.standardNoWindows(),
                  NoShowPolicy.none(),
                  quote.baseConverted(),
                  quote.basePrice(),
                  now);
            });
  }

  private CancellationPolicy resolvePolicy(String scopeRef) {
    if (scopeRef == null) {
      return CancellationPolicy.standardNoWindows();
    }
    return policySourceRepository
        .findByScopeRef(scopeRef)
        .map(CancellationPolicySource::toPolicy)
        .orElseGet(CancellationPolicy::standardNoWindows);
  }

  private NoShowPolicy resolveNoShow(String scopeRef) {
    if (scopeRef == null) {
      return NoShowPolicy.none();
    }
    return policySourceRepository
        .findByScopeRef(scopeRef)
        .map(CancellationPolicySource::toNoShowPolicy)
        .orElseGet(NoShowPolicy::none);
  }
}
