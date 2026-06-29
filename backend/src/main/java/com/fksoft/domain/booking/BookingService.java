package com.fksoft.domain.booking;

import com.fksoft.domain.booking.internal.Booking;
import com.fksoft.domain.booking.internal.BookingRepository;
import com.fksoft.domain.quoting.QuoteDirectory;
import com.fksoft.domain.quoting.QuoteSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Application service for the booking module (SPEC-0006): creates bookings from quotes, drives the
 * lifecycle state machine, publishes the lifecycle events, and expires stale PENDING bookings
 * (BR4). The quote is validated through the Quoting facade; the account is copied from its snapshot
 * (BR1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

  /** A PENDING booking older than this is auto-cancelled (BR4). */
  public static final Duration PENDING_TIMEOUT = Duration.ofHours(72);

  private static final String PENDING_TIMEOUT_REASON = "PENDING_TIMEOUT";

  private final BookingRepository repository;
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
   * @param actor who creates it (audit)
   * @return the created booking view
   * @throws BookingQuoteNotFoundException when the quote does not exist (BR1)
   * @throws BookingLocatorDuplicateException when the locator already exists (BR3)
   */
  @Transactional
  public BookingView create(UUID quoteId, LocatorOrigin origin, String externalCode, String actor) {
    QuoteSnapshot quote =
        quoteDirectory.find(quoteId).orElseThrow(BookingQuoteNotFoundException::new);
    Locator locator =
        origin == LocatorOrigin.INTERNAL
            ? Locator.internalGenerated()
            : Locator.external(externalCode);
    if (repository.existsByLocatorOriginAndLocatorCode(locator.origin(), locator.code())) {
      throw new BookingLocatorDuplicateException();
    }
    Booking booking = Booking.create(quoteId, quote.accountId(), locator, clock.instant(), actor);
    try {
      repository.saveAndFlush(booking);
    } catch (DataIntegrityViolationException duplicate) {
      throw new BookingLocatorDuplicateException();
    }
    log.info(
        "BookingCreated bookingId={} quoteId={} locator={}/{}",
        booking.id(),
        quoteId,
        locator.origin(),
        locator.code());
    return booking.toView();
  }

  /**
   * Applies a lifecycle transition (BR2) and publishes the matching event for CONFIRMED/CANCELLED/
   * NO_SHOW (BR5). Important transitions are audited (BR6).
   *
   * @param bookingId the booking id
   * @param target the target status
   * @param reason the reason (required by the controller for cancellation)
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
    publishTransition(booking, target, reason, now);
    log.info("BookingTransition bookingId={} to={} performedBy={}", booking.id(), target, actor);
    return booking.toView();
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
   * {@code BookingCancelled} with reason {@code PENDING_TIMEOUT}. Idempotent: a second run finds
   * none (they are no longer PENDING).
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

  private void publishTransition(
      Booking booking, BookingStatus target, String reason, Instant now) {
    switch (target) {
      case CONFIRMED ->
          events.publishEvent(
              new BookingConfirmed(booking.id(), booking.quoteId(), booking.accountId(), now));
      case CANCELLED -> events.publishEvent(new BookingCancelled(booking.id(), reason, now));
      case NO_SHOW -> events.publishEvent(new BookingNoShow(booking.id(), now));
      default -> {
        // other transitions publish no domain event in Phase 1
      }
    }
  }
}
