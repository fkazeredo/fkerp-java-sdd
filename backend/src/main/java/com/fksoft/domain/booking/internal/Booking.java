package com.fksoft.domain.booking.internal;

import com.fksoft.domain.booking.BookingStatus;
import com.fksoft.domain.booking.BookingTransitionInvalidException;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.Locator;
import com.fksoft.domain.booking.LocatorOrigin;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Booking aggregate (SPEC-0006): a quote turned into an operational commitment, governed by an
 * explicit state machine ({@link BookingStatus}). A new booking starts {@link
 * BookingStatus#ORDERED} (BR1); {@link #transitionTo} rejects invalid transitions (BR2) and records
 * the timing/audit fields. Module-internal.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

  @Id private UUID id;

  private UUID quoteId;

  private UUID accountId;

  /** Product/supplier scope reference used to resolve the cancellation policy (SPEC-0010 BR1). */
  private String scopeRef;

  @Enumerated(EnumType.STRING)
  private BookingStatus status;

  @Enumerated(EnumType.STRING)
  private LocatorOrigin locatorOrigin;

  private String locatorCode;

  private Instant pendingSince;

  private Instant confirmedAt;

  private String cancelReason;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Creates a booking in {@link BookingStatus#ORDERED} for a quote (its account is copied in).
   *
   * @param quoteId the originating quote id
   * @param accountId the account id copied from the quote
   * @param scopeRef the product/supplier scope reference for the cancellation policy, or {@code
   *     null} when none (then the safe default policy applies — SPEC-0010 BR1)
   * @param locator the locator (internal-generated or external)
   * @param now creation instant (UTC)
   * @param actor who created it (audit)
   * @return a new, persistable booking
   */
  public static Booking create(
      UUID quoteId, UUID accountId, String scopeRef, Locator locator, Instant now, String actor) {
    Booking booking = new Booking();
    booking.id = UUID.randomUUID();
    booking.quoteId = quoteId;
    booking.accountId = accountId;
    booking.scopeRef = scopeRef == null || scopeRef.isBlank() ? null : scopeRef.trim();
    booking.status = BookingStatus.ORDERED;
    booking.locatorOrigin = locator.origin();
    booking.locatorCode = locator.code();
    booking.createdAt = now;
    booking.updatedAt = now;
    booking.createdBy = actor;
    booking.updatedBy = actor;
    return booking;
  }

  /**
   * Transitions to {@code target} if the state machine allows it (BR2), updating the timing fields:
   * {@code pendingSince} on PENDING, {@code confirmedAt} on CONFIRMED, {@code cancelReason} on
   * CANCELLED.
   *
   * @param target the target status
   * @param reason the reason (used on cancellation), or {@code null}
   * @param now the transition instant (UTC)
   * @param actor who performed the transition (audit)
   * @throws BookingTransitionInvalidException when the transition is not allowed (BR2)
   */
  public void transitionTo(BookingStatus target, String reason, Instant now, String actor) {
    if (!status.canTransitionTo(target)) {
      throw new BookingTransitionInvalidException();
    }
    switch (target) {
      case PENDING -> pendingSince = now;
      case CONFIRMED -> confirmedAt = now;
      case CANCELLED -> cancelReason = reason;
      default -> {
        // no extra timing field for the other states
      }
    }
    status = target;
    updatedAt = now;
    updatedBy = actor;
  }

  /** Projects this aggregate to its public read view. */
  public BookingView toView() {
    return new BookingView(
        id,
        quoteId,
        accountId,
        status,
        new Locator(locatorOrigin, locatorCode),
        pendingSince,
        confirmedAt,
        cancelReason,
        createdAt);
  }
}
