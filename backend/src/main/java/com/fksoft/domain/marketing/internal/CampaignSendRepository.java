package com.fksoft.domain.marketing.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the {@link CampaignSend} idempotency records (SPEC-0019 BR4). Module-internal. The
 * existence check before enqueuing (a recipient already sent to) plus the composite primary key
 * make a re-issued dispatch never double-mail.
 */
public interface CampaignSendRepository extends JpaRepository<CampaignSend, CampaignSend.Key> {

  /** Whether this recipient was already sent to for this campaign (the BR4 idempotency check). */
  boolean existsByCampaignIdAndRecipientRef(UUID campaignId, String recipientRef);
}
