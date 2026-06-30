package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Records that a campaign was dispatched to one recipient (SPEC-0019 BR4): the {@code (campaignId,
 * recipientRef)} pair is the primary key, so a re-issued send can never double-mail the same
 * recipient — the second insert is a duplicate-key the application skips. Module-internal.
 */
@Entity
@Table(name = "campaign_sends")
@IdClass(CampaignSend.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class CampaignSend {

  @Id private UUID campaignId;

  @Id private String recipientRef;

  private Instant sentAt;
  private String providerRef;

  /**
   * Records a dispatch to one recipient.
   *
   * @param campaignId the campaign id
   * @param recipientRef the recipient subject id (value)
   * @param providerRef the provider's message reference (audit), or {@code null}
   * @param now the dispatch instant (UTC)
   * @return a new, persistable send record
   */
  public static CampaignSend record(
      UUID campaignId, String recipientRef, String providerRef, Instant now) {
    CampaignSend send = new CampaignSend();
    send.campaignId = campaignId;
    send.recipientRef = recipientRef;
    send.providerRef = providerRef;
    send.sentAt = now;
    return send;
  }

  /** Composite primary key of {@link CampaignSend} (the BR4 idempotency guard). */
  @NoArgsConstructor
  @Getter
  public static class Key implements Serializable {

    private UUID campaignId;
    private String recipientRef;

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Key key)) {
        return false;
      }
      return java.util.Objects.equals(campaignId, key.campaignId)
          && java.util.Objects.equals(recipientRef, key.recipientRef);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(campaignId, recipientRef);
    }
  }
}
