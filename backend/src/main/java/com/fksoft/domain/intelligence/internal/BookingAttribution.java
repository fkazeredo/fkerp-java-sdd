package com.fksoft.domain.intelligence.internal;

import jakarta.persistence.Entity;
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
 * Per-booking correlation record the DSS learns purely from events (DL-0034): it maps a booking to
 * its account (agency) — learned from {@code BookingConfirmed} — and buffers the FX facts keyed by
 * {@code bookingId} ({@code RateSubsidyAccrued.subsidy}, {@code FxPositionClosed.totalGap}).
 * Because event order is not guaranteed, a fact may arrive before or after the {@code
 * BookingConfirmed} mapping, and across several transactions; the buffered totals are rolled into
 * the agency accrual <em>incrementally</em>, tracking what has already been applied so nothing is
 * double-counted and nothing is lost. Module-internal; intelligence NEVER calls a producer to
 * resolve the account (BR2 / consumer-leaf).
 */
@Entity
@Table(name = "intelligence_booking_attribution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingAttribution {

  @Id private UUID bookingId;

  /** The agency/account id, or {@code null} until {@code BookingConfirmed} is consumed. */
  private UUID accountId;

  // running buffered totals (what we have observed from events)
  private BigDecimal pendingSubsidyBrl;
  private BigDecimal pendingGapBrl;
  private boolean positionClosed;

  // what has already been rolled into the agency accrual (incremental application)
  private BigDecimal appliedSubsidyBrl;
  private BigDecimal appliedGapBrl;
  private boolean volumeCounted;

  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  /** Creates a fresh attribution record for a booking (no account linked yet). */
  public static BookingAttribution forBooking(UUID bookingId, Instant now) {
    BookingAttribution attribution = new BookingAttribution();
    attribution.bookingId = bookingId;
    attribution.pendingSubsidyBrl = BigDecimal.ZERO;
    attribution.pendingGapBrl = BigDecimal.ZERO;
    attribution.positionClosed = false;
    attribution.appliedSubsidyBrl = BigDecimal.ZERO;
    attribution.appliedGapBrl = BigDecimal.ZERO;
    attribution.volumeCounted = false;
    attribution.createdAt = now;
    attribution.updatedAt = now;
    return attribution;
  }

  /** Links the agency/account learned from {@code BookingConfirmed}. */
  public void linkAccount(UUID accountId, Instant now) {
    this.accountId = accountId;
    this.updatedAt = now;
  }

  /** Buffers an accrued subsidy (BRL) keyed by booking until the agency is known. */
  public void addSubsidy(BigDecimal subsidyBrl, Instant now) {
    this.pendingSubsidyBrl = this.pendingSubsidyBrl.add(subsidyBrl);
    this.updatedAt = now;
  }

  /** Buffers a closed FX position's total gap (BRL) keyed by booking. */
  public void recordPositionClosed(BigDecimal totalGapBrl, Instant now) {
    this.pendingGapBrl = this.pendingGapBrl.add(totalGapBrl);
    this.positionClosed = true;
    this.updatedAt = now;
  }

  /** Whether the account is known so buffered facts can be rolled into the agency accrual. */
  public boolean mapped() {
    return accountId != null;
  }

  /** The subsidy observed but not yet rolled into the agency accrual (the delta to apply). */
  public BigDecimal unappliedSubsidyBrl() {
    return pendingSubsidyBrl.subtract(appliedSubsidyBrl);
  }

  /** The gap observed but not yet rolled into the agency accrual (the delta to apply). */
  public BigDecimal unappliedGapBrl() {
    return pendingGapBrl.subtract(appliedGapBrl);
  }

  /** Whether the FX position closed and the booking has not yet been counted into volume. */
  public boolean shouldCountVolume() {
    return positionClosed && !volumeCounted;
  }

  /** Records the deltas just rolled into the agency accrual (incremental, no double-count). */
  public void markApplied(boolean countedVolume, Instant now) {
    this.appliedSubsidyBrl = this.pendingSubsidyBrl;
    this.appliedGapBrl = this.pendingGapBrl;
    if (countedVolume) {
      this.volumeCounted = true;
    }
    this.updatedAt = now;
  }
}
