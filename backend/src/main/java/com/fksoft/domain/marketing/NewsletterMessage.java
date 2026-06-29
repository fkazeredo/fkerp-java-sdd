package com.fksoft.domain.marketing;

import java.util.UUID;

/**
 * The domain-shaped message handed to the {@link NewsletterSender} port (SPEC-0019; DL-0055). It
 * carries only what the ACL needs to dispatch one recipient — the campaign id, the recipient
 * reference (value) and the pointer to the external creative. The provider's own request/response
 * DTOs live in the adapter and never cross into the domain (ArchUnit boundary).
 *
 * @param campaignId the campaign id
 * @param recipientRef the recipient subject id (value)
 * @param contentRef pointer to the external creative, or {@code null}
 */
public record NewsletterMessage(UUID campaignId, String recipientRef, String contentRef) {

  public NewsletterMessage {
    if (campaignId == null || recipientRef == null || recipientRef.isBlank()) {
      throw new CampaignInvalidException();
    }
    recipientRef = recipientRef.trim();
  }
}
