package com.fksoft.domain.portfolio.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sale-to-brand attribution intake (SPEC-0020 BR4; DL-0062): links a {@code bookingId} to a {@code
 * brandRef} (both values, never FKs) so the realized projection can group a sale by the represented
 * brand <strong>without changing the sale event</strong>. The carrier of the sale (the agent/portal
 * that knows which brand was sold) registers the link; it is unique per booking (idempotency).
 *
 * <p>The {@code caseId} is filled when {@code ReconciliationCaseOpened} arrives for this booking,
 * so the REVENUE projection — which sees {@code SpreadRealized} carrying only the caseId — can
 * resolve case→booking→brand. Module-internal.
 */
@Entity
@Table(name = "brand_sale_attributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandSaleAttribution {

  @Id private UUID id;

  private UUID bookingId;
  private String brandRef;
  private UUID caseId;
  private Instant attributedAt;

  /**
   * Registers a new sale→brand attribution (DL-0062).
   *
   * @param bookingId the booking (value)
   * @param brandRef the attributed brand (value)
   * @param now the registration instant (UTC)
   * @return a new, persistable attribution
   */
  public static BrandSaleAttribution register(UUID bookingId, String brandRef, Instant now) {
    BrandSaleAttribution attribution = new BrandSaleAttribution();
    attribution.id = UUID.randomUUID();
    attribution.bookingId = bookingId;
    attribution.brandRef = brandRef;
    attribution.attributedAt = now;
    return attribution;
  }

  /**
   * Links the reconciliation case to this attribution (so REVENUE can resolve case→booking→brand).
   * Idempotent: re-linking the same case is a no-op.
   *
   * @param caseId the reconciliation case (value)
   */
  public void linkCase(UUID caseId) {
    this.caseId = caseId;
  }

  /** The booking (value). */
  public UUID bookingId() {
    return bookingId;
  }

  /** The attributed brand (value). */
  public String brandRef() {
    return brandRef;
  }
}
