package com.fksoft.domain.portfolio;

import com.fksoft.domain.ModuleInternal;
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
 * Represented brand aggregate root (SPEC-0020 BR1): a brand/supplier the Acme represents
 * commercially, identified by a unique {@code brandRef} (value), with a display name and an
 * ACTIVE/INACTIVE status. It does <strong>not</strong> price or compute commission (BR6) — it is
 * the reference "which brand" that Quoting/Commissioning/DSS group by. Module-internal.
 */
@Entity
@Table(name = "represented_brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class RepresentedBrand {

  @Id private UUID id;

  private String brandRef;
  private String displayName;

  @Enumerated(EnumType.STRING)
  private BrandStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new brand in {@link BrandStatus#ACTIVE} (BR1).
   *
   * @param brandRef the unique brand/supplier identifier (required)
   * @param displayName the human-readable name (required)
   * @param now the creation instant (UTC)
   * @param actor who registers it (audit)
   * @return a new, persistable ACTIVE brand
   * @throws BrandInvalidException when brandRef or displayName is missing (BR1)
   */
  public static RepresentedBrand register(
      String brandRef, String displayName, Instant now, String actor) {
    if (brandRef == null || brandRef.isBlank() || displayName == null || displayName.isBlank()) {
      throw new BrandInvalidException();
    }
    RepresentedBrand brand = new RepresentedBrand();
    brand.id = UUID.randomUUID();
    brand.brandRef = brandRef.trim();
    brand.displayName = displayName.trim();
    brand.status = BrandStatus.ACTIVE;
    brand.createdAt = now;
    brand.updatedAt = now;
    brand.createdBy = actor;
    brand.updatedBy = actor;
    return brand;
  }

  /** Deactivates the brand (BR5: audited). Idempotent: staying INACTIVE on a re-call. */
  public void deactivate(Instant now, String actor) {
    this.status = BrandStatus.INACTIVE;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /** The brand id. */
  public UUID id() {
    return id;
  }

  /** The unique brand/supplier identifier (value). */
  public String brandRef() {
    return brandRef;
  }

  /** The current status. */
  public BrandStatus status() {
    return status;
  }

  /** Projects the aggregate to its public read view. */
  public BrandView toView() {
    return new BrandView(id, brandRef, displayName, status, createdAt, updatedAt);
  }
}
