package com.fksoft.domain.marketing;

import java.util.UUID;

/**
 * The outcome of dispatching a campaign (SPEC-0019 Input/Output Examples): how many recipients were
 * targeted, how many were suppressed for lack of consent (BR2), and how many were actually queued
 * to the newsletter provider (the ones with consent that had not already been sent — BR4
 * idempotency).
 *
 * @param campaignId the campaign id
 * @param targeted the total candidate recipients
 * @param suppressedNoConsent recipients excluded because they had no GRANTED consent (BR2)
 * @param queued recipients newly queued to the provider
 */
public record CampaignSendResult(
    UUID campaignId, int targeted, int suppressedNoConsent, int queued) {}
