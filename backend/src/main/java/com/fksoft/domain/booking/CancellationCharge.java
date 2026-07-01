package com.fksoft.domain.booking;

import com.fksoft.domain.ModuleInternal;
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
 * A persisted cancellation/no-show charge (SPEC-0010 BR5/BR7): one row per obligation. These are
 * distinct facts that NEVER net out (BR11/DL-0024) — each is stored on its own line, with its own
 * currency and cost bearer, and audited (who/when). Module-internal.
 */
@Entity
@Table(name = "cancellation_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class CancellationCharge {

  @Id private UUID id;

  private UUID bookingId;

  /** The charge-kind cadastro code (was {@code ChargeKind}; SPEC-0031/DL-0117). */
  private String kind;

  private BigDecimal amount;
  private String currency;

  @Enumerated(EnumType.STRING)
  private CostBearer costBearer;

  private Instant createdAt;
  private String createdBy;

  /**
   * Persists a charge for a booking.
   *
   * @param bookingId the booking the charge belongs to
   * @param charge the charge value object
   * @param now creation instant (UTC)
   * @param actor who triggered it (audit)
   * @return a new, persistable charge row
   */
  public static CancellationCharge of(UUID bookingId, Charge charge, Instant now, String actor) {
    CancellationCharge c = new CancellationCharge();
    c.id = UUID.randomUUID();
    c.bookingId = bookingId;
    c.kind = charge.kind();
    c.amount = charge.amount().amount();
    c.currency = charge.amount().currency();
    c.costBearer = charge.costBearer();
    c.createdAt = now;
    c.createdBy = actor;
    return c;
  }

  /** The charge value object reconstructed from this row. */
  public Charge toCharge() {
    return new Charge(kind, Money.of(amount, currency), costBearer);
  }
}
