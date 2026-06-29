package com.fksoft.domain.booking.internal;

import com.fksoft.domain.booking.CancellationPolicy;
import com.fksoft.domain.booking.CancellationType;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.NoShowPolicy;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The cancellation/no-show policy frozen onto a booking at confirmation (SPEC-0010 BR1) — the
 * provenance of the policy that governs this sale, exactly like the quote's frozen financials. The
 * cancellation uses THIS snapshot, never the current administered policy, so a later policy change
 * does not retroactively alter an already-confirmed booking. Also freezes the reference amounts the
 * charge math needs: the customer-paid sale amount and the supplier cost (both from the quote).
 * Module-internal, keyed by {@code bookingId}.
 */
@Entity
@Table(name = "booking_cancellation_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingCancellationSnapshot {

  @Id private UUID bookingId;

  @Enumerated(EnumType.STRING)
  private CancellationType type;

  private String windowsEncoded;

  private boolean refundable;

  @Enumerated(EnumType.STRING)
  private CostBearer costBearer;

  private boolean merchantOfRecord;

  private BigDecimal noShowFeeAmount;
  private String noShowFeeCurrency;
  private boolean waivedIfFlightCancelled;

  // Frozen reference amounts (from the quote) for the charge math.
  private BigDecimal saleAmount; // customer-paid reference (e.g. BRL)
  private String saleCurrency;
  private BigDecimal supplierAmount; // supplier cost reference (e.g. USD)
  private String supplierCurrency;

  private Instant frozenAt;

  /**
   * Freezes a snapshot for a booking at confirmation.
   *
   * @param bookingId the confirmed booking id
   * @param policy the cancellation policy in force at confirmation
   * @param noShow the no-show policy in force at confirmation
   * @param saleAmount the customer-paid reference amount (sale currency)
   * @param supplierAmount the supplier cost reference amount (supplier currency)
   * @param now the confirmation instant (UTC)
   * @return a new, persistable snapshot
   */
  public static BookingCancellationSnapshot freeze(
      UUID bookingId,
      CancellationPolicy policy,
      NoShowPolicy noShow,
      Money saleAmount,
      Money supplierAmount,
      Instant now) {
    BookingCancellationSnapshot s = new BookingCancellationSnapshot();
    s.bookingId = bookingId;
    s.type = policy.type();
    s.windowsEncoded = PenaltyWindowsCodec.encode(policy.windows());
    s.refundable = policy.refundable();
    s.costBearer = policy.costBearer();
    s.merchantOfRecord = policy.merchantOfRecord();
    s.noShowFeeAmount = noShow.fee() == null ? null : noShow.fee().amount();
    s.noShowFeeCurrency = noShow.fee() == null ? null : noShow.fee().currency();
    s.waivedIfFlightCancelled = noShow.waivedIfFlightCancelled();
    s.saleAmount = saleAmount.amount();
    s.saleCurrency = saleAmount.currency();
    s.supplierAmount = supplierAmount.amount();
    s.supplierCurrency = supplierAmount.currency();
    s.frozenAt = now;
    return s;
  }

  /** The frozen cancellation policy value object. */
  public CancellationPolicy policy() {
    return new CancellationPolicy(
        type, PenaltyWindowsCodec.decode(windowsEncoded), refundable, costBearer, merchantOfRecord);
  }

  /** The frozen no-show policy value object. */
  public NoShowPolicy noShowPolicy() {
    Money fee = noShowFeeAmount == null ? null : Money.of(noShowFeeAmount, noShowFeeCurrency);
    return new NoShowPolicy(fee, waivedIfFlightCancelled);
  }

  /** The frozen customer-paid reference amount (penalty/refund base). */
  public Money sale() {
    return Money.of(saleAmount, saleCurrency);
  }

  /** The frozen supplier cost reference amount (ALL_SALES_FINAL supplier charge base). */
  public Money supplierCost() {
    return Money.of(supplierAmount, supplierCurrency);
  }
}
