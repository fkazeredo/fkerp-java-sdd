package com.fksoft.domain.admin;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal administrative-contract aggregate root (SPEC-0025 BR2): the contract that sustains a
 * recurring administrative cost — its validity window, recurrence, recurring amount and the
 * contract {@code documentId} already stored in the Compliance vault (value, never an FK to
 * Compliance). The {@code supplierId} is an internal Admin reference (same module).
 * Module-internal.
 *
 * <p>The controlled-clock expiry alert (DL-0087) is idempotent per contract via {@code
 * expirySignaledAt}: a contract approaching {@code validUntil} is flagged once.
 */
@Entity
@Table(name = "admin_contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class AdminContract {

  /** The look-ahead window (days) for the expiry alert (DL-0087) — same as Portfolio/Assets. */
  public static final int EXPIRY_WARNING_DAYS = 30;

  @Id private UUID id;

  private UUID supplierId;

  private LocalDate validFrom;
  private LocalDate validUntil;

  @Enumerated(EnumType.STRING)
  private AdminRecurrence recurrence;

  private BigDecimal amount;
  private String currency;

  private UUID documentId;

  /** When the expiry alert was raised for this contract, for idempotency of the sweep (DL-0087). */
  private Instant expirySignaledAt;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new administrative contract for a supplier (BR2). Validates the validity window
   * ({@code validUntil}, when present, must not precede {@code validFrom}) and binds the optional
   * recurring amount and contract document (value).
   *
   * @param supplierId the supplier the contract covers (required)
   * @param validFrom the start of validity (required)
   * @param validUntil the end of validity, or {@code null} when open-ended
   * @param recurrence the recurring-charge cadence, or {@code null}
   * @param amount the recurring amount (Money), or {@code null}
   * @param documentId the Compliance document id (value), or {@code null}
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable contract
   * @throws AdminContractInvalidException when the validity window is invalid (BR2)
   */
  public static AdminContract register(
      UUID supplierId,
      LocalDate validFrom,
      LocalDate validUntil,
      AdminRecurrence recurrence,
      Money amount,
      UUID documentId,
      Instant now,
      String actor) {
    if (supplierId == null || validFrom == null) {
      throw new AdminContractInvalidException();
    }
    if (validUntil != null && validUntil.isBefore(validFrom)) {
      throw new AdminContractInvalidException();
    }
    AdminContract contract = new AdminContract();
    contract.id = UUID.randomUUID();
    contract.supplierId = supplierId;
    contract.validFrom = validFrom;
    contract.validUntil = validUntil;
    contract.recurrence = recurrence;
    if (amount != null) {
      contract.amount = amount.amount();
      contract.currency = amount.currency();
    }
    contract.documentId = documentId;
    contract.createdAt = now;
    contract.updatedAt = now;
    contract.createdBy = actor;
    contract.updatedBy = actor;
    return contract;
  }

  /**
   * Whether this contract is expiring within {@code days} of {@code asOf} (or already past): it has
   * a {@code validUntil} and that date is not after {@code asOf + days}.
   *
   * @param asOf the reference date
   * @param days the look-ahead window in days
   * @return whether the contract is expiring within the window
   */
  public boolean isExpiringWithin(LocalDate asOf, int days) {
    return validUntil != null && !validUntil.isAfter(asOf.plusDays(days));
  }

  /**
   * Raises the expiry alert once for a contract whose {@code validUntil} is within the warning
   * window of {@code asOf} (DL-0087). Idempotent: a contract already signaled returns {@code false}
   * and is left untouched.
   *
   * @param now the evaluation instant (UTC, stored as the signal time)
   * @param asOf the evaluation date (UTC date of {@code now})
   * @return {@code true} when the alert was newly raised (the caller should publish the event)
   */
  public boolean signalExpiringIfDue(Instant now, LocalDate asOf) {
    if (expirySignaledAt != null) {
      return false; // already alerted — idempotent
    }
    if (!isExpiringWithin(asOf, EXPIRY_WARNING_DAYS)) {
      return false;
    }
    this.expirySignaledAt = now;
    this.updatedAt = now;
    return true;
  }

  /** The contract id. */
  public UUID id() {
    return id;
  }

  /** The supplier the contract covers. */
  public UUID supplierId() {
    return supplierId;
  }

  /** The end of validity, or {@code null} when open-ended. */
  public LocalDate validUntil() {
    return validUntil;
  }

  /** Projects the aggregate to its public read view. */
  public AdminContractView toView() {
    return new AdminContractView(
        id,
        supplierId,
        validFrom,
        validUntil,
        recurrence,
        amount == null ? null : Money.of(amount, currency),
        documentId,
        createdAt);
  }
}
