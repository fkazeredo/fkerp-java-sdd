package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Campaign aggregate root (SPEC-0019): a campaign over a segment (referenced by value), with a
 * unique public attribution {@code code} (the UTM token used by attribution, DL-0057), an optional
 * pointer to the external creative, a send window and a minimal DRAFT→SENT status. Module-internal.
 */
@Entity
@Table(name = "campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Campaign {

  @Id private UUID id;

  private UUID segmentId;
  private String code;
  private String contentRef;
  private LocalDate windowFrom;
  private LocalDate windowTo;

  @Enumerated(EnumType.STRING)
  private CampaignStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Creates a campaign in {@link CampaignStatus#DRAFT}.
   *
   * @param segmentId the targeted segment (value, required)
   * @param code the public attribution code (required)
   * @param contentRef pointer to the external creative, or {@code null}
   * @param windowFrom start of the send window, or {@code null}
   * @param windowTo end of the send window, or {@code null}
   * @param now the creation instant (UTC)
   * @param actor who creates it (audit)
   * @return a new, persistable DRAFT campaign
   * @throws CampaignInvalidException when the segment or code is missing
   */
  public static Campaign create(
      UUID segmentId,
      String code,
      String contentRef,
      LocalDate windowFrom,
      LocalDate windowTo,
      Instant now,
      String actor) {
    if (segmentId == null || code == null || code.isBlank()) {
      throw new CampaignInvalidException();
    }
    Campaign campaign = new Campaign();
    campaign.id = UUID.randomUUID();
    campaign.segmentId = segmentId;
    campaign.code = code.trim();
    campaign.contentRef = blankToNull(contentRef);
    campaign.windowFrom = windowFrom;
    campaign.windowTo = windowTo;
    campaign.status = CampaignStatus.DRAFT;
    campaign.createdAt = now;
    campaign.updatedAt = now;
    campaign.createdBy = actor;
    campaign.updatedBy = actor;
    return campaign;
  }

  /** Marks the campaign dispatched (idempotent: staying SENT on a re-send). */
  public void markSent(Instant now, String actor) {
    this.status = CampaignStatus.SENT;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /** The campaign id. */
  public UUID id() {
    return id;
  }

  /** The targeted segment id (value). */
  public UUID segmentId() {
    return segmentId;
  }

  /** The public attribution code. */
  public String code() {
    return code;
  }

  /** The current status. */
  public CampaignStatus status() {
    return status;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Projects the aggregate to its public read view. */
  public CampaignView toView() {
    return new CampaignView(
        id, segmentId, code, contentRef, windowFrom, windowTo, status, createdAt);
  }
}
