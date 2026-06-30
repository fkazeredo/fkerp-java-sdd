package com.fksoft.domain.assets.internal;

import com.fksoft.domain.assets.AssetAlreadyRetiredException;
import com.fksoft.domain.assets.AssetInvalidException;
import com.fksoft.domain.assets.AssetStatus;
import com.fksoft.domain.assets.AssetType;
import com.fksoft.domain.assets.AssetView;
import com.fksoft.domain.assets.LicenseExpiryRequiredException;
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
 * Internal asset aggregate root (SPEC-0021): a piece of the Acme's internal patrimony — equipment,
 * a software license or another good — with a basic lifecycle (ACTIVE → RETIRED) and the value
 * links the rest of the system needs (the acquisition/contract {@code documentId} in the Compliance
 * vault and the cost {@code financeEntryId} in the Finance ledger — both values, never FKs — BR2).
 *
 * <p>It is <strong>patrimony, not a product</strong> (BR5): it never prices a sale nor takes part
 * in the commercial flow. There is no depreciation here (DL-0065). Module-internal.
 */
@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private AssetType type;

  private String identifier;

  @Enumerated(EnumType.STRING)
  private AssetStatus status;

  private LocalDate acquisitionDate;
  private BigDecimal acquisitionCost;
  private String currency;

  private LocalDate expiresAt;
  private String supplierRef;

  private UUID documentId;
  private UUID financeEntryId;

  /** When the expiry alert was raised for this license, for idempotency of the sweep (DL-0066). */
  private Instant expirySignaledAt;

  private Instant retiredAt;
  private String retiredBy;
  private String retirementReason;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new asset in {@link AssetStatus#ACTIVE} (BR1). Validates the mandatory data and the
   * license-specific rule: a {@link AssetType#SOFTWARE_LICENSE} must carry an {@code expiresAt}.
   *
   * @param type the asset type (required)
   * @param identifier the identification/description (required)
   * @param acquisitionDate when it was acquired (required)
   * @param acquisitionCost the acquisition cost (required)
   * @param expiresAt the license expiry date — required for SOFTWARE_LICENSE, optional otherwise
   * @param supplierRef the supplier reference (value), or {@code null}
   * @param documentId the Compliance document id (value), or {@code null}
   * @param financeEntryId the Finance cost entry id (value), or {@code null}
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable ACTIVE asset
   * @throws AssetInvalidException when a mandatory field is missing (BR1)
   * @throws LicenseExpiryRequiredException when a SOFTWARE_LICENSE has no expiresAt (BR1)
   */
  public static Asset register(
      AssetType type,
      String identifier,
      LocalDate acquisitionDate,
      Money acquisitionCost,
      LocalDate expiresAt,
      String supplierRef,
      UUID documentId,
      UUID financeEntryId,
      Instant now,
      String actor) {
    if (type == null
        || identifier == null
        || identifier.isBlank()
        || acquisitionDate == null
        || acquisitionCost == null) {
      throw new AssetInvalidException();
    }
    if (type == AssetType.SOFTWARE_LICENSE && expiresAt == null) {
      throw new LicenseExpiryRequiredException();
    }
    Asset asset = new Asset();
    asset.id = UUID.randomUUID();
    asset.type = type;
    asset.identifier = identifier.trim();
    asset.status = AssetStatus.ACTIVE;
    asset.acquisitionDate = acquisitionDate;
    asset.acquisitionCost = acquisitionCost.amount();
    asset.currency = acquisitionCost.currency();
    asset.expiresAt = expiresAt;
    asset.supplierRef = supplierRef == null || supplierRef.isBlank() ? null : supplierRef.trim();
    asset.documentId = documentId;
    asset.financeEntryId = financeEntryId;
    asset.createdAt = now;
    asset.updatedAt = now;
    asset.createdBy = actor;
    asset.updatedBy = actor;
    return asset;
  }

  /**
   * Retires the asset (BR4), recording who/when/why. {@link AssetStatus#RETIRED} is terminal
   * (DL-0068): retiring an already-retired asset is rejected, preserving the first audit.
   *
   * @param reason the retirement reason (audited)
   * @param now the retirement instant (UTC)
   * @param actor who retires it (audit)
   * @throws AssetAlreadyRetiredException when the asset is already RETIRED (BR4)
   */
  public void retire(String reason, Instant now, String actor) {
    if (status == AssetStatus.RETIRED) {
      throw new AssetAlreadyRetiredException();
    }
    this.status = AssetStatus.RETIRED;
    this.retiredAt = now;
    this.retiredBy = actor;
    this.retirementReason = reason == null || reason.isBlank() ? null : reason.trim();
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /**
   * Whether this asset is a license that is expiring within {@code days} of {@code asOf} (or
   * already past), used by the ad-hoc {@code ?expiringWithinDays} listing (DL-0066). A non-license,
   * an asset with no {@code expiresAt}, or a RETIRED asset never qualifies.
   *
   * @param asOf the reference date
   * @param days the look-ahead window in days
   * @return whether the license expires within the window
   */
  public boolean isLicenseExpiringWithin(LocalDate asOf, int days) {
    if (type != AssetType.SOFTWARE_LICENSE || expiresAt == null || status != AssetStatus.ACTIVE) {
      return false;
    }
    return !expiresAt.isAfter(asOf.plusDays(days));
  }

  /**
   * Raises the expiry alert once for an active license whose {@code expiresAt} is within the
   * warning window of {@code asOf} (DL-0066). Idempotent: a license already signaled returns {@code
   * false} and is left untouched.
   *
   * @param now the evaluation instant (UTC, stored as the signal time)
   * @param asOf the evaluation date (UTC date of {@code now})
   * @param warningDays the warning window in days
   * @return {@code true} when the alert was newly raised (the caller should publish the event)
   */
  public boolean signalExpiringIfDue(Instant now, LocalDate asOf, int warningDays) {
    if (expirySignaledAt != null) {
      return false; // already alerted — idempotent
    }
    if (!isLicenseExpiringWithin(asOf, warningDays)) {
      return false;
    }
    this.expirySignaledAt = now;
    this.updatedAt = now;
    return true;
  }

  /** The asset id. */
  public UUID id() {
    return id;
  }

  /** The asset type. */
  public AssetType type() {
    return type;
  }

  /** The license expiry date, or {@code null} for non-licenses. */
  public LocalDate expiresAt() {
    return expiresAt;
  }

  /** The current status. */
  public AssetStatus status() {
    return status;
  }

  /** Projects the aggregate to its public read view. */
  public AssetView toView() {
    return new AssetView(
        id,
        type,
        identifier,
        status,
        acquisitionDate,
        Money.of(acquisitionCost, currency),
        expiresAt,
        supplierRef,
        documentId,
        financeEntryId,
        retiredAt,
        retiredBy,
        retirementReason,
        createdAt);
  }
}
