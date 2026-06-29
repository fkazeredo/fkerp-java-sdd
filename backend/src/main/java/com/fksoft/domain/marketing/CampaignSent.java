package com.fksoft.domain.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a campaign was dispatched (SPEC-0019 Events). Published in-process; carries only
 * counts (no PII). Useful for metrics/auditing how many recipients were targeted and suppressed.
 *
 * @param campaignId the campaign id
 * @param targeted total candidate recipients
 * @param suppressed recipients suppressed for lack of consent (BR2)
 * @param occurredAt when the dispatch happened
 */
public record CampaignSent(UUID campaignId, int targeted, int suppressed, Instant occurredAt) {}
