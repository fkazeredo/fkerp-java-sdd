package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Attribution aggregate root (SPEC-0019 BR5; DL-0057): links a campaign {@code code} to a {@code
 * bookingId} (both values, never FKs). The link is registered by the Marketing intake; {@code
 * converted} flips to {@code true} when the booking is confirmed (the {@code BookingConfirmed}
 * consumer), at which point a {@code CampaignConverted} is published for the Intelligence. The
 * {@code (campaignCode, bookingId)} pair is unique (idempotency). Module-internal.
 */
@Entity
@Table(name = "attributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Attribution {

  @Id private UUID id;

  private String campaignCode;
  private UUID bookingId;
  private boolean converted;
  private Instant attributedAt;
  private Instant convertedAt;

  /**
   * Registers a new attribution intake (not yet converted).
   *
   * @param campaignCode the campaign's public code (value)
   * @param bookingId the booking (value)
   * @param now the registration instant (UTC)
   * @return a new, persistable attribution
   */
  public static Attribution register(String campaignCode, UUID bookingId, Instant now) {
    Attribution attribution = new Attribution();
    attribution.id = UUID.randomUUID();
    attribution.campaignCode = campaignCode;
    attribution.bookingId = bookingId;
    attribution.converted = false;
    attribution.attributedAt = now;
    return attribution;
  }

  /**
   * Confirms the conversion when the booking is confirmed (DL-0057). Idempotent: a second
   * confirmation is a no-op and returns {@code false} (so the event is published only once).
   *
   * @param now the confirmation instant (UTC)
   * @return {@code true} when this call newly confirmed the conversion
   */
  public boolean confirmConversion(Instant now) {
    if (converted) {
      return false;
    }
    this.converted = true;
    this.convertedAt = now;
    return true;
  }

  /** The attribution id. */
  public UUID id() {
    return id;
  }

  /** The campaign code (value). */
  public String campaignCode() {
    return campaignCode;
  }

  /** The booking (value). */
  public UUID bookingId() {
    return bookingId;
  }

  /** Whether the conversion was confirmed. */
  public boolean isConverted() {
    return converted;
  }

  /** Projects the aggregate to its public read view. */
  public AttributionView toView() {
    return new AttributionView(id, campaignCode, bookingId, converted, attributedAt, convertedAt);
  }
}
