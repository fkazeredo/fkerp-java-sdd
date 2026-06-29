package com.fksoft.domain.marketing;

/**
 * Campaign lifecycle status (SPEC-0019). v1 is intentionally minimal: a campaign is DRAFT until it
 * is dispatched, then SENT. Idempotency of the actual per-recipient send is enforced separately by
 * the {@code campaign_sends} unique constraint (BR4), so re-issuing a send never double-mails.
 */
public enum CampaignStatus {

  /** Created but not yet dispatched. */
  DRAFT,

  /** Dispatched at least once (the recipients with consent were queued). */
  SENT
}
